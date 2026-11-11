package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralBinding;
import com.radaeli.aeronauticsflybyinstruments.integration.PeripheralEvents;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Network topology endpoint and holder of the bridge's optional CC:Tweaked peripheral. */
public final class NetworkBridgeBlockEntity extends BlockEntity implements AvionicsEndpoint {
    private final PeripheralBinding<PeripheralEvents.Bridge> peripheral =
            new PeripheralBinding<>(new PeripheralEvents.Bridge() {});

    public NetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.NETWORK_BRIDGE_ENTITY.get(), pos, state);
    }

    public PeripheralBinding<PeripheralEvents.Bridge> peripheral() {
        return peripheral;
    }

    @Override
    public String avionicsType() {
        return "network_bridge";
    }
}
