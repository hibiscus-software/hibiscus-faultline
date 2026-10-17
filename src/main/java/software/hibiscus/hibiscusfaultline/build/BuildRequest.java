/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One build operation as the client describes it: the tool, its option values, the selection and where it goes.
 * Only ever a description; the server reads the blocks themselves from its own world.
 *
 * @param block the block Set fills with
 * @param mask  the block Erase's Mask and Set's Replace are limited to
 */
public record BuildRequest(BuildTool tool, int options, BuildBox box, BuildTransform transform,
                           Optional<Holder<Block>> block, Optional<Holder<Block>> mask) {
    public static final StreamCodec<ByteBuf, BuildTool> TOOL_CODEC = ByteBufCodecs.VAR_INT.map(BuildTool::byIndex, BuildTool::ordinal);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Holder<Block>>> BLOCK_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(Registries.BLOCK));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildRequest> STREAM_CODEC = StreamCodec.composite(
            TOOL_CODEC, BuildRequest::tool,
            ByteBufCodecs.VAR_INT, BuildRequest::options,
            BuildBox.STREAM_CODEC, BuildRequest::box,
            BuildTransform.STREAM_CODEC, BuildRequest::transform,
            BLOCK_CODEC, BuildRequest::block,
            BLOCK_CODEC, BuildRequest::mask,
            BuildRequest::new);

    public boolean has(BuildOption option) {
        return tool.options().contains(option) && (options & (1 << option.ordinal())) != 0;
    }

    /** {@return whether {@code state} passes the mask, which only applies with Erase's Mask or Set's Replace on} */
    public boolean masked(BlockState state) {
        boolean active = tool == BuildTool.ERASE ? has(BuildOption.MASK) : tool == BuildTool.SET && has(BuildOption.REPLACE);
        return !active || mask.map(state::is).orElse(false);
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
