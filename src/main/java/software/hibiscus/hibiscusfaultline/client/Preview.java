/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.config.Settings;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;
import software.hibiscus.hibiscusfaultline.selection.Selector;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of the selection, worked out with the same {@link Selector} the server uses.
 * It is recomputed only when something that affects it changes, never every frame.
 */
final class Preview {
    private static @Nullable Inputs lastInputs;
    private static int count;
    private static int maxBlocks = ServerConfig.MAX_BLOCKS.getDefault();
    private static OutlineMesh mesh = OutlineMesh.EMPTY;

    private Preview() {}

    static int count() {
        return count;
    }

    static int maxBlocks() {
        return maxBlocks;
    }

    static OutlineMesh mesh() {
        return mesh;
    }

    static void clear() {
        lastInputs = null;
        count = 0;
        mesh = OutlineMesh.EMPTY;
    }

    /** Called once per client tick while the key is held. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            clear();
            return;
        }
        Settings settings = ServerConfig.settings();
        maxBlocks = settings.maxBlocks();
        ClientState.blocked = Selector.blockedReason(player, settings);
        Mode mode = Modes.usable(ClientState.mode, settings);

        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || ClientState.blocked != null || mode == null) {
            clear();
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Inputs inputs = new Inputs(pos, level.getBlockState(pos), hit.getDirection(), mode,
                player.getMainHandItem().copy(), player.getDirection(), Mth.floor(player.getY()), settings);
        if (inputs.sameAs(lastInputs)) {
            return;
        }
        lastInputs = inputs;
        List<BlockPos> selection = Selector.select(level, player, pos, hit.getDirection(), mode, settings);
        count = selection.size();
        mesh = OutlineMesh.of(selection);
    }

    /** Everything the selection depends on besides the surrounding blocks. */
    private record Inputs(BlockPos pos, BlockState state, Direction face, Mode mode, ItemStack held,
                          Direction facing, int feetY, Settings settings) {
        boolean sameAs(@Nullable Inputs other) {
            return other != null
                    && pos.equals(other.pos)
                    && state == other.state
                    && face == other.face
                    && mode == other.mode
                    && ItemStack.matches(held, other.held)
                    && facing == other.facing
                    && feetY == other.feetY
                    && settings.equals(other.settings);
        }
    }
}
