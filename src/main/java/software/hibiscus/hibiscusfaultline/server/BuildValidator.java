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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildMaterials;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildPermissions;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.selection.FaultlineTags;

/**
 * Checks a build operation against the world as the server sees it. Reads only; nothing here changes the world, so
 * it is safe to run for every preview.
 *
 * <p>Protection in a preview uses the checks vanilla itself makes (spawn protection, the world border, build height).
 * The break and place events that claim mods listen to are fired only when an operation is confirmed: firing them for
 * every preview would make some protection mods message the player each time the selection moves.
 */
public final class BuildValidator {
    private BuildValidator() {}

    public static BuildPreview preview(ServerPlayer player, int requestId, BuildRequest request) {
        boolean creative = player.isCreative();
        BuildBox box = request.box();
        BuildBox target = request.target();
        long volume = box.volume();

        if (player.isSpectator() || !BuildPermissions.canUse(player, request.tool())) {
            return problem(requestId, volume, Component.translatable("hibiscusfaultline.build.problem.no_permission"));
        }
        long limit = maxVolume(player, creative);
        if (volume > limit) {
            return problem(requestId, volume, Component.translatable("hibiscusfaultline.build.problem.too_big", volume, limit));
        }
        int reach = ServerConfig.buildMaxDistance(creative);
        Vec3 eye = player.getEyePosition();
        if (box.distanceTo(eye) > reach || target.distanceTo(eye) > reach) {
            return problem(requestId, volume, Component.translatable("hibiscusfaultline.build.problem.too_far", reach));
        }
        ServerLevel level = player.level();
        if (!level.hasChunksAt(box.min(), box.max()) || !level.hasChunksAt(target.min(), target.max())) {
            return problem(requestId, volume, Component.translatable("hibiscusfaultline.build.problem.not_loaded"));
        }

        Scan scan = new Scan(ServerConfig.buildGhostPreviewLimit());
        switch (request.tool()) {
            case MOVE, CLONE -> {
                // Until a destination is placed there is nothing to move onto, only the limits above to check.
                if (!request.transform().isIdentity()) {
                    scanTransfer(player, level, request, creative, scan);
                }
            }
            case ERASE -> scanErase(player, level, box, scan);
            case STACK, SET -> {
                // Their geometry and materials depend on choices made with the tool itself; checked once those exist.
            }
        }
        return scan.finish(requestId, volume, player, creative, request.has(BuildOption.PARTIAL));
    }

    /** {@return the volume limit, from the permission node if a permissions mod sets one, otherwise the config} */
    public static long maxVolume(ServerPlayer player, boolean creative) {
        int override = PermissionAPI.getPermission(player, BuildPermissions.MAX_VOLUME);
        return override > 0 ? override : ServerConfig.buildMaxVolume(creative);
    }

    /** Blocks the build tools never break, move or replace: unbreakable ones and the {@code build_immovable} tag. */
    public static boolean isImmovable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0 || state.is(FaultlineTags.BUILD_IMMOVABLE);
    }

    /** Vanilla's own limits on editing a position: build height, the world border and spawn protection. */
    private static boolean mayEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.mayInteract(player, pos);
    }

    private static void scanTransfer(ServerPlayer player, ServerLevel level, BuildRequest request, boolean creative, Scan scan) {
        boolean move = request.tool() == BuildTool.MOVE;
        boolean takeAir = request.has(move ? BuildOption.MOVE_AIR : BuildOption.COPY_AIR);
        boolean keepExisting = request.has(BuildOption.KEEP_EXISTING);
        BuildBox box = request.box();
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() && !takeAir) {
                continue;
            }
            BlockPos dest = request.transform().apply(box, pos);
            if (!mayEdit(player, level, dest) || (move && (!mayEdit(player, level, pos) || isImmovable(level, pos, state)))) {
                scan.skip(dest);
                continue;
            }
            // A Move into its own footprint lands on blocks that are moving away themselves.
            BlockState destState = level.getBlockState(dest);
            boolean occupied = !destState.isAir() && !(move && box.contains(dest));
            if (occupied && keepExisting) {
                continue;
            }
            if (occupied && isImmovable(level, dest, destState)) {
                scan.skip(dest);
                continue;
            }
            if (!move && !creative) {
                BuildMaterials.Cost cost = BuildMaterials.costOf(state, level, pos, player);
                if (cost instanceof BuildMaterials.Cost.Of(Item item)) {
                    scan.cost(dest, item);
                } else if (cost == BuildMaterials.Cost.NONE) {
                    scan.skip(dest);
                }
            }
        }
    }

    private static void scanErase(ServerPlayer player, ServerLevel level, BuildBox box, Scan scan) {
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && (!mayEdit(player, level, pos) || isImmovable(level, pos, state))) {
                scan.skip(pos);
            }
        }
    }

    private static BuildPreview problem(int requestId, long volume, Component message) {
        return new BuildPreview(requestId, Optional.of(message), volume, List.of(), new long[0]);
    }

    /** What a scan found: positions to skip, and every block that has to be paid for, with its destination. */
    private static final class Scan {
        private final int skipLimit;
        private final LongList skipped = new LongArrayList();
        private final List<Paid> paid = new ArrayList<>();
        private final Object2IntMap<Item> needed = new Object2IntLinkedOpenHashMap<>();

        Scan(int skipLimit) {
            this.skipLimit = skipLimit;
        }

        void skip(BlockPos pos) {
            if (skipped.size() < skipLimit) {
                skipped.add(pos.asLong());
            }
        }

        void cost(BlockPos dest, Item item) {
            paid.add(new Paid(dest.immutable(), item));
            needed.mergeInt(item, 1, Integer::sum);
        }

        BuildPreview finish(int requestId, long volume, ServerPlayer player, boolean creative, boolean partial) {
            if (creative || needed.isEmpty()) {
                return new BuildPreview(requestId, Optional.empty(), volume, List.of(), skipped.toLongArray());
            }
            Object2IntMap<Item> have = BuildMaterials.available(player);
            List<BuildPreview.MaterialLine> lines = new ArrayList<>();
            boolean isShort = false;
            for (Object2IntMap.Entry<Item> entry : needed.object2IntEntrySet()) {
                int owned = have.getInt(entry.getKey());
                lines.add(new BuildPreview.MaterialLine(entry.getKey().builtInRegistryHolder(), entry.getIntValue(), owned));
                isShort |= owned < entry.getIntValue();
            }
            lines.sort(Comparator.comparingInt(BuildPreview.MaterialLine::needed).reversed());

            Optional<Component> problem = Optional.empty();
            if (isShort && partial) {
                // Partial placement fills from the bottom layer up; whatever the materials run out before is skipped.
                paid.sort(Comparator.comparingInt(entry -> entry.dest().getY()));
                Object2IntMap<Item> left = new Object2IntLinkedOpenHashMap<>(have);
                for (Paid entry : paid) {
                    int remaining = left.getInt(entry.item());
                    if (remaining > 0) {
                        left.put(entry.item(), remaining - 1);
                    } else {
                        skip(entry.dest());
                    }
                }
            } else if (isShort) {
                problem = Optional.of(Component.translatable("hibiscusfaultline.build.problem.short"));
            }
            return new BuildPreview(requestId, problem, volume, lines, skipped.toLongArray());
        }
    }

    private record Paid(BlockPos dest, Item item) {}
}
