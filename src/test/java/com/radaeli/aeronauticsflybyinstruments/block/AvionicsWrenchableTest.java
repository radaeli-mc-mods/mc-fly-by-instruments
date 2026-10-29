package com.radaeli.aeronauticsflybyinstruments.block;

import com.radaeli.aeronauticsflybyinstruments.sensor.AbstractTerrainScannerBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.AdvancedTerrainScannerBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.RadarAltimeterBlock;
import com.radaeli.aeronauticsflybyinstruments.sensor.TerrainScannerBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvionicsWrenchableTest {
    @Test
    void everyPhysicalAvionicsBlockSupportsTheCreateWrenchLifecycle() {
        assertAll(
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(ModularTableBaseBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(NetworkBridgeBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(AvionicsComputerBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(RadarAltimeterBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(AbstractTerrainScannerBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(TerrainScannerBlock.class)),
                () -> assertTrue(AvionicsWrenchable.class.isAssignableFrom(AdvancedTerrainScannerBlock.class))
        );
    }

    @Test
    void ordinaryWrenchUseDoesNotRotateAvionicsBlocks() {
        AvionicsWrenchable behavior = new AvionicsWrenchable() {};
        BlockState state = Blocks.STONE.defaultBlockState();

        assertSame(state, behavior.getRotatedBlockState(state, Direction.NORTH));
        assertEquals(InteractionResult.PASS, behavior.onWrenched(state, null));
    }
}
