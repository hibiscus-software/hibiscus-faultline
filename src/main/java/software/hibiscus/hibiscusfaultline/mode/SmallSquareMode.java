/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import java.util.List;
import net.minecraft.core.BlockPos;
import software.hibiscus.hibiscusfaultline.selection.SelectionContext;

/** A flat 3x3 on the face that was hit: level for floors and ceilings, upright for walls. */
public final class SmallSquareMode extends ShapedMode {
    @Override
    public String id() {
        return "small_square";
    }

    @Override
    protected int depth(SelectionContext context) {
        return 1;
    }

    @Override
    protected List<BlockPos> slice(SelectionContext context, int step) {
        return square(context.origin(), context.face().getAxis());
    }
}
