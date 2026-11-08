package com.radaeli.aeronauticsflybyinstruments.avionics;

import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * What a block entity is on the cable network: its endpoint type and its published channels. Our own block
 * entities implement {@link AvionicsEndpoint} and {@link AvionicsSignalProvider} themselves; blocks from other mods
 * are adapted here, one class at a time, so the other mod's code is never changed.
 */
public final class AvionicsEndpoints {
    private AvionicsEndpoints() {}

    /** The endpoint type, or null when the block entity is not on the network. */
    public static String typeOf(BlockEntity blockEntity) {
        if (blockEntity instanceof AvionicsEndpoint endpoint) return endpoint.avionicsType();
        if (VelocitySensorEndpoint.is(blockEntity)) return VelocitySensorEndpoint.TYPE;
        return null;
    }

    /** The channels the block entity publishes now; empty for blocks that publish nothing. */
    public static List<AvionicsDataChannel<?>> signalsOf(Level level, BlockEntity blockEntity) {
        if (blockEntity instanceof AvionicsSignalProvider provider) return provider.avionicsSignals();
        if (blockEntity instanceof VelocitySensorBlockEntity sensor) {
            return VelocitySensorEndpoint.signals(sensor, level.getGameTime());
        }
        return List.of();
    }
}
