/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import software.hibiscus.hibiscusfaultline.build.BuildPermissions;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.network.BuildAccessPayload;
import software.hibiscus.hibiscusfaultline.network.BuildPreviewPayload;
import software.hibiscus.hibiscusfaultline.network.BuildPreviewRequestPayload;
import software.hibiscus.hibiscusfaultline.network.FaultlineNetwork;

/** Server side of the build tools: permissions, tool access and previews. */
public final class BuildServer {
    private static final Map<UUID, BuildPreviewRequestPayload> PENDING_PREVIEWS = new HashMap<>();

    private BuildServer() {}

    public static void register(IEventBus modBus, IEventBus bus) {
        modBus.addListener(BuildServer::onConfigReloaded);
        bus.addListener(BuildPermissions::register);
        bus.addListener(BuildServer::onLoggedIn);
        bus.addListener(BuildServer::onRespawn);
        bus.addListener(BuildServer::onChangedDimension);
        bus.addListener(BuildServer::onLoggedOut);
        bus.addListener(BuildServer::onServerTick);
        bus.addListener(BuildServer::onServerStopped);
        bus.addListener(BuildCommands::register);
    }

    /**
     * Queues a preview. Only the newest request per player is kept and they are answered at the end of the tick, so
     * a client sending many requests costs one scan per tick at most.
     */
    public static void requestPreview(Player player, BuildPreviewRequestPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PENDING_PREVIEWS.put(serverPlayer.getUUID(), payload);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        BuildJobs.tick();
        if (PENDING_PREVIEWS.isEmpty()) {
            return;
        }
        PlayerList players = event.getServer().getPlayerList();
        for (Map.Entry<UUID, BuildPreviewRequestPayload> entry : PENDING_PREVIEWS.entrySet()) {
            ServerPlayer player = players.getPlayer(entry.getKey());
            if (player != null) {
                BuildPreviewRequestPayload request = entry.getValue();
                FaultlineNetwork.send(player,
                        new BuildPreviewPayload(BuildValidator.preview(player, request.requestId(), request.request())));
            }
        }
        PENDING_PREVIEWS.clear();
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_PREVIEWS.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildJobs.stop(player, null);
            LinkedContainers.forget(player);
            BuildHistory.forget(player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        PENDING_PREVIEWS.clear();
        BuildJobs.clear();
        LinkedContainers.clear();
        BuildHistory.clear();
    }

    /** Sends the player their current tool access. Call again whenever a permission may have changed. */
    public static void sendAccess(ServerPlayer player) {
        FaultlineNetwork.send(player, new BuildAccessPayload(BuildPermissions.toolMask(player)));
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAccess(player);
            LinkedContainers.send(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAccess(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BuildJobs.stop(player, Component.translatable("hibiscusfaultline.build.stopped.left"));
            BuildHistory.forget(player);
            sendAccess(player);
            LinkedContainers.send(player);
        }
    }

    /** Turning {@code buildTools.enabled} on or off takes effect for everyone online straight away. */
    private static void onConfigReloaded(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() == ServerConfig.SPEC && server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(BuildServer::sendAccess));
        }
    }
}
