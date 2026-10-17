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
import software.hibiscus.hibiscusfaultline.build.BuildPreview;

/** Server to client: the result of a {@link BuildPreviewRequestPayload}. */
public record BuildPreviewPayload(BuildPreview preview) implements CustomPacketPayload {
    public static final Type<BuildPreviewPayload> TYPE = new Type<>(HibiscusFaultline.id("build_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreviewPayload> STREAM_CODEC =
            BuildPreview.STREAM_CODEC.map(BuildPreviewPayload::new, BuildPreviewPayload::preview);

    @Override
    public Type<BuildPreviewPayload> type() {
        return TYPE;
    }
}
