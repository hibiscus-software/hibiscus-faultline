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

/** Client to server: pause, resume or stop the player's running operation, or undo or redo the last one. */
public record BuildControlPayload(Action action) implements CustomPacketPayload {
    public static final Type<BuildControlPayload> TYPE = new Type<>(HibiscusFaultline.id("build_control"));
    public static final StreamCodec<ByteBuf, BuildControlPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            index -> new BuildControlPayload(Action.values()[Math.floorMod(index, Action.values().length)]),
            payload -> payload.action().ordinal());

    public enum Action {
        PAUSE,
        RESUME,
        STOP,
        UNDO,
        REDO
    }

    @Override
    public Type<BuildControlPayload> type() {
        return TYPE;
    }
}
