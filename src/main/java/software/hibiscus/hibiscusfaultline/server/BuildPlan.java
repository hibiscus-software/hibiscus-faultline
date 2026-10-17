/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildMaterials;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.build.BuildTransform;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * What an operation will do, position by position, worked out from the server's world. The same plan feeds the
 * preview and the operation itself, so what the player sees is what runs.
 */
public final class BuildPlan {
    /**
     * One position: break what's there first (Erase, or a block in the way), then optionally place a block, paid for
     * with {@code cost} in survival, and give it {@code data} if it has a block entity (creative copies only).
     */
    public record Task(BlockPos pos, boolean breakFirst, @Nullable BlockState place, @Nullable Item cost, @Nullable CompoundTag data) {
        public Task(BlockPos pos, boolean breakFirst, @Nullable BlockState place, @Nullable Item cost) {
            this(pos, breakFirst, place, cost, null);
        }
    }

    /** One block Move lifts from {@code source} (where it was {@code state}) and puts down at {@code dest} as {@code placed}. */
    public record MovePair(BlockPos source, BlockPos dest, BlockState state, BlockState placed) {}

    private final List<Task> tasks = new ArrayList<>();
    private final List<MovePair> moves = new ArrayList<>();
    private final LongList skipped = new LongArrayList();
    private final Object2IntMap<Item> needed = new Object2IntLinkedOpenHashMap<>();
    private final int skipLimit;
    private List<BuildPreview.MaterialLine> materials = List.of();
    private @Nullable Component problem;

    private BuildPlan(int skipLimit) {
        this.skipLimit = skipLimit;
    }

    public List<Task> tasks() {
        return tasks;
    }

    /** For Move: the blocks to lift and put down, once {@link #tasks()} has cleared their destinations. */
    public List<MovePair> moves() {
        return moves;
    }

    public boolean isEmpty() {
        return tasks.isEmpty() && moves.isEmpty();
    }

    public Optional<Component> problem() {
        return Optional.ofNullable(problem);
    }

    public BuildPreview preview(int requestId, long volume) {
        return new BuildPreview(requestId, problem(), volume, materials, skipped.toLongArray());
    }

