package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeDamping;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeOrientation;
import com.radaeli.aeronauticsflybyinstruments.attitude.AttitudeReferenceUnitBlockEntity;
import com.radaeli.aeronauticsflybyinstruments.panel.PanelSpatial;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AttitudeReferenceNetwork {
    private AttitudeReferenceNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(AttitudeReferenceActionPayload.TYPE, AttitudeReferenceActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> apply(payload, context.player())));
        registrar.playToClient(AttitudeReferenceScreenPayload.TYPE, AttitudeReferenceScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        com.radaeli.aeronauticsflybyinstruments.client.AttitudeReferenceClient.openOrUpdate(payload);
                    }
                }));
    }

    public static void open(ServerPlayer player, AttitudeReferenceUnitBlockEntity unit) {
        PacketDistributor.sendToPlayer(player, state(unit));
    }

    public static AttitudeReferenceScreenPayload state(AttitudeReferenceUnitBlockEntity unit) {
        var reading = unit.reading();
        var orientation = unit.orientation();
        return new AttitudeReferenceScreenPayload(unit.getBlockPos(), orientation.nose().getSerializedName(),
                orientation.up().getSerializedName(), unit.pitchOffset(), unit.rollOffset(), unit.damping().id(),
                reading.status().lowerName(), reading.pitch(), reading.roll(), reading.heading(),
                unit.onSubLevel());
    }

    private static void apply(AttitudeReferenceActionPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var pos = payload.position();
        if (!serverPlayer.level().hasChunkAt(pos)) return;
        if (!(serverPlayer.level().getBlockEntity(pos) instanceof AttitudeReferenceUnitBlockEntity unit)) return;
        if (!PanelSpatial.canReach(serverPlayer, unit)) return;
        switch (payload.action()) {
            case "nose", "up" -> {
                Direction face = Direction.byName(payload.face());
                if (face != null) {
                    AttitudeOrientation current = unit.orientation();
                    unit.setOrientation(payload.action().equals("nose") ? current.withNose(face) : current.withUp(face));
                }
            }
            case "calibrate" -> unit.calibrateLevel();
            case "reset" -> unit.resetCalibration();
            case "damping" -> AttitudeDamping.parse(payload.damping()).ifPresent(unit::setDamping);
            default -> { }
        }
        // Every action answers with the fresh state, so the open screen never shows a stale choice.
        PacketDistributor.sendToPlayer(serverPlayer, state(unit));
    }
}
