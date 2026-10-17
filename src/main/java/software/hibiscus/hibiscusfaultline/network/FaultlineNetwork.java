/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import software.hibiscus.hibiscusfaultline.server.BuildJobs;
import software.hibiscus.hibiscusfaultline.server.BuildServer;
import software.hibiscus.hibiscusfaultline.server.LinkedContainers;
import software.hibiscus.hibiscusfaultline.server.VeinMineHandler;

public final class FaultlineNetwork {
    private static final String VERSION = "5";

    private FaultlineNetwork() {}

    /**
     * Sends a payload to a player who can receive it. A client without Faultline (or a mock player in a gametest)
     * never negotiated our channels, and sending to it would throw.
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(KeyStatePayload.TYPE, KeyStatePayload.STREAM_CODEC,
                (payload, context) -> VeinMineHandler.setKeyHeld(context.player(), payload.held()));
        registrar.playToServer(ModePayload.TYPE, ModePayload.STREAM_CODEC,
                (payload, context) -> VeinMineHandler.setMode(context.player(), payload.mode()));
        registrar.playToServer(BuildPreviewRequestPayload.TYPE, BuildPreviewRequestPayload.STREAM_CODEC,
                (payload, context) -> BuildServer.requestPreview(context.player(), payload));
        registrar.playToServer(BuildConfirmPayload.TYPE, BuildConfirmPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                BuildJobs.confirm(player, payload.request());
            }
        });
        registrar.playToServer(BuildControlPayload.TYPE, BuildControlPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                BuildJobs.control(player, payload.action());
            }
        });
        registrar.playToServer(BuildLinkPayload.TYPE, BuildLinkPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.unlinkOnly()) {
                    LinkedContainers.unlink(player, payload.pos());
                } else {
                    LinkedContainers.toggle(player, payload.pos());
                }
            }
        });
        // The client registers its handler in RegisterClientPayloadHandlersEvent, keeping client code out of here.
        registrar.playToClient(CooldownPayload.TYPE, CooldownPayload.STREAM_CODEC);
        registrar.playToClient(BuildAccessPayload.TYPE, BuildAccessPayload.STREAM_CODEC);
        registrar.playToClient(BuildPreviewPayload.TYPE, BuildPreviewPayload.STREAM_CODEC);
        registrar.playToClient(BuildProgressPayload.TYPE, BuildProgressPayload.STREAM_CODEC);
        registrar.playToClient(BuildLinkedPayload.TYPE, BuildLinkedPayload.STREAM_CODEC);
    }
}
