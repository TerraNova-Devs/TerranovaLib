package de.mcterranova.terranovaLib.npcs;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.CurrentLocation;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Makes Citizens match the definitions.
 * <p>
 * The NPCs live in Citizens' normal registry, so Citizens saves them, its commands work on them and
 * BetonQuest's own Citizens click handling sees them. What makes one managed is its permanent id,
 * stored in the NPC's persistent data under {@value #KEY}. Citizens' numeric id may change when an
 * NPC is recreated; this one never does.
 */
final class NpcReconciler {

    static final String KEY = "terranova-id";

    private final Plugin plugin;

    private final Logger log;

    private final Map<String, NPC> byId = new ConcurrentHashMap<>();

    NpcReconciler(final Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /**
     * The managed NPC with this permanent id.
     */
    @Nullable
    NPC get(final String id) {
        final NPC npc = byId.get(id);
        // Citizens replaces its NPC objects on /citizens reload; a stale one is no longer registered.
        if (npc != null && npc.getOwningRegistry().getByUniqueId(npc.getUniqueId()) == npc) {
            return npc;
        }
        return null;
    }

    Map<String, NPC> all() {
        return Map.copyOf(byId);
    }

    /**
     * Creates, takes over, updates and (if allowed) removes NPCs. Call on the main thread (global
     * region on Folia).
     *
     * @param removeOrphans destroy managed NPCs that no longer have a definition, and a copy made
     *                      before its definition named the original it replaces
     */
    void reconcile(final List<NpcDefinition> definitions, final boolean removeOrphans) {
        final NPCRegistry registry = CitizensAPI.getNPCRegistry();
        final Map<String, NPC> managed = new HashMap<>();
        for (final NPC npc : registry) {
            final String id = npc.data().get(KEY);
            if (id == null) {
                continue;
            }
            final NPC other = managed.putIfAbsent(id, npc);
            if (other != null) {
                // Most likely made with /npc copy, which copies persistent data too.
                log.warning("NPC #" + npc.getId() + " is a copy of '" + id + "' (#" + other.getId()
                        + "); it is no longer managed.");
                npc.data().remove(KEY);
            }
        }

        byId.clear();
        int created = 0;
        int takenOver = 0;
        for (final NpcDefinition definition : definitions) {
            NPC npc = managed.remove(definition.id());
            final NPC original = definition.replaces() == null ? null : registry.getByUniqueId(definition.replaces());
            // Skipped when the original is not on this server, already is this NPC, or belongs to another id.
            if (original != null && original != npc && !original.data().has(KEY)) {
                if (npc != null && !removeOrphans) {
                    log.warning("Citizens NPC #" + original.getId() + " and #" + npc.getId() + " are both '"
                            + definition.id() + "'; kept both this time.");
                } else {
                    if (npc != null) {
                        // Made before the definition named its original. The original stays, since
                        // other quests or plugins may still refer to its Citizens id.
                        log.info("Removing Citizens NPC #" + npc.getId() + ", a copy of '" + definition.id()
                                + "'; #" + original.getId() + " takes its place.");
                        npc.destroy();
                    }
                    log.info("Taking over Citizens NPC #" + original.getId() + " as '" + definition.id() + "'.");
                    npc = original;
                    npc.data().setPersistent(KEY, definition.id());
                    takenOver++;
                }
            }
            if (npc == null) {
                npc = registry.createNPC(definition.type(), definition.name());
                npc.data().setPersistent(KEY, definition.id());
                created++;
            }
            byId.put(definition.id(), npc);
            apply(npc, definition);
        }

        for (final Map.Entry<String, NPC> orphan : managed.entrySet()) {
            if (removeOrphans) {
                log.info("Removing NPC '" + orphan.getKey() + "' (#" + orphan.getValue().getId() + "), its definition is gone.");
                orphan.getValue().destroy();
            } else {
                log.warning("NPC '" + orphan.getKey() + "' (#" + orphan.getValue().getId() + ") has no definition; kept.");
            }
        }
        log.info(definitions.size() + " NPC(s) in sync, " + created + " created, " + takenOver + " taken over.");
    }

    /**
     * Tags an existing NPC with a permanent id.
     */
    void adopt(final NPC npc, final String id) {
        npc.data().setPersistent(KEY, id);
        byId.put(id, npc);
    }

    private void apply(final NPC npc, final NpcDefinition definition) {
        final Location location = definition.resolveLocation();
        if (location == null) {
            log.warning("NPC '" + definition.id() + "': world of " + definition.location() + " is not loaded; not spawned.");
            return;
        }
        // Entities belong to the region they stand in on Folia; on Paper this is the next tick.
        Bukkit.getRegionScheduler().execute(plugin, location, () -> {
            if (npc.getOrAddTrait(MobType.class).getType() != definition.type()) {
                npc.setBukkitEntityType(definition.type());
            }
            if (!definition.name().equals(npc.getRawName())) {
                npc.setName(definition.name());
            }
            npc.setProtected(definition.protect());
            final LookClose lookClose = npc.getOrAddTrait(LookClose.class);
            if (lookClose.isEnabled() != definition.lookClose()) {
                lookClose.lookClose(definition.lookClose());
            }
            applySkin(npc, definition);
            final Location stored = npc.getStoredLocation();
            if (moved(stored, location)) {
                log.info("Moving '" + definition.id() + "' (#" + npc.getId() + ") from "
                        + (stored == null || stored.getWorld() == null ? "nowhere" : NpcDefinition.format(stored))
                        + " to " + definition.location() + ".");
                // No teleport: Citizens ignores it for unspawned NPCs, and for spawned ones it runs
                // through teleport events that another plugin can cancel (it had no effect on the
                // prod mirror). Respawning at the stored spot depends on neither.
                if (npc.isSpawned()) {
                    npc.despawn(DespawnReason.PENDING_RESPAWN);
                }
                npc.getOrAddTrait(CurrentLocation.class).setLocation(location);
            }
            if (!npc.isSpawned()) {
                npc.spawn(location, SpawnReason.RESPAWN);
            }
        });
    }

    private static void applySkin(final NPC npc, final NpcDefinition definition) {
        if (definition.skinTexture() != null) {
            final SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
            if (!definition.skinTexture().equals(skin.getTexture())) {
                skin.setSkinPersistent(definition.id(), definition.skinSignature(), definition.skinTexture());
            }
        } else if (definition.skinName() != null) {
            final SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
            if (!definition.skinName().equalsIgnoreCase(skin.getSkinName())) {
                skin.setSkinName(definition.skinName(), true);
            }
        }
    }

    private static boolean moved(@Nullable final Location stored, final Location target) {
        return stored == null
                || !Objects.equals(stored.getWorld(), target.getWorld())
                || stored.distanceSquared(target) > 0.0001
                || Math.abs(stored.getYaw() - target.getYaw()) > 0.5
                || Math.abs(stored.getPitch() - target.getPitch()) > 0.5;
    }
}
