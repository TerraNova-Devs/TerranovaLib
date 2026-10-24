package de.mcterranova.terranovaLib.dialoguehud;

import de.mcterranova.terranovaLib.dialoguehud.HudConfig.Input;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns vanilla player events into dialogue input.
 * <p>
 * The server never sees arbitrary key presses, only what the client reports as game actions: clicks,
 * the hotbar slot, swapping hands, and (since 1.21.2) the movement keys via {@link PlayerInputEvent}.
 * Every handler first checks for a running dialogue, so outside a conversation nothing is touched;
 * inside one, an event is only cancelled when the dialogue used it.
 */
final class DialogueInputHandler implements Listener {

    private final DialogueHud hud;

    /** Last movement key state per player, to react to presses rather than to held keys. */
    private final Map<UUID, org.bukkit.Input> lastInput = new ConcurrentHashMap<>();

    DialogueInputHandler(final DialogueHud hud) {
        this.hud = hud;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(final PlayerInteractEvent event) {
        // Clicks into the air arrive already cancelled, so this handler must not ignore cancelled events.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Action action = event.getAction();
        final Input input = action.isRightClick() ? Input.RIGHT_CLICK : action.isLeftClick() ? Input.LEFT_CLICK : null;
        if (input != null) {
            handle(event.getPlayer(), input, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            handle(event.getPlayer(), Input.RIGHT_CLICK, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(final PrePlayerAttackEntityEvent event) {
        handle(event.getPlayer(), Input.LEFT_CLICK, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(final PlayerSwapHandItemsEvent event) {
        handle(event.getPlayer(), Input.SWAP_HANDS, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMovementKeys(final PlayerInputEvent event) {
        final org.bukkit.Input now = event.getInput();
        final org.bukkit.Input before = lastInput.put(event.getPlayer().getUniqueId(), now);
        if (now.isJump() && (before == null || !before.isJump())) {
            handle(event.getPlayer(), Input.JUMP, null);
        }
        if (now.isSneak() && (before == null || !before.isSneak())) {
            handle(event.getPlayer(), Input.SNEAK, null);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onScroll(final PlayerItemHeldEvent event) {
        final DialogueSession session = hud.session(event.getPlayer());
        if (session == null) {
            return;
        }
        // Shortest way round the nine slots, so scrolling from 8 to 0 is one step forward.
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta > 4) {
            delta -= 9;
        } else if (delta < -4) {
            delta += 9;
        }
        if (session.scroll(delta)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        lastInput.remove(event.getPlayer().getUniqueId());
        final DialogueSession session = hud.session(event.getPlayer());
        if (session != null) {
            session.dispose();
        }
    }

    private void handle(final Player player, final Input input, @Nullable final Cancellable event) {
        final DialogueSession session = hud.session(player);
        if (session != null && session.input(input) && event != null) {
            event.setCancelled(true);
        }
    }
}
