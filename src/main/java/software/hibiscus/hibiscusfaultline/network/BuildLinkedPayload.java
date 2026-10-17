/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

/** Server to client: the containers the player has linked in their current dimension. */
public record BuildLinkedPayload(List<BlockPos> containers) implements CustomPacketPayload {
    public static final Type<BuildLinkedPayload> TYPE = new Type<>(HibiscusFaultline.id("build_linked"));
    public static final StreamCodec<ByteBuf, BuildLinkedPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)).map(BuildLinkedPayload::new, BuildLinkedPayload::containers);

    @Override
    public Type<BuildLinkedPayload> type() {
        return TYPE;
    }
}
