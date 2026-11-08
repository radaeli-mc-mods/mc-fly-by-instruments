package com.radaeli.aeronauticsflybyinstruments.panel;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsEndpoint;
import com.radaeli.aeronauticsflybyinstruments.integration.TablePeripheral;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsLocation;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleData;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsModuleIndex;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsSignalLogic;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsSignalSnapshot;
import com.radaeli.aeronauticsflybyinstruments.fms.FmsWaypoint;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsDataChannel;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsSignalProvider;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import com.radaeli.aeronauticsflybyinstruments.item.FmsModuleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.sound.PanelAudio;

public final class ModularTableBlockEntity extends BlockEntity implements AvionicsEndpoint, AvionicsSignalProvider {
    private PanelGrid grid = new PanelGrid();
    private final UUID interactionToken = UUID.randomUUID();
    private final Map<Integer, UUID> installationTokens = new HashMap<>();

    public UUID interactionToken() { return interactionToken; }
    public UUID moduleToken(int column, int row) {
        return grid.at(column, row).map(m -> installationTokens.computeIfAbsent(
                cellKey(m.column(), m.row()), ignored -> UUID.randomUUID())).orElse(null);
    }
    private final Map<String, Double> testSignals = new HashMap<>();
    private final Map<Integer, Integer> lampTestIntensities = new HashMap<>();
    private final Map<Integer, FmsModuleData> fmsModules = new HashMap<>();
    /** Settings of each Attitude Indicator, by origin cell; created on first use. */
    private final Map<Integer, AttitudeIndicatorData> attitudeModules = new HashMap<>();
    /** What each Attitude Indicator's clients were last told, and when (game time). Server only, not saved. */
    private final Map<Integer, SentAttitude> attitudeSent = new HashMap<>();
    private final Map<java.util.UUID, FmsSignalSnapshot> fmsSignals = new HashMap<>();
    private long lastFmsSignalUpdate = Long.MIN_VALUE;
    private Map<String, Integer> gpwsSeverities = Map.of();
    private NetworkStatus alertNetworkStatus = NetworkStatus.DISCONNECTED;
    private boolean gpwsAvailable;
    private boolean muted;
    private long testPanelUntil;
    private Map<String, Integer> activeSoundAlerts = Map.of();
    private final TablePeripheral peripheral = new TablePeripheral(this);
    /** Joined-panel cache, owned by TablePanel.of. */
    TablePanel panel;
    int panelVersion = -1;
    private int footprintVersion = -1;

