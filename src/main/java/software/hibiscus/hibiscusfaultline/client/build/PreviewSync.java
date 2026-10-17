/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.build.BuildTransform;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.network.BuildPreviewRequestPayload;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the server's verdict on the current operation up to date, and the ghost blocks that show its result.
 * Requests go out when the operation changes, no more than every few ticks, and again now and then so the bill of
 * materials follows the inventory.
 */
final class PreviewSync {
    private static final int MIN_INTERVAL = 4;
    private static final int REFRESH_INTERVAL = 40;
    private static final int GHOST_REFRESH_INTERVAL = 10;

    private static @Nullable BuildRequest sent;
    private static int sentId;
    private static int ticksSinceSend;
    private static @Nullable BuildPreview result;
    private static LongSet skipped = new LongOpenHashSet();

    private static List<Ghost> ghosts = List.of();
    private static @Nullable BuildRequest ghostsFor;
    private static int ticksSinceGhosts;

    private PreviewSync() {}

    /** One block of the result, drawn translucent where it would land. */
    record Ghost(BlockPos pos, BlockState state) {}

    static void tick() {
        BuildRequest current = BuildClient.isActive() ? currentRequest() : null;
        ticksSinceSend++;
        ticksSinceGhosts++;
        if (current == null) {
            sent = null;
            result = null;
            skipped = new LongOpenHashSet();
            ghosts = List.of();
            ghostsFor = null;
            return;
        }
        boolean changed = !current.equals(sent);
        if ((changed && ticksSinceSend >= MIN_INTERVAL) || ticksSinceSend >= REFRESH_INTERVAL) {
            sent = current;
            sentId++;
            ticksSinceSend = 0;
            ClientPacketDistributor.sendToServer(new BuildPreviewRequestPayload(sentId, current));
        }
        if (!current.equals(ghostsFor) || ticksSinceGhosts >= GHOST_REFRESH_INTERVAL) {
            ghostsFor = current;
            ticksSinceGhosts = 0;
            ghosts = buildGhosts(current);
        }
    }

    static void receive(BuildPreview preview) {
        if (preview.requestId() == sentId) {
            result = preview;
            skipped = new LongOpenHashSet(preview.skipped());
        }
    }

    /** {@return the server's answer for the operation as it is now} {@code null} while it's on its way. */
    static @Nullable BuildPreview result() {
        return result != null && Objects.equals(sent, currentRequest()) ? result : null;
    }

    /** Positions the operation would skip; only meaningful while {@link #result()} is current. */
    static LongSet skipped() {
        return result() != null ? skipped : LongSets.EMPTY_SET;
    }

    static List<Ghost> ghosts() {
        return ghosts;
    }

    static void clear() {
        sent = null;
        result = null;
        skipped = new LongOpenHashSet();
        ghosts = List.of();
        ghostsFor = null;
    }

    private static @Nullable BuildRequest currentRequest() {
        BuildTool tool = BuildClient.tool();
        return Selection.request(tool, BuildClient.optionMask(tool));
    }

    /** The blocks a Move, Clone or Stack would place, read from the client's own world. Empty above the ghost limit. */
    private static List<Ghost> buildGhosts(BuildRequest request) {
        ClientLevel level = Minecraft.getInstance().level;
        BuildTransform transform = request.transform();
        BuildBox box = request.box();
        if (level == null || !ClientConfig.BUILD_GHOSTS.get() || request.volume() > ServerConfig.buildGhostPreviewLimit()) {
            return List.of();
        }
        if (request.tool() == BuildTool.STACK) {
            return stackGhosts(level, request);
        }
        if (!request.hasDestination() || transform.isIdentity()) {
            return List.of();
        }
        boolean move = request.tool() == BuildTool.MOVE;
        boolean keepExisting = request.has(BuildOption.KEEP_EXISTING);
        List<Ghost> result = new ArrayList<>();
        for (BlockPos pos : box.positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockPos dest = transform.apply(box, pos);
            if (keepExisting && !level.getBlockState(dest).isAir() && !(move && box.contains(dest))) {
                continue;
            }
            result.add(new Ghost(dest, transform.apply(state)));
        }
        return result;
    }

    private static List<Ghost> stackGhosts(ClientLevel level, BuildRequest request) {
        BuildBox box = request.box();
        boolean keepExisting = request.has(BuildOption.KEEP_EXISTING);
        List<Ghost> result = new ArrayList<>();
        for (BlockPos offset : request.stack().offsets(box)) {
            for (BlockPos pos : box.positions()) {
                BlockState state = level.getBlockState(pos);
                BlockPos dest = pos.offset(offset);
                if (!state.isAir() && !(keepExisting && !level.getBlockState(dest).isAir())) {
                    result.add(new Ghost(dest.immutable(), state));
                }
            }
        }
        return result;
    }
}
