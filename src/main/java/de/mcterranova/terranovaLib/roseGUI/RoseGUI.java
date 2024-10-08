package de.mcterranova.terranovaLib.roseGUI;

import de.mcterranova.terranovaLib.utils.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;

import static org.bukkit.Bukkit.createInventory;

public abstract class RoseGUI implements InventoryHolder {

    public final static HashMap<UUID, RoseGUI> players = new HashMap<>();

    private final Map<Integer, RoseItem> registeredIcons;
    private final List<BukkitTask> taskList = new ArrayList<>();
    public final Player player;
    private final String id;
    private String title;
    private int size;
    private final InventoryType inventoryType;
    private Inventory inventory;
    private boolean isClosed = false;

    public RoseGUI(@Nonnull Player player, @Nonnull String id, Component title, @Nonnegative int rows) {
        this.size = rows * 9;
        this.registeredIcons = new HashMap<>(this.size);
        this.player = player;
        this.title = LegacyComponentSerializer.builder().hexColors().useUnusualXRepeatedCharacterHexFormat().build().serialize(title);
        this.id = id;
        this.inventoryType = InventoryType.CHEST;

    }

    public void open() {
        RoseGUI currentGui = players.get(player.getUniqueId());
        while (currentGui != null) {
            //call Bukkit's inventory close event
            player.closeInventory();
            currentGui = players.get(player.getUniqueId());
        }

        players.put(this.player.getUniqueId(), this);
        this.inventory = createInventory(null, this.size, Chat.stringToComponent(this.title));
        this.player.openInventory(inventory);
    }

    public void fillGui(RoseItem icon) {
        for (int slot = 0; slot < size; slot++) {
            this.addItem(slot, icon);
        }
    }

    public void addItem(@Nonnegative int slot, @Nullable RoseItem icon) {
        if (this.inventory.getSize() <= slot) {
            throw new IndexOutOfBoundsException("Slot cannot be bigger than inventory size! [ " + slot + " >= " + this.inventory.getSize() + " ]");
        }

        this.registeredIcons.put(slot, icon);
        this.inventory.setItem(slot, (icon == null ? null : icon.stack));
    }

    public void addItem(@Nullable RoseItem item, @Nonnull Integer... slots) {
        for (int slot : slots) {
            this.addItem(slot, item);
        }
    }

    @Nonnull
    public Map<Integer, RoseItem> getItems() {
        return registeredIcons;
    }

    public void onOpen(InventoryOpenEvent event) throws SQLException {

    }

    public void onClose(InventoryCloseEvent event) {
        final RoseGUI gui = getGuiFromInventory(event.getPlayer().getOpenInventory().getTopInventory());
        if (gui == null) return;
        if (!gui.equals(this)) return;
        gui.stopAllTasks();
    }

    @Nullable
    public RoseGUI getGuiFromInventory(final Inventory inventory) {
        return players.values().stream()
                .filter(gui -> gui.getInventory().equals(inventory))
                .findFirst().orElse(null);
    }

    public void setClosed(boolean closed) {
        this.isClosed = closed;
    }

    public void stopAllTasks() {
        taskList.forEach(BukkitTask::cancel);
        taskList.clear();
    }

    public boolean onClick(InventoryClickEvent event) {
        return false;
    }

    public boolean onDrag(InventoryDragEvent event) {
        return false;
    }

    @Override
    @Nonnull
    public Inventory getInventory() {
        return this.inventory;
    }

}
