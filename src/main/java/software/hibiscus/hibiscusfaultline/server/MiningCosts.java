/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.hibiscus.hibiscusfaultline.config.Settings;
import org.jspecify.annotations.Nullable;

/**
 * What breaking a block costs a player: hunger, shared with vein mining, and time, worked out the way vanilla's
 * {@code getDestroyProgress} does but for the best tool the player carries rather than the one in hand.
 */
public final class MiningCosts {
    private MiningCosts() {}

    /**
     * Faultline's extra exhaustion for one block beyond the one the player broke, the same for vein mining and the
     * build tools. Creative players pay nothing.
     */
    public static void chargeExtraBlock(ServerPlayer player, Settings settings) {
        if (!player.isCreative() && settings.hungerPerBlock() > 0) {
            player.causeFoodExhaustion(settings.hungerPerBlock());
        }
    }

    /**
     * The full cost of a block the build tools break: vanilla's own mining exhaustion, which breaking a block by hand
     * would have charged, plus {@link #chargeExtraBlock}.
     */
    public static void chargeBuildBlock(ServerPlayer player, Settings settings) {
        if (!player.isCreative()) {
            player.causeFoodExhaustion(FoodConstants.EXHAUSTION_MINE);
        }
        chargeExtraBlock(player, settings);
    }

    /** {@return whether the player is too hungry to keep breaking blocks} Never in creative. */
    public static boolean outOfFood(ServerPlayer player) {
        return !player.isCreative() && player.getFoodData().getFoodLevel() <= 0;
    }

    /** {@return the best stack the player carries for breaking {@code state}, or {@code null} if none can harvest it} */
    public static @Nullable ItemStack bestTool(ServerPlayer player, BlockState state) {
        ItemStack best = null;
        float bestSpeed = -1;
        for (ItemStack candidate : candidates(player)) {
            if (state.requiresCorrectToolForDrops() && !candidate.isCorrectToolForDrops(state)) {
                continue;
            }
            float speed = speed(player, candidate, state);
            if (speed > bestSpeed) {
                best = candidate;
                bestSpeed = speed;
            }
        }
        return best;
    }

    /** {@return the fastest stack the player carries for {@code state}, harvesting or not} */
    public static ItemStack fastestTool(ServerPlayer player, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = -1;
        for (ItemStack candidate : candidates(player)) {
            float speed = speed(player, candidate, state);
            if (speed > bestSpeed) {
                best = candidate;
                bestSpeed = speed;
            }
        }
        return best;
    }

    /**
     * {@return how many ticks breaking {@code state} with {@code tool} takes, times {@code multiplier}} At least one.
     * Follows vanilla: speed over hardness, divided by 30 with a tool that harvests the block and 100 without.
     */
    public static int breakTicks(ServerPlayer player, ItemStack tool, BlockState state, Level level, BlockPos pos, float multiplier) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) {
            return 1;
        }
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float progress = speed(player, tool, state) / hardness / (harvests ? 30.0F : 100.0F);
        if (progress <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(multiplier / progress));
    }

    /** The stack used for loot: the tool without its enchantments, so Fortune and Silk Touch don't apply. */
    public static ItemStack lootTool(ItemStack tool) {
        ItemStack copy = tool.copyWithCount(1);
        copy.remove(DataComponents.ENCHANTMENTS);
        return copy;
    }

    /**
     * Vanilla's {@code Player.getDestroySpeed}, for any stack. Efficiency comes from the stack's own attribute
     * modifiers; effects, the block break speed attribute, working underwater and being airborne come from the player.
     */
    private static float speed(ServerPlayer player, ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            float[] efficiency = {0};
            EnchantmentHelper.forEachModifier(tool, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.is(Attributes.MINING_EFFICIENCY) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    efficiency[0] += (float) modifier.amount();
                }
            });
            speed += efficiency[0];
        }
        if (MobEffectUtil.hasDigSpeed(player)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            speed *= (float) Math.pow(0.3, player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() + 1);
        }
        speed *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.getFluidInteraction().isEyeInFluidMatching(player, (entity, type, fluid) -> type.getIsWaterLike())) {
            speed *= (float) player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }
        if (!player.onGround()) {
            speed /= 5.0F;
        }
        return speed;
    }

    /** The empty hand, then every stack in the main inventory and the offhand. */
    private static List<ItemStack> candidates(ServerPlayer player) {
        List<ItemStack> candidates = new ArrayList<>();
        candidates.add(ItemStack.EMPTY);
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }
        if (!player.getOffhandItem().isEmpty()) {
            candidates.add(player.getOffhandItem());
        }
        return candidates;
    }
}
