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
 * Server to client: which build tools the player may use, a bit per {@link software.hibiscus.hibiscusfaultline.build.BuildTool}
 * ordinal. 0 hides the tool slot. Only a hint for the UI; the server checks again for every operation.
 */
public record BuildAccessPayload(int toolMask) implements CustomPacketPayload {
    public static final Type<BuildAccessPayload> TYPE = new Type<>(HibiscusFaultline.id("build_access"));
    public static final StreamCodec<ByteBuf, BuildAccessPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(BuildAccessPayload::new, BuildAccessPayload::toolMask);

    @Override
    public Type<BuildAccessPayload> type() {
        return TYPE;
    }
}
