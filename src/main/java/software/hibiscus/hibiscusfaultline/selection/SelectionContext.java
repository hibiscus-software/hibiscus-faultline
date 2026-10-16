/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.selection;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** What a {@link software.hibiscus.hibiscusfaultline.mode.Mode} needs to know, and where it puts its picks. */
public final class SelectionContext {
    private final BlockPos origin;
    private final Direction face;
    private final Direction facing;
    private final int feetY;
    private final int maxBlocks;
    private final int maxDistance;
    private final Predicate<BlockPos> filter;
    private final Long2BooleanOpenHashMap tested = new Long2BooleanOpenHashMap();
    private final Set<BlockPos> selected = new LinkedHashSet<>();

    SelectionContext(BlockPos origin, Direction face, Direction facing, int feetY, int maxBlocks, int maxDistance, Predicate<BlockPos> filter) {
        this.origin = origin.immutable();
        this.face = face;
        this.facing = facing;
        this.feetY = feetY;
        this.maxBlocks = maxBlocks;
        this.maxDistance = maxDistance;
        this.filter = filter;
        this.selected.add(this.origin);
    }

    public BlockPos origin() {
        return origin;
    }

    /** The face of the origin block the player was looking at. */
    public Direction face() {
        return face;
    }

    /** The horizontal direction the player is facing. */
    public Direction facing() {
        return facing;
    }

    /** The block Y the player is standing in. */
    public int feetY() {
        return feetY;
    }

    public int maxDistance() {
        return maxDistance;
    }

    public boolean inRange(BlockPos pos) {
        return origin.distSqr(pos) <= (double) maxDistance * maxDistance;
    }

    /** Whether the block at {@code pos} could be selected. Results are cached per position. */
    public boolean accepts(BlockPos pos) {
        long key = pos.asLong();
        if (tested.containsKey(key)) {
            return tested.get(key);
        }
        boolean result = filter.test(pos);
        tested.put(key, result);
        return result;
    }

    /** Adds {@code pos} if it is acceptable and not already selected. Returns whether it was added. */
    public boolean offer(BlockPos pos) {
        if (isFull() || selected.contains(pos) || !accepts(pos)) {
            return false;
        }
        selected.add(pos.immutable());
        return true;
    }

    public boolean isFull() {
        return selected.size() >= maxBlocks;
    }

    List<BlockPos> result() {
        return new ArrayList<>(selected);
    }
}
