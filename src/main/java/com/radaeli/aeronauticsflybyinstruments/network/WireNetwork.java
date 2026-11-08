package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsEndpoints;
import com.radaeli.aeronauticsflybyinstruments.avionics.AvionicsNetworkManager;
import com.radaeli.aeronauticsflybyinstruments.wire.WireMaintenance;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlock;
import com.radaeli.aeronauticsflybyinstruments.wire.WireNodeBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.wire.WirePath;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;

public final class WireNetwork {
    /** The Wiring View draws within this distance; the server answers for nothing farther. */
    public static final double VIEW_RANGE = 48;
    private static final double CUT_REACH = 8;

    private WireNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(WiringStatusRequestPayload.TYPE, WiringStatusRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) answerStatus(player, payload);
                }));
        registrar.playToServer(CutWirePayload.TYPE, CutWirePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) cut(player, payload);
                }));
        registrar.playToClient(WiringStatusPayload.TYPE, WiringStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        com.radaeli.aeronauticsflybyinstruments.client.WiringView.acceptStatus(payload);
                    }
                }));
    }

    private static void answerStatus(ServerPlayer player, WiringStatusRequestPayload payload) {
        var level = player.serverLevel();
        var entries = new ArrayList<WiringStatusPayload.Entry>();
        for (var pos : payload.connectors()) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof WireNodeBlockEntity node) || !node.isConnector()) {
                continue;
            }
            if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.getEyePosition(), Vec3.atCenterOf(pos))
                    > VIEW_RANGE * VIEW_RANGE * 4) continue;
            var snapshot = AvionicsNetworkManager.connectorSnapshot(level, pos);
            entries.add(new WiringStatusPayload.Entry(pos, snapshot.status().ordinal(), snapshot.endpointCount()));
        }
        PacketDistributor.sendToPlayer(player, new WiringStatusPayload(entries));
    }

    private static void cut(ServerPlayer player, CutWirePayload payload) {
        var level = player.serverLevel();
        var holder = payload.holder();
        if (!level.isLoaded(holder) || !(level.getBlockEntity(holder) instanceof WireNodeBlockEntity node)) return;
        var route = node.routes().stream().filter(candidate -> candidate.id().equals(payload.route())).findFirst();
        if (route.isEmpty()) return;
        // The player must stand next to some part of the wire, wherever it hangs.
        boolean near = false;
        for (var point : WirePath.centreLine(level, holder, route.get())) {
            Vec3 world = Vec3.atLowerCornerOf(holder).add(point);
            if (Sable.HELPER.distanceSquaredWithSubLevels(level, player.getEyePosition(), world) <= CUT_REACH * CUT_REACH) {
                near = true;
                break;
            }
        }
        if (!near) return;
        var refund = WireMaintenance.cutEverywhere(level, holder, route.get());
        if (!player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(refund);
        level.playSound(null, holder, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** Whether a connector's host is a device (for the Wiring View markers). */
    public static boolean hostIsDevice(net.minecraft.world.level.Level level, WireNodeBlockEntity node) {
        var host = WireNodeBlock.host(node.getBlockState(), node.getBlockPos());
        return level.isLoaded(host) && AvionicsEndpoints.typeOf(level.getBlockEntity(host)) != null;
    }
}
