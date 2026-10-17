/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscuslib.client.gui.widget.ToggleButton;
import org.jspecify.annotations.Nullable;

/**
 * Open while Alt is held in the tool slot: the tool palette stacked above the slot, and the current tool's options to
 * its left. Being a screen releases the mouse so the options can be clicked; releasing Alt closes it.
 */
final class PaletteScreen extends Screen {
    private static final int CELL = BuildHud.SLOT_SIZE;
    private static final int CELL_GAP = 1;
    private static final int OPTION_HEIGHT = 16;
    private static final int OPTION_GAP = 2;
    private static final int OPTION_MIN_WIDTH = 120;

    PaletteScreen() {
        super(Component.translatable("hibiscusfaultline.build.palette"));
    }

    @Override
    protected void init() {
        BuildTool tool = BuildClient.tool();
        List<BuildOption> options = tool.options();
        int width = OPTION_MIN_WIDTH;
        for (BuildOption option : options) {
            // The toggle switch and its padding take about 30 px beside the label.
            width = Math.max(width, font.width(option.displayName()) + 30);
        }
        int right = BuildHud.slotX(this.width) - 4;
        int bottom = BuildHud.slotY(this.height) + CELL;
        boolean creative = minecraft.player != null && minecraft.player.isCreative();
        for (int i = 0; i < options.size(); i++) {
            BuildOption option = options.get(i);
            int y = bottom - (options.size() - i) * (OPTION_HEIGHT + OPTION_GAP) + OPTION_GAP;
            ToggleButton button = new ToggleButton(right - width, y, width, option.displayName(),
                    BuildClient.option(option), value -> BuildClient.setOption(option, value));
            if (option.creativeOnly() && !creative) {
                button.active = false;
                button.withTooltip(Component.translatable("hibiscusfaultline.build.option.creative_only"));
            }
            addRenderableWidget(button);
        }
    }

    @Override
    public void tick() {
        if (!BuildClient.isActive() || !minecraft.hasAltDown()) {
            onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The world stays visible: this is part of the HUD, not a menu.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        BuildTool current = BuildClient.tool();
        BuildTool hovered = toolAt(mouseX, mouseY);
        int x = BuildHud.slotX(width);
        for (BuildTool tool : BuildTool.values()) {
            int y = cellY(tool);
            boolean allowed = BuildClient.allowed(tool);
            FaultlineTheme.hibiscus().button(graphics, x, y, CELL, CELL, allowed, tool == hovered, false);
            int inset = (CELL - 16) / 2;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FaultlineTheme.toolIcon(tool), x + inset, y + inset, 16, 16, allowed ? 1.0F : 0.35F);
            if (tool == current) {
                graphics.outline(x - 1, y - 1, CELL + 2, CELL + 2, FaultlineTheme.tool(tool));
                graphics.text(font, tool.displayName(), x + CELL + 4, y + (CELL - font.lineHeight) / 2 + 1, FaultlineTheme.tool(tool), true);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        BuildTool tool = toolAt(event.x(), event.y());
        if (tool != null) {
            select(tool);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            BuildTool before = BuildClient.tool();
            BuildClient.cycleTool(scrollY > 0 ? -1 : 1);
            if (BuildClient.tool() != before) {
                rebuildWidgets();
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(BuildTool tool) {
        if (tool != BuildClient.tool() && BuildClient.allowed(tool)) {
            BuildClient.selectTool(tool);
            rebuildWidgets();
        }
    }

    /** Move sits directly above the slot, and the rest stack upwards in tool order. */
    private int cellY(BuildTool tool) {
        return BuildHud.slotY(height) - (tool.ordinal() + 1) * (CELL + CELL_GAP);
    }

    private @Nullable BuildTool toolAt(double mouseX, double mouseY) {
        int x = BuildHud.slotX(width);
        if (mouseX < x || mouseX >= x + CELL) {
            return null;
        }
        for (BuildTool tool : BuildTool.values()) {
            int y = cellY(tool);
            if (mouseY >= y && mouseY < y + CELL) {
                return tool;
            }
        }
        return null;
    }
}
