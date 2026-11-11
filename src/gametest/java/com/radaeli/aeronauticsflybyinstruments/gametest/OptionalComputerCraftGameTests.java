package com.radaeli.aeronauticsflybyinstruments.gametest;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsComputerBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.avionics.GpwsSensitivity;
import com.radaeli.aeronauticsflybyinstruments.avionics.NetworkBridgeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises normal instrument ticks with the runtime dependencies selected by Gradle. */
@GameTestHolder("aeronautics_fly_by_instruments")
@PrefixGameTestTemplate(false)
public final class OptionalComputerCraftGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void instrumentsTickWithoutRequiringComputerPeripherals(GameTestHelper helper) {
        var blocks = java.util.List.of(ModBlocks.MODULAR_TABLE_BASE.get(), ModBlocks.RADAR_ALTIMETER.get(),
                ModBlocks.TERRAIN_SCANNER.get(), ModBlocks.TERRAIN_SCANNER_ADVANCED.get(),
                ModBlocks.AVIONICS_COMPUTER.get(), ModBlocks.ATTITUDE_REFERENCE_UNIT.get(), ModBlocks.NETWORK_BRIDGE.get());
        for (int i = 0; i < blocks.size(); i++) {
            helper.setBlock(new BlockPos(i, 1, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(i, 2, 3), blocks.get(i));
        }
        var table = (ModularTableBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 3));
        helper.assertTrue(table.placeLamp(0, 0) && table.placeBuzzer(1, 0), "Panel modules must work");
        var computer = (AvionicsComputerBlockEntity) helper.getBlockEntity(new BlockPos(4, 2, 3));
        helper.assertTrue(computer.setSensitivity(GpwsSensitivity.HIGH), "GPWS settings must work");
        helper.assertTrue(computer.startTest(), "GPWS self-test must start");
        var advanced = (TerrainScannerBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        helper.assertTrue(advanced.setOpeningAngle(60), "Scanner settings must work");

        helper.runAfterDelay(20, () -> {
            var radar = (RadarAltimeterBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 3));
            var scanner = (TerrainScannerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 3));
            var unit = (AttitudeReferenceUnitBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 3));
            var bridge = (NetworkBridgeBlockEntity) helper.getBlockEntity(new BlockPos(6, 2, 3));
            helper.assertTrue(radar.reading().gameTime() > 0, "Radar must sample");
            helper.assertTrue(scanner.reading().gameTime() > 0, "Scanner must sample");
            helper.assertTrue(advanced.reading().gameTime() > 0, "Advanced scanner must sample");
            helper.assertTrue(unit.reading().gameTime() > 0, "Attitude unit must sample");
            helper.assertTrue(bridge.avionicsType().equals("network_bridge"), "Bridge must remain usable");
            helper.succeed();
        });
    }
}
