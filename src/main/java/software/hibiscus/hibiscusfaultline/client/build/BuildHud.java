/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildPreview;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.client.FaultlineKeys;
import software.hibiscus.hibiscusfaultline.client.build.FaultlineTheme.MouseHint;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.network.BuildProgressPayload;
import org.jspecify.annotations.Nullable;

/** The tool slot beside the hotbar, the active tool's name and hints, and the selection readouts. */
final class BuildHud {
    static final int SLOT_SIZE = 22;
    private static final int ICON_SIZE = 16;
    private static final int MOUSE_WIDTH = 7;
    private static final int MOUSE_HEIGHT = 9;
    /** Space between the hotbar (or whatever sits at its right end) and the slot. */
    private static final int GAP = 4;
    private static final int PADDING = 4;
    private static final int ITEM_SIZE = 16;
    private static final int MAX_MATERIAL_LINES = 8;
    /** How far above the bottom of the screen the readout sits: clear of the hotbar, health, hunger and armour. */
    private static final int READOUT_RISE = 72;
    private static final int KEYS_RISE = 30;
    private static final int PROGRESS_WIDTH = 120;
    private static final int PROGRESS_HEIGHT = 3;

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
            Font font = minecraft.font;
            drawLabel(graphics, font, x, y, tool);
            int hintsRight = drawContextHints(graphics, font);
            drawMaterials(graphics, font, hintsRight + 12);
            drawReadout(graphics, font);
            drawProgress(graphics, font);
            drawKeys(graphics, font);
        }
    }

    /** The hints for what the mouse does right now, just under the crosshair. {@return the right edge of the block} */
    private static int drawContextHints(GuiGraphicsExtractor graphics, Font font) {
        int centreX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 10;
        if (!ClientConfig.BUILD_HINTS.get()) {
            return centreX;
        }
        List<Hint> hints = contextHints();
        int width = 0;
        for (Hint hint : hints) {
            width = Math.max(width, hintWidth(font, hint));
        }
        int x = centreX - width / 2;
        for (int i = 0; i < hints.size(); i++) {
            drawHint(graphics, font, hints.get(i), x, y + i * (font.lineHeight + 1), FaultlineTheme.text());
        }
        return x + width;
    }

    static List<Hint> contextHints() {
        BuildBox box = Selection.box();
        SelectionInput.Kind hovered = SelectionInput.hover().kind();
        List<Hint> hints = new ArrayList<>();
        if (BuildOperation.running()) {
            hints.add(hint(MouseHint.LEFT, BuildOperation.paused() ? "stop" : "pause"));
            if (BuildOperation.paused()) {
                hints.add(hint(MouseHint.RIGHT, "resume"));
            }
            return hints;
        }
        if (Minecraft.getInstance().hasShiftDown()) {
            hints.add(hint(MouseHint.RIGHT, "link"));
        }
        if (box == null) {
            hints.add(hint(MouseHint.LEFT, Selection.first() == null ? "first_point" : "second_point"));
            return hints;
        }
        if (BuildClient.tool() == BuildTool.STACK) {
            hints.add(hint(MouseHint.SCROLL, "scroll_count"));
            if (Selection.stack().count() > 0) {
                hints.add(hint(MouseHint.SCROLL, "scroll_gap"));
                hints.add(hint(MouseHint.RIGHT, "confirm"));
            }
            return hints;
        }
        boolean destinationTool = Selection.hasDestination(BuildClient.tool());
        if (hovered == SelectionInput.Kind.GIZMO || SelectionInput.dragging()) {
            hints.add(hint(MouseHint.LEFT, "drag"));
        } else if (Selection.pending()) {
            hints.add(hint(MouseHint.LEFT, "cancel"));
        } else if (hovered == SelectionInput.Kind.BLOCK) {
            hints.add(hint(MouseHint.LEFT, "extend"));
        } else if (hovered == SelectionInput.Kind.NONE) {
            hints.add(hint(MouseHint.LEFT, "clear"));
        }
        if (Selection.pending()) {
            hints.add(hint(MouseHint.SCROLL, "scroll_move"));
        } else if (hovered == SelectionInput.Kind.FACE) {
            hints.add(hint(MouseHint.SCROLL, "scroll_face"));
        }
        boolean moveTo = destinationTool && (Selection.pending() || !BuildClient.option(BuildOption.MIDDLE_EXTEND));
        hints.add(hint(MouseHint.MIDDLE, moveTo ? "move_to" : "extend_to"));
        if (!Minecraft.getInstance().hasShiftDown() && (!destinationTool || Selection.pending())) {
            hints.add(hint(MouseHint.RIGHT, "confirm"));
        }
        return hints;
    }

    private static Hint hint(MouseHint mouse, String key) {
        return new Hint(mouse, Component.translatable("hibiscusfaultline.build.hint." + key));
    }

    /** "Size: X Y Z", or "Offset: X Y Z" while a destination is placed, at the bottom centre with coloured axes. */
    private static void drawReadout(GuiGraphicsExtractor graphics, Font font) {
        BuildBox box = Selection.box();
        Component label;
        int[] values;
        if (BuildClient.tool() == BuildTool.STACK && Selection.stack().count() > 0) {
            label = Component.translatable("hibiscusfaultline.build.readout.stack");
            values = new int[] {Selection.stack().count(), Selection.stack().gap()};
        } else if (Selection.pending()) {
            BlockPos offset = Selection.transform().offset();
            label = Component.translatable("hibiscusfaultline.build.readout.offset");
            values = new int[] {offset.getX(), offset.getY(), offset.getZ()};
        } else {
            if (box == null) {
                BlockPos first = Selection.first();
                SelectionInput.Hover hover = SelectionInput.hover();
                if (first == null) {
                    return;
                }
                box = hover.kind() == SelectionInput.Kind.BLOCK ? BuildBox.of(first, hover.block()) : BuildBox.of(first, first);
            }
            label = Component.translatable("hibiscusfaultline.build.readout.size");
            values = new int[] {box.sizeX(), box.sizeY(), box.sizeZ()};
        }
        int[] colours = {FaultlineTheme.axisX(), FaultlineTheme.axisY(), FaultlineTheme.axisZ()};
        int width = font.width(label);
        for (int value : values) {
            width += font.width(" " + value);
        }
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - READOUT_RISE;
        graphics.fill(x - 2, y - 2, x + width + 2, y + font.lineHeight, FaultlineTheme.backdrop());
        graphics.text(font, label, x, y, FaultlineTheme.readoutLabel(), true);
        x += font.width(label);
        for (int i = 0; i < values.length; i++) {
            String text = " " + values[i];
            graphics.text(font, text, x, y, colours[i], true);
            x += font.width(text);
        }
    }

    /** A bar above the readout while an operation runs: blocks done of the total, and the time left or why it's paused. */
    private static void drawProgress(GuiGraphicsExtractor graphics, Font font) {
        BuildProgressPayload progress = BuildOperation.progress();
        if (progress == null) {
            return;
        }
        Component status = switch (progress.state()) {
            case BuildProgressPayload.PAUSED -> Component.translatable("hibiscusfaultline.build.progress.paused");
            case BuildProgressPayload.OUT_OF_REACH -> Component.translatable("hibiscusfaultline.build.progress.out_of_reach");
            default -> Component.translatable("hibiscusfaultline.build.progress.time", String.format("%.0f", Math.ceil(progress.ticksLeft() / 20.0)));
        };
        Component text = Component.translatable("hibiscusfaultline.build.progress", progress.done(), progress.total(), status);
        int width = Math.max(PROGRESS_WIDTH, font.width(text));
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - READOUT_RISE - font.lineHeight - PROGRESS_HEIGHT - 6;
        graphics.text(font, text, (graphics.guiWidth() - font.width(text)) / 2, y, FaultlineTheme.text(), true);
        int barY = y + font.lineHeight + 1;
        int filled = progress.total() > 0 ? (int) ((long) width * progress.done() / progress.total()) : 0;
        graphics.fill(x - 1, barY - 1, x + width + 1, barY + PROGRESS_HEIGHT + 1, FaultlineTheme.backdrop());
        int colour = progress.state() == BuildProgressPayload.RUNNING ? FaultlineTheme.tool(BuildClient.tool()) : FaultlineTheme.textMuted();
        graphics.fill(x, barY, x + filled, barY + PROGRESS_HEIGHT, colour);
    }

    /** The rotate and flip keys, in the bottom right corner, whenever a Move or Clone selection exists. */
    private static void drawKeys(GuiGraphicsExtractor graphics, Font font) {
        if (Selection.box() == null || !Selection.hasDestination(BuildClient.tool())) {
            return;
        }
        List<Component> lines = List.of(
                Component.translatable("hibiscusfaultline.build.keys.rotate", FaultlineKeys.ROTATE.getTranslatedKeyMessage()),
                Component.translatable("hibiscusfaultline.build.keys.flip", FaultlineKeys.FLIP.getTranslatedKeyMessage()));
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int right = graphics.guiWidth() - 2;
        int top = graphics.guiHeight() - KEYS_RISE - lines.size() * font.lineHeight;
        graphics.fill(right - width - 2, top - 2, right + 1, top + lines.size() * font.lineHeight, FaultlineTheme.backdrop());
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            graphics.text(font, line, right - font.width(line), top + i * font.lineHeight, FaultlineTheme.text(), true);
        }
    }

    /**
     * The server's verdict beside the hints: why the operation can't run, and the bill of materials with what the
     * player has, short lines in red.
     */
    private static void drawMaterials(GuiGraphicsExtractor graphics, Font font, int left) {
        BuildPreview preview = PreviewSync.result();
        if (preview == null || (preview.problem().isEmpty() && preview.materials().isEmpty())) {
            return;
        }
        List<BuildPreview.MaterialLine> lines = preview.materials();
        int shown = Math.min(lines.size(), MAX_MATERIAL_LINES);
        Component problem = preview.problem().orElse(null);
        int width = problem != null ? font.width(problem) : 0;
        for (int i = 0; i < shown; i++) {
            width = Math.max(width, ITEM_SIZE + 4 + font.width(materialText(lines.get(i))));
        }
        int height = (problem != null ? font.lineHeight + 2 : 0) + shown * ITEM_SIZE + (lines.size() > shown ? font.lineHeight : 0);
        int x = Math.min(left, graphics.guiWidth() - width - PADDING * 2 - 2);
        int y = graphics.guiHeight() / 2 + 6;
        FaultlineTheme.hibiscus().panel(graphics, x, y, width + PADDING * 2, height + PADDING * 2);
        x += PADDING;
        y += PADDING;
        if (problem != null) {
            graphics.text(font, problem, x, y, FaultlineTheme.negative(), false);
            y += font.lineHeight + 2;
        }
        for (int i = 0; i < shown; i++) {
            BuildPreview.MaterialLine line = lines.get(i);
            graphics.fakeItem(new ItemStack(line.item()), x, y);
            graphics.text(font, materialText(line), x + ITEM_SIZE + 4, y + (ITEM_SIZE - font.lineHeight) / 2 + 1,
                    line.isShort() ? FaultlineTheme.negative() : FaultlineTheme.text(), false);
            y += ITEM_SIZE;
        }
        if (lines.size() > shown) {
            graphics.text(font, Component.translatable("hibiscusfaultline.build.materials.more", lines.size() - shown), x, y,
                    FaultlineTheme.textMuted(), false);
        }
    }

    private static Component materialText(BuildPreview.MaterialLine line) {
        return Component.translatable("hibiscusfaultline.build.materials.line", new ItemStack(line.item()).getHoverName(), line.needed(), line.have());
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
        int x = guiWidth / 2 + 91 + GAP + ClientConfig.BUILD_SLOT_OFFSET_X.get();
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
        return guiHeight - SLOT_SIZE - ClientConfig.BUILD_SLOT_OFFSET_Y.get();
    }
}