    public ModularTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MODULAR_TABLE_ENTITY.get(), pos, state);
    }

    public PanelGrid grid() { return grid; }
    public TablePeripheral peripheral() { return peripheral; }
    @Override public String avionicsType() { return "modular_table"; }
    public boolean configureModule(int column, int row, String channel, double threshold) {
        if (channel == null || channel.isBlank() || channel.length() > 64 || !Double.isFinite(threshold)) return false;
        if (grid.at(column, row).filter(ModularTableBlockEntity::isLampModule).isEmpty()) return false;
        if (!grid.configureAt(column, row, channel, threshold)) return false;
        var module = grid.at(column, row).orElseThrow();
        changed("configured", module);
        return true;
    }

    public boolean setTestSignal(String channel, double value) {
        if (channel == null || channel.isBlank() || channel.length() > 64 || !Double.isFinite(value)) return false;
        Map<Integer, Integer> previousOutputs = outputLevels();
        testSignals.put(channel, value);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            peripheral.testSignalChanged(channel, value);
            if (!previousOutputs.equals(outputLevels())) peripheral.alertOutputsChanged();
        }
        return true;
    }

    public double testSignal(String channel) { return testSignals.getOrDefault(channel, 0.0); }
    public boolean isModuleActive(PanelGrid.Module module) {
        if (!isLampModule(module) && !module.type().equals("buzzer")) return false;
        if (testPanelUntil > (level == null ? 0 : level.getGameTime())) return true;
        if (module.outputSource().equals("test")) return testSignal(module.channel()) >= module.threshold();
        // Reserved until the Redstone Link mode ships (plan §7): stored, never lit.
        if (module.outputSource().equals(LampIndication.SOURCE_REDSTONE_LINK)) return false;
        if (module.alertChannel().equals(INOP_CHANNEL)) return inopActive();
        return com.radaeli.aeronauticsflybyinstruments.panel.PanelAlertLogic.active(
                gpwsSeverities.getOrDefault(module.alertChannel(), 0), module.minimumSeverity());
    }

    private static final String INOP_CHANNEL = "gpws.sensor_fail";

    /** GPWS INOP: the computer reports INOP, or no computer answers on a complete cable network. */
    private boolean inopActive() {
        return gpwsSeverities.getOrDefault(INOP_CHANNEL, 0) >= 1 || !gpwsAvailable
                || alertNetworkStatus != NetworkStatus.CONNECTED;
    }

    public String indication(PanelGrid.Module module) { return LampIndication.of(module); }

    /** Lens colour now (plan §6): fixed by the indication, or by severity for single-function lamps. */
    public LampIndication.Color lampColor(PanelGrid.Module module) {
        return LampIndication.color(LampIndication.of(module), moduleSeverity(module));
    }

    /** Applies one indication from the screen, the wrench scroll or Lua; false when not offered. */
    public boolean setIndication(int column, int row, String indication) {
        var target = grid.at(column, row);
        if (target.isEmpty() || !(isLampModule(target.get()) || target.get().type().equals("buzzer"))) return false;
        String type = isLampModule(target.get()) ? "lamp" : "buzzer";
        if (!LampIndication.choices(type).contains(indication) || !LampIndication.available(indication)) return false;
        var fields = LampIndication.fields(indication).orElseThrow();
        return configureAlertModule(column, row, fields.source(), fields.alertChannel(), fields.minimumSeverity());
    }

    public int moduleSeverity(PanelGrid.Module module) {
        if (!isModuleActive(module)) return 0;
        if (testPanelUntil > (level == null ? 0 : level.getGameTime())) return 3;
        if (module.outputSource().equals("test")) return 3;
        if (module.alertChannel().equals(INOP_CHANNEL)) return 2;
        return gpwsSeverities.getOrDefault(module.alertChannel(), 0);
    }

    public NetworkStatus alertNetworkStatus() { return alertNetworkStatus; }
    public String alertStatusId() {
        return alertNetworkStatus == NetworkStatus.CONNECTED && !gpwsAvailable
                ? "no_gpws" : alertNetworkStatus.name().toLowerCase(java.util.Locale.ROOT);
    }
    public boolean gpwsAvailable() { return gpwsAvailable; }

    /** Avionics Computer state for the general panel: ok, inop, no_gpws or the cable network status. */
    public String panelStatusId() {
        if (alertNetworkStatus != NetworkStatus.CONNECTED) return alertStatusId();
        if (!gpwsAvailable) return "no_gpws";
        return gpwsSeverities.getOrDefault(INOP_CHANNEL, 0) >= 1 ? "inop" : "ok";
    }
    public boolean muted() { return muted; }

    public boolean configureAlertModule(int column, int row, String source, String channel, int severity) {
        return configureAlertModule(column, row, source, channel, severity, channel, 0.5);
    }

    public boolean configureAlertModule(int column, int row, String source, String channel, int severity,
                                        String localChannel, double localThreshold) {
        var target = grid.at(column, row);
        if (target.isEmpty() || !(isLampModule(target.get()) || target.get().type().equals("buzzer"))) return false;
        PanelGrid.Module previous = target.orElseThrow();
        if (!grid.configureAlertAt(column, row, source, channel, severity, localChannel, localThreshold)) return false;
        if (previous.equals(grid.at(column, row).orElseThrow())) return true;
        changed("configured", grid.at(column, row).orElseThrow());
        return true;
    }

    public boolean setBuzzerVolume(int column, int row, int volume) {
        var previous = grid.at(column, row);
        if (!grid.setBuzzerVolumeAt(column, row, volume)) return false;
        if (previous.equals(grid.at(column, row))) return true;
        changed("volume_changed", grid.at(column, row).orElseThrow());
        return true;
    }

    /** Apply one validated UI draft and publish only one configuration event. */
    public boolean saveAlertDraft(int column, int row, String source, String channel, int severity,
                                  String localChannel, double localThreshold, int volume) {
        var previous = grid.at(column, row).orElse(null);
        if (previous == null || !(isLampModule(previous) || previous.type().equals("buzzer"))
                || volume < 0 || volume > 100) return false;
        if (!grid.configureAlertAt(column, row, source, channel, severity, localChannel, localThreshold)) return false;
        if (previous.type().equals("buzzer")) grid.setBuzzerVolumeAt(column, row, volume);
        var current = grid.at(column, row).orElseThrow();
        if (!previous.equals(current)) changed("configured", current);
        return true;
    }

    /** Drain before dropping, making repeated teardown callbacks harmless. */
    public java.util.List<ItemStack> drainModuleStacks() {
        var stacks = moduleStacks();
        grid = new PanelGrid();
        installationTokens.clear();
        fmsModules.clear();
        attitudeModules.clear();
        attitudeSent.clear();
        fmsSignals.clear();
        lampTestIntensities.clear();
        return stacks;
    }

    public void setMuted(boolean value) {
        if (muted == value) return;
        muted = value;
        if (level != null && !level.isClientSide) {
            syncClient();
            peripheral.panelStateChanged("mute", muted);
        }
    }

    public void testPanel() {
        if (level == null || level.isClientSide) return;
        testPanelUntil = level.getGameTime() + 60;
        syncClient();
        peripheral.panelStateChanged("test", true);
        peripheral.alertOutputsChanged();
    }

    /** Plays one immediate test beep from a specific buzzer without activating the whole panel. */
    public boolean testBuzzer(int column, int row) {
        return grid.at(column, row).filter(m -> m.type().equals("buzzer"))
                .map(m -> testBuzzer(column, row, m.buzzerVolume())).orElse(false);
    }

    public boolean testBuzzer(int column, int row, int draftVolume) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        var module = grid.at(column, row).filter(value -> value.type().equals("buzzer"));
        if (module.isEmpty() || draftVolume <= 0 || draftVolume > 100) return false;
        PanelAudio.play(serverLevel, this, module.get(), 3, draftVolume);
        return true;
    }

    public int applyBasicSetup() {
        int configured = 0;
        for (PanelGrid.Module module : grid.modules()) {
            if (!isLampModule(module) && !module.type().equals("buzzer")) continue;
            if (configureAlertModule(module.column(), module.row(), "gpws", "gpws.any", 2)) configured++;
            if (module.type().equals("buzzer")) setBuzzerVolume(module.column(), module.row(), 100);
        }
        return configured;
    }

    public static boolean isLampModule(PanelGrid.Module module) {
        return module.type().equals("lamp") || module.type().equals("lamp_2x1");
    }

    public int lampRenderIntensity(PanelGrid.Module module) {
        if (testPanelUntil > (level == null ? 0 : level.getGameTime())) return 15;
        int key = cellKey(module.column(), module.row());
        return lampTestIntensities.getOrDefault(key, isModuleActive(module) ? 15 : 0);
    }

    public boolean setLampTestIntensity(int column, int row, int intensity) {
        var module = grid.at(column, row).filter(ModularTableBlockEntity::isLampModule);
        if (intensity < 0 || intensity > 15 || module.isEmpty()) return false;
        var lamp = module.orElseThrow();
        lampTestIntensities.put(cellKey(lamp.column(), lamp.row()), intensity);
        syncLampTestIntensities();
        return true;
    }

    public boolean clearLampTestIntensity(int column, int row) {
        var module = grid.at(column, row).filter(ModularTableBlockEntity::isLampModule);
        if (module.isEmpty()) return false;
        var lamp = module.orElseThrow();
        if (lampTestIntensities.remove(cellKey(lamp.column(), lamp.row())) == null) return false;
        syncLampTestIntensities();
        return true;
    }

    private static int cellKey(int column, int row) {
        return row * PanelGrid.SIZE + column;
    }

    private void syncLampTestIntensities() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean placeLamp(int column, int row) {
        return placeModule(defaultAlertModule("lamp", column, row, 1, 1));
    }

    public boolean placeBuzzer(int column, int row) {
        return placeModule(defaultAlertModule("buzzer", column, row, 1, 1));
    }

    private static PanelGrid.Module defaultAlertModule(String type, int column, int row, int width, int height) {
        return defaultAlertModule(type, column, row, width, height, Direction.NORTH);
    }

    private static PanelGrid.Module defaultAlertModule(String type, int column, int row, int width, int height,
                                                       Direction view) {
        return new PanelGrid.Module(type, column, row, width, height,
                "test", 0.5, "gpws", "gpws.any", 2, 100, view);
    }

    public boolean placeFmsModule(int column, int row, FmsModuleData data) {
        return placeFms(column, row, 2, 3, data, false);
    }

    private boolean placeFms(int column, int row, int width, int height, FmsModuleData data, boolean spanning) {
        if (data == null || width != 2 || height != 3) return false;
        var module = new PanelGrid.Module("fms", column, row, width, height);
        if (!spanning && !grid.canFit(column, row, width, height)) return false;
        FmsModuleData installed = data.copy();
        if (level != null && FmsModuleIndex.hasIdentity(level, installed.id())) {
            installed.replaceIdentity(java.util.UUID.randomUUID());
        }
        if (!(spanning ? grid.placeAnchored(module) : grid.place(module))) return false;
        fmsModules.put(cellKey(column, row), installed);
        sampleFmsSignals(true);
        changed("placed", grid.at(column, row).orElseThrow());
        return true;
    }

    public boolean placeLamp2x1(int column, int row, boolean vertical) {
        var module = vertical
                ? defaultAlertModule("lamp_2x1", column, row, 1, 2)
                : defaultAlertModule("lamp_2x1", column, row, 2, 1);
        return placeModule(module);
    }

    /**
     * Places a module whose top-left cell is in this table and whose footprint may run into the
     * joined tables after it. Callers check the whole footprint with {@link TablePanel#canFit} first.
     */
    public boolean placeSpanning(String type, int column, int row, int width, int height, FmsModuleData fms) {
        return placeSpanning(type, column, row, width, height, fms, Direction.NORTH);
    }

    /** As above; {@code view} is the horizontal direction the player looks when placing (see {@link PanelGrid.Module#view()}). */
    public boolean placeSpanning(String type, int column, int row, int width, int height, FmsModuleData fms,
                                 Direction view) {
        if (type.equals("fms")) return placeFms(column, row, width, height, fms, true);
        return placeModule(defaultAlertModule(type, column, row, width, height, view), true);
    }

    private boolean placeModule(PanelGrid.Module module) {
        return placeModule(module, false);
    }

    private boolean placeModule(PanelGrid.Module module, boolean spanning) {
        if (!(spanning ? grid.placeAnchored(module) : grid.place(module))) return false;
        lampTestIntensities.remove(cellKey(module.column(), module.row()));
        changed("placed", module);
        return true;
    }

    // --- Attitude Indicator ----------------------------------------------------------------

    /** Ticks between samples of the attitude feed (10 Hz): the clients smooth between them. */
    static final int ATTITUDE_SAMPLE_TICKS = 2;
    /** An unchanged feed is still repeated this often, so a player who just came into range gets it. */
    static final int ATTITUDE_HEARTBEAT_TICKS = 20;

    private record SentAttitude(AttitudeIndicatorFeed.Sample sample, long gameTime) {}

    /** Settings of the Attitude Indicator at this origin cell, or empty when there is none. */
    public java.util.Optional<AttitudeIndicatorData> attitudeIndicatorAt(int column, int row) {
        var module = grid.at(column, row).filter(found -> found.type().equals(PanelCoordinates.ATTITUDE_INDICATOR));
        if (module.isEmpty()) return java.util.Optional.empty();
        var found = module.orElseThrow();
        return java.util.Optional.of(attitudeModules.computeIfAbsent(cellKey(found.column(), found.row()),
                ignored -> new AttitudeIndicatorData()).copy());
    }

    public boolean setAttitudeAltitudeTape(int column, int row, boolean shown) {
        return changeAttitudeModule(column, row, data -> {
            if (data.altitudeTape() == shown) return false;
            data.setAltitudeTape(shown);
            return true;
        });
    }

    public boolean setAttitudeSpeedTape(int column, int row, boolean shown) {
        return changeAttitudeModule(column, row, data -> {
            if (data.speedTape() == shown) return false;
            data.setSpeedTape(shown);
            return true;
        });
    }

    /** Applies a setting to the module at this origin cell; {@code change} says whether anything changed. */
    private boolean changeAttitudeModule(int column, int row, java.util.function.Predicate<AttitudeIndicatorData> change) {
        var module = grid.at(column, row).filter(found -> found.type().equals(PanelCoordinates.ATTITUDE_INDICATOR));
        if (module.isEmpty()) return false;
        var found = module.orElseThrow();
        var data = attitudeModules.computeIfAbsent(cellKey(found.column(), found.row()),
                ignored -> new AttitudeIndicatorData());
        if (!change.test(data)) return true;
        setChanged();
        syncClient();
        // The displays redraw at once instead of waiting for the next sample.
        attitudeSent.remove(cellKey(found.column(), found.row()));
        return true;
    }

    /** The feed of the Attitude Indicator at this origin cell as of now; server only. */
    public AttitudeIndicatorFeed.Sample attitudeSample(int column, int row) {
        var data = attitudeIndicatorAt(column, row);
        if (data.isEmpty() || !(level instanceof ServerLevel serverLevel)) return AttitudeIndicatorFeed.Sample.DISCONNECTED;
        var source = connectedSource(serverLevel, TablePanel.of(this));
        return AttitudeIndicatorSampler.sample(serverLevel, this, source.position(), source.topology(),
                data.get().altitudeTape(), data.get().speedTape(), serverLevel.getGameTime());
    }

    /** Samples every Attitude Indicator of this table and tells the players watching it what changed. */
    private void sampleAttitudeIndicators(ServerLevel serverLevel, long now) {
        var indicators = grid.modules().stream()
                .filter(module -> module.type().equals(PanelCoordinates.ATTITUDE_INDICATOR)).toList();
        if (indicators.isEmpty()) {
            attitudeSent.clear();
            return;
        }
        // Tables take turns, so a big panel does not sample everything in one tick.
        if (Math.floorMod(now + worldPosition.asLong(), (long) ATTITUDE_SAMPLE_TICKS) != 0) return;
        var source = connectedSource(serverLevel, TablePanel.of(this));
        for (var module : indicators) {
            int key = cellKey(module.column(), module.row());
            var settings = attitudeModules.computeIfAbsent(key, ignored -> new AttitudeIndicatorData());
            var sample = AttitudeIndicatorSampler.sample(serverLevel, this, source.position(), source.topology(),
                    settings.altitudeTape(), settings.speedTape(), now);
            var last = attitudeSent.get(key);
            if (last != null && !sample.differsFrom(last.sample()) && now - last.gameTime() < ATTITUDE_HEARTBEAT_TICKS) {
                continue;
            }
            attitudeSent.put(key, new SentAttitude(sample, now));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(serverLevel,
                    new net.minecraft.world.level.ChunkPos(worldPosition),
                    com.radaeli.aeronauticsflybyinstruments.network.AttitudeDisplayPayload.of(worldPosition,
                            module.column(), module.row(), sample));
        }
        attitudeSent.keySet().removeIf(key -> indicators.stream()
                .noneMatch(module -> cellKey(module.column(), module.row()) == key));
    }

    public record FmsModuleAt(int column, int row, FmsModuleData data) {}

    public java.util.List<FmsModuleAt> fmsModules() {
        return grid.modules().stream().filter(module -> module.type().equals("fms"))
                .map(module -> new FmsModuleAt(module.column(), module.row(),
                        fmsModules.computeIfAbsent(cellKey(module.column(), module.row()), ignored -> new FmsModuleData()).copy()))
                .toList();
    }

    public java.util.Optional<FmsModuleAt> findFmsById(java.util.UUID id) {
        return fmsModules().stream().filter(module -> module.data().id().equals(id)).findFirst();
    }

    public java.util.Optional<FmsModuleAt> fmsAt(int column, int row) {
        var module = grid.at(column, row).filter(found -> found.type().equals("fms"));
        if (module.isEmpty()) return java.util.Optional.empty();
        var found = module.orElseThrow();
        FmsModuleData data = fmsModules.computeIfAbsent(cellKey(found.column(), found.row()), ignored -> new FmsModuleData());
        return java.util.Optional.of(new FmsModuleAt(found.column(), found.row(), data.copy()));
    }

    public boolean replaceFmsIdentity(int column, int row, java.util.UUID id) {
        FmsModuleData data = fmsModules.get(cellKey(column, row));
        if (data == null || id == null) return false;
        data.replaceIdentity(id);
        setChanged();
        syncClient();
        return true;
    }

    public boolean addFmsWaypoint(int column, int row, String name, double x, double y, double z) {
        FmsModuleData data = mutableFmsAt(column, row);
        if (data == null) return false;
        try {
            var waypoint = new FmsWaypoint(java.util.UUID.randomUUID(), name,
                    level.dimension().location().toString(), x, y, z);
            if (!data.add(waypoint)) return false;
            fmsChanged(column, row, "created", waypoint);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public boolean updateFmsWaypoint(int column, int row, java.util.UUID id, String name,
                                     double x, double y, double z) {
        FmsModuleData data = mutableFmsAt(column, row);
        if (data == null || id == null) return false;
        var previous = data.find(id).orElse(null);
        if (previous == null) return false;
        try {
            var updated = new FmsWaypoint(id, name, previous.dimension(), x, y, z);
            if (!data.update(updated)) return false;
            fmsChanged(column, row, "updated", updated);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public boolean deleteFmsWaypoint(int column, int row, java.util.UUID id) {
        FmsModuleData data = mutableFmsAt(column, row);
        if (data == null || id == null) return false;
        var deleted = data.find(id).orElse(null);
        if (!data.delete(id)) return false;
        fmsChanged(column, row, "deleted", deleted);
        return true;
    }

    public boolean selectFmsWaypoint(int column, int row, java.util.UUID id) {
        FmsModuleData data = mutableFmsAt(column, row);
        if (data == null || !data.select(id)) return false;
        fmsChanged(column, row, id == null ? "cleared" : "selected", data.selectedWaypoint().orElse(null));
        return true;
    }

    /** Destination selected on the FMS at this origin cell (synced to clients), or null; no copy. */
    public FmsWaypoint fmsTarget(int column, int row) {
        FmsModuleData data = fmsModules.get(cellKey(column, row));
        return data == null ? null : data.selectedWaypoint().orElse(null);
    }

    private FmsModuleData mutableFmsAt(int column, int row) {
        var module = grid.at(column, row).filter(found -> found.type().equals("fms"));
        if (module.isEmpty() || level == null) return null;
        var found = module.orElseThrow();
        return fmsModules.computeIfAbsent(cellKey(found.column(), found.row()), ignored -> new FmsModuleData());
    }

    private void fmsChanged(int column, int row, String action, FmsWaypoint waypoint) {
        var module = grid.at(column, row).orElseThrow();
        setChanged();
        sampleFmsSignals(true);
        syncClient();
        boolean selected = waypoint != null && fmsAt(column, row)
                .flatMap(found -> found.data().selectedWaypointId())
                .filter(waypoint.id()::equals).isPresent();
        peripheral.fmsWaypointChanged(action, module, waypoint, selected);
    }

    public FmsSignalSnapshot fmsSignals(int column, int row) {
        var data = fmsAt(column, row).map(FmsModuleAt::data).orElse(null);
        return data == null ? null : FmsSignalLogic.sample(data, dimensionId(), FmsLocation.forBlockEntity(this),
                level == null ? 0 : level.getGameTime());
    }

    @Override
    public java.util.List<AvionicsDataChannel<?>> avionicsSignals() {
        java.util.List<AvionicsDataChannel<?>> channels = new java.util.ArrayList<>();
        for (var module : fmsModules()) {
            var snapshot = fmsSignals.get(module.data().id());
            if (snapshot == null) snapshot = FmsSignalLogic.sample(module.data(), dimensionId(),
                    FmsLocation.forBlockEntity(this), level == null ? 0 : level.getGameTime());
            var metadata = Map.<String, Object>of("module_column", module.column() + 1,
                    "module_row", module.row() + 1);
            channels.add(new AvionicsDataChannel<>(FmsSignalSnapshot.WAYPOINT_SELECTED,
                    snapshot.waypointSelected(), metadata));
            channels.add(new AvionicsDataChannel<>(FmsSignalSnapshot.DISTANCE_TO_WAYPOINT,
                    snapshot.distanceToWaypoint(), metadata));
        }
        return java.util.List.copyOf(channels);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ModularTableBlockEntity table) {
        int interval = ModConfig.SENSOR_SCAN_INTERVAL_TICKS.get();
        long now = level.getGameTime();
        if (table.lastFmsSignalUpdate == Long.MIN_VALUE || now - table.lastFmsSignalUpdate >= interval) {
            table.lastFmsSignalUpdate = now;
            if (table.grid.modules().stream().anyMatch(module -> module.type().equals("fms"))) table.sampleFmsSignals(true);
        }
        table.sampleAttitudeIndicators(level, now);
        var panel = TablePanel.of(table);
        panel.tickAlerts(table, now, interval, () -> sampleGpwsAlerts(level, panel),
                (owner, module, severity) -> PanelAudio.play(level, owner, module, severity, module.buzzerVolume()));
        int version = TablePanel.version(level);
        if (table.footprintVersion != version) {
            table.footprintVersion = version;
            table.dropUnsupportedModules(level);
        }
        int light = emittedLight(table.brightestLampIntensity(), ModConfig.LAMP_LIGHT_LEVEL.get());
        if (state.hasProperty(ModularTableBaseBlock.LIGHT) && state.getValue(ModularTableBaseBlock.LIGHT) != light) {
            level.setBlock(pos, state.setValue(ModularTableBaseBlock.LIGHT, light), Block.UPDATE_CLIENTS);
        }
    }

    public int brightestLampIntensity() {
        return grid.modules().stream().filter(ModularTableBlockEntity::isLampModule)
                .mapToInt(this::lampRenderIntensity).max().orElse(0);
    }

    /** Scales a lamp intensity (0-15) to the configured block light ceiling. */
    public static int emittedLight(int intensity, int ceiling) {
        return Math.round(intensity * ceiling / 15.0f);
    }

    /** The cable network a panel is read from: where it was discovered and what it holds. */
    private record NetworkSource(BlockPos position, com.radaeli.aeronauticsflybyinstruments.avionics.NetworkSnapshot topology) {}

    /**
     * A cable on any joined table feeds the whole panel: the leader's network, or else the first member's that is
     * complete. When none is, the leader's incomplete network stands (its status is what the panel reports).
     */
    private static NetworkSource connectedSource(ServerLevel serverLevel, TablePanel panel) {
        var leader = panel.leader();
        BlockPos source = leader.worldPosition;
        var topology = AvionicsNetworkManager.snapshot(serverLevel, source);
        for (var member : panel.members()) {
            if (topology.status() == NetworkStatus.CONNECTED) break;
            if (member == leader) continue;
            var candidate = AvionicsNetworkManager.snapshot(serverLevel, member.worldPosition);
            if (candidate.status() == NetworkStatus.CONNECTED) {
                topology = candidate;
                source = member.worldPosition;
            }
        }
        return new NetworkSource(source, topology);
    }

    /**
     * Run by the panel leader for every joined table: a cable on any of them feeds the whole
     * panel, and mute clears on all of them together.
     */
    private static void sampleGpwsAlerts(ServerLevel serverLevel, TablePanel panel) {
        var members = panel.members();
        if (members.stream().noneMatch(member -> member.grid.modules().stream()
                .anyMatch(module -> isLampModule(module) || module.type().equals("buzzer")))) return;
        var leader = panel.leader();
        var network = connectedSource(serverLevel, panel);
        BlockPos source = network.position();
        var topology = network.topology();
        Map<String, Integer> updated = new HashMap<>();
        boolean foundGpws = false;
        if (topology.status() == NetworkStatus.CONNECTED) {
            for (AvionicsNetworkManager.ConnectedSignal signal :
                    AvionicsNetworkManager.connectedSignals(serverLevel, source)) {
                String channel = signal.channel().name();
                if (!channel.startsWith("gpws.")) continue;
                foundGpws = true;
                var reading = signal.channel().reading();
                if (reading.status() != ReadingStatus.VALID || reading.value().isEmpty()) continue;
                int severity = reading.value().filter(Integer.class::isInstance).map(Integer.class::cast).orElse(0);
                updated.merge(channel, severity, Math::max);
            }
        }
        Map<String, Integer> nowActive = new HashMap<>();
        for (var member : members) {
            member.applyGpws(Map.copyOf(updated), topology.status(), foundGpws, nowActive);
        }
        boolean newOrEscalated = PanelAlertLogic.hasNewOrEscalated(nowActive, leader.activeSoundAlerts);
        if (members.stream().anyMatch(ModularTableBlockEntity::muted) && (nowActive.isEmpty() || newOrEscalated)) {
            panel.setMuted(false);
        }
        leader.activeSoundAlerts = Map.copyOf(nowActive);
    }

    /** Takes the panel's GPWS reading and adds this table's sounding buzzers to {@code nowActive}. */
    private void applyGpws(Map<String, Integer> severities, NetworkStatus status, boolean foundGpws,
                           Map<String, Integer> nowActive) {
        boolean changed = !severities.equals(gpwsSeverities) || alertNetworkStatus != status
                || gpwsAvailable != foundGpws;
        gpwsSeverities = severities;
        alertNetworkStatus = status;
        gpwsAvailable = foundGpws;
        for (PanelGrid.Module module : grid.modules()) {
            if (module.outputSource().equals("gpws") && module.type().equals("buzzer")
                    && isModuleActive(module)) {
                String moduleKey = worldPosition.asLong() + ":" + module.column() + "," + module.row() + "|";
                if (module.alertChannel().equals("gpws.any")) {
                    gpwsSeverities.forEach((channel, severity) -> {
                        if (!channel.equals("gpws.any") && severity >= module.minimumSeverity())
                            nowActive.put(moduleKey + channel, severity);
                    });
                } else {
                    nowActive.put(moduleKey + module.alertChannel(), moduleSeverity(module));
                }
            }
        }
        if (changed) {
            syncClient();
            peripheral.alertOutputsChanged();
        }
    }

    /** Pops modules whose footprint lost a table under it (a safety net behind onRemove). */
    private void dropUnsupportedModules(ServerLevel serverLevel) {
        for (var module : grid.modules()) {
            if (!PanelGrid.spans(module) || TablePanel.supported(this, module)) continue;
            var stack = removeModuleStack(module.column(), module.row());
            if (!stack.isEmpty()) Block.popResource(serverLevel, worldPosition, stack);
        }
    }

    boolean testingPanel(long now) { return testPanelUntil > now; }

    void expirePanelTest(long now) {
        if (now >= testPanelUntil && testPanelUntil != 0) {
            testPanelUntil = 0;
            syncClient();
            peripheral.panelStateChanged("test", false);
            peripheral.alertOutputsChanged();
        }
    }

    private void sampleFmsSignals(boolean publish) {
        if (level == null || level.isClientSide) return;
        String dimension = dimensionId();
        var position = FmsLocation.forBlockEntity(this);
        long now = level.getGameTime();
        for (var module : fmsModules()) {
            FmsSignalSnapshot updated = FmsSignalLogic.sample(module.data(), dimension, position, now);
            FmsSignalSnapshot previous = fmsSignals.put(module.data().id(), updated);
            if (publish && !FmsSignalLogic.sameValues(previous, updated)) {
                peripheral.fmsSignalChanged(module, previous, updated);
            }
        }
        if (publish) {
            var liveIds = fmsModules().stream().map(module -> module.data().id()).collect(java.util.stream.Collectors.toSet());
            fmsSignals.keySet().removeIf(id -> !liveIds.contains(id));
        }
    }

    private String dimensionId() {
        return level == null ? "" : level.dimension().location().toString();
    }

    private void syncClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public ItemStack moduleStack(PanelGrid.Module module) {
        if (!module.type().equals("fms")) return com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock.itemStack(module);
        ItemStack stack = new ItemStack(ModBlocks.FMS_MODULE.get());
        var data = fmsModules.get(cellKey(module.column(), module.row()));
        if (data != null) FmsModuleItem.store(stack, data);
        return stack;
    }

    public java.util.List<ItemStack> moduleStacks() {
        return grid.modules().stream().map(this::moduleStack).toList();
    }

    public ItemStack removeModuleStack(int column, int row) {
        var module = grid.at(column, row);
        if (module.isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = moduleStack(module.get());
        if (!removeModuleInternal(column, row)) return ItemStack.EMPTY;
        return stack;
    }

    public boolean removeModule(int column, int row) {
        return removeModuleInternal(column, row);
    }

    private boolean removeModuleInternal(int column, int row) {
        var removed = grid.removeAt(column, row);
        removed.ifPresent(module -> {
            installationTokens.remove(cellKey(module.column(), module.row()));
            // Keys are per cell of this table; cells past it would alias other keys.
            for (int y = module.row(); y < Math.min(PanelGrid.SIZE, module.row() + module.height()); y++) {
                for (int x = module.column(); x < Math.min(PanelGrid.SIZE, module.column() + module.width()); x++) {
                    lampTestIntensities.remove(cellKey(x, y));
                }
            }
            if (module.type().equals("fms")) {
                var data = fmsModules.remove(cellKey(module.column(), module.row()));
                if (data != null) fmsSignals.remove(data.id());
            }
            if (module.type().equals(PanelCoordinates.ATTITUDE_INDICATOR)) {
                attitudeModules.remove(cellKey(module.column(), module.row()));
                attitudeSent.remove(cellKey(module.column(), module.row()));
            }
            changed("removed", module);
        });
        return removed.isPresent();
    }

    private void changed(String action, PanelGrid.Module module) {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            peripheral.moduleChanged(action, module);
            if (isLampModule(module) || module.type().equals("buzzer")) peripheral.alertOutputsChanged();
        }
    }

    private Map<Integer, Integer> outputLevels() {
        Map<Integer, Integer> outputs = new HashMap<>();
        for (PanelGrid.Module module : grid.modules()) {
            if (isLampModule(module) || module.type().equals("buzzer"))
                outputs.put(cellKey(module.column(), module.row()), moduleSeverity(module));
        }
        return outputs;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var list = new ListTag();
        for (var module : grid.modules()) {
            var entry = new CompoundTag();
            entry.putString("Type", module.type());
            entry.putInt("Column", module.column());
            entry.putInt("Row", module.row());
            entry.putInt("Width", module.width());
            entry.putInt("Height", module.height());
            entry.putString("Channel", module.channel());
            entry.putDouble("Threshold", module.threshold());
            entry.putString("OutputSource", module.outputSource());
            entry.putString("AlertChannel", module.alertChannel());
            entry.putInt("MinimumSeverity", module.minimumSeverity());
            entry.putInt("BuzzerVolume", module.buzzerVolume());
            entry.putString("View", module.view().getName());
            if (module.type().equals("fms")) {
                var data = fmsModules.get(cellKey(module.column(), module.row()));
                if (data != null) entry.put("FmsData", data.save());
            }
            if (module.type().equals(PanelCoordinates.ATTITUDE_INDICATOR)) {
                var data = attitudeModules.get(cellKey(module.column(), module.row()));
                if (data != null) entry.put("AttitudeData", data.save());
            }
            list.add(entry);
        }
        tag.put("Modules", list);
        var signals = new CompoundTag();
        testSignals.forEach(signals::putDouble);
        tag.put("TestSignals", signals);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        grid = new PanelGrid();
        installationTokens.clear();
        fmsModules.clear();
        attitudeModules.clear();
        fmsSignals.clear();
        var list = tag.getList("Modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            // Only registered module types and their real footprints may be restored.
            var type = entry.getString("Type");
            int width = entry.getInt("Width");
            int height = entry.getInt("Height");
            boolean validLamp = type.equals("lamp") && width == 1 && height == 1;
            boolean validLamp2x1 = type.equals("lamp_2x1")
                    && (width == 2 && height == 1 || width == 1 && height == 2);
            boolean validBuzzer = type.equals("buzzer") && width == 1 && height == 1;
            boolean validFms = type.equals("fms") && width == 2 && (height == 3 || height == 2);
            boolean validAttitude = type.equals(PanelCoordinates.ATTITUDE_INDICATOR) && width == 2 && height == 2;
            if (validLamp || validLamp2x1 || validBuzzer || validFms || validAttitude) {
                String channel = entry.contains("Channel", Tag.TAG_STRING) ? entry.getString("Channel") : "test";
                double threshold = entry.contains("Threshold", Tag.TAG_DOUBLE) ? entry.getDouble("Threshold") : 0.5;
                String outputSource = entry.contains("OutputSource", Tag.TAG_STRING)
                        ? entry.getString("OutputSource") : "test";
                String alertChannel = entry.contains("AlertChannel", Tag.TAG_STRING)
                        ? entry.getString("AlertChannel") : "gpws.any";
                int minimumSeverity = entry.contains("MinimumSeverity", Tag.TAG_INT)
                        ? entry.getInt("MinimumSeverity") : 2;
                int buzzerVolume = entry.contains("BuzzerVolume", Tag.TAG_INT)
                        ? entry.getInt("BuzzerVolume") : 100;
                // Saves from before the attitude indicator turned had no view: they keep the old north view.
                Direction view = Direction.byName(entry.getString("View"));
                if (view == null || !view.getAxis().isHorizontal()) view = Direction.NORTH;
                if (!channel.isBlank() && channel.length() <= 64 && Double.isFinite(threshold)
                        && (outputSource.equals("test") || outputSource.equals("gpws")
                            || outputSource.equals(LampIndication.SOURCE_REDSTONE_LINK))
                        && !alertChannel.isBlank() && alertChannel.length() <= 64
                        && minimumSeverity >= 1 && minimumSeverity <= 3 && buzzerVolume >= 0 && buzzerVolume <= 100) {
                    int column = entry.getInt("Column");
                    int row = entry.getInt("Row");
                    // Spanning footprints restore as saved; serverTick drops them if a table is gone.
                    boolean restored = grid.placeAnchored(new PanelGrid.Module(type, column, row, width, height,
                            channel, threshold, outputSource, alertChannel, minimumSeverity, buzzerVolume, view));
                    if (restored && validFms) {
                        fmsModules.put(cellKey(column, row), entry.contains("FmsData", Tag.TAG_COMPOUND)
                                ? FmsModuleData.load(entry.getCompound("FmsData")) : new FmsModuleData());
                    } else if (restored && validAttitude && entry.contains("AttitudeData", Tag.TAG_COMPOUND)) {
                        attitudeModules.put(cellKey(column, row),
                                AttitudeIndicatorData.load(entry.getCompound("AttitudeData")));
                    }
                }
            }
        }
        // Restore every saved module before growing legacy footprints, so an FMS
        // cannot consume cells belonging to a module later in the saved list.
        for (var module : grid.modules()) {
            if (!module.type().equals("fms") || module.height() != 2) continue;
            grid.removeAt(module.column(), module.row());
            int height = grid.canFit(module.column(), module.row(), 2, 3) ? 3 : 2;
            grid.place(new PanelGrid.Module(module.type(), module.column(), module.row(), 2, height,
                    module.channel(), module.threshold(), module.outputSource(), module.alertChannel(),
                    module.minimumSeverity(), module.buzzerVolume(), module.view()));
        }
        testSignals.clear();
        if (tag.contains("TestSignals", Tag.TAG_COMPOUND)) {
            var signals = tag.getCompound("TestSignals");
            for (String channel : signals.getAllKeys()) {
                double value = signals.getDouble(channel);
                if (!channel.isBlank() && channel.length() <= 64 && Double.isFinite(value)) testSignals.put(channel, value);
            }
        }
        lampTestIntensities.clear();
        if (tag.contains("LampTestIntensities", Tag.TAG_LIST)) {
            var overrides = tag.getList("LampTestIntensities", Tag.TAG_COMPOUND);
            for (int i = 0; i < overrides.size(); i++) {
                var entry = overrides.getCompound(i);
                int column = entry.getInt("Column");
                int row = entry.getInt("Row");
                int intensity = entry.getInt("Level");
                if (column >= 0 && column < PanelGrid.SIZE && row >= 0 && row < PanelGrid.SIZE
                        && intensity >= 0 && intensity <= 15
                        && grid.at(column, row).filter(ModularTableBlockEntity::isLampModule).isPresent()) {
                    lampTestIntensities.put(cellKey(column, row), intensity);
                }
            }
        }
        if (tag.contains("AlertSeverities", Tag.TAG_COMPOUND)) {
            var alerts = tag.getCompound("AlertSeverities");
            Map<String, Integer> severities = new HashMap<>();
            for (String channel : alerts.getAllKeys()) severities.put(channel, alerts.getInt(channel));
            gpwsSeverities = Map.copyOf(severities);
            try {
                alertNetworkStatus = NetworkStatus.valueOf(tag.getString("AlertNetwork"));
            } catch (IllegalArgumentException ignored) {
                alertNetworkStatus = NetworkStatus.DISCONNECTED;
            }
            gpwsAvailable = tag.getBoolean("AlertAvailable");
            muted = tag.getBoolean("AlertMuted");
        }
        if (level != null && level.isClientSide && tag.contains("PanelTestRemaining", Tag.TAG_LONG)) {
            testPanelUntil = level.getGameTime() + Math.max(0, tag.getLong("PanelTestRemaining"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        var overrides = new ListTag();
        lampTestIntensities.forEach((key, intensity) -> {
            var entry = new CompoundTag();
            entry.putInt("Column", key % PanelGrid.SIZE);
            entry.putInt("Row", key / PanelGrid.SIZE);
            entry.putInt("Level", intensity);
            overrides.add(entry);
        });
        tag.put("LampTestIntensities", overrides);
        tag.putLong("PanelTestRemaining", level == null ? 0
                : Math.max(0, testPanelUntil - level.getGameTime()));
        // Lamps are rendered on the client from the GPWS state: send it with every update.
        var alerts = new CompoundTag();
        gpwsSeverities.forEach(alerts::putInt);
        tag.put("AlertSeverities", alerts);
        tag.putString("AlertNetwork", alertNetworkStatus.name());
        tag.putBoolean("AlertAvailable", gpwsAvailable);
        tag.putBoolean("AlertMuted", muted);
        return tag;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = getBlockState().getValue(ModularTableBaseBlock.FACING)
                != state.getValue(ModularTableBaseBlock.FACING);
        super.setBlockState(state);
        if (turned) TablePanel.invalidate(level);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        TablePanel.invalidate(level);
        FmsModuleIndex.register(this);
        sampleFmsSignals(false);
    }

    @Override
    public void setRemoved() {
        TablePanel.invalidate(level);
        FmsModuleIndex.unregister(this);
        super.setRemoved();
    }
}
