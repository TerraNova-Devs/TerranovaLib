package de.mcterranova.terranovaLib.crops;

import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Rice grows only in water exactly one block deep: its Nexo furniture (reis_*) may only be placed into a water
 * source with no water above it. The ground below Nexo checks itself (limited_placing). Nexo fires the event after
 * placing and on a cancel takes the furniture away again before it uses up the seed, so the seed stays in the hand.
 * <p>
 * The furniture needs {@code waterloggable: true} in Nexo, or Nexo turns the water it was placed into to air.
 */
public class RicePlanting implements Listener {

    private static final String RICE = "reis_";

    @EventHandler(ignoreCancelled = true)
    public void onPlace(NexoFurniturePlaceEvent event) {
        if (!event.getMechanic().getItemID().startsWith(RICE)) return;
        Block block = event.getBlock();
        if (isSource(block) && !isWater(block.getRelative(BlockFace.UP))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Chat.stringToComponent("<red>Reis wächst nur in flachem Wasser (genau 1 Block tief)."));
    }

    private static boolean isSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0;
    }

    private static boolean isWater(Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }
}
