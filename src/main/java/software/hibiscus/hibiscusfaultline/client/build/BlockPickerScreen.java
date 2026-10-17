/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import software.hibiscus.hibiscuslib.client.gui.BaseScreen;
import software.hibiscus.hibiscuslib.client.gui.widget.TextButton;
import software.hibiscus.hibiscuslib.client.gui.widget.TextInput;
import org.jspecify.annotations.Nullable;

/**
 * Picks the block Set fills with. In survival it lists the placeable blocks you carry; in creative, every block in the
 * game, searchable like the creative inventory. "Use offhand" goes back to whatever is in your offhand.
 */
final class BlockPickerScreen extends BaseScreen {
    private static final int COLUMNS = 9;
    private static final int CELL = 18;
    private static final int SEARCH_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 20;

    private final List<ItemStack> all = new ArrayList<>();
    private List<ItemStack> shown = List.of();
    private int scrollRow;
    private String query = "";

    BlockPickerScreen() {
        super(Component.translatable("hibiscusfaultline.build.picker"), null);
    }

    @Override
    protected int preferredWidth() {
        return COLUMNS * CELL + MARGIN * 2;
    }

    @Override
    protected int preferredHeight() {
        return 230;
    }

    @Override
    protected void initContent() {
        all.clear();
        LocalPlayer player = minecraft.player;
        Set<Item> items = new LinkedHashSet<>();
        if (player != null && player.isCreative()) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof BlockItem) {
                    items.add(item);
                }
            }
        } else if (player != null) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.getItem() instanceof BlockItem) {
                    items.add(stack.getItem());
                }
            }
            if (player.getOffhandItem().getItem() instanceof BlockItem) {
                items.add(player.getOffhandItem().getItem());
            }
        }
        for (Item item : items) {
            all.add(new ItemStack(item));
        }

        TextInput search = new TextInput(panelX + MARGIN, panelY + HEADER, panelWidth - MARGIN * 2, SEARCH_HEIGHT,
                Component.translatable("hibiscusfaultline.build.picker.search"));
        search.placeholder(Component.translatable("hibiscusfaultline.build.picker.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        addRenderableWidget(new TextButton(panelX + MARGIN, panelY + panelHeight - MARGIN - BUTTON_HEIGHT, panelWidth - MARGIN * 2,
                BUTTON_HEIGHT, Component.translatable("hibiscusfaultline.build.picker.offhand"), button -> pick(null)));
        filter();
    }

    @Override
    protected void extractForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int top = gridTop();
        int rows = visibleRows();
        ItemStack hovered = null;
        Block current = BuildOperation.setBlock().map(holder -> holder.value()).orElse(null);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (scrollRow + row) * COLUMNS + column;
                if (index >= shown.size()) {
                    break;
                }
                ItemStack stack = shown.get(index);
                int x = panelX + MARGIN + column * CELL;
                int y = top + row * CELL;
                FaultlineTheme.hibiscus().inset(graphics, x, y, CELL, CELL);
                if (stack.getItem() instanceof BlockItem item && item.getBlock() == current) {
                    graphics.outline(x, y, CELL, CELL, FaultlineTheme.tool(BuildClient.tool()));
                }
                graphics.fakeItem(stack, x + 1, y + 1);
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                    hovered = stack;
                }
            }
        }
        if (shown.isEmpty()) {
            graphics.centeredText(font, Component.translatable("hibiscusfaultline.build.picker.empty"), panelX + panelWidth / 2, top + 8,
                    FaultlineTheme.textMuted());
        }
        if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ItemStack stack = stackAt(event.x(), event.y());
        if (stack != null && stack.getItem() instanceof BlockItem item) {
            pick(item.getBlock());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int maxRow = Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - visibleRows());
        scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxRow);
        return true;
    }

    private void pick(@Nullable Block block) {
        BuildOperation.pickSetBlock(block);
        onClose();
    }

    private void filter() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        shown = needle.isEmpty() ? all : all.stream()
                .filter(stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(needle))
                .toList();
        scrollRow = 0;
    }

    private @Nullable ItemStack stackAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - panelX - MARGIN) / CELL);
        int row = (int) Math.floor((mouseY - gridTop()) / CELL);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= visibleRows()) {
            return null;
        }
        int index = (scrollRow + row) * COLUMNS + column;
        return index < shown.size() ? shown.get(index) : null;
    }

    private int gridTop() {
        return panelY + HEADER + SEARCH_HEIGHT + 4;
    }

    private int visibleRows() {
        return Math.max(1, (panelY + panelHeight - MARGIN - BUTTON_HEIGHT - 4 - gridTop()) / CELL);
    }
}
