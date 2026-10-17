/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import software.hibiscus.hibiscusfaultline.build.BuildPermissions;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.network.BuildAccessPayload;

/** Server side of the build tools: permissions, and telling each client which tools it may use. */
public final class BuildServer {
    private BuildServer() {}

    public static void register(IEventBus modBus, IEventBus bus) {
        modBus.addListener(BuildServer::onConfigReloaded);
        bus.addListener(BuildPermissions::register);
        bus.addListener(BuildServer::onLoggedIn);
        bus.addListener(BuildServer::onRespawn);
        bus.addListener(BuildServer::onChangedDimension);
    }

    /** Sends the player their current tool access. Call again whenever a permission may have changed. */
    public static void sendAccess(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BuildAccessPayload(BuildPermissions.toolMask(player)));
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAccess(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAccess(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAccess(player);
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
