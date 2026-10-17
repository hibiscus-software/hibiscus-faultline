/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.client.build.FaultlineTheme.MouseHint;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import org.jspecify.annotations.Nullable;

/** The tool slot beside the hotbar, and the active tool's name and hints. */
final class BuildHud {
    static final int SLOT_SIZE = 22;
    private static final int ICON_SIZE = 16;
    private static final int MOUSE_WIDTH = 7;
    private static final int MOUSE_HEIGHT = 9;
    /** Space between the hotbar (or whatever sits at its right end) and the slot. */
    private static final int GAP = 4;

    private BuildHud() {}

    /** One hint line: an optional mouse icon, then text. */
    record Hint(@Nullable MouseHint mouse, Component text) {}

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!BuildClient.available() || minecraft.gui.hud.isHidden()) {
            return;
        }
        int x = slotX(graphics.guiWidth());
        int y = slotY(graphics.guiHeight());
        BuildTool tool = BuildClient.tool();
        boolean active = BuildClient.isActive();

        FaultlineTheme.hibiscus().panel(graphics, x, y, SLOT_SIZE, SLOT_SIZE);
        if (active) {
            graphics.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, FaultlineTheme.tool(tool));
        }
        int inset = (SLOT_SIZE - ICON_SIZE) / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FaultlineTheme.toolIcon(tool), x + inset, y + inset, ICON_SIZE, ICON_SIZE, active ? 1.0F : 0.6F);

        if (active && !(minecraft.gui.screen() instanceof PaletteScreen)) {
            drawLabel(graphics, minecraft.font, x, y, tool);
        }
    }

    /** The tool's name and hints, beside the slot if the screen is wide enough and above it otherwise. */
    private static void drawLabel(GuiGraphicsExtractor graphics, Font font, int slotX, int slotY, BuildTool tool) {
        List<Hint> hints = ClientConfig.BUILD_HINTS.get() ? hints() : List.of();
        int width = font.width(tool.displayName());
        for (Hint hint : hints) {
            width = Math.max(width, hintWidth(font, hint));
        }
        int lines = 1 + hints.size();
        int height = lines * font.lineHeight;

        int x = slotX + SLOT_SIZE + GAP;
        int bottom = slotY + SLOT_SIZE;
        if (x + width > graphics.guiWidth() - 2) {
            x = Math.max(2, graphics.guiWidth() - 2 - width);
            bottom = slotY - GAP;
        }
        int y = bottom - height;
        graphics.text(font, tool.displayName(), x, y, FaultlineTheme.tool(tool), true);
        for (int i = 0; i < hints.size(); i++) {
            drawHint(graphics, font, hints.get(i), x, y + (i + 1) * font.lineHeight, FaultlineTheme.textMuted());
        }
    }

    /** What the mouse does right now with the active tool. */
    static List<Hint> hints() {
        return List.of(
                new Hint(MouseHint.SCROLL, Component.translatable("hibiscusfaultline.build.hint.alt_scroll")),
                new Hint(MouseHint.SCROLL, Component.translatable("hibiscusfaultline.build.hint.ctrl_scroll")));
    }

    static int hintWidth(Font font, Hint hint) {
        return (hint.mouse() != null ? MOUSE_WIDTH + 3 : 0) + font.width(hint.text());
    }

    static void drawHint(GuiGraphicsExtractor graphics, Font font, Hint hint, int x, int y, int color) {
        if (hint.mouse() != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FaultlineTheme.mouseIcon(hint.mouse()), x, y - 1, MOUSE_WIDTH, MOUSE_HEIGHT);
            x += MOUSE_WIDTH + 3;
        }
        graphics.text(font, hint.text(), x, y, color, true);
    }

    /** Right of the hotbar, past the offhand slot or the attack indicator when either is drawn on that side. */
    static int slotX(int guiWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        int x = guiWidth / 2 + 91 + GAP;
        if (player != null) {
            boolean offhandOnRight = player.getMainArm().getOpposite() == HumanoidArm.RIGHT;
            if (offhandOnRight && !player.getOffhandItem().isEmpty()) {
                x += 29;
            } else if (!offhandOnRight && minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                x += 20;
            }
        }
        return x;
    }

    static int slotY(int guiHeight) {
        return guiHeight - SLOT_SIZE;
    }
}
