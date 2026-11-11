package com.radaeli.aeronauticsflybyinstruments.integration;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlockEntity;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputerCraftIntegrationTest {
    @Test
    void everyPeripheralIsCreatedLazilyAndReusedAcrossCapabilityQueries() {
        var table = new ModularTableBlockEntity(BlockPos.ZERO, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
        var bridge = new NetworkBridgeBlockEntity(BlockPos.ZERO, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState());
        var computer = new AvionicsComputerBlockEntity(BlockPos.ZERO, ModBlocks.AVIONICS_COMPUTER.get().defaultBlockState());
        var radar = new RadarAltimeterBlockEntity(BlockPos.ZERO, ModBlocks.RADAR_ALTIMETER.get().defaultBlockState());
        var unit = new AttitudeReferenceUnitBlockEntity(BlockPos.ZERO,
                ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState());
        var simple = new TerrainScannerBlockEntity(BlockPos.ZERO, ModBlocks.TERRAIN_SCANNER.get().defaultBlockState());
        var advanced = new TerrainScannerBlockEntity(BlockPos.ZERO,
                ModBlocks.TERRAIN_SCANNER_ADVANCED.get().defaultBlockState());

        for (var binding : java.util.List.of(table.peripheral(), bridge.peripheral(), computer.peripheral(),
                radar.peripheral(), unit.peripheral(), simple.peripheral(), advanced.peripheral())) {
            assertFalse(binding.events() instanceof IPeripheral, "Block construction must not create a CC peripheral");
        }

        assertSame(ComputerCraftIntegration.peripheral(table), ComputerCraftIntegration.peripheral(table));
        assertSame(ComputerCraftIntegration.peripheral(bridge), ComputerCraftIntegration.peripheral(bridge));
        assertSame(ComputerCraftIntegration.peripheral(computer), ComputerCraftIntegration.peripheral(computer));
        assertSame(ComputerCraftIntegration.peripheral(radar), ComputerCraftIntegration.peripheral(radar));
        assertSame(ComputerCraftIntegration.peripheral(unit), ComputerCraftIntegration.peripheral(unit));
        assertSame(ComputerCraftIntegration.peripheral(simple), ComputerCraftIntegration.peripheral(simple));
        assertSame(ComputerCraftIntegration.peripheral(advanced), ComputerCraftIntegration.peripheral(advanced));
        assertSame(ComputerCraftIntegration.peripheral(radar), radar.peripheral().events());
        assertEquals("terrain_scanner", ComputerCraftIntegration.peripheral(simple).getType());
        assertEquals("terrain_scanner_advanced", ComputerCraftIntegration.peripheral(advanced).getType());
    }
}
