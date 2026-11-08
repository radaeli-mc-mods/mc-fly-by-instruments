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
 * One change from the Attitude Indicator screen. {@code action} is {@code altitude_tape} or {@code speed_tape} (with
 * {@code value}) or {@code refresh}. The token is the one the screen payload carried: it proves the screen was opened on this module
 * installation and not on a module that was removed and replaced since.
 */
public record AttitudeIndicatorActionPayload(BlockPos position, int column, int row, UUID interactionToken,
                                             String action, boolean value) implements CustomPacketPayload {
    public static final Type<AttitudeIndicatorActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsFlyByInstruments.MOD_ID, "attitude_indicator_action"));
    public static final StreamCodec<ByteBuf, AttitudeIndicatorActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AttitudeIndicatorActionPayload::position,
            ByteBufCodecs.VAR_INT, AttitudeIndicatorActionPayload::column,
            ByteBufCodecs.VAR_INT, AttitudeIndicatorActionPayload::row,
            UUIDUtil.STREAM_CODEC, AttitudeIndicatorActionPayload::interactionToken,
            ByteBufCodecs.STRING_UTF8, AttitudeIndicatorActionPayload::action,
            ByteBufCodecs.BOOL, AttitudeIndicatorActionPayload::value,
            AttitudeIndicatorActionPayload::new);

    public AttitudeIndicatorActionPayload {
        position = position.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
