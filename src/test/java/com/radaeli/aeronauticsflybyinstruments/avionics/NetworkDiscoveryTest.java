package com.radaeli.aeronauticsflybyinstruments.avionics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDiscoveryTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    @Test
    void visitsBranchesAndCyclesOnlyOnce() {
        var world = new FakeAccess();
        BlockPos a = new BlockPos(0, 1, 0), b = new BlockPos(5, 1, 0), c = new BlockPos(5, 1, 5);
        BlockPos d = new BlockPos(9, 1, 0);
        world.connector(a, ORIGIN, b, c);
        world.connector(b, new BlockPos(5, 0, 0), a, c, d);
        world.connector(c, new BlockPos(5, 0, 5), a, b);
        world.connector(d, new BlockPos(9, 0, 0), b);
        world.endpoint(new BlockPos(9, 0, 0), "modular_table");

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.CONNECTED, snapshot.status());
        assertEquals(1, snapshot.endpointCount());
        assertEquals(4, snapshot.wireCount());
        assertEquals(new NetworkDevice("modular_table", 9, 0, 0), snapshot.devices().getFirst());
    }

    @Test
    void reportsDisconnectedWhenNoWireTouchesEndpoint() {
        var snapshot = NetworkDiscovery.discover(ORIGIN, new FakeAccess(), 4096);
        assertEquals(NetworkSnapshot.disconnected(), snapshot);
    }

    @Test
    void unloadedBoundaryMakesResultIncompleteWithoutReturningPartialDevices() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(5, 1, 0), new BlockPos(0, 1, 40));
        world.connector(new BlockPos(5, 1, 0), new BlockPos(5, 0, 0), new BlockPos(0, 1, 0));
        world.endpoint(new BlockPos(5, 0, 0), "modular_table");
        world.unloaded.add(new BlockPos(0, 1, 40));

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.INCOMPLETE, snapshot.status());
        assertFalse(snapshot.complete());
        assertEquals(1, snapshot.endpointCount());
        assertTrue(snapshot.devices().isEmpty());
    }

    @Test
    void neverQueriesBlocksInsideAnUnloadedChunk() {
        var world = new FakeAccess();
        world.unloaded.add(new BlockPos(1, 0, 0));
        world.throwWhenQueryingUnloaded = true;

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.INCOMPLETE, snapshot.status());
        assertEquals(0, world.unloadedBlockQueries);
    }

    @Test
    void stopsAtConfiguredVisitedBlockLimit() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(4, 1, 0));
        world.connector(new BlockPos(4, 1, 0), new BlockPos(4, 0, 0), new BlockPos(0, 1, 0));
        world.endpoint(new BlockPos(4, 0, 0), "modular_table");

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 1);

        assertEquals(NetworkStatus.LIMIT_EXCEEDED, snapshot.status());
        assertFalse(snapshot.complete());
        assertTrue(snapshot.devices().isEmpty());
    }

    @Test
    void aFreshDiscoveryReflectsWireRemovalAndReconnection() {
        var world = new FakeAccess();
        world.endpoint(new BlockPos(10, 0, 0), "modular_table");
        Runnable wire = () -> {
            world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(10, 1, 0));
            world.connector(new BlockPos(10, 1, 0), new BlockPos(10, 0, 0), new BlockPos(0, 1, 0));
        };
        wire.run();
        assertEquals(NetworkStatus.CONNECTED, NetworkDiscovery.discover(ORIGIN, world, 4096).status());

        world.connectors.clear();
        assertEquals(NetworkStatus.DISCONNECTED, NetworkDiscovery.discover(ORIGIN, world, 4096).status());

        wire.run();
        assertEquals(NetworkStatus.CONNECTED, NetworkDiscovery.discover(ORIGIN, world, 4096).status());
    }

    @Test
    void followsAWireBetweenConnectorsOnTwoDevices() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(10, 1, 0));
        world.connector(new BlockPos(10, 1, 0), new BlockPos(10, 0, 0), new BlockPos(0, 1, 0));
        world.endpoint(new BlockPos(10, 0, 0), "radar_altimeter");

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.CONNECTED, snapshot.status());
        assertEquals(1, snapshot.wireCount());
        assertEquals(new NetworkDevice("radar_altimeter", 10, 0, 0), snapshot.devices().getFirst());
    }

    @Test
    void aConnectorOnAPlainBlockIsAJunction() {
        var world = new FakeAccess();
        var junction = new BlockPos(5, 1, 0);
        world.connector(new BlockPos(0, 1, 0), ORIGIN, junction);
        world.connector(junction, new BlockPos(5, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(9, 1, 0), new BlockPos(5, 1, 7));
        world.connector(new BlockPos(9, 1, 0), new BlockPos(9, 0, 0), junction);
        world.connector(new BlockPos(5, 1, 7), new BlockPos(5, 0, 7), junction);
        world.endpoint(new BlockPos(9, 0, 0), "radar_altimeter");
        world.endpoint(new BlockPos(5, 0, 7), "terrain_scanner");

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.CONNECTED, snapshot.status());
        assertEquals(2, snapshot.endpointCount());
        assertEquals(3, snapshot.wireCount());
    }

    @Test
    void anUnloadedFarEndMakesTheNetworkIncomplete() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(40, 1, 0));
        world.unloaded.add(new BlockPos(40, 1, 0));
        world.throwWhenQueryingUnloaded = true;

        var snapshot = NetworkDiscovery.discover(ORIGIN, world, 4096);

        assertEquals(NetworkStatus.INCOMPLETE, snapshot.status());
        assertTrue(snapshot.devices().isEmpty());
    }

    @Test
    void aConnectorMountedElsewhereDoesNotJoinTheDeviceItTouches() {
        var world = new FakeAccess();
        // Sits next to the origin but hangs from the block above it.
        world.connector(new BlockPos(1, 0, 0), new BlockPos(1, 1, 0), new BlockPos(6, 0, 0));
        world.connector(new BlockPos(6, 0, 0), new BlockPos(7, 0, 0), new BlockPos(1, 0, 0));
        world.endpoint(new BlockPos(7, 0, 0), "radar_altimeter");

        assertEquals(NetworkStatus.DISCONNECTED, NetworkDiscovery.discover(ORIGIN, world, 4096).status());
    }

    @Test
    void fromAConnectorEveryEndpointCountsIncludingItsOwnDevice() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(10, 1, 0));
        world.connector(new BlockPos(10, 1, 0), new BlockPos(10, 0, 0), new BlockPos(0, 1, 0));
        world.endpoint(ORIGIN, "avionics_computer");
        world.endpoint(new BlockPos(10, 0, 0), "radar_altimeter");

        var snapshot = NetworkDiscovery.discoverFromConnector(new BlockPos(0, 1, 0), world, 4096);

        assertEquals(NetworkStatus.CONNECTED, snapshot.status());
        assertEquals(2, snapshot.endpointCount());
        assertEquals(NetworkSnapshot.disconnected(),
                NetworkDiscovery.discoverFromConnector(new BlockPos(3, 3, 3), world, 4096));
    }

    @Test
    void connectorsCountTowardsTheVisitLimit() {
        var world = new FakeAccess();
        world.connector(new BlockPos(0, 1, 0), ORIGIN, new BlockPos(4, 1, 0));
        world.connector(new BlockPos(4, 1, 0), new BlockPos(4, 0, 0), new BlockPos(0, 1, 0));

        assertEquals(NetworkStatus.LIMIT_EXCEEDED, NetworkDiscovery.discover(ORIGIN, world, 1).status());
    }

    @Test
    void aTableDockedOnTheComputerIsOnItsNetworkWithoutAWire() {
        var world = new FakeAccess();
        BlockPos computer = new BlockPos(0, 0, 0), table = new BlockPos(0, 1, 0);
        world.endpoint(computer, "avionics_computer");
        world.endpoint(table, "modular_table");
        world.dock(table, computer);

        var fromTable = NetworkDiscovery.discover(table, world, 64);
        assertEquals(NetworkStatus.CONNECTED, fromTable.status());
        assertEquals(1, fromTable.endpointCount());
        assertEquals("avionics_computer", fromTable.devices().getFirst().type());
        assertEquals(1, NetworkDiscovery.discover(computer, world, 64).endpointCount());
    }

    @Test
    void aDockedTableReachesTheComputersWireAndIsReachedThroughIt() {
        var world = new FakeAccess();
        BlockPos computer = new BlockPos(0, 0, 0), table = new BlockPos(0, 1, 0), sensor = new BlockPos(6, 0, 0);
        world.connector(new BlockPos(1, 0, 0), computer, new BlockPos(5, 0, 0));
        world.connector(new BlockPos(5, 0, 0), sensor, new BlockPos(1, 0, 0));
        world.endpoint(computer, "avionics_computer");
        world.endpoint(table, "modular_table");
        world.endpoint(sensor, "radar_altimeter");
        world.dock(table, computer);

        var fromTable = NetworkDiscovery.discover(table, world, 64);
        assertEquals(NetworkStatus.CONNECTED, fromTable.status());
        assertEquals(java.util.Set.of("avionics_computer", "radar_altimeter"),
                fromTable.devices().stream().map(NetworkDevice::type).collect(java.util.stream.Collectors.toSet()));
        var fromSensor = NetworkDiscovery.discover(sensor, world, 64);
        assertTrue(fromSensor.devices().stream().anyMatch(device -> device.type().equals("modular_table")),
                "reaching the computer reaches the table docked on it");
    }

    private static final class FakeAccess implements NetworkAccess {
        private final Map<BlockPos, WireConnectorView> connectors = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private final Map<BlockPos, String> endpoints = new HashMap<>();
        private final Map<BlockPos, java.util.List<BlockPos>> docks = new HashMap<>();
        private boolean throwWhenQueryingUnloaded;
        private int unloadedBlockQueries;

        void endpoint(BlockPos pos, String type) { endpoints.put(pos, type); }
        void dock(BlockPos a, BlockPos b) {
            docks.computeIfAbsent(a, ignored -> new java.util.ArrayList<>()).add(b);
            docks.computeIfAbsent(b, ignored -> new java.util.ArrayList<>()).add(a);
        }
        @Override public java.util.List<BlockPos> docked(BlockPos pos) {
            assertLoadedBeforeBlockQuery(pos);
            return docks.getOrDefault(pos, java.util.List.of());
        }
        void connector(BlockPos pos, BlockPos host, BlockPos... farEnds) {
            connectors.put(pos, new WireConnectorView(host, java.util.List.of(farEnds)));
        }
        @Override public WireConnectorView wireConnector(BlockPos pos) {
            assertLoadedBeforeBlockQuery(pos);
            return connectors.get(pos);
        }
        @Override public boolean isLoaded(BlockPos pos) { return !unloaded.contains(pos); }
        @Override public String endpointType(BlockPos pos) {
            assertLoadedBeforeBlockQuery(pos);
            return endpoints.get(pos);
        }
        private void assertLoadedBeforeBlockQuery(BlockPos pos) {
            if (unloaded.contains(pos)) {
                unloadedBlockQueries++;
                if (throwWhenQueryingUnloaded) throw new AssertionError("Queried block in unloaded chunk: " + pos);
            }
        }
    }
}
