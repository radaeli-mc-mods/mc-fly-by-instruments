package com.radaeli.aeronauticsflybyinstruments.wire;

import com.mojang.serialization.Codec;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import com.radaeli.aeronauticsflybyinstruments.block.ModBlocks;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wire connector or support. Holds a copy of every route passing through it, relative to itself; the first
 * connector of a route draws it. No ticker: validation after loads is queued in {@link WireMaintenance}.
 */
public final class WireNodeBlockEntity extends BlockEntity implements TransformableBlockEntity, IHaveGoggleInformation {
    private static final Codec<List<WireRoute>> ROUTES = WireRoute.CODEC.listOf();
    private List<WireRoute> routes = List.of();
    /** Client-only cache owned by the renderer, dropped whenever routes change. */
    @Nullable public Object renderCache;

    public WireNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WIRE_NODE_ENTITY.get(), pos, state);
    }

    public boolean isConnector() {
        return getBlockState().getBlock() instanceof WireConnectorBlock;
    }

    public List<WireRoute> routes() {
        return routes;
    }

    public boolean hasRoute(UUID id) {
        return routes.stream().anyMatch(route -> route.id().equals(id));
    }

    public int capacity() {
        return isConnector() ? WireRules.CONNECTOR_CAPACITY : WireRules.SUPPORT_LANES;
    }

    public boolean isFull() {
        return routes.size() >= capacity();
    }

    public List<Integer> usedLanes() {
        var lanes = new ArrayList<Integer>();
        for (var route : routes) {
            var self = route.self();
            if (self != null) lanes.add(self.lane());
        }
        return lanes;
    }

    /** Absolute positions of the opposite connectors, for network discovery. */
    public List<BlockPos> farEnds() {
        var result = new ArrayList<BlockPos>();
        for (var route : routes) {
            var far = route.farEnd();
            if (far != null) result.add(worldPosition.offset(far));
        }
        return result;
    }

    public void addRoute(WireRoute route) {
        if (hasRoute(route.id())) return;
        var updated = new ArrayList<>(routes);
        updated.add(route);
        routes = List.copyOf(updated);
        changed();
    }

    public boolean removeRoute(UUID id) {
        var updated = routes.stream().filter(route -> !route.id().equals(id)).toList();
        if (updated.size() == routes.size()) return false;
        routes = updated;
        changed();
        return true;
    }

    /** Drops routes whose other nodes are loaded but no longer carry them (cut while this chunk was away). */
    void validateRoutes() {
        if (level == null || level.isClientSide) return;
        for (var route : routes) {
            for (var anchor : route.anchors()) {
                if (anchor.offset().equals(BlockPos.ZERO)) continue;
                BlockPos other = worldPosition.offset(anchor.offset());
                if (!level.isLoaded(other)) continue;
                if (!(level.getBlockEntity(other) instanceof WireNodeBlockEntity node) || !node.hasRoute(route.id())) {
                    removeRoute(route.id());
                    break;
                }
            }
        }
    }

    private void changed() {
        renderCache = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            AvionicsNetworkManager.markDirty(level);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        if (level.isClientSide) ClientWireIndex.add(this);
        else if (!routes.isEmpty()) WireMaintenance.validateLater(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) ClientWireIndex.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) ClientWireIndex.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // NeoForge ignores empty block-entity update packets. Send the empty list too so removing
        // the last wire clears the client's route data and cached geometry.
        tag.put("Routes", ROUTES.encodeStart(NbtOps.INSTANCE, routes).getOrThrow());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        routes = tag.contains("Routes")
                ? List.copyOf(ROUTES.parse(NbtOps.INSTANCE, tag.get("Routes")).result().orElse(List.of()))
                : List.of();
        renderCache = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        routes = routes.stream().map(route -> route.transformed(transform::applyWithoutOffset)).toList();
        renderCache = null;
        setChanged();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        String prefix = "goggles.aeronautics_fly_by_instruments.wire.";
        tooltip.add(Component.literal("    ").append(Component.translatable(prefix + (isConnector() ? "connector" : "support"))));
        tooltip.add(Component.literal("    ").append(Component.translatable(prefix + (isConnector() ? "wires" : "lanes"),
                routes.size(), capacity()).withStyle(ChatFormatting.GRAY)));
        double length = routes.stream().mapToDouble(WireRoute::length).sum();
        if (!routes.isEmpty()) tooltip.add(Component.literal("    ").append(Component.translatable(prefix + "length",
                String.format(java.util.Locale.ROOT, "%.1f", length)).withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }
}
