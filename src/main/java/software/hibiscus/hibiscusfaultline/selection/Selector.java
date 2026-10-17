/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.selection;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import software.hibiscus.hibiscusfaultline.config.Settings;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import org.jspecify.annotations.Nullable;

/**
 * Works out which blocks a vein-mine takes. Used by the server to break them and by the client to
 * preview them, so it only reads the level and the player and has no side-specific code.
 */
public final class Selector {
    private Selector() {}

    /**
     * {@return why the player cannot use Faultline at all right now, or {@code null} if they can}
     * Cooldowns are tracked separately by whoever calls this.
     */
    public static @Nullable BlockedReason blockedReason(Player player, Settings settings) {
        ItemStack held = player.getMainHandItem();
        if (held.is(FaultlineTags.EXCLUDED_TOOLS)) {
            return BlockedReason.EXCLUDED_TOOL;
        }
        if (settings.requireTool() && !isTool(held)) {
            return BlockedReason.TOOL_REQUIRED;
        }
        GameType gameType = gameType(player);
        if (gameType == GameType.SPECTATOR
                || (gameType.isBlockPlacingRestricted() && !player.mayBuild() && !held.has(DataComponents.CAN_BREAK))) {
            return BlockedReason.NO_PERMISSION;
        }
        if (!settings.anyModeEnabled()) {
            return BlockedReason.NO_MODES;
        }
        return null;
    }

    /**
     * {@return the blocks to mine, starting with {@code origin}} Empty if the origin itself cannot be
     * broken. Never longer than {@link Settings#maxBlocks()}.
     */
    public static List<BlockPos> select(Level level, Player player, BlockPos origin, Direction face, Mode mode, Settings settings) {
        GameType gameType = gameType(player);
        boolean includedOnly = BuiltInRegistries.BLOCK.get(FaultlineTags.INCLUDED_ONLY).map(set -> set.size() > 0).orElse(false);
        BlockState originState = level.getBlockState(origin);
        ItemStack held = player.getMainHandItem();
        boolean originInstant = originState.getDestroySpeed(level, origin) == 0;
        if (!canBreak(level, player, gameType, origin, originState, includedOnly)
                || (settings.toolMustSuitBlock() && !originInstant && !toolSuits(held, originState))) {
            return List.of();
        }

        Predicate<BlockState> same = mode.alwaysMatches() || settings.shapedModesMatch()
                ? MatchRules.sameAs(originState, settings)
                : state -> true;
        Predicate<BlockPos> filter = pos -> {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            return MatchRules.leavesAllowed(originState, state)
                    && same.test(state)
                    && canBreak(level, player, gameType, pos, state, includedOnly)
                    && (!settings.toolMustSuitBlock() || toolSuits(held, state)
                            || (originInstant && state.getDestroySpeed(level, pos) == 0));
        };

        SelectionContext context = new SelectionContext(
                origin, face, player.getDirection(), Mth.floor(player.getY()),
                settings.maxBlocks(), settings.maxDistance(), filter);
        mode.collect(context);
        return context.result();
    }

    /**
     * {@return the face of {@code pos} the player is looking at} Falls back to the face pointing at
     * the player's eyes when the look ray no longer lands on that block.
     */
    public static Direction lookedAtFace(Player player, BlockPos pos) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(pos)) {
            return blockHit.getDirection();
        }
        return Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
    }

    public static boolean isTool(ItemStack stack) {
        return stack.has(DataComponents.TOOL) || stack.is(Tags.Items.TOOLS);
    }

    /**
     * {@return whether {@code held} is the right thing to mine {@code state} with} A tool has to be
     * effective on the block (a shovel on dirt, not on cobblestone). Anything else, including a bare
     * hand, only suits blocks that drop without a tool.
     */
    public static boolean toolSuits(ItemStack held, BlockState state) {
        if (held.has(DataComponents.TOOL)) {
            return held.isCorrectToolForDrops(state) || held.getDestroySpeed(state) > 1.0F;
        }
        return !state.requiresCorrectToolForDrops();
    }

    private static boolean canBreak(Level level, Player player, GameType gameType, BlockPos pos, BlockState state, boolean includedOnly) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        if (state.is(FaultlineTags.EXCLUDED) || (includedOnly && !state.is(FaultlineTags.INCLUDED_ONLY))) {
            return false;
        }
        if (player.blockActionRestricted(level, pos, gameType)) {
            return false;
        }
        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || held.canDestroyBlock(state, level, pos, player);
    }

    private static GameType gameType(Player player) {
        GameType type = player.gameMode();
        return type != null ? type : GameType.DEFAULT_MODE;
    }
}
