/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.config.Settings;
import software.hibiscus.hibiscusfaultline.network.BuildProgressPayload;
import software.hibiscus.hibiscusfaultline.network.FaultlineNetwork;
import org.jspecify.annotations.Nullable;

/**
 * One operation working through its plan over several ticks.
 *
 * <p>In survival each block that has to be broken takes its real mining time with the best tool the player carries,
 * scaled by {@code eraseTimeMultiplier}, several at once with the crack animation showing; it costs the same hunger
 * as vein mining and its drops go to the player. In creative everything is instant and nothing drops. Either way,
 * blocks are placed without neighbour updates, which all run once at the end, so redstone, water and falling blocks
 * settle once.
 *
 * <p>An operation keeps the rules it was confirmed under, even if the player changes game mode halfway.
 */
final class BuildJob {
    /** Placing without neighbour updates; those run once when the job ends. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int PROGRESS_INTERVAL = 5;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BuildRequest request;
    private final List<BuildPlan.Task> tasks;
    private final boolean creative;
    private final boolean bypass;
    private final boolean partial;
    private final Settings settings;
    private final float timeMultiplier;
    private final boolean breakWithoutTool;
    private final BuildSources sources;
    private final Slot[] slots;
    private final Set<BlockPos> changed = new LinkedHashSet<>();

    private int next;
    private int done;
    private int denied;
    private int experience;
    private long breakTicksSpent;
    private int breaksDone;
    private boolean paused;
    private boolean outOfReach;
    private boolean finished;
    private @Nullable Component stopReason;
    private int sinceProgress = PROGRESS_INTERVAL;

    BuildJob(ServerPlayer player, BuildRequest request, BuildPlan plan, boolean creative) {
        this.player = player;
        this.level = player.level();
        this.request = request;
        this.tasks = plan.tasks();
        this.creative = creative;
        this.bypass = BuildProtection.bypasses(player);
        this.partial = request.has(BuildOption.PARTIAL);
        this.settings = ServerConfig.settings();
        this.timeMultiplier = ServerConfig.buildEraseTimeMultiplier();
        this.breakWithoutTool = ServerConfig.buildEraseWithoutTool();
        this.sources = new BuildSources(player);
        this.slots = new Slot[creative ? 0 : ServerConfig.buildEraseParallel()];
    }

    ServerPlayer player() {
        return player;
    }

    boolean finished() {
        return finished;
    }

    void pause() {
        paused = true;
        clearCracks();
        sendProgress();
    }

    void resume() {
        paused = false;
        sendProgress();
    }

    boolean paused() {
        return paused;
    }

    /** Stops where it is. What's already done stays done. */
    void stop(@Nullable Component reason) {
        if (!finished) {
            stopReason = reason;
            finish();
        }
    }

    void tick() {
        if (finished) {
            return;
        }
        if (player.isRemoved() || !player.isAlive() || player.level() != level) {
            stop(null);
            return;
        }
        int reach = ServerConfig.buildMaxDistance(creative);
        boolean wasOutOfReach = outOfReach;
        outOfReach = request.box().distanceTo(player.getEyePosition()) > reach;
        if (outOfReach != wasOutOfReach) {
            if (outOfReach) {
                clearCracks();
            }
            sendProgress();
        }
        if (paused || outOfReach) {
            return;
        }
        if (creative) {
            int budget = ServerConfig.buildBlocksPerTick();
            while (budget-- > 0 && next < tasks.size() && !finished) {
                startTask(tasks.get(next++));
            }
        } else {
            tickSurvival();
        }
        if (!finished && next >= tasks.size() && activeSlots() == 0) {
            finish();
        }
        if (!finished && ++sinceProgress >= PROGRESS_INTERVAL) {
            sendProgress();
        }
    }

    private void tickSurvival() {
        // Placing into empty positions takes no time, so those run straight through, up to the per-tick budget.
        int budget = ServerConfig.buildBlocksPerTick();
        for (int i = 0; i < slots.length && !finished; i++) {
            while (slots[i] == null && next < tasks.size() && budget-- > 0 && !finished) {
                slots[i] = startTask(tasks.get(next++));
            }
        }
        for (int i = 0; i < slots.length && !finished; i++) {
            Slot slot = slots[i];
            if (slot == null) {
                continue;
            }
            slot.elapsed++;
            breakTicksSpent++;
            if (slot.elapsed >= slot.ticks) {
                level.destroyBlockProgress(crackId(i), slot.task.pos(), -1);
                slots[i] = null;
                completeTask(slot.task, slot.tool);
            } else {
                int stage = (int) (slot.elapsed * 10L / slot.ticks);
                if (stage != slot.stage) {
                    slot.stage = stage;
                    level.destroyBlockProgress(crackId(i), slot.task.pos(), stage);
                }
            }
        }
    }

    /**
     * Checks a task against the world as it is now and either finishes it at once or, when a block has to be broken
     * in survival, returns a slot that breaks it over time.
     */
    private @Nullable Slot startTask(BuildPlan.Task task) {
        BlockPos pos = task.pos();
        BlockState state = level.getBlockState(pos);
        boolean occupied = task.breakFirst() && !state.isAir();
        if (!task.breakFirst() && !state.isAir() && !BuildPlan.isLiquid(state)) {
            // Something appeared here since the plan was made; leave it alone.
            done++;
            return null;
        }
        if (occupied && (BuildValidator.isImmovable(level, pos, state) || !request.masked(state))) {
            done++;
            return null;
        }
        if ((occupied && !BuildProtection.mayBreak(player, level, pos, state, bypass))
                || (task.place() != null && !BuildProtection.mayPlace(player, level, pos, bypass))) {
            denied++;
            done++;
            return null;
        }
        if (!occupied || creative || BuildPlan.isLiquid(state)) {
            completeTask(task, ItemStack.EMPTY);
            return null;
        }
        ItemStack tool = MiningCosts.bestTool(player, state);
        if (tool == null) {
            if (!breakWithoutTool) {
                done++;
                return null;
            }
            tool = MiningCosts.fastestTool(player, state);
        }
        int ticks = MiningCosts.breakTicks(player, tool, state, level, pos, timeMultiplier);
        return new Slot(task, tool.copy(), ticks);
    }

