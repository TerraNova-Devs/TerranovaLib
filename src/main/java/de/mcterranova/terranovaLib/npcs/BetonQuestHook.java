package de.mcterranova.terranovaLib.npcs;

import net.citizensnpcs.api.npc.NPC;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.identifier.NpcIdentifier;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.integration.IntegrationService;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.npc.Npc;
import org.betonquest.betonquest.api.quest.npc.NpcFactory;
import org.betonquest.betonquest.api.quest.npc.NpcReverseIdentifier;
import org.betonquest.betonquest.api.quest.npc.NpcWrapper;
import org.betonquest.betonquest.compatibility.npc.citizens.CitizensAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds the NPC type {@value #TYPE} to BetonQuest, so a quest package refers to an NPC by its
 * permanent id instead of Citizens' numeric one:
 * <pre>
 * npcs:
 *   rotbart: "tnpc rotbart"
 * </pre>
 * Clicks need nothing extra: the NPCs are ordinary Citizens NPCs, BetonQuest's Citizens integration
 * reports the click, and {@link ReverseIdentifier} tells it which {@value #TYPE} entries match.
 * <p>
 * Uses only BetonQuest's public API, except {@link CitizensAdapter} (BetonQuest's own wrapper for a
 * Citizens NPC), which is reused so these NPCs behave exactly like {@code citizens} ones.
 */
final class BetonQuestHook {

    static final String TYPE = "tnpc";

    private BetonQuestHook() {
    }

    static boolean register(final Plugin plugin, final NpcReconciler npcs) {
        final IntegrationService integrations = plugin.getServer().getServicesManager().load(IntegrationService.class);
        if (integrations == null) {
            return false;
        }
        integrations.withPolicies().builder()
                .enable(api -> {
                    api.npcs().registry().register(TYPE, new Factory(npcs));
                    api.npcs().registry().registerIdentifier(new ReverseIdentifier());
                })
                .integrate(plugin);
        return true;
    }

    private record Factory(NpcReconciler npcs) implements NpcFactory {

        @Override
        public NpcWrapper<?> parseInstruction(final Instruction instruction) throws QuestException {
            return new Wrapper(instruction.string().get(), npcs);
        }
    }

    private record Wrapper(Argument<String> id, NpcReconciler npcs) implements NpcWrapper<NPC> {

        @Override
        public Npc<NPC> getNpc(@Nullable final Profile profile) throws QuestException {
            final String value = id.getValue(profile);
            final NPC npc = npcs.get(value);
            if (npc == null) {
                throw new QuestException("No TerranovaLib NPC with id '" + value + "'");
            }
            return new CitizensAdapter(npc);
        }

        @Override
        public Set<Npc<NPC>> getNpcs(@Nullable final Profile profile) throws QuestException {
            return Set.of(getNpc(profile));
        }
    }

    /**
     * Maps a clicked Citizens NPC back to the quest packages' {@value #TYPE} entries.
     */
    private static final class ReverseIdentifier implements NpcReverseIdentifier {

        private static final String PREFIX = TYPE + " ";

        private final Map<String, Set<NpcIdentifier>> byInstruction = new ConcurrentHashMap<>();

        @Override
        public Set<NpcIdentifier> getIdsFromNpc(final Npc<?> npc, @Nullable final OnlineProfile profile) {
            if (npc.getOriginal() instanceof final NPC citizen) {
                final String id = citizen.data().get(NpcReconciler.KEY);
                if (id != null) {
                    return byInstruction.getOrDefault(PREFIX + id, Set.of());
                }
            }
            return Set.of();
        }

        @Override
        public void addID(final NpcIdentifier npcId, final String instruction) {
            if (instruction.startsWith(PREFIX)) {
                byInstruction.computeIfAbsent(instruction.strip(), key -> ConcurrentHashMap.newKeySet()).add(npcId);
            }
        }

        @Override
        public void reset() {
            byInstruction.clear();
        }
    }
}
