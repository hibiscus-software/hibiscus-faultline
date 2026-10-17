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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a Move, Clone or Stack puts the selection: mirrored within the box first, then turned clockwise around Y in
 * quarter turns, then shifted by {@code offset}. The destination box keeps the source's minimum corner (plus the
 * offset), so rotating turns the copy in place instead of swinging it around a corner.
 */
public record BuildTransform(BlockPos offset, int rotation, boolean flipX, boolean flipZ) {
    public static final BuildTransform IDENTITY = new BuildTransform(BlockPos.ZERO, 0, false, false);
    public static final StreamCodec<ByteBuf, BuildTransform> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BuildTransform::offset,
            ByteBufCodecs.VAR_INT, BuildTransform::rotation,
            ByteBufCodecs.BOOL, BuildTransform::flipX,
            ByteBufCodecs.BOOL, BuildTransform::flipZ,
            BuildTransform::new);

    public BuildTransform {
        offset = offset.immutable();
        rotation = Math.floorMod(rotation, 4);
    }

    public boolean isIdentity() {
        return equals(IDENTITY);
    }

    public BuildTransform withOffset(BlockPos newOffset) {
        return new BuildTransform(newOffset, rotation, flipX, flipZ);
    }

    public BuildTransform moved(Direction direction, int amount) {
        return withOffset(offset.relative(direction, amount));
    }

    /** A further quarter turn clockwise. */
    public BuildTransform rotated() {
        return new BuildTransform(offset, rotation + 1, flipX, flipZ);
    }

    /**
     * Flips the destination along a world axis. Mirroring happens before the rotation, so after an odd number of
     * quarter turns a world X flip is a mirror along the source's Z.
     */
    public BuildTransform flipped(Direction.Axis worldAxis) {
        boolean alongX = (worldAxis == Direction.Axis.X) != (rotation % 2 == 1);
        return new BuildTransform(offset, rotation, flipX ^ alongX, flipZ ^ !alongX);
    }

    /** {@return the box the selection lands in} */
    public BuildBox destination(BuildBox source) {
        int sizeX = rotation % 2 == 0 ? source.sizeX() : source.sizeZ();
        int sizeZ = rotation % 2 == 0 ? source.sizeZ() : source.sizeX();
        BlockPos min = source.min().offset(offset);
        return new BuildBox(min, min.offset(sizeX - 1, source.sizeY() - 1, sizeZ - 1));
    }

    /** {@return where the block at {@code pos} in {@code source} lands} */
    public BlockPos apply(BuildBox source, BlockPos pos) {
        int x = pos.getX() - source.min().getX();
        int y = pos.getY() - source.min().getY();
        int z = pos.getZ() - source.min().getZ();
        int sizeX = source.sizeX();
        int sizeZ = source.sizeZ();
        if (flipX) {
            x = sizeX - 1 - x;
        }
        if (flipZ) {
            z = sizeZ - 1 - z;
        }
        for (int i = 0; i < rotation; i++) {
            // A clockwise quarter turn seen from above: north becomes east.
            int turnedX = sizeZ - 1 - z;
            z = x;
            x = turnedX;
            int swap = sizeX;
            sizeX = sizeZ;
            sizeZ = swap;
        }
        return source.min().offset(offset).offset(x, y, z);
    }

    /** {@return the state turned and mirrored the same way, so stairs, logs and doors face the right way} */
    public BlockState apply(BlockState state) {
        if (flipX) {
            state = state.mirror(Mirror.FRONT_BACK);
        }
        if (flipZ) {
            state = state.mirror(Mirror.LEFT_RIGHT);
        }
        return state.rotate(switch (rotation) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        });
    }
}
