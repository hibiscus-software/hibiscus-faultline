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
import software.hibiscus.hibiscusfaultline.build.BuildRequest;

/** Client to server: check this operation and send back a {@link BuildPreviewPayload}. Changes nothing. */
public record BuildPreviewRequestPayload(int requestId, BuildRequest request) implements CustomPacketPayload {
    public static final Type<BuildPreviewRequestPayload> TYPE = new Type<>(HibiscusFaultline.id("build_preview_request"));
    public static final StreamCodec<ByteBuf, BuildPreviewRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuildPreviewRequestPayload::requestId,
            BuildRequest.STREAM_CODEC, BuildPreviewRequestPayload::request,
            BuildPreviewRequestPayload::new);

    @Override
    public Type<BuildPreviewRequestPayload> type() {
        return TYPE;
    }
}
