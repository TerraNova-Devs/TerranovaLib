package de.mcterranova.terranovaLib.roseGUI.listener;

import de.mcterranova.terranovaLib.TerranovaLib;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Objects;

public class RoseCraftListener implements Listener {

    public static NamespacedKey key;

    public RoseCraftListener (TerranovaLib plugin){
        key = new NamespacedKey(plugin, "craft");
    }

    @EventHandler
    public void onCrafting(PrepareItemCraftEvent event){
        Arrays.stream(event.getInventory().getMatrix())
                .filter(Objects::nonNull)
                .forEach(itemStack -> {
                    itemStack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
                    boolean craft = Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN));
                    if (!craft) {
                        event.getRecipe().getResult().getType().isAir();
                    }
                });
    }
}