package com.radaeli.aeronauticsflybyinstruments.avionics;

import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.block.ModularTableBaseBlock;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlock;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;

/** Event-invalidated, per-level topology cache. World traversal only occurs on query or dirty events. */
public final class AvionicsNetworkManager {
    private static final Map<Level, Map<BlockPos, NetworkSnapshot>> CACHE = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, NetworkSnapshot>> CONNECTOR_CACHE = new WeakHashMap<>();
    private static final Map<Level, Set<NetworkBridgePeripheral>> ATTACHED_PERIPHERALS = new WeakHashMap<>();
    private static final Set<Level> DIRTY_LEVELS = new HashSet<>();

    private AvionicsNetworkManager() {}

    public static NetworkSnapshot snapshot(Level level, BlockPos position) {
        if (level == null || level.isClientSide) return NetworkSnapshot.disconnected();
        BlockPos origin = position.immutable();
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(origin,
                    ignored -> NetworkDiscovery.discover(origin, new LevelAccess(level), ModConfig.MAX_AVIONICS_NETWORK_BLOCKS.get()));
        }
    }

    /** Network of a wire connector, for the Wiring View. Cached and invalidated like {@link #snapshot}. */
    public static NetworkSnapshot connectorSnapshot(Level level, BlockPos connector) {
        if (level == null || level.isClientSide) return NetworkSnapshot.disconnected();
        BlockPos origin = connector.immutable();
        synchronized (CACHE) {
            return CONNECTOR_CACHE.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(origin,
                    ignored -> NetworkDiscovery.discoverFromConnector(origin, new LevelAccess(level),
                            ModConfig.MAX_AVIONICS_NETWORK_BLOCKS.get()));
        }
    }

    /** Reads published channels from endpoints in the complete physical network without loading chunks. */
    public static List<ConnectedSignal> connectedSignals(Level level, BlockPos origin) {
        NetworkSnapshot topology = snapshot(level, origin);
        if (topology.status() != NetworkStatus.CONNECTED) return List.of();
        List<ConnectedSignal> result = new ArrayList<>();
        for (NetworkDevice device : topology.devices()) {
            BlockPos position = origin.offset(device.x(), device.y(), device.z());
            if (!level.hasChunkAt(position)) continue;
            var blockEntity = level.getBlockEntity(position);
            if (!(blockEntity instanceof AvionicsSignalProvider provider)) continue;
            for (AvionicsDataChannel<?> signal : provider.avionicsSignals()) {
                result.add(new ConnectedSignal(device, signal));
            }
        }
        return List.copyOf(result);
    }

    public record ConnectedSignal(NetworkDevice device, AvionicsDataChannel<?> channel) {}

    public static void register(NetworkBridgePeripheral peripheral, Level level) {
        if (level == null || level.isClientSide) return;
        synchronized (ATTACHED_PERIPHERALS) {
            ATTACHED_PERIPHERALS.computeIfAbsent(level, ignored -> new HashSet<>()).add(peripheral);
        }
    }

    public static void unregister(NetworkBridgePeripheral peripheral, Level level) {
        if (level == null || level.isClientSide) return;
        synchronized (ATTACHED_PERIPHERALS) {
            Set<NetworkBridgePeripheral> set = ATTACHED_PERIPHERALS.get(level);
            if (set != null) {
                set.remove(peripheral);
                if (set.isEmpty()) ATTACHED_PERIPHERALS.remove(level);
            }
        }
    }

    public static void markDirty(Level level) {
        if (level == null || level.isClientSide) return;
        synchronized (DIRTY_LEVELS) {
            DIRTY_LEVELS.add(level);
        }
    }

    public static void clearLevel(Level level) {
        synchronized (CACHE) {
            CACHE.remove(level);
            CONNECTOR_CACHE.remove(level);
        }
        synchronized (ATTACHED_PERIPHERALS) { ATTACHED_PERIPHERALS.remove(level); }
        synchronized (DIRTY_LEVELS) { DIRTY_LEVELS.remove(level); }
    }

    /** Called at server tick end; multiple block/chunk events are coalesced into one refresh. */
    public static void flushDirty() {
        Set<Level> dirty;
        synchronized (DIRTY_LEVELS) {
            dirty = Set.copyOf(DIRTY_LEVELS);
            DIRTY_LEVELS.clear();
        }
        for (Level level : dirty) {
            synchronized (CACHE) {
                CACHE.remove(level);
                CONNECTOR_CACHE.remove(level);
            }
            Set<NetworkBridgePeripheral> peripherals;
            synchronized (ATTACHED_PERIPHERALS) {
                peripherals = new HashSet<>(ATTACHED_PERIPHERALS.getOrDefault(level, Set.of()));
            }
            peripherals.forEach(NetworkBridgePeripheral::refreshNetworkAndNotify);
        }
    }

    private record LevelAccess(Level level) implements NetworkAccess {
        @Override public boolean isLoaded(BlockPos pos) { return level.hasChunkAt(pos); }
        @Override public WireConnectorView wireConnector(BlockPos pos) {
            if (!isLoaded(pos) || !(level.getBlockEntity(pos) instanceof WireNodeBlockEntity node) || !node.isConnector()) {
                return null;
            }
            return new WireConnectorView(WireNodeBlock.host(node.getBlockState(), pos), node.farEnds());
        }
        @Override public List<BlockPos> docked(BlockPos pos) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ModularTableBaseBlock) {
                BlockPos support = ModularTableBaseBlock.support(state, pos);
                return isLoaded(support) && level.getBlockState(support).is(ModBlocks.AVIONICS_COMPUTER.get())
                        ? List.of(support) : List.of();
            }
            if (!state.is(ModBlocks.AVIONICS_COMPUTER.get())) return List.of();
            var tables = new ArrayList<BlockPos>();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!isLoaded(neighbor)) continue;
                var table = level.getBlockState(neighbor);
                if (table.getBlock() instanceof ModularTableBaseBlock
                        && ModularTableBaseBlock.support(table, neighbor).equals(pos)) tables.add(neighbor);
            }
            return tables;
        }
        @Override public String endpointType(BlockPos pos) {
            if (!isLoaded(pos)) return null;
            return level.getBlockEntity(pos) instanceof AvionicsEndpoint endpoint ? endpoint.avionicsType() : null;
        }
    }
}
