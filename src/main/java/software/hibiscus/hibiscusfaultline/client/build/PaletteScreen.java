/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.ToggleButton;
import org.jspecify.annotations.Nullable;

/**
 * Open while Alt is held in the tool slot: the tool palette stacked above the slot, and the current tool's options to
 * its left. Being a screen releases the mouse so the options can be clicked; releasing Alt closes it.
 */
final class PaletteScreen extends Screen {
    private static final int CELL = BuildHud.SLOT_SIZE;
    private static final int CELL_GAP = 1;
    private static final int NAME_GAP = 4;
    private static final int PANEL_GAP = 8;
    private static final int OPTION_HEIGHT = 16;
    private static final int OPTION_GAP = 2;
    private static final int OPTION_MIN_WIDTH = 120;

    PaletteScreen() {
        super(Component.translatable("hibiscusfaultline.build.palette"));
    }

    /**
     * Lays out the panel left of the palette, bottom up: the tool's options, then Set's block, then the linked
     * containers, each of which unlinks when clicked.
     */
    @Override
    protected void init() {
        BuildTool tool = BuildClient.tool();
        List<AbstractWidget> rows = new ArrayList<>();
        for (BlockPos pos : BuildOperation.linked()) {
            Component name = minecraft.level != null ? minecraft.level.getBlockState(pos).getBlock().getName() : Component.empty();
            rows.add(new TextButton(0, Component.translatable("hibiscusfaultline.build.linked", name, pos.getX(), pos.getY(), pos.getZ()),
                    button -> BuildOperation.unlink(pos)).withTooltip(Component.translatable("hibiscusfaultline.build.linked.unlink")));
        }
        if (tool == BuildTool.SET) {
            Component block = BuildOperation.setBlock().map(holder -> holder.value().getName())
                    .orElse(Component.translatable("hibiscusfaultline.build.block.none"));
            rows.add(new TextButton(0, Component.translatable("hibiscusfaultline.build.block", block),
                    button -> minecraft.gui.setScreen(new BlockPickerScreen())));
        }
        boolean creative = minecraft.player != null && minecraft.player.isCreative();
        for (BuildOption option : tool.options()) {
            ToggleButton button = new ToggleButton(0, 0, 0, label(tool, option), BuildClient.option(option), value -> {
                BuildClient.setOption(option, value);
                if (option == BuildOption.MASK || option == BuildOption.REPLACE) {
                    rebuildWidgets();
                }
            });
            if (option.creativeOnly() && !creative) {
                button.active = false;
                button.withTooltip(Component.translatable("hibiscusfaultline.build.option.creative_only"));
            }
            rows.add(button);
        }

        int width = OPTION_MIN_WIDTH;
        for (AbstractWidget row : rows) {
            // Toggles and buttons need about 30 px beside the label.
            width = Math.max(width, font.width(row.getMessage()) + 30);
        }
        // Right of the palette, past the room the tool names take; on the left only if the screen is too narrow.
        int paletteX = BuildHud.slotX(this.width);
        int left = paletteX + CELL + NAME_GAP + maxNameWidth() + PANEL_GAP;
        if (left + width > this.width - 2) {
            left = paletteX - PANEL_GAP - width;
        }
        int y = BuildHud.slotY(this.height) + CELL - rows.size() * (OPTION_HEIGHT + OPTION_GAP) + OPTION_GAP;
        for (AbstractWidget row : rows) {
            row.setRectangle(width, OPTION_HEIGHT, left, y);
            addRenderableWidget(row);
            y += OPTION_HEIGHT + OPTION_GAP;
        }
    }

    private int maxNameWidth() {
        int width = 0;
        for (BuildTool tool : BuildTool.values()) {
            width = Math.max(width, font.width(tool.displayName()));
        }
        return width;
    }

    /** The option's name, with the mask block for Mask and Replace once one is set. */
    private static Component label(BuildTool tool, BuildOption option) {
        if (option == BuildOption.MASK || option == BuildOption.REPLACE) {
            return BuildOperation.mask(tool)
                    .<Component>map(block -> Component.translatable("hibiscusfaultline.build.option.with_mask", option.displayName(), block.value().getName()))
                    .orElse(option.displayName());
        }
        return option.displayName();
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
        // Draw the unselected cells first so the selected one's frame sits on top of its neighbours.
        for (BuildTool tool : BuildTool.values()) {
            if (tool != current) {
                drawCell(graphics, x, tool, false, tool == hovered);
            }
        }
        drawCell(graphics, x, current, true, current == hovered);
        graphics.text(font, current.displayName(), x + CELL + NAME_GAP, cellY(current) + (CELL - font.lineHeight) / 2 + 1,
                FaultlineTheme.tool(current), true);
    }

    private void drawCell(GuiGraphicsExtractor graphics, int x, BuildTool tool, boolean selected, boolean hovered) {
        int y = cellY(tool);
        BuildHud.drawSlot(graphics, x, y, tool, selected);
        if (!BuildClient.allowed(tool)) {
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xA0000000);
        } else if (hovered && !selected) {
            graphics.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, 0x40FFFFFF);
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

    /** The column takes the slot's place: Move where the slot is, the rest stacking upwards in tool order. */
    private int cellY(BuildTool tool) {
        return BuildHud.slotY(height) - tool.ordinal() * (CELL + CELL_GAP);
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
