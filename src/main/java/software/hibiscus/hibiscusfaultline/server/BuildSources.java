/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import software.hibiscus.hibiscusfaultline.build.BuildMaterials;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;

/**
 * Where a player's building materials come from and where returned items go: the inventory (with the offhand) first,
 * then linked containers. Only plain stacks are ever spent, so named, enchanted or filled items are safe.
 */
public final class BuildSources {
    private final ServerPlayer player;

    public BuildSources(ServerPlayer player) {
        this.player = player;
    }

    /** {@return how many plain {@code item}s the player can pay with} */
    public int count(Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            total += spendable(stack, item) ? stack.getCount() : 0;
        }
        ItemStack offhand = player.getOffhandItem();
        total += spendable(offhand, item) ? offhand.getCount() : 0;
        for (ResourceHandler<ItemResource> handler : LinkedContainers.handlers(player)) {
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                if (resource.is(item) && resource.isComponentsPatchEmpty()) {
                    total += handler.getAmountAsInt(slot);
                }
            }
        }
        return total;
    }

    /** Spends one {@code item}. {@return whether there was one to spend} */
    public boolean take(Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (spendable(stack, item)) {
                stack.shrink(1);
                return true;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (spendable(offhand, item)) {
            offhand.shrink(1);
            return true;
        }
        ItemResource resource = ItemResource.of(item);
        for (ResourceHandler<ItemResource> handler : LinkedContainers.handlers(player)) {
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.extract(resource, 1, transaction) == 1) {
                    transaction.commit();
                    return true;
                }
            }
        }
        return false;
    }

    /** {@return whether every stack in {@code stacks} would fit in the inventory and linked containers} Changes nothing. */
    public boolean fits(List<ItemStack> stacks) {
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            inventory.add(stack.copy());
        }
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack rest = merge(inventory, stack.copy());
            if (!rest.isEmpty()) {
                left.add(rest);
            }
        }
        if (left.isEmpty()) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (ItemStack stack : left) {
                if (insertLinked(stack, transaction) < stack.getCount()) {
                    return false;
                }
            }
            // Not committed: this was only a test.
            return true;
        }
    }

    /**
     * Gives the player {@code stacks}: into the inventory, then linked containers, then dropped at their feet whatever
     * the {@code overflow} setting says (callers check {@link #fits} first when it is set to stop).
     */
    public void give(List<ItemStack> stacks) {
        for (ItemStack original : stacks) {
            ItemStack stack = original.copy();
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                stack.shrink(insertLinked(stack, transaction));
                transaction.commit();
            }
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
                drop.setNoPickUpDelay();
                player.level().addFreshEntity(drop);
            }
        }
    }

    /** {@return whether a full inventory and full containers should stop the operation instead of dropping items} */
    public static boolean stopsOnOverflow() {
        return ServerConfig.buildOverflow() == ServerConfig.Overflow.STOP;
    }

    private int insertLinked(ItemStack stack, Transaction transaction) {
        int inserted = 0;
        ItemResource resource = ItemResource.of(stack);
        for (ResourceHandler<ItemResource> handler : LinkedContainers.handlers(player)) {
            inserted += handler.insert(resource, stack.getCount() - inserted, transaction);
            if (inserted >= stack.getCount()) {
                break;
            }
        }
        return inserted;
    }

    /** Merges {@code stack} into a copy of an inventory the way {@code Inventory.add} would. {@return what's left} */
    private static ItemStack merge(List<ItemStack> inventory, ItemStack stack) {
        for (ItemStack slot : inventory) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, stack.copy());
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private static boolean spendable(ItemStack stack, Item item) {
        return stack.is(item) && BuildMaterials.isSpendable(stack);
    }
}
