package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeDamping;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReading;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import com.radaeli.aeronauticsflybyinstruments.avionics.GpwsSensitivity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.avionics.SensorReading;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Run with -PwithoutComputerCraft test --tests '*WithoutComputerCraftTest'. */
@EnabledIfSystemProperty(named = "flybyinstruments.test.withoutComputerCraft", matches = "true")
class WithoutComputerCraftTest {
    @Test
    void modInitializesAndBlocksOperateWithoutComputerCraftClasses() {
        assertTrue(ModList.get().isLoaded("aeronautics_fly_by_instruments"));
        assertFalse(ModList.get().isLoaded("computercraft"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("dan200.computercraft.api.peripheral.IPeripheral"));

        var table = new ModularTableBlockEntity(BlockPos.ZERO, ModBlocks.MODULAR_TABLE_BASE.get().defaultBlockState());
        assertTrue(table.placeLamp(0, 0));
        assertTrue(table.placeBuzzer(1, 0));
        assertTrue(table.removeModule(0, 0));

        var radar = new RadarAltimeterBlockEntity(BlockPos.ZERO, ModBlocks.RADAR_ALTIMETER.get().defaultBlockState());
        radar.acceptReading(SensorReading.valid(12.5, "blocks", 5));
        assertEquals(12.5, radar.reading().value().orElseThrow());
        assertEquals(6, radar.outputLevels().get(SensorOutputConfig.Channel.ALTITUDE));
        radar.acceptReading(SensorReading.unavailable("blocks", 10));
        assertEquals(0, radar.outputLevels().get(SensorOutputConfig.Channel.ALTITUDE));

        var scan = new TerrainScanSnapshot(ReadingStatus.VALID, List.of(), 1, 1, 5);
        var scanner = new TerrainScannerBlockEntity(BlockPos.ZERO, ModBlocks.TERRAIN_SCANNER.get().defaultBlockState());
        scanner.acceptReading(scan);
        assertEquals(scan, scanner.reading());
        var advanced = new TerrainScannerBlockEntity(BlockPos.ZERO,
                ModBlocks.TERRAIN_SCANNER_ADVANCED.get().defaultBlockState());
        assertTrue(advanced.setOpeningAngle(60));
        advanced.acceptReading(scan);
        assertEquals(scan, advanced.reading());

        var computer = new AvionicsComputerBlockEntity(BlockPos.ZERO, ModBlocks.AVIONICS_COMPUTER.get().defaultBlockState());
        assertTrue(computer.setSensitivity(GpwsSensitivity.HIGH));
        var unit = new AttitudeReferenceUnitBlockEntity(BlockPos.ZERO,
                ModBlocks.ATTITUDE_REFERENCE_UNIT.get().defaultBlockState());
        unit.setDamping(AttitudeDamping.OFF);
        unit.peripheral().events().publishReading(AttitudeReading.unavailable(10));
        var bridge = new NetworkBridgeBlockEntity(BlockPos.ZERO, ModBlocks.NETWORK_BRIDGE.get().defaultBlockState());
        assertEquals("network_bridge", bridge.avionicsType());
        assertDoesNotThrow(AvionicsNetworkManager::flushDirty);
        assertDoesNotThrow(() -> AvionicsNetworkManager.class.getDeclaredMethods());

        // Resolve all declared method signatures, catching accidental references to absent CC APIs.
        for (var block : List.of(table, radar, scanner, advanced, computer, unit, bridge)) {
            assertDoesNotThrow(() -> block.getClass().getDeclaredMethods());
        }
    }
}
