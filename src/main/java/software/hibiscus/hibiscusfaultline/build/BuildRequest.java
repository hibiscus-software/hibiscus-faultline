/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One build operation as the client describes it: the tool, its option values, the selection and where it goes.
 * Only ever a description; the server reads the blocks themselves from its own world.
 */
public record BuildRequest(BuildTool tool, int options, BuildBox box, BuildTransform transform) {
    public static final StreamCodec<ByteBuf, BuildTool> TOOL_CODEC = ByteBufCodecs.VAR_INT.map(BuildTool::byIndex, BuildTool::ordinal);
    public static final StreamCodec<ByteBuf, BuildRequest> STREAM_CODEC = StreamCodec.composite(
            TOOL_CODEC, BuildRequest::tool,
            ByteBufCodecs.VAR_INT, BuildRequest::options,
            BuildBox.STREAM_CODEC, BuildRequest::box,
            BuildTransform.STREAM_CODEC, BuildRequest::transform,
            BuildRequest::new);

    public boolean has(BuildOption option) {
        return tool.options().contains(option) && (options & (1 << option.ordinal())) != 0;
    }

    /** Whether the tool puts the selection somewhere else, so the transform matters. */
    public boolean hasDestination() {
        return tool == BuildTool.MOVE || tool == BuildTool.CLONE;
    }

    /** {@return the box the operation changes besides the source} The source itself for tools without a destination. */
    public BuildBox target() {
        return hasDestination() ? transform.destination(box) : box;
    }
}
