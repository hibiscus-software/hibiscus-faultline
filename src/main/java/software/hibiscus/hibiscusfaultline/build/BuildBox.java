/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A box of whole blocks, both corners inclusive. */
public record BuildBox(BlockPos min, BlockPos max) {
    public static final StreamCodec<ByteBuf, BuildBox> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BuildBox::min,
            BlockPos.STREAM_CODEC, BuildBox::max,
            BuildBox::of);

    public BuildBox {
        min = min.immutable();
        max = max.immutable();
    }

    /** The box spanning two corners in any order. */
    public static BuildBox of(BlockPos a, BlockPos b) {
        return new BuildBox(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public int sizeAlong(Direction.Axis axis) {
        return switch (axis) {
            case X -> sizeX();
            case Y -> sizeY();
            case Z -> sizeZ();
        };
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** {@return the smallest box holding this one and {@code pos}} */
    public BuildBox including(BlockPos pos) {
        return of(BlockPos.min(min, pos), BlockPos.max(max, pos));
    }

    /** {@return this box with one face pushed out by {@code amount} blocks, or pulled in when negative} Never thinner than one block. */
    public BuildBox withFaceMoved(Direction face, int amount) {
        int step = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? amount : -amount;
        BlockPos newMin = min;
        BlockPos newMax = max;
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            newMax = max.relative(face.getAxis(), step);
            if (newMax.get(face.getAxis()) < min.get(face.getAxis())) {
                return this;
            }
        } else {
            newMin = min.relative(face.getAxis(), step);
            if (newMin.get(face.getAxis()) > max.get(face.getAxis())) {
                return this;
            }
        }
        return new BuildBox(newMin, newMax);
    }

    public BuildBox offset(BlockPos by) {
        return new BuildBox(min.offset(by), max.offset(by));
    }

    public AABB aabb() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /** {@return how far {@code point} is from the nearest point of the box} 0 inside it. */
    public double distanceTo(Vec3 point) {
        AABB box = aabb();
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Every position in the box. The returned position is mutable and reused between steps. */
    public Iterable<BlockPos> positions() {
        return BlockPos.betweenClosed(min, max);
    }
}
