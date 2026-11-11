package com.radaeli.aeronauticsflybyinstruments.integration;

import com.radaeli.aeronauticsflybyinstruments.panel.TablePanel;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsSignalSnapshot;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lua coordinates are 1-based. Modules are installed physically, not created through Lua. */
public final class TablePeripheral implements IPeripheral, PeripheralEvents.Table {
    private final ModularTableBlockEntity table;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public TablePeripheral(ModularTableBlockEntity table) { this.table = table; }
    @Override public String getType() { return "modular_table"; }
    @Override public Object getTarget() { return table; }
    @Override public boolean equals(IPeripheral other) {
        return other instanceof TablePeripheral peripheral && peripheral.table == table;
    }
    @Override public void attach(IComputerAccess computer) {
        computers.add(computer);
    }
    @Override public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getGridSize() {
        return Map.of("columns", PanelGrid.SIZE, "rows", PanelGrid.SIZE);
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getModules() {
        return table.grid().modules().stream().map(TablePeripheral::describe).toList();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getSlot(int column, int row) throws LuaException {
        validateCell(column, row);
        return table.grid().at(column - 1, row - 1).map(TablePeripheral::describe)
                .orElseGet(() -> Map.of("occupied", false, "column", column, "row", row));
    }

    @LuaFunction(mainThread = true)
    public boolean canFit(int column, int row, int width, int height) throws LuaException {
        validateCell(column, row);
        if (width < 1 || height < 1 || width > PanelGrid.SIZE || height > PanelGrid.SIZE) {
            throw new LuaException("Footprint dimensions must be between 1 and 3");
        }
        return table.grid().canFit(column - 1, row - 1, width, height);
    }

    @LuaFunction(mainThread = true)
    public boolean configureModule(int column, int row, String channel, double threshold) throws LuaException {
        validateCell(column, row);
        if (channel == null || channel.isBlank() || channel.length() > 64) throw new LuaException("Channel must contain 1 to 64 characters");
        if (!Double.isFinite(threshold)) throw new LuaException("Threshold must be finite");
        if (table.grid().at(column - 1, row - 1).filter(ModularTableBlockEntity::isLampModule).isEmpty()) {
            throw new LuaException("Only installed lamp modules can be configured");
        }
        return table.configureModule(column - 1, row - 1, channel, threshold);
    }

    @LuaFunction(mainThread = true)
    public boolean setTestSignal(String channel, double value) throws LuaException {
        if (channel == null || channel.isBlank() || channel.length() > 64) throw new LuaException("Channel must contain 1 to 64 characters");
        if (!Double.isFinite(value)) throw new LuaException("Signal must be finite");
        return TablePanel.of(table).setTestSignal(channel, value);
    }

    @LuaFunction(mainThread = true)
    public boolean setLampTestIntensity(int column, int row, int intensity) throws LuaException {
        validateCell(column, row);
        if (intensity < 0 || intensity > 15) throw new LuaException("Lamp intensity must be between 0 and 15");
        if (table.grid().at(column - 1, row - 1).filter(ModularTableBlockEntity::isLampModule).isEmpty()) {
            throw new LuaException("Test intensity is available only for installed lamps");
        }
        return table.setLampTestIntensity(column - 1, row - 1, intensity);
    }

    @LuaFunction(mainThread = true)
    public boolean clearLampTestIntensity(int column, int row) throws LuaException {
        validateCell(column, row);
        if (table.grid().at(column - 1, row - 1).filter(ModularTableBlockEntity::isLampModule).isEmpty()) {
            throw new LuaException("Test intensity is available only for installed lamps");
        }
        return table.clearLampTestIntensity(column - 1, row - 1);
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getOutputs() {
        return table.grid().modules().stream().map(module -> {
            var output = new java.util.HashMap<>(describe(module));
            output.put("active", table.isModuleActive(module));
            output.put("signal", table.testSignal(module.channel()));
            output.put("source", module.outputSource());
            output.put("minimumSeverity", module.minimumSeverity());
            output.put("severity", table.moduleSeverity(module));
            output.put("buzzerVolume", module.buzzerVolume());
            if (ModularTableBlockEntity.isLampModule(module) || module.type().equals("buzzer")) {
                output.put("indication", table.indication(module));
                if (ModularTableBlockEntity.isLampModule(module)) {
                    output.put("color", table.lampColor(module).name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return Map.copyOf(output);
        }).toList();
    }

    @LuaFunction(mainThread = true)
    public List<String> getAvailableChannels() {
        return List.of("gpws.any", "gpws.terrain", "gpws.pull_up", "gpws.sink_rate", "gpws.dont_sink",
                "gpws.too_low", "gpws.bank_angle", "gpws.sensor_fail");
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getPanelStatus() {
        long installed = table.grid().modules().stream().filter(module ->
                ModularTableBlockEntity.isLampModule(module) || module.type().equals("buzzer")).count();
        long active = table.grid().modules().stream().filter(module ->
                (ModularTableBlockEntity.isLampModule(module) || module.type().equals("buzzer"))
                        && table.isModuleActive(module)).count();
        return Map.of("networkStatus", table.alertNetworkStatus().name().toLowerCase(java.util.Locale.ROOT),
                "status", table.panelStatusId(),
                "gpwsAvailable", table.gpwsAvailable(), "muted", table.muted(),
                "installedModules", installed, "activeModules", active);
    }

    @LuaFunction(mainThread = true)
    public boolean configureAlertModule(int column, int row, String source, String channel,
                                        String minimumSeverity) throws LuaException {
        validateCell(column, row);
        int rank = switch (minimumSeverity == null ? "" : minimumSeverity.toLowerCase(java.util.Locale.ROOT)) {
            case "advisory" -> 1; case "caution" -> 2; case "warning" -> 3;
            default -> throw new LuaException("Severity must be advisory, caution, or warning");
        };
        if (channel == null || channel.isBlank() || channel.length() > 64)
            throw new LuaException("Channel must contain 1 to 64 characters");
        if (!(source.equals("gpws") || source.equals("test"))) throw new LuaException("Source must be gpws or test");
        return table.configureAlertModule(column - 1, row - 1, source, channel, rank);
    }

    /** What a lamp or buzzer indicates (plan §5.3): warning, caution, inop or function:&lt;id&gt;. */
    @LuaFunction(mainThread = true)
    public boolean setIndication(int column, int row, String indication) throws LuaException {
        validateCell(column, row);
        return table.setIndication(column - 1, row - 1, indication);
    }

    @LuaFunction(mainThread = true)
    public List<String> getIndications(String moduleType) {
        String type = moduleType == null ? "lamp" : moduleType;
        return com.radaeli.aeronauticsflybyinstruments.panel.LampIndication.choices(type.equals("buzzer") ? "buzzer" : "lamp")
                .stream().filter(com.radaeli.aeronauticsflybyinstruments.panel.LampIndication::available).toList();
    }

    @LuaFunction(mainThread = true)
    public boolean setBuzzerVolume(int column, int row, int volume) throws LuaException {
        validateCell(column, row);
        if (volume < 0 || volume > 100) throw new LuaException("Buzzer volume must be between 0 and 100");
        return table.setBuzzerVolume(column - 1, row - 1, volume);
    }

    @LuaFunction(mainThread = true)
    public int applyBasicSetup() { return TablePanel.of(table).applyBasicSetup(); }

    @LuaFunction(mainThread = true)
    public boolean testPanel() { TablePanel.of(table).testPanel(); return true; }

    @LuaFunction(mainThread = true)
    public boolean setMuted(boolean muted) { TablePanel.of(table).setMuted(muted); return true; }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getFmsWaypoints(int column, int row) throws LuaException {
        validateCell(column, row);
        FmsModuleData data = table.fmsAt(column - 1, row - 1)
                .orElseThrow(() -> new LuaException("There is no FMS module at that cell")).data();
        return data.waypoints().stream().map(waypoint -> describeWaypoint(waypoint,
                data.selectedWaypointId().filter(waypoint.id()::equals).isPresent())).toList();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getFmsSignals(int column, int row) throws LuaException {
        validateCell(column, row);
        var snapshot = table.fmsSignals(column - 1, row - 1);
        if (snapshot == null) throw new LuaException("There is no FMS module at that cell");
        return describeSignals(snapshot);
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getNetworkSignals() {
        if (table.getLevel() == null) return List.of();
        return AvionicsNetworkManager.connectedSignals(table.getLevel(), table.getBlockPos()).stream()
                .map(signal -> {
                    var result = new java.util.HashMap<>(describeReading(signal.channel().reading()));
                    result.put("type", signal.device().type());
                    result.put("x", signal.device().x());
                    result.put("y", signal.device().y());
                    result.put("z", signal.device().z());
                    result.put("channel", signal.channel().name());
                    result.putAll(signal.channel().metadata());
                    return Map.copyOf(result);
                }).toList();
    }

    @LuaFunction(mainThread = true)
    public boolean createWaypoint(int column, int row, String name, double x, double y, double z) throws LuaException {
        validateFmsCoordinates(column, row, x, y, z);
        return table.addFmsWaypoint(column - 1, row - 1, name, x, y, z);
    }

    @LuaFunction(mainThread = true)
    public boolean updateWaypoint(int column, int row, String waypointId, String name,
                                 double x, double y, double z) throws LuaException {
        validateFmsCoordinates(column, row, x, y, z);
        return table.updateFmsWaypoint(column - 1, row - 1, parseId(waypointId), name, x, y, z);
    }

    @LuaFunction(mainThread = true)
    public boolean deleteWaypoint(int column, int row, String waypointId) throws LuaException {
        validateCell(column, row);
        return table.deleteFmsWaypoint(column - 1, row - 1, parseId(waypointId));
    }

    @LuaFunction(mainThread = true)
    public boolean selectWaypoint(int column, int row, String waypointId) throws LuaException {
        validateCell(column, row);
        return table.selectFmsWaypoint(column - 1, row - 1, parseId(waypointId));
    }

    @LuaFunction(mainThread = true)
    public boolean clearWaypointSelection(int column, int row) throws LuaException {
        validateCell(column, row);
        return table.selectFmsWaypoint(column - 1, row - 1, null);
    }

    private static void validateCell(int column, int row) throws LuaException {
        if (column < 1 || column > PanelGrid.SIZE || row < 1 || row > PanelGrid.SIZE) {
            throw new LuaException("Column and row must be between 1 and 3");
        }
    }

    private void validateFmsCoordinates(int column, int row, double x, double y, double z) throws LuaException {
        validateCell(column, row);
        if (table.fmsAt(column - 1, row - 1).isEmpty()) throw new LuaException("There is no FMS module at that cell");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > FmsWaypoint.MAX_COORDINATE || Math.abs(y) > FmsWaypoint.MAX_COORDINATE
                || Math.abs(z) > FmsWaypoint.MAX_COORDINATE) throw new LuaException("Coordinates are outside the supported range");
    }

    private static java.util.UUID parseId(String id) throws LuaException {
        try {
            if (id == null || id.isBlank()) throw new IllegalArgumentException();
            return java.util.UUID.fromString(id);
        }
        catch (IllegalArgumentException invalid) { throw new LuaException("Invalid waypoint id"); }
    }

    public static Map<String, Object> describeWaypoint(FmsWaypoint waypoint, boolean selected) {
        return Map.of("id", waypoint.id().toString(), "name", waypoint.name(), "dimension", waypoint.dimension(),
                "x", waypoint.x(), "y", waypoint.y(), "z", waypoint.z(), "selected", selected);
    }

    public static Map<String, Object> describeSignals(FmsSignalSnapshot snapshot) {
        return Map.of(FmsSignalSnapshot.WAYPOINT_SELECTED, describeReading(snapshot.waypointSelected()),
                FmsSignalSnapshot.DISTANCE_TO_WAYPOINT, describeReading(snapshot.distanceToWaypoint()));
    }

    private static Map<String, Object> describeReading(com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading<?> reading) {
        var result = new java.util.HashMap<String, Object>();
        result.put("status", reading.status().name().toLowerCase(java.util.Locale.ROOT));
        result.put("unit", reading.unit());
        result.put("gameTime", reading.gameTime());
        reading.value().ifPresent(value -> result.put("value", value));
        return Map.copyOf(result);
    }

    public static Map<String, Object> describe(PanelGrid.Module module) {
        return Map.ofEntries(Map.entry("occupied", true), Map.entry("type", module.type()),
                Map.entry("column", module.column() + 1), Map.entry("row", module.row() + 1),
                Map.entry("width", module.width()), Map.entry("height", module.height()),
                Map.entry("channel", module.channel()), Map.entry("threshold", module.threshold()),
                Map.entry("source", module.outputSource()), Map.entry("alertChannel", module.alertChannel()),
                Map.entry("minimumSeverity", module.minimumSeverity()), Map.entry("buzzerVolume", module.buzzerVolume()));
    }

    public void moduleChanged(String action, PanelGrid.Module module) {
        for (var computer : computers) {
            computer.queueEvent("modular_table_changed", computer.getAttachmentName(), action, describe(module));
        }
    }

    public void testSignalChanged(String channel, double value) {
        for (var computer : computers) {
            computer.queueEvent("modular_table_signal_changed", computer.getAttachmentName(), channel, value);
        }
    }

    public void alertOutputsChanged() {
        for (var computer : computers)
            computer.queueEvent("modular_table_alert_outputs_changed", computer.getAttachmentName(),
                    getPanelStatus(), getOutputs());
    }

    public void panelStateChanged(String state, boolean value) {
        for (var computer : computers)
            computer.queueEvent("modular_table_panel_state_changed", computer.getAttachmentName(), state, value);
    }

    public void fmsWaypointChanged(String action, PanelGrid.Module module, FmsWaypoint waypoint, boolean selected) {
        Map<String, Object> waypointData = waypoint == null ? Map.of() : describeWaypoint(waypoint, selected);
        for (var computer : computers) {
            computer.queueEvent("modular_table_fms_waypoint_changed", computer.getAttachmentName(), action,
                    module.column() + 1, module.row() + 1, waypointData);
        }
    }

    public void fmsSignalChanged(ModularTableBlockEntity.FmsModuleAt module, FmsSignalSnapshot previous,
                                 FmsSignalSnapshot snapshot) {
        for (String channel : List.of(FmsSignalSnapshot.WAYPOINT_SELECTED, FmsSignalSnapshot.DISTANCE_TO_WAYPOINT)) {
            var reading = channel.equals(FmsSignalSnapshot.WAYPOINT_SELECTED)
                    ? snapshot.waypointSelected() : snapshot.distanceToWaypoint();
            var oldReading = previous == null ? null : channel.equals(FmsSignalSnapshot.WAYPOINT_SELECTED)
                    ? previous.waypointSelected() : previous.distanceToWaypoint();
            if (oldReading != null && oldReading.status() == reading.status()
                    && oldReading.value().equals(reading.value()) && oldReading.unit().equals(reading.unit())) continue;
            var state = describeReading(reading);
            for (var computer : computers) {
                computer.queueEvent("modular_table_fms_signal_changed", computer.getAttachmentName(),
                        module.column() + 1, module.row() + 1, channel, state);
            }
        }
    }
}
