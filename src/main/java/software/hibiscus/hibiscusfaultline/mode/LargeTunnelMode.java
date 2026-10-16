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

/** A 3x3 tunnel centred on the target, going into the face that was hit. */
public final class LargeTunnelMode extends ShapedMode {
    @Override
    public String id() {
        return "large_tunnel";
    }

    @Override
    protected List<BlockPos> slice(SelectionContext context, int step) {
        Direction into = context.face().getOpposite();
        return square(context.origin().relative(into, step), into.getAxis());
    }
}
