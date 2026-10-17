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
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * What an operation will do, position by position, worked out from the server's world. The same plan feeds the
 * preview and the operation itself, so what the player sees is what runs.
 */
public final class BuildPlan {
    /**
     * One position: break what's there first (Erase, or Set over an occupied block), then optionally place a block,
     * paid for with {@code cost} in survival.
     */
    public record Task(BlockPos pos, boolean breakFirst, @Nullable BlockState place, @Nullable Item cost) {}

    private final List<Task> tasks = new ArrayList<>();
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
