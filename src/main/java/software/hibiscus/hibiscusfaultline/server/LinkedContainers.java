/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.network.BuildLinkedPayload;
import software.hibiscus.hibiscusfaultline.network.FaultlineNetwork;
import org.jspecify.annotations.Nullable;

/**
 * Containers a player has linked as extra material sources. Anything exposing NeoForge's item handler capability
 * works: chests, barrels, shulker boxes and other mods' storage. Kept for the session only.
 */
public final class LinkedContainers {
    private static final Map<UUID, List<GlobalPos>> LINKS = new HashMap<>();

    private LinkedContainers() {}

    /**
     * Links or unlinks a container. Linking counts as opening it, so the right-click event fires first and a claim mod
     * that keeps the player out of the chest keeps them from linking it too.
     */
    public static void toggle(ServerPlayer player, BlockPos pos) {
        List<GlobalPos> links = LINKS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        GlobalPos global = GlobalPos.of(player.level().dimension(), pos.immutable());
        if (links.remove(global)) {
            message(player, Component.translatable("hibiscusfaultline.build.link.removed"));
        } else if (links.size() >= ServerConfig.buildMaxLinkedContainers()) {
            message(player, Component.translatable("hibiscusfaultline.build.link.full", ServerConfig.buildMaxLinkedContainers()));
        } else if (!inRange(player, global)) {
            message(player, Component.translatable("hibiscusfaultline.build.link.too_far", ServerConfig.buildLinkRange()));
        } else if (handler(player.level(), pos) == null) {
            message(player, Component.translatable("hibiscusfaultline.build.link.not_container"));
        } else if (!player.level().mayInteract(player, pos) || CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).isCanceled()) {
            message(player, Component.translatable("hibiscusfaultline.build.link.denied"));
        } else {
            links.add(global);
            message(player, Component.translatable("hibiscusfaultline.build.link.added"));
        }
        send(player);
    }

    public static void unlink(ServerPlayer player, BlockPos pos) {
        List<GlobalPos> links = LINKS.get(player.getUUID());
        if (links != null && links.remove(GlobalPos.of(player.level().dimension(), pos))) {
            send(player);
        }
    }

    /**
     * {@return the item handlers of the player's linked containers that can be used right now} A container has to be
     * in the player's dimension, within range, loaded, and still a container.
     */
    public static List<ResourceHandler<ItemResource>> handlers(ServerPlayer player) {
        List<GlobalPos> links = LINKS.get(player.getUUID());
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        for (GlobalPos link : links) {
            if (inRange(player, link) && player.level().isLoaded(link.pos())) {
                ResourceHandler<ItemResource> handler = handler(player.level(), link.pos());
                if (handler != null) {
                    handlers.add(handler);
                }
            }
        }
        return handlers;
    }

    /** Tells the client which containers are linked in its current dimension, for the options panel. */
    public static void send(ServerPlayer player) {
        List<BlockPos> here = new ArrayList<>();
        for (GlobalPos link : LINKS.getOrDefault(player.getUUID(), List.of())) {
            if (link.dimension() == player.level().dimension()) {
                here.add(link.pos());
            }
        }
        FaultlineNetwork.send(player, new BuildLinkedPayload(here));
    }

    public static void forget(ServerPlayer player) {
        LINKS.remove(player.getUUID());
    }

    public static void clear() {
        LINKS.clear();
    }

    private static boolean inRange(ServerPlayer player, GlobalPos link) {
        int range = ServerConfig.buildLinkRange();
        return link.dimension() == player.level().dimension()
                && link.pos().distToCenterSqr(player.position()) <= (double) range * range;
    }

    private static @Nullable ResourceHandler<ItemResource> handler(ServerLevel level, BlockPos pos) {
        return level.getCapability(Capabilities.Item.BLOCK, pos, null);
    }

    private static void message(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true);
    }
}
