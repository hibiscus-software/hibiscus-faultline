/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildPermissions;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.selection.FaultlineTags;
import org.jspecify.annotations.Nullable;

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
        long volume = request.volume();
        Optional<Component> limits = checkLimits(player, request, creative);
        if (limits.isPresent()) {
            return problem(requestId, volume, limits.get());
        }
        return plan(player, request, creative).map(plan -> plan.preview(requestId, volume))
                .orElseGet(() -> problem(requestId, volume, null));
    }

    /**
     * {@return the plan for an operation, or nothing while there's nothing to plan yet} Move and Clone need a placed
     * destination and Stack at least one copy before they have one.
     */
    public static Optional<BuildPlan> plan(ServerPlayer player, BuildRequest request, boolean creative) {
        return Optional.ofNullable(switch (request.tool()) {
            case ERASE -> BuildPlan.erase(player, request, creative);
            case SET -> BuildPlan.set(player, request, creative);
            case MOVE -> request.transform().isIdentity() ? null : BuildPlan.move(player, request, creative);
            case CLONE -> request.transform().isIdentity() ? null : BuildPlan.copy(player, request, creative);
            case STACK -> request.stack().count() == 0 ? null : BuildPlan.copy(player, request, creative);
        });
    }

    /**
     * The checks every operation passes before anything else: permission, game mode, volume, reach and loaded chunks.
     * {@return why the operation can't run, if it can't}
     */
    public static Optional<Component> checkLimits(ServerPlayer player, BuildRequest request, boolean creative) {
        BuildBox box = request.box();
        BuildBox target = request.target();
        long volume = request.volume();
        if (player.isSpectator() || !BuildPermissions.canUse(player, request.tool())) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.no_permission"));
        }
        if (request.stack().count() > ServerConfig.buildMaxStack()) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.too_many", ServerConfig.buildMaxStack()));
        }
        long limit = maxVolume(player, creative);
        if (volume > limit) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.too_big", volume, limit));
        }
        int reach = ServerConfig.buildMaxDistance(creative);
        Vec3 eye = player.getEyePosition();
        if (box.distanceTo(eye) > reach || target.distanceTo(eye) > reach) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.too_far", reach));
        }
        ServerLevel level = player.level();
        if (!level.hasChunksAt(box.min(), box.max()) || !level.hasChunksAt(target.min(), target.max())) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.not_loaded"));
        }
        return Optional.empty();
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

    private static BuildPreview problem(int requestId, long volume, @Nullable Component message) {
        return new BuildPreview(requestId, Optional.ofNullable(message), volume, List.of(), new long[0]);
    }
}