    /** Breaks (if needed) and places (if needed) at one position. */
    private void completeTask(BuildPlan.Task task, ItemStack tool) {
        BlockPos pos = task.pos();
        BlockState state = level.getBlockState(pos);
        if (task.breakFirst() && !state.isAir()) {
            if (!creative && MiningCosts.outOfFood(player)) {
                stop(Component.translatable("hibiscusfaultline.build.stopped.hungry"));
                return;
            }
            if (!breakBlock(pos, state, tool)) {
                return;
            }
        }
        if (task.place() != null && !place(pos, task)) {
            return;
        }
        done++;
    }

    /** {@return whether the block was broken} False when the job had to stop. */
    private boolean breakBlock(BlockPos pos, BlockState state, ItemStack tool) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (creative || BuildPlan.isLiquid(state)) {
            // Creative deletes: no drops, and contents go with the block. Fluids have nothing to drop either way.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            changed.add(pos);
            return true;
        }
        List<ItemStack> returns = new ArrayList<>();
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        ItemStack lootTool = MiningCosts.lootTool(tool);
        if (harvests) {
            returns.addAll(Block.getDrops(state, level, pos, blockEntity, player, lootTool));
        }
        if (blockEntity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).isEmpty()) {
                    returns.add(container.getItem(slot).copy());
                }
            }
        }
        if (BuildSources.stopsOnOverflow() && !sources.fits(returns)) {
            stop(Component.translatable("hibiscusfaultline.build.stopped.full"));
            return false;
        }
        if (blockEntity instanceof Container container) {
            // Its contents are in `returns` now; empty it so removing the block doesn't spill them as well.
            container.clearContent();
        }
        if (harvests) {
            experience += state.getExpDrop(level, pos, blockEntity, player, lootTool);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
        changed.add(pos);
        sources.give(returns);
        MiningCosts.chargeBuildBlock(player, settings);
        breaksDone++;
        return true;
    }

    /** {@return whether the job can carry on} False when it ran out of materials without Partial. */
    private boolean place(BlockPos pos, BuildPlan.Task task) {
        if (task.cost() != null && !sources.take(task.cost())) {
            if (partial) {
                done++;
                return false;
            }
            stop(Component.translatable("hibiscusfaultline.build.stopped.materials"));
            return false;
        }
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos, PLACE_FLAGS);
        level.setBlock(pos, task.place(), PLACE_FLAGS);
        if (!BuildProtection.confirmPlace(player, before, PLACE_FLAGS, bypass)) {
            if (task.cost() != null) {
                sources.give(List.of(new ItemStack(task.cost())));
            }
            denied++;
            done++;
            return false;
        }
        changed.add(pos);
        return true;
    }

    /** Runs the neighbour and shape updates held back during the job, pays out experience and reports. */
    private void finish() {
        finished = true;
        clearCracks();
        for (BlockPos pos : changed) {
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state) {
                level.setBlock(pos, shaped, Block.UPDATE_ALL);
            }
            level.updateNeighborsAt(pos, shaped.getBlock());
            shaped.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
        if (experience > 0) {
            ExperienceOrb.award(level, player.position(), experience);
        }
        Component summary = Component.translatable("hibiscusfaultline.build.done", changed.size(), denied);
        player.sendSystemMessage(stopReason != null ? stopReason.copy().append(" ").append(summary) : summary, true);
        sendProgress();
    }

    private void clearCracks() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                level.destroyBlockProgress(crackId(i), slots[i].task.pos(), -1);
                slots[i].elapsed = 0;
                slots[i].stage = -1;
            }
        }
    }

    private void sendProgress() {
        sinceProgress = 0;
        int state = finished ? BuildProgressPayload.FINISHED
                : paused ? BuildProgressPayload.PAUSED
                : outOfReach ? BuildProgressPayload.OUT_OF_REACH
                : BuildProgressPayload.RUNNING;
        FaultlineNetwork.send(player, new BuildProgressPayload(done, tasks.size(), estimateTicksLeft(), state));
    }

    /** Remaining blocks times the average break so far, spread over the slots. A rough guide for the HUD. */
    private int estimateTicksLeft() {
        if (finished) {
            return 0;
        }
        int remaining = tasks.size() - done;
        if (creative) {
            return (int) Math.ceil(remaining / (double) ServerConfig.buildBlocksPerTick());
        }
        double perBreak = breaksDone > 0 ? breakTicksSpent / (double) breaksDone : 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(remaining * perBreak / Math.max(1, slots.length)));
    }

    private int activeSlots() {
        int active = 0;
        for (Slot slot : slots) {
            active += slot != null ? 1 : 0;
        }
        return active;
    }

    /** A crack id of our own per slot: negative, so it never clashes with an entity's. */
    private int crackId(int slot) {
        return -1 - (player.getId() * 64 + slot);
    }

    /** A block being broken over time. */
    private static final class Slot {
        final BuildPlan.Task task;
        final ItemStack tool;
        final int ticks;
        int elapsed;
        int stage = -1;

        Slot(BuildPlan.Task task, ItemStack tool, int ticks) {
            this.task = task;
            this.tool = tool;
            this.ticks = ticks;
        }
    }
}