    /** Erase: everything in the box (matching the mask, if on), top layer first, nearest first within a layer. */
    public static BuildPlan erase(ServerPlayer player, BuildRequest request, boolean creative) {
        BuildPlan plan = new BuildPlan(ServerConfig.buildGhostPreviewLimit());
        ServerLevel level = player.level();
        boolean fluids = request.has(BuildOption.FLUIDS);
        boolean withoutTool = ServerConfig.buildEraseWithoutTool();
        for (BlockPos pos : request.box().positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !request.masked(state)) {
                continue;
            }
            if (isLiquid(state)) {
                // Only sources need removing; flowing water dries up on its own once they're gone.
                if (fluids && state.getFluidState().isSource() && mayEdit(player, level, pos)) {
                    plan.tasks.add(new Task(pos.immutable(), true, null, null));
                }
                continue;
            }
            if (!mayEdit(player, level, pos) || BuildValidator.isImmovable(level, pos, state)
                    || (!creative && !withoutTool && MiningCosts.bestTool(player, state) == null)) {
                plan.skip(pos);
                continue;
            }
            plan.tasks.add(new Task(pos.immutable(), true, null, null));
        }
        plan.order(player.position(), false);
        return plan;
    }

    /** Set: the chosen block into every position of the shape, bottom layer first so falling blocks have support. */
    public static BuildPlan set(ServerPlayer player, BuildRequest request, boolean creative) {
        BuildPlan plan = new BuildPlan(ServerConfig.buildGhostPreviewLimit());
        Block block = request.block().map(holder -> holder.value()).orElse(null);
        if (block == null) {
            plan.problem = Component.translatable("hibiscusfaultline.build.problem.no_block");
            return plan;
        }
        Item item = block.asItem();
        if (!creative && item == Items.AIR) {
            plan.problem = Component.translatable("hibiscusfaultline.build.problem.no_item", block.getName());
            return plan;
        }
        BlockState target = block.defaultBlockState();
        ServerLevel level = player.level();
        BuildBox box = request.box();
        boolean airOnly = request.has(BuildOption.AIR_ONLY);
        boolean walls = request.has(BuildOption.WALLS);
        boolean hollow = request.has(BuildOption.HOLLOW);
        boolean withoutTool = ServerConfig.buildEraseWithoutTool();
        for (BlockPos pos : box.positions()) {
            if (!inShape(box, pos, walls, hollow)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state == target || (airOnly && !state.isAir()) || !request.masked(state)) {
                continue;
            }
            boolean occupied = !state.isAir() && !isLiquid(state);
            if (!mayEdit(player, level, pos)
                    || (occupied && BuildValidator.isImmovable(level, pos, state))
                    || (occupied && !creative && !withoutTool && MiningCosts.bestTool(player, state) == null)) {
                plan.skip(pos);
                continue;
            }
            plan.tasks.add(new Task(pos.immutable(), !state.isAir(), target, creative ? null : item));
            if (!creative) {
                plan.needed.mergeInt(item, 1, Integer::sum);
            }
        }
        plan.order(player.position(), true);
        plan.settleMaterials(new BuildSources(player), request.has(BuildOption.PARTIAL));
        return plan;
    }

    /**
     * Move: every block in the selection is lifted and put down at the destination with its block entity intact.
     * Occupied destinations are broken first (drops to the player, with the usual time and hunger) unless Keep
     * Existing is on, in which case the block that would land there stays where it is.
     */
    public static BuildPlan move(ServerPlayer player, BuildRequest request, boolean creative) {
        BuildPlan plan = new BuildPlan(ServerConfig.buildGhostPreviewLimit());
        ServerLevel level = player.level();
        BuildBox box = request.box();
        BuildTransform transform = request.transform();
        boolean moveAir = request.has(BuildOption.MOVE_AIR);
        boolean keepExisting = request.has(BuildOption.KEEP_EXISTING);
        boolean withoutTool = ServerConfig.buildEraseWithoutTool();

        // First pass: which sources move at all, so a destination inside the selection knows whether it's vacated.
        List<BlockPos> sources = new ArrayList<>();
        Set<BlockPos> vacated = new HashSet<>();
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            BlockPos dest = transform.apply(box, pos);
            if (state.isAir() && !moveAir) {
                continue;
            }
            if (!mayEdit(player, level, pos) || !mayEdit(player, level, dest)
                    || (!state.isAir() && BuildValidator.isImmovable(level, pos, state))) {
                plan.skip(dest);
                continue;
            }
            sources.add(pos.immutable());
            if (!state.isAir()) {
                vacated.add(pos.immutable());
            }
        }
        for (BlockPos pos : sources) {
            BlockState state = level.getBlockState(pos);
            BlockPos dest = transform.apply(box, pos);
            BlockState destState = level.getBlockState(dest);
            boolean occupied = !destState.isAir() && !vacated.contains(dest);
            if (occupied && keepExisting) {
                continue;
            }
            if (occupied && (BuildValidator.isImmovable(level, dest, destState)
                    || (!creative && !withoutTool && !isLiquid(destState) && MiningCosts.bestTool(player, destState) == null))) {
                plan.skip(dest);
                continue;
            }
            if (occupied) {
                plan.tasks.add(new Task(dest, true, null, null));
            }
            if (!state.isAir()) {
                plan.moves.add(new MovePair(pos, dest, state, transform.apply(state)));
            }
        }
        plan.order(player.position(), false);
        // Put down from the bottom up, so falling blocks land on what's already there.
        plan.moves.sort(Comparator.comparingInt(pair -> pair.dest().getY()));
        return plan;
    }

    /**
     * Clone and Stack: a copy of every block's state (never its contents, unless creative and the config allows) at
     * each destination, one matching item each in survival. Air copies only with Copy Air, as an erase.
     */
    public static BuildPlan copy(ServerPlayer player, BuildRequest request, boolean creative) {
        BuildPlan plan = new BuildPlan(ServerConfig.buildGhostPreviewLimit());
        ServerLevel level = player.level();
        BuildBox box = request.box();
        boolean copyAir = request.has(BuildOption.COPY_AIR);
        boolean keepExisting = request.has(BuildOption.KEEP_EXISTING);
        boolean withoutTool = ServerConfig.buildEraseWithoutTool();
        boolean contents = creative && ServerConfig.buildCreativeCloneContents();
        List<Function<BlockPos, BlockPos>> copies = new ArrayList<>();
        if (request.tool() == BuildTool.STACK) {
            for (int copy = 1; copy <= request.stack().count(); copy++) {
                BlockPos offset = request.stack().offset(box, copy);
                copies.add(pos -> pos.offset(offset));
            }
        } else {
            copies.add(pos -> request.transform().apply(box, pos));
        }
        BuildTransform turn = request.tool() == BuildTool.STACK ? BuildTransform.IDENTITY : request.transform();

        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() && !copyAir) {
                continue;
            }
            BuildMaterials.Cost cost = creative ? BuildMaterials.Cost.FREE : BuildMaterials.costOf(state, level, pos, player);
            CompoundTag data = null;
            if (contents && level.getBlockEntity(pos) instanceof BlockEntity blockEntity) {
                data = blockEntity.saveWithoutMetadata(level.registryAccess());
            }
            BlockState placed = turn.apply(state);
            for (Function<BlockPos, BlockPos> copy : copies) {
                BlockPos dest = copy.apply(pos).immutable();
                BlockState destState = level.getBlockState(dest);
                if (destState == placed || (state.isAir() && destState.isAir())) {
                    continue;
                }
                boolean occupied = !destState.isAir();
                if (occupied && keepExisting) {
                    continue;
                }
                if (!mayEdit(player, level, dest) || cost == BuildMaterials.Cost.NONE
                        || (occupied && BuildValidator.isImmovable(level, dest, destState))
                        || (occupied && !creative && !withoutTool && !isLiquid(destState) && MiningCosts.bestTool(player, destState) == null)) {
                    plan.skip(dest);
                    continue;
                }
                if (state.isAir()) {
                    plan.tasks.add(new Task(dest, true, null, null));
                    continue;
                }
                Item item = cost instanceof BuildMaterials.Cost.Of(Item paid) ? paid : null;
                plan.tasks.add(new Task(dest, occupied, placed, item, data));
                if (item != null) {
                    plan.needed.mergeInt(item, 1, Integer::sum);
                }
            }
        }
        plan.order(player.position(), true);
        plan.settleMaterials(new BuildSources(player), request.has(BuildOption.PARTIAL));
        return plan;
    }

    /**
     * Compares what the plan needs with what the player has. Short without Partial, the plan has a problem; with it,
     * the tasks the materials run out before (bottom layer first) are dropped and shown as skipped.
     */
    private void settleMaterials(BuildSources sources, boolean partial) {
        if (needed.isEmpty()) {
            return;
        }
        Object2IntMap<Item> left = new Object2IntLinkedOpenHashMap<>();
        List<BuildPreview.MaterialLine> lines = new ArrayList<>();
        boolean isShort = false;
        for (Object2IntMap.Entry<Item> entry : needed.object2IntEntrySet()) {
            int have = sources.count(entry.getKey());
            left.put(entry.getKey(), have);
            lines.add(new BuildPreview.MaterialLine(entry.getKey().builtInRegistryHolder(), entry.getIntValue(), have));
            isShort |= have < entry.getIntValue();
        }
        lines.sort(Comparator.comparingInt(BuildPreview.MaterialLine::needed).reversed());
        materials = lines;
        if (!isShort) {
            return;
        }
        if (!partial) {
            problem = Component.translatable("hibiscusfaultline.build.problem.short");
            return;
        }
        tasks.removeIf(task -> {
            if (task.cost() == null) {
                return false;
            }
            int remaining = left.getInt(task.cost());
            if (remaining > 0) {
                left.put(task.cost(), remaining - 1);
                return false;
            }
            skip(task.pos());
            return true;
        });
    }

    private void skip(BlockPos pos) {
        if (skipped.size() < skipLimit) {
            skipped.add(pos.asLong());
        }
    }

    private void order(Vec3 player, boolean bottomUp) {
        Comparator<Task> byLayer = Comparator.comparingInt(task -> task.pos().getY());
        tasks.sort((bottomUp ? byLayer : byLayer.reversed())
                .thenComparingDouble(task -> task.pos().distToCenterSqr(player)));
    }

    /** Walls: the four sides, full height. Hollow: every outer face. Walls wins when both are on. */
    private static boolean inShape(BuildBox box, BlockPos pos, boolean walls, boolean hollow) {
        boolean side = pos.getX() == box.min().getX() || pos.getX() == box.max().getX()
                || pos.getZ() == box.min().getZ() || pos.getZ() == box.max().getZ();
        if (walls) {
            return side;
        }
        if (hollow) {
            return side || pos.getY() == box.min().getY() || pos.getY() == box.max().getY();
        }
        return true;
    }

    static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    static boolean mayEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.mayInteract(player, pos);
    }
}
