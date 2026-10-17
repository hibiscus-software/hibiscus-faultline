/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import software.hibiscus.hibiscusfaultline.server.VeinMineHandler;

public final class FaultlineNetwork {
    private static final String VERSION = "2";

    private FaultlineNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(KeyStatePayload.TYPE, KeyStatePayload.STREAM_CODEC,
                (payload, context) -> VeinMineHandler.setKeyHeld(context.player(), payload.held()));
        registrar.playToServer(ModePayload.TYPE, ModePayload.STREAM_CODEC,
                (payload, context) -> VeinMineHandler.setMode(context.player(), payload.mode()));
        // The client registers its handler in RegisterClientPayloadHandlersEvent, keeping client code out of here.
        registrar.playToClient(CooldownPayload.TYPE, CooldownPayload.STREAM_CODEC);
        registrar.playToClient(BuildAccessPayload.TYPE, BuildAccessPayload.STREAM_CODEC);
    }
}
