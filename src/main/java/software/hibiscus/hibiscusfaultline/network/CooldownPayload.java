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

/** Server to client: the player has to wait this many ticks before the next vein-mine. */
public record CooldownPayload(int ticks) implements CustomPacketPayload {
    public static final Type<CooldownPayload> TYPE = new Type<>(HibiscusFaultline.id("cooldown"));
    public static final StreamCodec<ByteBuf, CooldownPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CooldownPayload::new, CooldownPayload::ticks);

    @Override
    public Type<CooldownPayload> type() {
        return TYPE;
    }
}
