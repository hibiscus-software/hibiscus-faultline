/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

/** Client to server: the player picked a mode, by its index in {@link software.hibiscus.hibiscusfaultline.mode.Modes#ALL}. */
public record ModePayload(int mode) implements CustomPacketPayload {
    public static final Type<ModePayload> TYPE = new Type<>(HibiscusFaultline.id("mode"));
    public static final StreamCodec<ByteBuf, ModePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ModePayload::new, ModePayload::mode);

    @Override
    public Type<ModePayload> type() {
        return TYPE;
    }
}
