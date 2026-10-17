/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/** What placing a copy of a block costs, and what the player has to pay with. */
public final class BuildMaterials {
    private BuildMaterials() {}

    /** The cost of one block. */
    public sealed interface Cost {
        /** Placed without using anything: air, and the second half of a door or bed. */
        Cost FREE = new Free();
        /** Can't be placed, because no item makes it (fire, portals, piston heads). */
        Cost NONE = new None();

        record Free() implements Cost {}

        record None() implements Cost {}

        record Of(Item item) implements Cost {}
    }

    /** {@return what placing a copy of {@code state}, read from {@code pos}, costs} */
    public static Cost costOf(BlockState state, LevelReader level, BlockPos pos, Player player) {
        if (state.isAir()) {
            return Cost.FREE;
        }
        if (state.getBlock() instanceof BaseFireBlock
                || state.getBlock() instanceof NetherPortalBlock
                || state.getBlock() instanceof EndPortalBlock
                || state.getBlock() instanceof EndGatewayBlock
                || state.getBlock() instanceof PistonHeadBlock
                || state.getBlock() instanceof MovingPistonBlock) {
            return Cost.NONE;
        }
        // Two-block blocks are paid for once, on the half that holds the item.
        if (state.getOptionalValue(BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null) == DoubleBlockHalf.UPPER
                || state.getOptionalValue(BlockStateProperties.BED_PART).orElse(null) == BedPart.HEAD) {
            return Cost.FREE;
        }
        Item item = itemFor(state, level, pos, player);
        return item == null ? Cost.NONE : new Cost.Of(item);
    }

    private static @Nullable Item itemFor(BlockState state, LevelReader level, BlockPos pos, Player player) {
        ItemStack clone = state.getCloneItemStack(pos, level, false, player);
        Item item = clone.isEmpty() ? state.getBlock().asItem() : clone.getItem();
        return item == Items.AIR ? null : item;
    }

    public static boolean isSpendable(ItemStack stack) {
        return !stack.isEmpty() && stack.isComponentsPatchEmpty();
    }
}
