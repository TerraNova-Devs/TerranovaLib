package de.mcterranova.terranovaLib.silver;

import de.mcterranova.terranovaLib.TerranovaLib;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;

import java.util.concurrent.TimeUnit;

public class Silver {
    private static InfluxDB influxDB = TerranovaLib.getInfluxDB();
    private static final String SILVER_ITEM_ID = "terranova_silver";

    public static boolean hasEnough(Player player, int amount) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isSilver(stack)) {
                total += stack.getAmount();
                if (total >= amount) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean charge(Player player, int amount) {
        return charge(player, amount, false);
    }

    public static boolean charge(Player player, int amount, boolean removed) {
        if (!hasEnough(player, amount)) {
            return false;
        }

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !isSilver(stack)) {
                continue;
            }

            int stackAmount = stack.getAmount();
            if (stackAmount <= remaining) {
                player.getInventory().clear(i);
                remaining -= stackAmount;
            } else {
                stack.setAmount(stackAmount - remaining);
                remaining = 0;
                break;
            }
        }
        player.updateInventory();
        if(removed){
            logTransactionToInfluxDB("consume", amount);
        } else {
            logTransactionToInfluxDB("transfer", amount);
        }
        return true;
    }

    public static ItemStack get(int amount) {
        // Falls Oraxen verwendet wird
        if (OraxenItems.exists(SILVER_ITEM_ID)) {
            ItemStack silverItem = OraxenItems.getItemById(SILVER_ITEM_ID).build();
            silverItem.setAmount(amount);
            return silverItem;
        }
        return ItemStack.empty();
    }

    public static void drop(Location location, int amount) {
        World world = location.getWorld();
        if (world != null) {
            ItemStack silverItem = get(amount);
            world.dropItemNaturally(location, silverItem);
            logTransactionToInfluxDB("generate", amount);
        }
    }

    private static void logTransactionToInfluxDB(String type, int amount) {
        influxDB.write(Point.measurement("silver_transactions")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("type", type)
                .addField("amount", amount)
                .build());
    }

    private static boolean isSilver(ItemStack item) {
        if (OraxenItems.exists(SILVER_ITEM_ID)) {
            return OraxenItems.getItemById(SILVER_ITEM_ID).build().isSimilar(item);
        }
        return false;
    }
}
