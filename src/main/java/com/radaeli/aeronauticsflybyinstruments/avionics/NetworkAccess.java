package com.radaeli.aeronauticsflybyinstruments.avionics;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Minimal world adapter so topology traversal can be tested without a running game. */
public interface NetworkAccess {
    boolean isLoaded(BlockPos pos);
    String endpointType(BlockPos pos);

    /** Wire connector at {@code pos}, or null. Only called for loaded positions. */
    @Nullable
    default WireConnectorView wireConnector(BlockPos pos) {
        return null;
    }

    /**
     * Devices docked directly to the device at {@code pos}: a Modular Table mounted on an Avionics Computer, and the
     * other way round. Docked devices act as one device. Only called for loaded positions.
     */
    default List<BlockPos> docked(BlockPos pos) {
        return List.of();
    }

    /** A connector joins the block it is mounted on and the far ends of its wires (absolute positions). */
    record WireConnectorView(BlockPos host, List<BlockPos> farEnds) {
        public WireConnectorView {
            farEnds = List.copyOf(farEnds);
        }
    }
}
