/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import java.util.List;
import net.minecraft.core.BlockPos;
import software.hibiscus.hibiscusfaultline.selection.SelectionContext;

/** A 1 wide, 2 tall staircase that climbs one block for every block it moves away from the player. */
public final class EscapeTunnelMode extends ShapedMode {
    @Override
    public String id() {
        return "escape_tunnel";
    }

    /** The staircase ignores the hit face; it always heads the way the player is facing. */
    @Override
    protected List<BlockPos> slice(SelectionContext context, int step) {
        BlockPos stair = context.origin().relative(context.facing(), step).above(step);
        return List.of(stair, stair.above());
    }
}
