package com.radaeli.aeronauticsflybyinstruments.wire;

import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.radaeli.aeronauticsflybyinstruments.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Deferred wire bookkeeping, run at server tick end. Removing a node does not cut its wires on the spot:
 * Sable assembly and Create contraptions remove and re-create every node of a route in one go, and an
 * immediate cascade would cut wires that are only moving. One tick later, a wire is cut only if some other
 * node still holds it in place.
 */
public final class WireMaintenance {
    private static final int VALIDATION_DELAY_TICKS = 20;
    private static final Map<Level, List<Cut>> CUTS = new WeakHashMap<>();
    private static final Map<Level, List<Check>> CHECKS = new WeakHashMap<>();
    private static final Map<Level, Set<BlockPos>> NO_REFUND = new WeakHashMap<>();

    private WireMaintenance() {}

    private record Cut(BlockPos pos, List<WireRoute> routes, boolean refund, long tick) {}

    private record Check(BlockPos pos, long tick) {}

    /** A creative player broke this node: its wires vanish without dropping items. */
    public static synchronized void suppressRefund(Level level, BlockPos pos) {
        NO_REFUND.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void nodeRemoved(Level level, BlockPos pos, List<WireRoute> routes) {
        if (routes.isEmpty()) return;
        var suppressed = NO_REFUND.get(level);
        boolean refund = suppressed == null || !suppressed.remove(pos);
        var queue = CUTS.computeIfAbsent(level, ignored -> new ArrayList<>());
        var alreadyQueued = new HashSet<java.util.UUID>();
        for (var cut : queue) {
            if (cut.pos().equals(pos)) {
                for (var route : cut.routes()) alreadyQueued.add(route.id());
            }
        }
        var newRoutes = routes.stream().filter(route -> !alreadyQueued.contains(route.id())).toList();
        if (newRoutes.isEmpty()) return;
        queue.add(new Cut(pos.immutable(), newRoutes, refund, level.getGameTime()));
    }

    public static synchronized void validateLater(Level level, BlockPos pos) {
        CHECKS.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new Check(pos.immutable(), level.getGameTime() + VALIDATION_DELAY_TICKS));
    }

    /** Removes one route from every loaded node and returns the items it was worth. */
    public static ItemStack cutEverywhere(Level level, BlockPos holder, WireRoute route) {
        for (var anchor : route.anchors()) {
            BlockPos pos = holder.offset(anchor.offset());
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WireNodeBlockEntity node) {
                node.removeRoute(route.id());
            }
        }
        AvionicsNetworkManager.markDirty(level);
        return refund(route);
    }

    public static ItemStack refund(WireRoute route) {
        return new ItemStack(ModBlocks.AVIONICS_WIRE.get(),
                WireRules.cost(route.length(), ModConfig.WIRE_BLOCKS_PER_ITEM.get()));
    }

    public static void tick(Level level) {
        List<Cut> cuts;
        List<Check> checks;
        synchronized (WireMaintenance.class) {
            cuts = take(CUTS, level, cut -> cut.tick() < level.getGameTime());
            checks = take(CHECKS, level, check -> check.tick() <= level.getGameTime());
            if (CUTS.isEmpty() && CHECKS.isEmpty()) NO_REFUND.remove(level);
        }
        for (var cut : cuts) applyCut(level, cut);
        for (var check : checks) {
            if (level.isLoaded(check.pos()) && level.getBlockEntity(check.pos()) instanceof WireNodeBlockEntity node) {
                node.validateRoutes();
            }
        }
    }

    public static synchronized void clearLevel(Level level) {
        CUTS.remove(level);
        CHECKS.remove(level);
        NO_REFUND.remove(level);
    }

    private static void applyCut(Level level, Cut cut) {
        for (var route : cut.routes()) {
            boolean heldElsewhere = false, unknown = false;
            for (var anchor : route.anchors()) {
                if (anchor.offset().equals(BlockPos.ZERO)) continue;
                BlockPos pos = cut.pos().offset(anchor.offset());
                if (!level.isLoaded(pos)) {
                    unknown = true;
                } else if (level.getBlockEntity(pos) instanceof WireNodeBlockEntity node && node.removeRoute(route.id())) {
                    heldElsewhere = true;
                }
            }
            // Every other node gone too: the whole route moved (assembly), nothing was cut.
            if (cut.refund() && (heldElsewhere || unknown) && level.isLoaded(cut.pos())) {
                Containers.dropItemStack(level, cut.pos().getX() + .5, cut.pos().getY() + .5, cut.pos().getZ() + .5,
                        refund(route));
            }
        }
        AvionicsNetworkManager.markDirty(level);
    }

    private static <T> List<T> take(Map<Level, List<T>> queues, Level level, java.util.function.Predicate<T> due) {
        var queue = queues.get(level);
        if (queue == null) return List.of();
        var ready = new ArrayList<T>();
        queue.removeIf(entry -> due.test(entry) && ready.add(entry));
        if (queue.isEmpty()) queues.remove(level);
        return ready;
    }
}
