package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One change from the Attitude Reference Unit screen. {@code action} is one of
 * {@code nose}, {@code up} (with {@code face}), {@code calibrate}, {@code reset},
 * {@code damping} (with {@code damping}) or {@code refresh}.
 */
public record AttitudeReferenceActionPayload(BlockPos position, String action, String face, String damping)
        implements CustomPacketPayload {
    public static final Type<AttitudeReferenceActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsFlyByInstruments.MOD_ID, "attitude_reference_action"));
    public static final StreamCodec<ByteBuf, AttitudeReferenceActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AttitudeReferenceActionPayload::position,
            ByteBufCodecs.STRING_UTF8, AttitudeReferenceActionPayload::action,
            ByteBufCodecs.STRING_UTF8, AttitudeReferenceActionPayload::face,
            ByteBufCodecs.STRING_UTF8, AttitudeReferenceActionPayload::damping,
            AttitudeReferenceActionPayload::new);

    public AttitudeReferenceActionPayload {
        position = position.immutable();
        face = face == null ? "" : face;
        damping = damping == null ? "" : damping;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
