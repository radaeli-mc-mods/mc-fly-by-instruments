package com.radaeli.aeronauticsflybyinstruments.sensor;

import com.radaeli.aeronauticsflybyinstruments.avionics.ReadingStatus;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannerFaceQueryTest {
    @Test void receiverQueriesReachTheCorrectPhysicalSectorInEveryOrientation() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = ModBlocks.TERRAIN_SCANNER_ADVANCED.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            var scanner = new TerrainScannerBlockEntity(BlockPos.ZERO, state);
            scanner.acceptReading(new TerrainScanSnapshot(ReadingStatus.VALID,
                    List.of(new TerrainScanContact(0, 18, 0, 0, 1),
                            new TerrainScanContact(1, 4, -1, 0, 1),
                            new TerrainScanContact(2, 10, 1, 0, 1)), 9, 9, 1));
            BlockGetter level = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(),
                    new Class<?>[]{BlockGetter.class}, (proxy, method, args) ->
                            method.getName().equals("getBlockEntity") ? scanner : null);
            for (Direction outward : Direction.values()) {
                int expected = outward == facing ? 8 : outward == facing.getCounterClockWise()
                        ? 15 : outward == facing.getClockWise() ? 12 : 0;
                assertEquals(expected, state.getSignal(level, BlockPos.ZERO, outward.getOpposite()),
                        facing + " output towards " + outward);
            }
        }
    }
}
