package de.mcterranova.terranovaLib.ghostblocks;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which groups a player sees and keeps their client in step.
 * <p>
 * A ghost block is a block change sent to one client; the server's world is never touched. The client
 * forgets it whenever the server sends that block again: when the chunk is sent anew (moving away and
 * back, teleport, world change, relog) and when the player clicks or digs at it. Both are handled
 * here: every chunk sent to a player gets its ghost blocks right after, and an interaction re-sends
 * the ghost blocks around the player a moment later.
 * <p>
 * Visibility is stored in the player's persistent data, one entry per group that a quest has shown or
 * hidden, and cached here so BetonQuest conditions can read it from any thread.
 */
final class GhostBlockService implements Listener {

    /** Ghost blocks this close to a player are re-sent after an interaction. */
    private static final double REFRESH_RADIUS_SQUARED = 8 * 8;

    /** At most one refresh per player in this many milliseconds; digging swings many times a second. */
    private static final long REFRESH_INTERVAL_MS = 250;

    private final Plugin plugin;

    private volatile Map<String, GhostGroup> groups = Map.of();

    private volatile Map<String, List<GhostGroup>> byWorld = Map.of();

    /** Per player: group id to visible, only for groups a quest has set. */
    private final Map<UUID, Map<String, Boolean>> overrides = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastRefresh = new ConcurrentHashMap<>();

    /**
     * Namespace of the persistent data keys. Fixed to the name of the plugin these blocks started in,
     * so the visibility stored with players survives the moves into BetonquestAdditions and TerranovaLib.
     */
    private static final String NAMESPACE = "terranovaghostblocks";

