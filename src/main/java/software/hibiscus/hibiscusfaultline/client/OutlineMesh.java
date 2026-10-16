/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The outline of a group of blocks, drawn as one shape: faces between two selected blocks are
 * dropped, and so are edges that lie flat inside a surface. Built once per selection change; the
 * renderer just replays the arrays. Coordinates are relative to {@link #anchor()} to keep float
 * precision far from the world origin.
 */
record OutlineMesh(BlockPos anchor, float[] lines, float[] quads) {
    static final OutlineMesh EMPTY = new OutlineMesh(BlockPos.ZERO, new float[0], new float[0]);

    /** Pushes the fill slightly outwards so it does not flicker against the block faces. */
    private static final float INFLATE = 0.002F;

    boolean isEmpty() {
        return lines.length == 0;
    }

    static OutlineMesh of(List<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return EMPTY;
        }
        BlockPos anchor = blocks.getFirst();
        LongSet cells = new LongOpenHashSet(blocks.size());
        for (BlockPos pos : blocks) {
            cells.add(pos.asLong());
        }

        FloatArrayList lines = new FloatArrayList();
        FloatArrayList quads = new FloatArrayList();
        LongSet[] seenEdges = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};

        for (BlockPos pos : blocks) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            for (Direction side : Direction.values()) {
                if (!cells.contains(pos.relative(side).asLong())) {
                    addFace(quads, x - anchor.getX(), y - anchor.getY(), z - anchor.getZ(), side);
                }
            }
            for (Direction.Axis axis : Direction.Axis.values()) {
                for (int a = 0; a <= 1; a++) {
                    for (int b = 0; b <= 1; b++) {
                        // The corner the edge starts at; the edge runs one block along `axis`.
                        int cx = x + (axis == Direction.Axis.X ? 0 : a);
                        int cy = y + (axis == Direction.Axis.Y ? 0 : axis == Direction.Axis.X ? a : b);
                        int cz = z + (axis == Direction.Axis.Z ? 0 : b);
                        if (seenEdges[axis.ordinal()].add(BlockPos.asLong(cx, cy, cz)) && isVisibleEdge(cells, axis, cx, cy, cz)) {
                            addEdge(lines, axis, cx - anchor.getX(), cy - anchor.getY(), cz - anchor.getZ());
                        }
                    }
                }
            }
        }
        return new OutlineMesh(anchor, lines.toFloatArray(), quads.toFloatArray());
    }

    /**
     * An edge is part of the merged outline unless the four cells around it make it disappear:
     * all empty or all full (inside or outside the shape), or two side by side (a flat surface).
     */
    private static boolean isVisibleEdge(LongSet cells, Direction.Axis axis, int x, int y, int z) {
        boolean[] around = new boolean[4];
        for (int i = 0; i < 4; i++) {
            int u = (i & 1) - 1;
            int v = (i >> 1) - 1;
            long cell = switch (axis) {
                case X -> BlockPos.asLong(x, y + u, z + v);
                case Y -> BlockPos.asLong(x + u, y, z + v);
                case Z -> BlockPos.asLong(x + u, y + v, z);
            };
            around[i] = cells.contains(cell);
        }
        int count = (around[0] ? 1 : 0) + (around[1] ? 1 : 0) + (around[2] ? 1 : 0) + (around[3] ? 1 : 0);
        if (count == 1 || count == 3) {
            return true;
        }
        // Two diagonal cells meet only along the edge, so it is still a corner of the shape.
        return count == 2 && around[0] == around[3];
    }

    private static void addEdge(FloatArrayList out, Direction.Axis axis, int x, int y, int z) {
        out.add(x);
        out.add(y);
        out.add(z);
        out.add(x + (axis == Direction.Axis.X ? 1 : 0));
        out.add(y + (axis == Direction.Axis.Y ? 1 : 0));
        out.add(z + (axis == Direction.Axis.Z ? 1 : 0));
    }

    private static void addFace(FloatArrayList out, int x, int y, int z, Direction side) {
        float lo = -INFLATE;
        float hi = 1 + INFLATE;
        float[][] corners = switch (side) {
            case DOWN -> new float[][] {{lo, lo, lo}, {hi, lo, lo}, {hi, lo, hi}, {lo, lo, hi}};
            case UP -> new float[][] {{lo, hi, lo}, {lo, hi, hi}, {hi, hi, hi}, {hi, hi, lo}};
            case NORTH -> new float[][] {{lo, lo, lo}, {lo, hi, lo}, {hi, hi, lo}, {hi, lo, lo}};
            case SOUTH -> new float[][] {{lo, lo, hi}, {hi, lo, hi}, {hi, hi, hi}, {lo, hi, hi}};
            case WEST -> new float[][] {{lo, lo, lo}, {lo, lo, hi}, {lo, hi, hi}, {lo, hi, lo}};
            case EAST -> new float[][] {{hi, lo, lo}, {hi, hi, lo}, {hi, hi, hi}, {hi, lo, hi}};
        };
        for (float[] corner : corners) {
            out.add(x + corner[0]);
            out.add(y + corner[1]);
            out.add(z + corner[2]);
        }
    }
}
