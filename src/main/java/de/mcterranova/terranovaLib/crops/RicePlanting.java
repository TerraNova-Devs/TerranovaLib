package de.mcterranova.terranovaLib.crops;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rice grows only in water exactly one block deep: its Nexo furniture (reis_*) may only be placed into a water
 * source with no water above it. The ground below Nexo checks itself (limited_placing). Nexo fires the event after
 * placing and on a cancel takes the furniture away again before it uses up the seed, so the seed stays in the hand.
 * <p>
 * Ripe rice comes in three looks. Nexo grows one fixed chain of stages, never a random next one, so the look is
 * rolled at planting: a third keeps the chain of the seed, the others swap to the first stage of chain b or c.
 * <p>
 * The furniture needs {@code waterloggable: true} in Nexo, or Nexo turns the water it was placed into to air.
 */
public class RicePlanting implements Listener {

    private static final String RICE = "reis_";
    private static final String SEED = "reis_saat";
    private static final List<String> OTHER_CHAINS = List.of("reis_b_stufe0", "reis_c_stufe0");

    private final Plugin plugin;

    public RicePlanting(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(NexoFurniturePlaceEvent event) {
        if (!event.getMechanic().getItemID().startsWith(RICE)) return;
        Block block = event.getBlock();
        if (isSource(block) && !isWater(block.getRelative(BlockFace.UP))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Chat.stringToComponent("<red>Reis wächst nur in flachem Wasser (genau 1 Block tief)."));
    }

    // last, so only a planting that stays; the seed is used up by then, the swap gives none back
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlanted(NexoFurniturePlaceEvent event) {
        if (!event.getMechanic().getItemID().equals(SEED)) return;
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) return;
        String chain = OTHER_CHAINS.get(roll - 1);
        if (NexoFurniture.furnitureMechanic(chain) == null) return; // that chain is missing in Nexo; keep the seed's
        ItemDisplay planted = event.getBaseEntity();
        Location at = event.getBlock().getLocation();
        float yaw = planted.getLocation().getYaw();
        // a tick later, when Nexo is done placing; removing without a player drops nothing
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!planted.isValid()) return;
            NexoFurniture.remove(planted);
            NexoFurniture.place(chain, at, yaw, BlockFace.UP);
        });
    }

    private static boolean isSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0;
    }

    private static boolean isWater(Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }
}
