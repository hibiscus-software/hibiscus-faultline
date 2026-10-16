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

/** Client to server: the Faultline key was pressed or released. */
public record KeyStatePayload(boolean held) implements CustomPacketPayload {
    public static final Type<KeyStatePayload> TYPE = new Type<>(HibiscusFaultline.id("key_state"));
    public static final StreamCodec<ByteBuf, KeyStatePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(KeyStatePayload::new, KeyStatePayload::held);

    @Override
    public Type<KeyStatePayload> type() {
        return TYPE;
    }
}
