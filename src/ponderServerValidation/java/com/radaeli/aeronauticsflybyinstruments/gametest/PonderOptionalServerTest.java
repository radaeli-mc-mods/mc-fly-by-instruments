package com.radaeli.aeronauticsflybyinstruments.gametest;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server isolation gate, only compiled with -PponderServerValidation. */
@GameTestHolder("aeronautics_fly_by_instruments")
@PrefixGameTestTemplate(false)
public final class PonderOptionalServerTest {
    @GameTest(template = "empty")
    public static void gameplayLoadsWithoutClientTutorialLibraries(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("ponderjs") && !ModList.get().isLoaded("kubejs"),
                "Tutorial libraries must be absent for this isolation test");
        BlockPos computer = new BlockPos(2, 2, 2);
        helper.setBlock(computer, ModBlocks.AVIONICS_COMPUTER.get());
        helper.setBlock(computer.above(), ModBlocks.MODULAR_TABLE_BASE.get());
        var table = (ModularTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(computer.above()));
        helper.assertTrue(table != null && table.placeLamp(0, 0) && table.placeBuzzer(1, 0),
                "Common gameplay must work without the optional client integration");
        helper.succeed();
    }
}
