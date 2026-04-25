package de.mcterranova.terranovaLib.InventoryUtil;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemTransfer {

    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("nexo", "id");

    public static Integer charge(Player player, String itemString, int amount, boolean onlyFullCharge) {
        ItemStack[] inventory = player.getInventory().getContents();

        if (onlyFullCharge && countTotalItems(inventory, itemString) < amount) {
            return -1;
        }

        int remaining = amount;

        for (int i = 0; i < inventory.length && remaining > 0; i++) {
            ItemStack stack = inventory[i];

            if (!hasItemId(stack, itemString)) continue;

            int stackAmount = stack.getAmount();

            if (stackAmount <= remaining) {
                inventory[i] = null;
                remaining -= stackAmount;
            } else {
                stack.setAmount(stackAmount - remaining);
                remaining = 0;
            }
        }

        player.getInventory().setContents(inventory);
        player.updateInventory();

        return amount - remaining;
    }

    public static Integer credit(Player player, String itemString, int amount, boolean onlyFullCredit) {
        ItemStack item = resolveItem(itemString);
        ItemStack[] inventory = player.getInventory().getContents();

        if (onlyFullCredit && countAvailableSpace(inventory, item) < amount) {
            return -1;
        }

        int remaining = amount;

        for (int i = 0; i < inventory.length && remaining > 0; i++) {
            ItemStack stack = inventory[i];

            if (stack == null) {
                int addable = Math.min(remaining, item.getMaxStackSize());
                inventory[i] = item.asQuantity(addable);
                remaining -= addable;
            } else if (stack.isSimilar(item)) {
                int addable = Math.min(remaining, stack.getMaxStackSize() - stack.getAmount());
                stack.setAmount(stack.getAmount() + addable);
                remaining -= addable;
            }
        }

        player.getInventory().setContents(inventory);
        player.updateInventory();

        return amount - remaining;
    }

    private static ItemStack resolveItem(String itemString) {
        if (NexoItems.exists(itemString)) {
            return NexoItems.itemFromId(itemString).build();
        }

        return new ItemStack(Material.valueOf(itemString));
    }

    private static boolean hasItemId(ItemStack stack, String expectedId) {
        if (stack == null || stack.getType().isAir()) return false;

        String actualId = stack.getPersistentDataContainer().get(
                ITEM_ID_KEY,
                PersistentDataType.STRING
        );

        return expectedId.equals(actualId);
    }

    private static int countTotalItems(ItemStack[] inventory, String itemString) {
        int total = 0;

        for (ItemStack stack : inventory) {
            if (hasItemId(stack, itemString)) {
                total += stack.getAmount();
            }
        }

        return total;
    }

    private static int countAvailableSpace(ItemStack[] inventory, ItemStack item) {
        int totalSpace = 0;

        for (ItemStack stack : inventory) {
            if (stack == null) {
                totalSpace += item.getMaxStackSize();
            } else if (stack.isSimilar(item)) {
                totalSpace += stack.getMaxStackSize() - stack.getAmount();
            }
        }

        return totalSpace;
    }
}