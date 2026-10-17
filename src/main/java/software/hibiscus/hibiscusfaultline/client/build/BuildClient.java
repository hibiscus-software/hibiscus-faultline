/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildOptions;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.client.FaultlineKeys;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.network.BuildAccessPayload;
import software.hibiscus.hibiscusfaultline.network.BuildLinkedPayload;
import software.hibiscus.hibiscusfaultline.network.BuildPreviewPayload;
import software.hibiscus.hibiscusfaultline.network.BuildProgressPayload;

/**
 * The build tool slot on the client. While the slot is active the mouse drives the tool instead of the held item:
 * clicks are taken before vanilla sees them, so the server never gets an attack or an item use from them.
 */
public final class BuildClient {
    private static boolean active;
    private static BuildTool tool = BuildTool.MOVE;
    private static BuildOptions options = new BuildOptions();
    /** Which tools the server lets this player use, a bit per {@link BuildTool} ordinal. 0 hides the slot. */
    private static int toolMask;

    private BuildClient() {}

    public static void register(IEventBus modBus, IEventBus bus) {
        modBus.addListener(BuildClient::registerLayers);
        modBus.addListener(BuildClient::registerPayloads);
        bus.addListener(BuildClient::onTick);
        bus.addListener(BuildClient::onScroll);
        bus.addListener(BuildClient::onInteraction);
        bus.addListener(BuildClient::onRenderHand);
        bus.addListener(BuildClient::onLoggingIn);
        bus.addListener(BuildClient::onLoggingOut);
        bus.addListener(BuildClient::onPlayerRecreated);
        bus.addListener(BuildClient::onScreenOpening);
        bus.addListener(BuildClient::onTickPost);
        bus.addListener(SelectionRenderer::submit);
    }

    public static boolean isActive() {
        return active;
    }

    public static BuildTool tool() {
        return tool;
    }

    public static boolean option(BuildOption option) {
        return options.get(tool, option);
    }

    public static int optionMask(BuildTool forTool) {
        return options.mask(forTool);
    }

    /** {@return whether the slot is shown at all} False until the server says the player may use it, and for spectators. */
    public static boolean available() {
        LocalPlayer player = Minecraft.getInstance().player;
        return toolMask != 0 && player != null && !player.isSpectator();
    }

    public static boolean allowed(BuildTool candidate) {
        return (toolMask & (1 << candidate.ordinal())) != 0;
    }

