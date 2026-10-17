/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import software.hibiscus.hibiscusfaultline.build.BuildMaterials;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * Each player's recent operations, for undo and redo. Kept in memory for the session, cleared on logout and dimension
 * change.
 *
 * <p>Undo only touches positions still exactly as the operation left them, so it never overwrites anyone's work since.
 * A survival operation is undone by survival rules whatever mode the player is in now: placed blocks are refunded, and
 * erased blocks come back only if the player can pay for them again, so undo never makes blocks from nothing. A
 * creative operation is simply reversed. Moved blocks go back with whatever their block entity holds now.
 */
public final class BuildHistory {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, Stacks> HISTORY = new HashMap<>();

    private BuildHistory() {}

    /** One change at one position. {@code to} is filled in when the operation finishes. */
    public static final class Change {
        final BlockPos pos;
        final BlockState from;
        final @Nullable CompoundTag fromData;
        BlockState to;

        Change(BlockPos pos, BlockState from, @Nullable CompoundTag fromData) {
            this.pos = pos.immutable();
            this.from = from;
            this.fromData = fromData;
            this.to = from;
        }
    }

    /** A block Move carried from {@code source} (where it was {@code state}) to {@code dest} (where it became {@code placed}). */
    public static final class Moved {
        final BlockPos source;
        final BlockPos dest;
        final BlockState state;
        BlockState placed;

        Moved(BlockPos source, BlockPos dest, BlockState state) {
            this.source = source.immutable();
            this.dest = dest.immutable();
            this.state = state;
            this.placed = state;
        }
    }

    /** Everything one operation did, in order, and the rules it ran under. */
    public static final class Operation {
        final ResourceKey<Level> dimension;
        final boolean creative;
        final List<Change> changes = new ArrayList<>();
        final List<Moved> moves = new ArrayList<>();

        Operation(ResourceKey<Level> dimension, boolean creative) {
            this.dimension = dimension;
            this.creative = creative;
        }

        public void changed(BlockPos pos, BlockState from, @Nullable CompoundTag fromData) {
            changes.add(new Change(pos, from, fromData));
        }

        public void moved(BlockPos source, BlockPos dest, BlockState state) {
            moves.add(new Moved(source, dest, state));
        }

        int size() {
            return changes.size() + moves.size() * 2;
        }

        boolean isEmpty() {
            return changes.isEmpty() && moves.isEmpty();
        }

        /**
         * Records how the operation left each position, once its final shape updates have run: each change ends
         * where the next change at the same position starts, and the last one at whatever is there now.
         */
        void seal(ServerLevel level) {
            Map<BlockPos, Change> later = new HashMap<>();
            for (int i = changes.size() - 1; i >= 0; i--) {
                Change change = changes.get(i);
                Change next = later.put(change.pos, change);
                change.to = next != null ? next.from : level.getBlockState(change.pos);
            }
            for (Moved move : moves) {
                move.placed = level.getBlockState(move.dest);
            }
        }
    }

    private static final class Stacks {
        final Deque<Operation> undo = new ArrayDeque<>();
        final Deque<Operation> redo = new ArrayDeque<>();
    }

    public static Operation begin(ServerPlayer player, boolean creative) {
        return new Operation(player.level().dimension(), creative);
    }

    /** Keeps a finished operation for undo, dropping the oldest ones past the configured depth and size. */
    static void record(ServerPlayer player, Operation operation) {
        operation.seal(player.level());
        if (operation.isEmpty() || operation.size() > ServerConfig.buildUndoMaxBlocks()) {
            return;
        }
        Stacks stacks = HISTORY.computeIfAbsent(player.getUUID(), id -> new Stacks());
        stacks.redo.clear();
        stacks.undo.push(operation);
        trim(stacks.undo);
    }

    /** Drops the oldest operations: past the depth for their game mode, then while the total is over the block budget. */
    private static void trim(Deque<Operation> undo) {
        int survival = 0;
        int creative = 0;
        long blocks = 0;
        for (Iterator<Operation> it = undo.iterator(); it.hasNext(); ) {
            Operation operation = it.next();
            int count = operation.creative ? ++creative : ++survival;
            int depth = operation.creative ? ServerConfig.buildUndoDepthCreative() : ServerConfig.buildUndoDepth();
            blocks += operation.size();
            if (count > depth || blocks > ServerConfig.buildUndoMaxBlocks()) {
                it.remove();
            }
        }
    }

    public static boolean canUndo(ServerPlayer player) {
        Stacks stacks = HISTORY.get(player.getUUID());
        return stacks != null && !stacks.undo.isEmpty();
    }

    /** {@return a message saying how it went} */
    public static Component undo(ServerPlayer player) {
        Stacks stacks = HISTORY.get(player.getUUID());
        if (stacks == null || stacks.undo.isEmpty()) {
            return Component.translatable("hibiscusfaultline.build.undo.nothing");
        }
        Operation operation = stacks.undo.pop();
        Result result = apply(player, operation, false);
        stacks.redo.push(operation);
        return Component.translatable("hibiscusfaultline.build.undo.done", result.applied, result.skipped);
    }

