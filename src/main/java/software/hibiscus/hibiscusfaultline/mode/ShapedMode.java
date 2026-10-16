/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import software.hibiscus.hibiscusfaultline.selection.SelectionContext;

/** A fixed shape, walked one slice at a time moving away from the origin. */
abstract class ShapedMode implements Mode {
    /** Offsets of a 3x3 slice, centre first, then edges, then corners. */
    private static final int[][] SQUARE = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** How many slices the shape has at most; the context's distance limit also applies. */
    protected int depth(SelectionContext context) {
        return context.maxDistance() + 1;
    }

    /** The positions of the slice {@code step} slices away from the origin. */
    protected abstract List<BlockPos> slice(SelectionContext context, int step);

    @Override
    public final void collect(SelectionContext context) {
        int depth = depth(context);
        for (int step = 0; step < depth; step++) {
            boolean anyInRange = false;
            for (BlockPos pos : slice(context, step)) {
                if (!context.inRange(pos)) {
                    continue;
                }
                anyInRange = true;
                if (context.offer(pos) && context.isFull()) {
                    return;
                }
            }
            if (!anyInRange) {
                return;
            }
        }
    }

    /** A 3x3 square centred on {@code centre}, lying across {@code normal}. */
    protected static List<BlockPos> square(BlockPos centre, Direction.Axis normal) {
        Direction a = Direction.fromAxisAndDirection(normal == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X, Direction.AxisDirection.POSITIVE);
        Direction b = Direction.fromAxisAndDirection(normal == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z, Direction.AxisDirection.POSITIVE);
        BlockPos[] positions = new BlockPos[SQUARE.length];
        for (int i = 0; i < SQUARE.length; i++) {
            positions[i] = centre.relative(a, SQUARE[i][0]).relative(b, SQUARE[i][1]);
        }
        return List.of(positions);
    }
}
