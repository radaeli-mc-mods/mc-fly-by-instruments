package com.radaeli.aeronauticsflybyinstruments.integration;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferencePeripheral;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerPeripheral;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgePeripheral;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.AdvancedTerrainScannerPeripheral;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterPeripheral;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Loaded only when CC:Tweaked is present. Owns every reference to its capability API. */
public final class ComputerCraftIntegration {
    private ComputerCraftIntegration() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ComputerCraftIntegration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.MODULAR_TABLE_ENTITY.get(),
                (table, side) -> peripheral(table));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.NETWORK_BRIDGE_ENTITY.get(),
                (bridge, side) -> peripheral(bridge));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.AVIONICS_COMPUTER_ENTITY.get(),
                (computer, side) -> peripheral(computer));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.RADAR_ALTIMETER_ENTITY.get(),
                (altimeter, side) -> peripheral(altimeter));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.ATTITUDE_REFERENCE_UNIT_ENTITY.get(),
                (unit, side) -> peripheral(unit));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlocks.TERRAIN_SCANNER_ENTITY.get(),
                (scanner, side) -> peripheral(scanner));
    }

    public static TablePeripheral peripheral(ModularTableBlockEntity table) {
        return (TablePeripheral) table.peripheral().getOrCreate(() -> new TablePeripheral(table));
    }

    public static NetworkBridgePeripheral peripheral(NetworkBridgeBlockEntity bridge) {
        return (NetworkBridgePeripheral) bridge.peripheral().getOrCreate(() -> new NetworkBridgePeripheral(bridge));
    }

    public static AvionicsComputerPeripheral peripheral(AvionicsComputerBlockEntity computer) {
        return (AvionicsComputerPeripheral) computer.peripheral().getOrCreate(() -> new AvionicsComputerPeripheral(computer));
    }

    public static RadarAltimeterPeripheral peripheral(RadarAltimeterBlockEntity altimeter) {
        return (RadarAltimeterPeripheral) altimeter.peripheral().getOrCreate(() -> new RadarAltimeterPeripheral(altimeter));
    }

    public static AttitudeReferencePeripheral peripheral(AttitudeReferenceUnitBlockEntity unit) {
        return (AttitudeReferencePeripheral) unit.peripheral().getOrCreate(() -> new AttitudeReferencePeripheral(unit));
    }

    public static TerrainScannerPeripheral peripheral(TerrainScannerBlockEntity scanner) {
        return (TerrainScannerPeripheral) scanner.peripheral().getOrCreate(() -> scanner.advanced()
                ? new AdvancedTerrainScannerPeripheral(scanner) : new TerrainScannerPeripheral(scanner, "terrain_scanner"));
    }
}