    public static Component redo(ServerPlayer player) {
        Stacks stacks = HISTORY.get(player.getUUID());
        if (stacks == null || stacks.redo.isEmpty()) {
            return Component.translatable("hibiscusfaultline.build.redo.nothing");
        }
        Operation operation = stacks.redo.pop();
        Result result = apply(player, operation, true);
        stacks.undo.push(operation);
        return Component.translatable("hibiscusfaultline.build.redo.done", result.applied, result.skipped);
    }

    public static void forget(ServerPlayer player) {
        HISTORY.remove(player.getUUID());
    }

    public static void clear() {
        HISTORY.clear();
    }

    private record Result(int applied, int skipped) {}

    /**
     * Plays an operation backwards (undo) or forwards again (redo). Every position is checked against how the
     * operation (or the undo) left it, protection is asked again, and survival operations pay and refund as they go.
     */
    private static Result apply(ServerPlayer player, Operation operation, boolean forward) {
        ServerLevel level = player.level();
        if (level.dimension() != operation.dimension) {
            return new Result(0, operation.size());
        }
        boolean bypass = BuildProtection.bypasses(player);
        BuildSources sources = new BuildSources(player);
        Set<BlockPos> touched = new LinkedHashSet<>();
        int applied = 0;
        int skipped = 0;

        // Moves first, as one unit: pick up everything that's still where the last pass put it, then put it all back.
        List<Moved> moves = forward ? operation.moves : operation.moves.reversed();
        List<Moved> carried = new ArrayList<>();
        List<CompoundTag> data = new ArrayList<>();
        Set<BlockPos> leaving = new LinkedHashSet<>();
        for (Moved move : moves) {
            leaving.add(forward ? move.source : move.dest);
        }
        for (Moved move : moves) {
            BlockPos at = forward ? move.source : move.dest;
            BlockPos to = forward ? move.dest : move.source;
            BlockState here = forward ? move.state : move.placed;
            BlockState target = level.getBlockState(to);
            if (level.getBlockState(at) != here || (!target.isAir() && !leaving.contains(to))
                    || !BuildProtection.mayBreak(player, level, at, here, bypass) || !BuildProtection.mayPlace(player, level, to, bypass)) {
                skipped++;
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(at);
            data.add(blockEntity != null ? blockEntity.saveWithoutMetadata(level.registryAccess()) : null);
            carried.add(move);
        }
        for (Moved move : carried) {
            BlockPos at = forward ? move.source : move.dest;
            level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            touched.add(at);
        }
        for (int i = 0; i < carried.size(); i++) {
            Moved move = carried.get(i);
            BlockPos to = forward ? move.dest : move.source;
            level.setBlock(to, forward ? move.placed : move.state, FLAGS);
            if (data.get(i) != null && level.getBlockEntity(to) instanceof BlockEntity blockEntity) {
                load(level, blockEntity, data.get(i));
            }
            touched.add(to);
            applied++;
        }

        List<Change> changes = forward ? operation.changes : operation.changes.reversed();
        for (Change change : changes) {
            BlockState expected = forward ? change.from : change.to;
            BlockState target = forward ? change.to : change.from;
            BlockPos pos = change.pos;
            if (level.getBlockState(pos) != expected
                    || (!expected.isAir() && !BuildProtection.mayBreak(player, level, pos, expected, bypass))
                    || (!target.isAir() && !BuildProtection.mayPlace(player, level, pos, bypass))) {
                skipped++;
                continue;
            }
            Item payment = null;
            if (!operation.creative && !target.isAir() && !BuildPlan.isLiquid(target)) {
                BuildMaterials.Cost cost = BuildMaterials.costOf(target, level, pos, player);
                if (cost == BuildMaterials.Cost.NONE) {
                    skipped++;
                    continue;
                }
                if (cost instanceof BuildMaterials.Cost.Of(Item item)) {
                    if (sources.count(item) < 1) {
                        skipped++;
                        continue;
                    }
                    payment = item;
                }
            }
            List<ItemStack> refunds = new ArrayList<>();
            if (level.getBlockEntity(pos) instanceof Container container) {
                // Whatever was put in since goes back to the player, never into the void or onto the floor.
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (!container.getItem(slot).isEmpty()) {
                        refunds.add(container.getItem(slot).copy());
                    }
                }
                container.clearContent();
            }
            if (!operation.creative && !expected.isAir() && !BuildPlan.isLiquid(expected)
                    && BuildMaterials.costOf(expected, level, pos, player) instanceof BuildMaterials.Cost.Of(Item item)) {
                refunds.add(new ItemStack(item));
            }
            if (payment != null) {
                sources.take(payment);
            }
            level.setBlock(pos, target, FLAGS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            if (operation.creative && !forward && change.fromData != null && level.getBlockEntity(pos) instanceof BlockEntity blockEntity) {
                load(level, blockEntity, change.fromData);
            }
            sources.give(refunds);
            touched.add(pos);
            applied++;
        }

        for (BlockPos pos : touched) {
            BlockState state = level.getBlockState(pos);
            level.updateNeighborsAt(pos, state.getBlock());
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
        return new Result(applied, skipped);
    }

    private static void load(ServerLevel level, BlockEntity blockEntity, CompoundTag data) {
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        blockEntity.setChanged();
    }
}
