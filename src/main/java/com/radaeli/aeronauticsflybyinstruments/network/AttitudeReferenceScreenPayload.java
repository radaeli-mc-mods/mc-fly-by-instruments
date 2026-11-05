package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server state of an Attitude Reference Unit, sent when its screen opens and after each change. */
public record AttitudeReferenceScreenPayload(BlockPos position, String nose, String up, double pitchOffset,
                                             double rollOffset, String damping, String status, double pitch,
                                             double roll, double heading, boolean onSubLevel)
        implements CustomPacketPayload {
    public static final Type<AttitudeReferenceScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsFlyByInstruments.MOD_ID, "attitude_reference_screen"));
    public static final StreamCodec<ByteBuf, AttitudeReferenceScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.position);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.nose);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.up);
                ByteBufCodecs.DOUBLE.encode(buf, payload.pitchOffset);
                ByteBufCodecs.DOUBLE.encode(buf, payload.rollOffset);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.damping);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.status);
                ByteBufCodecs.DOUBLE.encode(buf, payload.pitch);
                ByteBufCodecs.DOUBLE.encode(buf, payload.roll);
                ByteBufCodecs.DOUBLE.encode(buf, payload.heading);
                ByteBufCodecs.BOOL.encode(buf, payload.onSubLevel);
            },
            buf -> new AttitudeReferenceScreenPayload(BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    public AttitudeReferenceScreenPayload {
        position = position.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
