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

/**
 * Server to client: how the player's running operation is getting on, for the HUD progress bar.
 *
 * @param state    0 running, 1 paused by the player, 2 paused because they moved out of reach, 3 finished
 * @param ticksLeft an estimate of the time remaining
 */
public record BuildProgressPayload(int done, int total, int ticksLeft, int state) implements CustomPacketPayload {
    public static final int RUNNING = 0;
    public static final int PAUSED = 1;
    public static final int OUT_OF_REACH = 2;
    public static final int FINISHED = 3;

    public static final Type<BuildProgressPayload> TYPE = new Type<>(HibiscusFaultline.id("build_progress"));
    public static final StreamCodec<ByteBuf, BuildProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuildProgressPayload::done,
            ByteBufCodecs.VAR_INT, BuildProgressPayload::total,
            ByteBufCodecs.VAR_INT, BuildProgressPayload::ticksLeft,
            ByteBufCodecs.VAR_INT, BuildProgressPayload::state,
            BuildProgressPayload::new);

    @Override
    public Type<BuildProgressPayload> type() {
        return TYPE;
    }
}
