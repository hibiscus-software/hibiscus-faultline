/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How Stack repeats the selection: a count of copies along each axis (negative going the other way), making a row
 * or, with more than one axis, a grid, with {@code gap} blocks between copies.
 */
public record BuildStack(int x, int y, int z, int gap) {
    public static final BuildStack NONE = new BuildStack(0, 0, 0, 0);
    public static final StreamCodec<ByteBuf, BuildStack> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuildStack::x,
            ByteBufCodecs.VAR_INT, BuildStack::y,
            ByteBufCodecs.VAR_INT, BuildStack::z,
            ByteBufCodecs.VAR_INT, BuildStack::gap,
            BuildStack::new);

    public BuildStack {
        gap = Math.max(0, gap);
    }

    public boolean isEmpty() {
        return x == 0 && y == 0 && z == 0;
    }

    public int count(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public BuildStack withCount(Direction.Axis axis, int count) {
        BuildStack stack = switch (axis) {
            case X -> new BuildStack(count, y, z, gap);
            case Y -> new BuildStack(x, count, z, gap);
            case Z -> new BuildStack(x, y, count, gap);
        };
        return stack.isEmpty() ? NONE : stack;
    }

    public BuildStack withGap(int newGap) {
        return new BuildStack(x, y, z, newGap);
    }

    /** {@return how many copies there are, not counting the selection itself} */
    public int copies() {
        return (Math.abs(x) + 1) * (Math.abs(y) + 1) * (Math.abs(z) + 1) - 1;
    }

    /** {@return where each copy sits relative to the selection, every cell of the grid but the selection's own} */
    public List<BlockPos> offsets(BuildBox box) {
        List<BlockPos> offsets = new ArrayList<>();
        int stepX = box.sizeX() + gap;
        int stepY = box.sizeY() + gap;
        int stepZ = box.sizeZ() + gap;
        for (int i = Math.min(0, x); i <= Math.max(0, x); i++) {
            for (int j = Math.min(0, y); j <= Math.max(0, y); j++) {
                for (int k = Math.min(0, z); k <= Math.max(0, z); k++) {
                    if (i != 0 || j != 0 || k != 0) {
                        offsets.add(new BlockPos(i * stepX, j * stepY, k * stepZ));
                    }
                }
            }
        }
        return offsets;
    }

    /** {@return the box the selection and every copy fill together} */
    public BuildBox span(BuildBox box) {
        BlockPos low = new BlockPos(Math.min(0, x) * (box.sizeX() + gap), Math.min(0, y) * (box.sizeY() + gap), Math.min(0, z) * (box.sizeZ() + gap));
        BlockPos high = new BlockPos(Math.max(0, x) * (box.sizeX() + gap), Math.max(0, y) * (box.sizeY() + gap), Math.max(0, z) * (box.sizeZ() + gap));
        return new BuildBox(box.min().offset(low), box.max().offset(high));
    }
}
