package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import com.radaeli.aeronauticsflybyinstruments.panel.AttitudeIndicatorFeed;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The live feed of one Attitude Indicator, sent to the players tracking its table about ten times a second while it
 * changes, and once a second otherwise. The client smooths between samples.
 *
 * @param column origin cell of the module in its table (0-2)
 * @param status {@link AttitudeIndicatorFeed.Status} ordinal
 * @param agl    height above ground in blocks, NaN when the tape has no reading
 * @param speed  speed along the velocity sensor's face in blocks per second, NaN when the tape has no reading
 */
public record AttitudeDisplayPayload(BlockPos position, int column, int row, int status, float pitch, float roll,
                                     float agl, boolean altitudeTape, float speed, boolean speedTape)
        implements CustomPacketPayload {
    public static final Type<AttitudeDisplayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsFlyByInstruments.MOD_ID, "attitude_display"));
    public static final StreamCodec<ByteBuf, AttitudeDisplayPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.position);
                ByteBufCodecs.VAR_INT.encode(buf, payload.column);
                ByteBufCodecs.VAR_INT.encode(buf, payload.row);
                ByteBufCodecs.VAR_INT.encode(buf, payload.status);
                ByteBufCodecs.FLOAT.encode(buf, payload.pitch);
                ByteBufCodecs.FLOAT.encode(buf, payload.roll);
                ByteBufCodecs.FLOAT.encode(buf, payload.agl);
                ByteBufCodecs.BOOL.encode(buf, payload.altitudeTape);
                ByteBufCodecs.FLOAT.encode(buf, payload.speed);
                ByteBufCodecs.BOOL.encode(buf, payload.speedTape);
            },
            buf -> new AttitudeDisplayPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    public AttitudeDisplayPayload {
        position = position.immutable();
    }

    public static AttitudeDisplayPayload of(BlockPos position, int column, int row, AttitudeIndicatorFeed.Sample sample) {
        return new AttitudeDisplayPayload(position, column, row, sample.status().ordinal(), (float) sample.pitch(),
                (float) sample.roll(), (float) sample.agl(), sample.altitudeTape(), (float) sample.speed(),
                sample.speedTape());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
