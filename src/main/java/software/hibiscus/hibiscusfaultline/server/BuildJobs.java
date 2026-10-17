/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.network.BuildControlPayload;
import org.jspecify.annotations.Nullable;

/** The operations running right now, one per player at most. */
public final class BuildJobs {
    private static final Map<UUID, BuildJob> JOBS = new HashMap<>();

    private BuildJobs() {}

    /**
     * Checks the operation again from scratch, against the world as it is now, and starts it.
     * {@return why it didn't start, if it didn't}
     */
    public static Optional<Component> start(ServerPlayer player, BuildRequest request) {
        if (JOBS.containsKey(player.getUUID())) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.busy"));
        }
        boolean creative = player.isCreative();
        Optional<Component> limits = BuildValidator.checkLimits(player, request, creative);
        if (limits.isPresent()) {
            return limits;
        }
        BuildPlan plan = BuildValidator.plan(player, request, creative).orElse(null);
        if (plan == null) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.no_destination"));
        }
        if (plan.problem().isPresent()) {
            return plan.problem();
        }
        boolean entitiesOnly = request.tool() == BuildTool.MOVE && request.has(BuildOption.MOVE_ENTITIES);
        if (plan.isEmpty() && !entitiesOnly) {
            return Optional.of(Component.translatable("hibiscusfaultline.build.problem.nothing"));
        }
        BuildJob job = new BuildJob(player, request, plan, creative);
        JOBS.put(player.getUUID(), job);
        job.tick();
        if (job.finished()) {
            JOBS.remove(player.getUUID(), job);
        }
        return Optional.empty();
    }

    /** Confirms from the client: start, and tell the player if it couldn't. */
    public static void confirm(ServerPlayer player, BuildRequest request) {
        start(player, request).ifPresent(problem -> player.sendSystemMessage(problem, true));
    }

    public static void control(ServerPlayer player, BuildControlPayload.Action action) {
        BuildJob job = JOBS.get(player.getUUID());
        if (job == null) {
            return;
        }
        switch (action) {
            case PAUSE -> job.pause();
            case RESUME -> job.resume();
            case STOP -> stop(player, Component.translatable("hibiscusfaultline.build.stopped.cancelled"));
        }
    }

    public static @Nullable BuildJob get(ServerPlayer player) {
        return JOBS.get(player.getUUID());
    }

    public static void stop(ServerPlayer player, @Nullable Component reason) {
        BuildJob job = JOBS.remove(player.getUUID());
        if (job != null) {
            job.stop(reason);
        }
    }

    static void tick() {
        if (JOBS.isEmpty()) {
            return;
        }
        for (BuildJob job : new ArrayList<>(JOBS.values())) {
            job.tick();
            if (job.finished()) {
                JOBS.remove(job.player().getUUID(), job);
            }
        }
    }

    static void clear() {
        JOBS.clear();
    }
}
