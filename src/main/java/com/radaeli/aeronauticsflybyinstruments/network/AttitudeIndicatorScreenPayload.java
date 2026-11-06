package com.radaeli.aeronauticsflybyinstruments.network;

import com.radaeli.aeronauticsflybyinstruments.AeronauticsFlyByInstruments;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server state of one Attitude Indicator for its screen, sent when it opens and after each change. {@code position}
 * is the table that owns the module and {@code column}/{@code row} its origin cell in that table.
 *
 * @param status   {@code ok}, {@code degraded} or {@code no_data}
 * @param attitude state of the Attitude Reference Unit link: {@code ok}, {@code no_network}, {@code none},
 *                 {@code other_vessel} or {@code stale}
 * @param altimeter the same for the Radar Altimeter
 * @param agl      height above ground in blocks, NaN without a reading
 * @param altitudeTape the module's setting (the tape also needs an altimeter on the network)
 */
public record AttitudeIndicatorScreenPayload(BlockPos position, int column, int row, UUID interactionToken,
                                             String status, String attitude, String altimeter, double pitch,
                                             double roll, double agl, boolean altitudeTape)
        implements CustomPacketPayload {
    public static final Type<AttitudeIndicatorScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsFlyByInstruments.MOD_ID, "attitude_indicator_screen"));
    public static final StreamCodec<ByteBuf, AttitudeIndicatorScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.position);
                ByteBufCodecs.VAR_INT.encode(buf, payload.column);
                ByteBufCodecs.VAR_INT.encode(buf, payload.row);
                UUIDUtil.STREAM_CODEC.encode(buf, payload.interactionToken);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.status);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.attitude);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.altimeter);
                ByteBufCodecs.DOUBLE.encode(buf, payload.pitch);
                ByteBufCodecs.DOUBLE.encode(buf, payload.roll);
                ByteBufCodecs.DOUBLE.encode(buf, payload.agl);
                ByteBufCodecs.BOOL.encode(buf, payload.altitudeTape);
            },
            buf -> new AttitudeIndicatorScreenPayload(BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    public AttitudeIndicatorScreenPayload {
        position = position.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
