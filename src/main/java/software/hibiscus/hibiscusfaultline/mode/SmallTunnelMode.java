/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import java.util.List;
import net.minecraft.core.BlockPos;
import software.hibiscus.hibiscusfaultline.selection.SelectionContext;

/** A 1x1 line straight into the face that was hit. */
public final class SmallTunnelMode extends ShapedMode {
    @Override
    public String id() {
        return "small_tunnel";
    }

    @Override
    protected List<BlockPos> slice(SelectionContext context, int step) {
        return List.of(context.origin().relative(context.face().getOpposite(), step));
    }
}
