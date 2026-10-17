/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

/** Client to server: link or unlink the container at {@code pos}, or only unlink it when {@code unlinkOnly}. */
public record BuildLinkPayload(BlockPos pos, boolean unlinkOnly) implements CustomPacketPayload {
    public static final Type<BuildLinkPayload> TYPE = new Type<>(HibiscusFaultline.id("build_link"));
    public static final StreamCodec<ByteBuf, BuildLinkPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BuildLinkPayload::pos,
            ByteBufCodecs.BOOL, BuildLinkPayload::unlinkOnly,
            BuildLinkPayload::new);

    @Override
    public Type<BuildLinkPayload> type() {
        return TYPE;
    }
}
