package de.mcterranova.terranovaLib.npcs;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.nekyia.heroicmap.api.HeroicMapApi;
import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject.Pin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Spawned;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * All Citizens NPCs as a HeroicMap layer, each with its head, name and spot. The only class that names the
 * classes of HeroicMap, so it must only load when HeroicMap is enabled.
 * <p>
 * Citizens has no event for a new skin, and NPCMoveEvent fires on every step, so instead of listeners the
 * layer is compared with the registry every 30 s: put for what changed, remove for what is gone. When the
 * lib is disabled, Bukkit stops the timer and HeroicMap drops the layer itself (PluginDisableEvent).
 */
public final class NpcMapLayer {

    private static final long PERIOD_TICKS = 30 * 20;

    // HeroicMap allows 1000 pins and banners together per layer, and 256 different heads; put throws above
    private static final int MAX_PINS = 1000;
    private static final int MAX_HEADS = 256;

    private final JavaPlugin plugin;

    private final Layer layer;

    private final Map<String, Pin> sent = new HashMap<>();

    private boolean warnedPins;
    private boolean warnedHeads;

    private NpcMapLayer(final JavaPlugin plugin, final Layer layer) {
        this.plugin = plugin;
        this.layer = layer;
    }

    /** Creates the layer and starts the comparison. Needs Citizens and HeroicMap 0.17.0 or newer. */
    public static void enable(final JavaPlugin plugin) {
        final HeroicMapApi api = Objects.requireNonNull(Bukkit.getServicesManager().load(HeroicMapApi.class),
                "HeroicMap is enabled but offers no API");
        final Layer layer = api.layer(plugin, "npcs");
        layer.name("NPCs", "NPCs");
        final NpcMapLayer feature = new NpcMapLayer(plugin, layer);
        // Citizens loads its NPCs a tick after the start; the first run after 1 s finds them
        Bukkit.getScheduler().runTaskTimer(plugin, feature::sync, 20, PERIOD_TICKS);
    }

    private void sync() {
        final Map<String, Pin> pins = new HashMap<>();
        final Set<String> heads = new HashSet<>();
        for (final NPC npc : CitizensAPI.getNPCRegistry().sorted()) {
            if (pins.size() >= MAX_PINS) {
                if (!warnedPins) {
                    warnedPins = true;
                    plugin.getLogger().warning("More than " + MAX_PINS + " NPCs, the rest stay off the map.");
                }
                break;
            }
            final Pin pin = pin(npc, heads);
            if (pin != null) {
                pins.put(pin.id(), pin);
            }
        }

        for (final Pin pin : pins.values()) {
            if (pin.equals(sent.get(pin.id()))) {
                continue;
            }
            try {
                layer.put(pin);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("NPC pin " + pin.id() + " with head " + pin.head() + " rejected: "
                        + e.getMessage());
                final Pin plain = pin.withHead(null);
                layer.put(plain);
                pins.put(plain.id(), plain);
            }
        }
        for (final String id : sent.keySet()) {
            if (!pins.containsKey(id)) {
                layer.remove(id);
            }
        }
        sent.clear();
        sent.putAll(pins);
    }

    /** The pin of an NPC, or null for one without name or spot, or one that is hidden. */
    private @Nullable Pin pin(final NPC npc, final Set<String> heads) {
        final Location location = npc.getStoredLocation();
        final String name = npc.getName();
        final Spawned spawned = npc.getTraitNullable(Spawned.class);
        // hidden: name tag switched off, or despawned on purpose (not just in an unloaded chunk)
        if (location == null || location.getWorld() == null || name == null || name.isBlank()
                || "false".equals(String.valueOf(npc.data().get(NPC.Metadata.NAMEPLATE_VISIBLE, true)))
                || spawned != null && !spawned.shouldSpawn()) {
            return null;
        }
        // y is the block the pin stands on; an NPC's feet are at blockY, so it stands on blockY - 1
        Pin pin = Pin.at("npc-" + npc.getId(), location.getBlockX() + 0.5, location.getBlockZ() + 0.5)
                .withDimension(location.getWorld().getKey().toString())
                .withY(location.getBlockY() - 1)
                .withName(name);
        final String head = head(npc);
        if (head != null) {
            if (heads.contains(head) || heads.size() < MAX_HEADS) {
                heads.add(head);
                pin = pin.withHead(head);
            } else if (!warnedHeads) {
                warnedHeads = true;
                plugin.getLogger().warning("More than " + MAX_HEADS + " NPC skins, the rest show a plain pin.");
            }
        }
        return pin;
    }

    /** The texture hash from the skin trait, else from the profile of the spawned NPC; null for mobs. */
    private static @Nullable String head(final NPC npc) {
        final SkinTrait skin = npc.getTraitNullable(SkinTrait.class);
        final String fromTrait = skin == null ? null : SkinHash.fromTexture(skin.getTexture());
        if (fromTrait != null || !(npc.getEntity() instanceof Player player)) {
            return fromTrait;
        }
        for (final ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if ("textures".equals(property.getName())) {
                return SkinHash.fromTexture(property.getValue());
            }
        }
        return null;
    }
}
