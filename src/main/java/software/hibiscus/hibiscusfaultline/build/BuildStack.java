/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** How Stack repeats the selection: {@code count} copies in {@code direction}, {@code gap} blocks apart. */
public record BuildStack(Direction direction, int count, int gap) {
    public static final BuildStack NONE = new BuildStack(Direction.UP, 0, 0);
    public static final StreamCodec<ByteBuf, BuildStack> STREAM_CODEC = StreamCodec.composite(
            Direction.STREAM_CODEC, BuildStack::direction,
            ByteBufCodecs.VAR_INT, BuildStack::count,
            ByteBufCodecs.VAR_INT, BuildStack::gap,
            BuildStack::new);

    public BuildStack {
        count = Math.max(0, count);
        gap = Math.max(0, gap);
    }

    /** {@return how far copy number {@code copy} (1 for the first) sits from the selection} */
    public BlockPos offset(BuildBox box, int copy) {
        int step = box.sizeAlong(direction.getAxis()) + gap;
        return BlockPos.ZERO.relative(direction, step * copy);
    }

    /** {@return the box every copy together fills} The selection itself when there are none. */
    public BuildBox span(BuildBox box) {
        if (count == 0) {
            return box;
        }
        BuildBox last = box.offset(offset(box, count));
        BuildBox first = box.offset(offset(box, 1));
        return BuildBox.of(BlockPos.min(first.min(), last.min()), BlockPos.max(first.max(), last.max()));
    }
}
