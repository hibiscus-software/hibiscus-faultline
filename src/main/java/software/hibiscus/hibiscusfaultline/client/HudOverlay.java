/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.selection.BlockedReason;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;

/**
 * The small panel showing the mode, the scroll hint and how many blocks are selected, drawn with the Hibiscus theme
 * so it matches the other Hibiscus mods and follows any resource pack that restyles them.
 */
final class HudOverlay {
    // The panel sprite's 2 px bevel plus 2 px of space before the text.
    private static final int PADDING = 4;

    private HudOverlay() {}

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientState.held || !ClientConfig.HUD_ENABLED.get() || minecraft.player == null) {
            return;
        }

        Mode mode = ClientState.mode;
        BlockedReason blocked = ClientState.effectiveBlock();
        Component status;
        if (blocked == BlockedReason.COOLDOWN) {
            status = Component.translatable("hibiscusfaultline.hud.cooldown", String.format("%.1f", ClientState.cooldownTicks / 20.0F));
        } else if (blocked != null) {
            status = blocked.message();
        } else {
            status = Component.translatable("hibiscusfaultline.hud.blocks", Preview.count(), Preview.maxBlocks());
        }
        List<Component> lines = List.of(mode.displayName(), Component.translatable("hibiscusfaultline.hud.scroll"), status);
        HibiscusTheme theme = HibiscusTheme.current();
        int negative = theme.color(HibiscusTheme.NEGATIVE);
        int[] colours = blocked != null
                ? new int[] {negative, negative, negative}
                : new int[] {theme.accent(), theme.textMuted(), theme.text()};

        Font font = minecraft.font;
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int panelWidth = width + PADDING * 2;
        int panelHeight = lines.size() * font.lineHeight + PADDING * 2;

        ClientConfig.Corner corner = ClientConfig.HUD_CORNER.get();
        boolean right = corner == ClientConfig.Corner.TOP_RIGHT || corner == ClientConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = corner == ClientConfig.Corner.BOTTOM_LEFT || corner == ClientConfig.Corner.BOTTOM_RIGHT;
        // Clamp so a large offset or a small window never pushes the panel off screen.
        int offsetX = Math.min(ClientConfig.HUD_OFFSET_X.get(), Math.max(0, graphics.guiWidth() - panelWidth));
        int offsetY = Math.min(ClientConfig.HUD_OFFSET_Y.get(), Math.max(0, graphics.guiHeight() - panelHeight));
        int x = right ? graphics.guiWidth() - offsetX - panelWidth : offsetX;
        int y = bottom ? graphics.guiHeight() - offsetY - panelHeight : offsetY;

        theme.panel(graphics, x, y, panelWidth, panelHeight);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + PADDING, y + PADDING + i * font.lineHeight, colours[i], false);
        }
    }
}
