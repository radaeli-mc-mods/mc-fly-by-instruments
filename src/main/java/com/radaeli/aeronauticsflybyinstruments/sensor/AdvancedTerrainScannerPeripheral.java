package com.radaeli.aeronauticsflybyinstruments.sensor;

import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

/** Configurable CC:Tweaked peripheral for the Advanced Terrain Scanner. */
public final class AdvancedTerrainScannerPeripheral extends TerrainScannerPeripheral {
    public static final String CONFIG_EVENT = "terrain_scanner_configuration_changed";
    private final TerrainScannerBlockEntity scanner;

    public AdvancedTerrainScannerPeripheral(TerrainScannerBlockEntity scanner) {
        super(scanner, "terrain_scanner_advanced");
        this.scanner = scanner;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getConfiguration() {
        return Map.of("openingAngle", scanner.openingAngle());
    }

    @LuaFunction(mainThread = true)
    public boolean setOpeningAngle(int degrees) {
        return scanner.setOpeningAngle(degrees);
    }

    public void publishConfiguration(int degrees) {
        Map<String, Object> value = Map.of("openingAngle", degrees);
        publishEvent(CONFIG_EVENT, value);
    }
}
