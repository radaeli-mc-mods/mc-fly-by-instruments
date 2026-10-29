package com.radaeli.aeronauticsflybyinstruments.avionics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded graph traversal over wire connectors (their host block plus the far end of each wire). Docked devices (a table on an Avionics Computer) count as one device: reaching
 * one reaches all, and the origin's group links through every member's faces. It never queries blocks in
 * unloaded chunks.
 */
public final class NetworkDiscovery {
    private NetworkDiscovery() {}

    public static NetworkSnapshot discover(BlockPos origin, NetworkAccess world, int maxVisitedBlocks) {
        return new Traversal(origin, world, maxVisitedBlocks, false).run();
    }

    /**
     * The network a wire connector belongs to, seen from the connector itself: every endpoint counts,
     * including the device it is mounted on. Device offsets are relative to the connector.
     */
    public static NetworkSnapshot discoverFromConnector(BlockPos connector, NetworkAccess world, int maxVisitedBlocks) {
        return new Traversal(connector, world, maxVisitedBlocks, true).run();
    }

    private static final class Traversal {
        private final BlockPos origin;
        private final NetworkAccess world;
        private final int limit;
        private final Set<BlockPos> connectors = new HashSet<>();
        private final Set<BlockPos> endpoints = new HashSet<>();
        private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        private boolean incomplete;
        private boolean exceeded;
        private int wireEnds;
        private final boolean fromConnector;

        private Traversal(BlockPos origin, NetworkAccess world, int limit, boolean fromConnector) {
            if (limit < 1) throw new IllegalArgumentException("maxVisitedBlocks must be positive");
            this.origin = origin;
            this.world = world;
            this.limit = limit;
            this.fromConnector = fromConnector;
        }

        private NetworkSnapshot run() {
            if (fromConnector) {
                if (world.isLoaded(origin) && world.wireConnector(origin) != null) visit(connectors, origin);
            } else {
                seedFromEndpoint();
            }
            if (pending.isEmpty() && endpoints.isEmpty() && !exceeded) {
                return incomplete
                        ? new NetworkSnapshot(NetworkStatus.INCOMPLETE, 0, List.of())
                        : NetworkSnapshot.disconnected();
            }

            while (!pending.isEmpty() && !exceeded) {
                expandConnector(pending.removeFirst());
            }
            if (exceeded) {
                return new NetworkSnapshot(NetworkStatus.LIMIT_EXCEEDED, endpoints.size(), List.of(), wires());
            }

            var status = incomplete ? NetworkStatus.INCOMPLETE : NetworkStatus.CONNECTED;
            List<NetworkDevice> devices = new ArrayList<>();
            if (!incomplete) {
                for (BlockPos pos : endpoints) {
                    String type = world.endpointType(pos);
                    if (type != null) devices.add(new NetworkDevice(type,
                            pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ()));
                }
            }
            return new NetworkSnapshot(status, endpoints.size(), devices, wires());
        }

        private void seedFromEndpoint() {
            for (BlockPos member : group(origin)) {
                if (!member.equals(origin) && endpoints.add(member)) checkLimit();
                seedAround(member);
            }
        }

        private void seedAround(BlockPos device) {
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = device.relative(direction);
                if (!world.isLoaded(adjacent)) {
                    incomplete = true;
                    continue;
                }
                var connector = world.wireConnector(adjacent);
                if (connector != null && connector.host().equals(device)) visit(connectors, adjacent);
            }
        }

        /** A device and every device docked to it, directly or through another. */
        private Set<BlockPos> group(BlockPos device) {
            var group = new LinkedHashSet<BlockPos>();
            var queue = new ArrayDeque<BlockPos>();
            group.add(device);
            queue.add(device);
            while (!queue.isEmpty()) {
                for (BlockPos partner : world.docked(queue.removeFirst())) {
                    if (!world.isLoaded(partner)) {
                        incomplete = true;
                    } else if (world.endpointType(partner) != null && group.add(partner)) {
                        queue.add(partner);
                    }
                }
            }
            return group;
        }

        private void expandConnector(BlockPos pos) {
            var connector = world.wireConnector(pos);
            if (connector == null) return;
            BlockPos host = connector.host();
            if (!world.isLoaded(host)) {
                incomplete = true;
            } else {
                endpoint(host);
            }
            for (BlockPos far : connector.farEnds()) {
                wireEnds++;
                if (!world.isLoaded(far)) {
                    incomplete = true;
                } else if (world.wireConnector(far) != null) {
                    visit(connectors, far);
                }
            }
        }

        private void endpoint(BlockPos pos) {
            if (pos.equals(origin) || endpoints.contains(pos) || world.endpointType(pos) == null) return;
            for (BlockPos member : group(pos)) {
                if (!member.equals(origin) && endpoints.add(member)) checkLimit();
            }
        }

        private void visit(Set<BlockPos> kind, BlockPos pos) {
            if (kind.add(pos)) {
                pending.addLast(pos);
                checkLimit();
            }
        }

        private void checkLimit() {
            if (connectors.size() + endpoints.size() > limit) exceeded = true;
        }

        private int wires() {
            return (wireEnds + 1) / 2;
        }
    }
}
