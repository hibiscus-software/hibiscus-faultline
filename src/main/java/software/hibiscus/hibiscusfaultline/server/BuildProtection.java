/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import software.hibiscus.hibiscusfaultline.build.BuildPermissions;

/**
 * Asks protection mods whether the player may change a position, by firing the events vanilla breaking and placing
 * fire. Players with {@code faultline.build.bypass_protection} skip the events.
 */
public final class BuildProtection {
    /** Set while one of our own events is being posted, so Faultline's vein miner doesn't react to it. */
    private static boolean posting;

    private BuildProtection() {}

    public static boolean isPosting() {
        return posting;
    }

    public static boolean bypasses(ServerPlayer player) {
        return PermissionAPI.getPermission(player, BuildPermissions.BYPASS_PROTECTION);
    }

    /** {@return whether the player may break the block at {@code pos}} */
    public static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, boolean bypass) {
        if (bypass) {
            return true;
        }
        posting = true;
        try {
            return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
        } finally {
            posting = false;
        }
    }

    /**
     * {@return whether the player may place at {@code pos}} Called before anything changes there, so the event sees
     * the position as it is; {@link #confirmPlace} fires it again once the block is in, as vanilla does.
     */
    public static boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, boolean bypass) {
        return bypass || !postPlace(player, BlockSnapshot.create(level.dimension(), level, pos));
    }

    /**
     * Fires the place event for a block that has just been placed over {@code before}, the way vanilla does after
     * placing. {@return whether it may stay} If not, the position is put back as it was.
     */
    public static boolean confirmPlace(ServerPlayer player, BlockSnapshot before, int flags, boolean bypass) {
        if (bypass || !postPlace(player, before)) {
            return true;
        }
        before.restore(flags);
        return false;
    }

    private static boolean postPlace(ServerPlayer player, BlockSnapshot snapshot) {
        posting = true;
        try {
            return EventHooks.onBlockPlace(player, snapshot, Direction.UP);
        } finally {
            posting = false;
        }
    }
}
