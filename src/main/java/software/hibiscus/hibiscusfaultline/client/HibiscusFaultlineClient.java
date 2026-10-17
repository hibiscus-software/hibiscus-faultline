/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;
import software.hibiscus.hibiscusfaultline.client.build.BuildClient;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;
import software.hibiscus.hibiscusfaultline.network.CooldownPayload;
import software.hibiscus.hibiscusfaultline.network.KeyStatePayload;
import software.hibiscus.hibiscusfaultline.network.ModePayload;

/** Client entry point. Only loaded on the physical client. */
@Mod(value = HibiscusFaultline.MOD_ID, dist = Dist.CLIENT)
public final class HibiscusFaultlineClient {
    public HibiscusFaultlineClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(FaultlineKeys::register);
        modBus.addListener(HibiscusFaultlineClient::registerHud);
        modBus.addListener(HibiscusFaultlineClient::registerPayloads);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(HibiscusFaultlineClient::onTick);
        bus.addListener(HibiscusFaultlineClient::onScroll);
        bus.addListener(OutlineRenderer::submit);
        bus.addListener(HibiscusFaultlineClient::onLoggingIn);
        bus.addListener(HibiscusFaultlineClient::onLoggingOut);
        bus.addListener(HibiscusFaultlineClient::onPlayerRecreated);
        BuildClient.register(modBus, bus);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HibiscusFaultline.id("hud"), HudOverlay::render);
    }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(CooldownPayload.TYPE, (payload, context) -> ClientState.cooldownTicks = payload.ticks());
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        boolean held = FaultlineKeys.ACTIVATE.isDown();
        ClientState.held = held;
        if (held != ClientState.sentHeld) {
            ClientState.sentHeld = held;
            ClientPacketDistributor.sendToServer(new KeyStatePayload(held));
        }
        if (ClientState.cooldownTicks > 0) {
            ClientState.cooldownTicks--;
        }
        if (held) {
            Preview.update(minecraft);
        } else {
            Preview.clear();
        }
    }

    /** While the key is held the wheel changes the mode, and the hotbar never sees the scroll. */
    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientState.held || minecraft.player == null || minecraft.gui.screen() != null || event.getScrollDeltaY() == 0) {
            return;
        }
        event.setCanceled(true);
        // Scrolling up moves to the previous mode, the same way it moves the hotbar left.
        int direction = event.getScrollDeltaY() > 0 ? -1 : 1;
        if (ClientConfig.INVERT_SCROLL.get()) {
            direction = -direction;
        }
        Mode next = Modes.cycle(ClientState.mode, direction, ServerConfig.settings());
        if (next != ClientState.mode) {
            selectMode(next);
        }
    }

    private static void selectMode(Mode mode) {
        ClientState.mode = mode;
        ClientPacketDistributor.sendToServer(new ModePayload(Modes.indexOf(mode)));
        if (ClientConfig.REMEMBER_LAST_MODE.get()) {
            ClientConfig.LAST_MODE.set(mode.id());
            ClientConfig.LAST_MODE.save();
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientState.reset();
        Preview.clear();
        ClientPacketDistributor.sendToServer(new ModePayload(Modes.indexOf(ClientState.mode)));
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.held = false;
        ClientState.sentHeld = false;
        Preview.clear();
    }

    /**
     * Respawning or changing dimension clears the key on the server. Forget what was sent so a key
     * that is still held gets sent again on the next tick.
     */
    private static void onPlayerRecreated(ClientPlayerNetworkEvent.Clone event) {
        ClientState.sentHeld = false;
        Preview.clear();
    }
}
