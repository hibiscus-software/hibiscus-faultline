/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;

/** Client to server: run this operation. The server checks it again from scratch before changing anything. */
public record BuildConfirmPayload(BuildRequest request) implements CustomPacketPayload {
    public static final Type<BuildConfirmPayload> TYPE = new Type<>(HibiscusFaultline.id("build_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildConfirmPayload> STREAM_CODEC =
            BuildRequest.STREAM_CODEC.map(BuildConfirmPayload::new, BuildConfirmPayload::request);

    @Override
    public Type<BuildConfirmPayload> type() {
        return TYPE;
    }
}
