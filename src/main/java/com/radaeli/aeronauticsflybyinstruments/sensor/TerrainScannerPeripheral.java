package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** CC:Tweaked read-only peripheral shared by the fixed and configurable scanner variants. */
public class TerrainScannerPeripheral implements IPeripheral, PeripheralEvents.Terrain {
    public static final String SCAN_EVENT = "terrain_scanner_scan_changed";
    private final TerrainScannerBlockEntity scanner;
    private final String type;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public TerrainScannerPeripheral(TerrainScannerBlockEntity scanner, String type) {
        this.scanner = scanner;
        this.type = type;
    }

    @Override public String getType() { return type; }
    @Override public Object getTarget() { return scanner; }
    @Override public boolean equals(IPeripheral other) {
        return other instanceof TerrainScannerPeripheral peripheral && peripheral.scanner == scanner
                && peripheral.type.equals(type);
    }
    @Override public void attach(IComputerAccess computer) { computers.add(computer); }
    @Override public void detach(IComputerAccess computer) { computers.remove(computer); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getScan() {
        return toLua(scanner.reading());
    }

    /** Side of the aircraft the scanner looks at: unset, front, rear, left or right. */
    @LuaFunction(mainThread = true)
    public String getAircraftSide() { return scanner.side().id(); }

    @LuaFunction(mainThread = true)
    public boolean setAircraftSide(String side) {
        return com.radaeli.aeronauticsflybyinstruments.avionics.ScannerSide.parse(side)
                .map(scanner::setSide).orElse(false);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getOutputConfiguration() { return SensorOutputLua.configuration(scanner.outputConfig()); }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getOutputs() { return SensorOutputLua.outputs(scanner.outputLevels()); }

    @LuaFunction(mainThread = true)
    public boolean setOutputRange(String channel, int min, int max) {
        SensorOutputConfig.Channel parsed = SensorOutputLua.channel(channel);
        if (parsed == null || !scanner.outputConfig().ranges().containsKey(parsed)) return false;
        return scanner.setOutputRange(parsed, min, max);
    }

    @LuaFunction(mainThread = true)
    public boolean setOutputMode(String mode) { return scanner.setOutputMode(mode); }

    @LuaFunction(mainThread = true)
    public boolean setLinkFrequencies(String channel, Map<?, ?> first, Map<?, ?> second) {
        SensorOutputConfig.Channel parsed = SensorOutputLua.channel(channel);
        if (parsed == null || !scanner.outputConfig().ranges().containsKey(parsed)) return false;
        ItemStack a = SensorOutputLua.frequency(first), b = SensorOutputLua.frequency(second);
        return a != null && b != null && scanner.setLinkFrequencies(parsed, a, b);
    }

    public void publishScan(TerrainScanSnapshot snapshot) {
        Map<String, Object> value = toLua(snapshot);
        publishEvent(SCAN_EVENT, value);
    }

    public void publishOutputConfiguration() {
        publishEvent(RadarAltimeterPeripheral.OUTPUT_CONFIG_EVENT,
                SensorOutputLua.configuration(scanner.outputConfig()));
    }
    public void publishOutputs() {
        publishEvent(RadarAltimeterPeripheral.OUTPUT_EVENT, SensorOutputLua.outputs(scanner.outputLevels()));
    }

    protected final void publishEvent(String event, Map<String, Object> value) {
        for (IComputerAccess computer : computers) computer.queueEvent(event, computer.getAttachmentName(), value);
    }

    public static Map<String, Object> toLua(TerrainScanSnapshot snapshot) {
        List<Map<String, Object>> contacts = new ArrayList<>(snapshot.contacts().size());
        for (TerrainScanContact contact : snapshot.contacts()) {
            Map<String, Object> item = new HashMap<>();
            item.put("rayIndex", contact.rayIndex());
            item.put("distance", contact.distance());
            item.put("directionX", contact.directionX());
            item.put("directionY", contact.directionY());
            item.put("directionZ", contact.directionZ());
            contacts.add(Map.copyOf(item));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("status", snapshot.status().name().toLowerCase(java.util.Locale.ROOT));
        result.put("contacts", List.copyOf(contacts));
        result.put("sampledRays", snapshot.sampledRays());
        result.put("totalRays", snapshot.totalRays());
        result.put("coverage", snapshot.coverage());
        result.put("unit", "blocks");
        result.put("gameTime", snapshot.gameTime());
        return Map.copyOf(result);
    }
}
