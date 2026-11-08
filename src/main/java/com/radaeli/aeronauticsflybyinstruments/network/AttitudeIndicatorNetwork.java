package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.panel.ModularTableBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelGrid;
import com.radaeli.aeronauticsflybyinstruments.panel.TableInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Attitude Indicator: the live display feed, and the configuration screen with its actions. */
public final class AttitudeIndicatorNetwork {
    private AttitudeIndicatorNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(AttitudeDisplayPayload.TYPE, AttitudeDisplayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayClient.accept(payload);
                    }
                }));
        registrar.playToClient(AttitudeIndicatorScreenPayload.TYPE, AttitudeIndicatorScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        com.radaeli.aeronauticsflybyinstruments.client.AttitudeDisplayClient.openOrUpdate(payload);
                    }
                }));
        registrar.playToServer(AttitudeIndicatorActionPayload.TYPE, AttitudeIndicatorActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handle(payload, context.player())));
    }

    /** Opens the screen of the module whose origin cell is {@code column}/{@code row} of {@code table}. */
    public static void open(ServerPlayer player, ModularTableBlockEntity table, int column, int row) {
        if (!TableInteraction.canReach(player, table)) return;
        send(player, table, column, row);
    }

    private static void send(ServerPlayer player, ModularTableBlockEntity table, int column, int row) {
        var data = table.attitudeIndicatorAt(column, row).orElse(null);
        var token = table.moduleToken(column, row);
        if (data == null || token == null) return;
        var sample = table.attitudeSample(column, row);
        PacketDistributor.sendToPlayer(player, new AttitudeIndicatorScreenPayload(table.getBlockPos(), column, row,
                token, sample.status().id(), sample.attitude().id(), sample.altimeter().id(), sample.pitch(),
                sample.roll(), sample.agl(), data.altitudeTape(), sample.velocity().id(), sample.speed(),
                data.speedTape()));
    }

    private static void handle(AttitudeIndicatorActionPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockPos position = payload.position();
        if (payload.column() < 0 || payload.column() >= PanelGrid.SIZE
                || payload.row() < 0 || payload.row() >= PanelGrid.SIZE
                || !serverPlayer.level().hasChunkAt(position)
                || !(serverPlayer.level().getBlockEntity(position) instanceof ModularTableBlockEntity table)
                || !TableInteraction.canReach(serverPlayer, table)
                || !payload.interactionToken().equals(table.moduleToken(payload.column(), payload.row()))
                || table.attitudeIndicatorAt(payload.column(), payload.row()).isEmpty()) return;
        switch (payload.action()) {
            case "altitude_tape" -> table.setAttitudeAltitudeTape(payload.column(), payload.row(), payload.value());
            case "speed_tape" -> table.setAttitudeSpeedTape(payload.column(), payload.row(), payload.value());
            case "refresh" -> { }
            default -> { return; }
        }
        // Every action answers with the fresh state, so the open screen never shows a stale choice.
        send(serverPlayer, table, payload.column(), payload.row());
    }
}