    public static void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (!value) {
            BuildOperation.onLeaveSlot();
            if (Minecraft.getInstance().gui.screen() instanceof PaletteScreen palette) {
                palette.onClose();
            }
        }
    }

    /** Switching tools starts afresh: a selection belongs to the tool it was made with. */
    public static void selectTool(BuildTool next) {
        if (next == tool || !allowed(next)) {
            return;
        }
        tool = next;
        Selection.clear();
        ClientConfig.BUILD_LAST_TOOL.set(next.id());
        ClientConfig.BUILD_LAST_TOOL.save();
    }

    /** Moves to the next tool the player may use, in the given direction. */
    public static void cycleTool(int direction) {
        BuildTool next = tool;
        for (int i = 0; i < BuildTool.values().length; i++) {
            next = next.cycle(direction);
            if (allowed(next)) {
                selectTool(next);
                return;
            }
        }
    }

    public static void setOption(BuildOption option, boolean value) {
        options.set(tool, option, value);
        if (value) {
            BuildOperation.captureMask(tool, option);
        }
        ClientConfig.BUILD_OPTIONS.set(options.write());
        ClientConfig.BUILD_OPTIONS.save();
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HibiscusFaultline.id("build_slot"), BuildHud::render);
        event.wrapLayer(VanillaGuiLayers.HOTBAR, BuildHud::wrapHotbar);
    }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(BuildPreviewPayload.TYPE, (payload, context) -> PreviewSync.receive(payload.preview()));
        event.register(BuildProgressPayload.TYPE, (payload, context) -> BuildOperation.receive(payload));
        event.register(BuildLinkedPayload.TYPE, (payload, context) -> BuildOperation.receiveLinked(payload.containers()));
        event.register(BuildAccessPayload.TYPE, (payload, context) -> {
            toolMask = payload.toolMask();
            if (!allowed(tool)) {
                cycleTool(1);
            }
            if (toolMask == 0) {
                setActive(false);
            }
        });
    }

    /**
     * Runs before vanilla handles the key bindings, so clicks meant for the tool are consumed here and never reach
     * {@code startAttack} or {@code startUseItem}.
     */
    private static void onTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!available()) {
            setActive(false);
            return;
        }
        while (FaultlineKeys.TOGGLE_BUILD_SLOT.consumeClick()) {
            setActive(!active);
        }
        if (!active || minecraft.gui.screen() != null) {
            // Ctrl+R and Ctrl+F only mean something in the slot; don't let presses from outside it pile up.
            drain(FaultlineKeys.ROTATE);
            drain(FaultlineKeys.FLIP);
            drain(FaultlineKeys.UNDO);
            drain(FaultlineKeys.REDO);
            return;
        }

        Options settings = minecraft.options;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (settings.keyHotbarSlots[i].consumeClick()) {
                player.getInventory().setSelectedSlot(i);
                setActive(false);
                return;
            }
        }
        while (settings.keyAttack.consumeClick()) {
            if (BuildOperation.running()) {
                BuildOperation.leftClick();
            } else {
                SelectionInput.leftClick();
            }
        }
        while (settings.keyUse.consumeClick()) {
            BuildOperation.rightClick();
        }
        while (settings.keyPickItem.consumeClick()) {
            SelectionInput.middleClick();
        }
        while (FaultlineKeys.ROTATE.consumeClick()) {
            SelectionInput.rotate();
        }
        while (FaultlineKeys.FLIP.consumeClick()) {
            SelectionInput.flip();
        }
        while (FaultlineKeys.UNDO.consumeClick()) {
            BuildOperation.undo();
        }
        while (FaultlineKeys.REDO.consumeClick()) {
            BuildOperation.redo();
        }
        if (minecraft.hasAltDown()) {
            minecraft.gui.setScreen(new PaletteScreen());
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // Discard.
        }
    }

    /**
     * Ctrl + scroll walks along the hotbar and past either end into the tool slot, like a tenth slot. Inside the slot,
     * Ctrl + scroll leaves it again, and the plain wheel belongs to the tool.
     */
    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (event.isCanceled() || player == null || minecraft.gui.screen() != null || !available()) {
            return;
        }
        // The same whole wheel steps vanilla moves the hotbar by, so "right" here is always the way vanilla goes right,
        // whatever the mouse, scroll speed or smooth-scrolling setting.
        int wheel = event.getAccumulatedScrollY() != 0 ? event.getAccumulatedScrollY() : -event.getAccumulatedScrollX();
        if (wheel == 0) {
            return;
        }
        int size = Inventory.getSelectionSize();
        boolean right = ScrollWheelHandler.getNextScrollWheelSelection(wheel, 0, size) == 1;
        Inventory inventory = player.getInventory();
        if (active) {
            // Inside the slot the wheel never touches the hotbar; only Ctrl + scroll leaves.
            event.setCanceled(true);
            if (minecraft.hasControlDown()) {
                inventory.setSelectedSlot(right ? 0 : size - 1);
                setActive(false);
            } else if (minecraft.hasAltDown()) {
                cycleTool(right ? 1 : -1);
            } else {
                SelectionInput.scroll(wheel > 0);
            }
        } else if (minecraft.hasControlDown()) {
            int slot = inventory.getSelectedSlot();
            if ((right && slot == size - 1) || (!right && slot == 0)) {
                event.setCanceled(true);
                setActive(true);
            }
        }
    }

    /** Catches whatever {@link #onTick} didn't consume, such as holding the button down, so vanilla never acts on it. */
    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Nothing is held while the tool slot is active, so the held item isn't drawn. */
    private static void onRenderHand(RenderHandEvent event) {
        if (active && event.getHand() == InteractionHand.MAIN_HAND) {
            event.setCanceled(true);
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        active = false;
        toolMask = 0;
        tool = BuildTool.byId(ClientConfig.BUILD_LAST_TOOL.get());
        options = BuildOptions.read(ClientConfig.BUILD_OPTIONS.get());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        toolMask = 0;
        Selection.clear();
        PreviewSync.clear();
        BuildOperation.reset();
    }

    /** Changing dimension (or respawning) recreates the player; a selection from the old world means nothing here. */
    private static void onPlayerRecreated(ClientPlayerNetworkEvent.Clone event) {
        Selection.clear();
        PreviewSync.clear();
    }

    /**
     * Escape steps back through the selection (cancel the destination, then clear the selection) before it opens the
     * pause menu. Only a real Escape press counts: the game also pauses when the window loses focus.
     */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (active && event.getNewScreen() instanceof PauseScreen && InputConstants.isKeyDown(InputConstants.KEY_ESCAPE)
                && (BuildOperation.stop() || SelectionInput.back())) {
            event.setCanceled(true);
        }
    }

    private static void onTickPost(ClientTickEvent.Post event) {
        PreviewSync.tick();
    }
}