    GhostBlockService(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    GhostGroup group(final String id) {
        return groups.get(id);
    }

    Map<String, GhostGroup> groups() {
        return groups;
    }

    /**
     * Replaces all groups and updates every online player's view.
     */
    void setGroups(final Map<String, GhostGroup> newGroups) {
        final Map<String, GhostGroup> old = groups;
        final Map<String, List<GhostGroup>> index = new HashMap<>();
        newGroups.values().forEach(group -> index.computeIfAbsent(group.world(), world -> new ArrayList<>()).add(group));
        index.replaceAll((world, list) -> List.copyOf(list));
        groups = Map.copyOf(newGroups);
        byWorld = Map.copyOf(index);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                old.values().stream().filter(group -> isVisible(player, group)).forEach(group -> restore(player, group));
                groups.values().stream().filter(group -> isVisible(player, group)).forEach(group -> send(player, group));
            }, null);
        }
    }

    boolean isVisible(final Player player, final GhostGroup group) {
        return overrides(player).getOrDefault(group.id(), group.defaultVisible());
    }

    /**
     * Shows or hides a group for a player and remembers it. Call on the player's thread.
     */
    void setVisible(final Player player, final GhostGroup group, final boolean visible) {
        final boolean before = isVisible(player, group);
        overrides(player).put(group.id(), visible);
        player.getPersistentDataContainer().set(key(group.id()), PersistentDataType.BYTE, (byte) (visible ? 1 : 0));
        if (before != visible) {
            if (visible) {
                send(player, group);
            } else {
                restore(player, group);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(final PlayerChunkLoadEvent event) {
        final List<GhostGroup> inWorld = byWorld.get(event.getWorld().getName());
        if (inWorld == null) {
            return;
        }
        final long chunk = event.getChunk().getChunkKey();
        Map<BlockPosition, BlockData> changes = null;
        for (final GhostGroup group : inWorld) {
            final Map<BlockPosition, BlockData> blocks = group.byChunk().get(chunk);
            if (blocks != null && isVisible(event.getPlayer(), group)) {
                if (changes == null) {
                    changes = new HashMap<>();
                }
                changes.putAll(blocks);
            }
        }
        if (changes != null) {
            event.getPlayer().sendMultiBlockChange(changes);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(final PlayerInteractEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(final PlayerArmSwingEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(final BlockPlaceEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        overrides.remove(event.getPlayer().getUniqueId());
        lastRefresh.remove(event.getPlayer().getUniqueId());
    }

    private Map<String, Boolean> overrides(final Player player) {
        return overrides.computeIfAbsent(player.getUniqueId(), uuid -> {
            final Map<String, Boolean> stored = new ConcurrentHashMap<>();
            final PersistentDataContainer data = player.getPersistentDataContainer();
            for (final NamespacedKey key : data.getKeys()) {
                if (key.getNamespace().equals(NAMESPACE)) {
                    final Byte value = data.get(key, PersistentDataType.BYTE);
                    if (value != null) {
                        stored.put(key.getKey(), value != 0);
                    }
                }
            }
            return stored;
        });
    }

    private NamespacedKey key(final String groupId) {
        return new NamespacedKey(NAMESPACE, groupId);
    }

    /**
     * Sends a group's blocks in every chunk the player currently has. Chunks sent later get them in
     * {@link #onChunkSent}.
     */
    private void send(final Player player, final GhostGroup group) {
        if (!player.getWorld().getName().equals(group.world())) {
            return;
        }
        final Map<BlockPosition, BlockData> changes = new HashMap<>();
        group.byChunk().forEach((chunk, blocks) -> {
            if (player.isChunkSent(chunk)) {
                changes.putAll(blocks);
            }
        });
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    /**
     * Shows the real blocks again where a group was, except where another visible group has a block.
     * The real blocks are read on the thread that owns their chunk (Folia's region thread).
     */
    private void restore(final Player player, final GhostGroup group) {
        final World world = Bukkit.getWorld(group.world());
        if (world == null || !player.getWorld().equals(world)) {
            return;
        }
        group.byChunk().forEach((chunk, blocks) -> {
            if (!player.isChunkSent(chunk)) {
                return;
            }
            Bukkit.getRegionScheduler().execute(plugin, world, GhostGroup.chunkX(chunk), GhostGroup.chunkZ(chunk), () -> {
                final Map<BlockPosition, BlockData> changes = new HashMap<>();
                blocks.keySet().forEach(position ->
                        changes.put(position, world.getBlockData(position.blockX(), position.blockY(), position.blockZ())));
                for (final GhostGroup other : byWorld.getOrDefault(group.world(), List.of())) {
                    final Map<BlockPosition, BlockData> overlap = other.byChunk().get(chunk);
                    if (other != group && overlap != null && isVisible(player, other)) {
                        blocks.keySet().stream().filter(overlap::containsKey).forEach(position -> changes.put(position, overlap.get(position)));
                    }
                }
                player.sendMultiBlockChange(changes);
            });
        });
    }

    private void scheduleRefresh(final Player player) {
        final List<GhostGroup> inWorld = byWorld.get(player.getWorld().getName());
        if (inWorld == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastRefresh.get(player.getUniqueId());
        if (last != null && now - last < REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefresh.put(player.getUniqueId(), now);
        // The server answers the click first; re-send after that answer has gone out.
        player.getScheduler().runDelayed(plugin, task -> refreshNearby(player, inWorld), null, 2);
    }

    private void refreshNearby(final Player player, final List<GhostGroup> inWorld) {
        final Location here = player.getLocation();
        final int chunkX = here.getBlockX() >> 4;
        final int chunkZ = here.getBlockZ() >> 4;
        final Map<BlockPosition, BlockData> changes = new HashMap<>();
        for (final GhostGroup group : inWorld) {
            if (!isVisible(player, group)) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Map<BlockPosition, BlockData> blocks = group.byChunk().get(Chunk.getChunkKey(chunkX + dx, chunkZ + dz));
                    if (blocks == null) {
                        continue;
                    }
                    blocks.forEach((position, data) -> {
                        if (distanceSquared(position, here) <= REFRESH_RADIUS_SQUARED) {
                            changes.put(position, data);
                        }
                    });
                }
            }
        }
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private static double distanceSquared(final Position position, final Location location) {
        final double dx = position.x() + 0.5 - location.getX();
        final double dy = position.y() + 0.5 - location.getY();
        final double dz = position.z() + 0.5 - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
