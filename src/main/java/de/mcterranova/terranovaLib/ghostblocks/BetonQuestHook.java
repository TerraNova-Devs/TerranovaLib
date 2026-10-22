package de.mcterranova.terranovaLib.ghostblocks;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.integration.IntegrationService;
import org.betonquest.betonquest.api.quest.action.OnlineActionAdapter;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;
import org.betonquest.betonquest.api.quest.condition.OnlineConditionAdapter;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * BetonQuest's {@value #NAME} action and condition, using only BetonQuest's public API.
 * <pre>
 * actions:
 *   showBridge: "ghostblock show harbour_bridge"
 *   hideBridge: "ghostblock hide harbour_bridge"
 * conditions:
 *   seesBridge: "ghostblock harbour_bridge"
 * </pre>
 */
final class BetonQuestHook {

    static final String NAME = "ghostblock";

    enum Operation { SHOW, HIDE, TOGGLE }

    private BetonQuestHook() {
    }

    static boolean register(final Plugin plugin, final GhostBlockService service) {
        final IntegrationService integrations = plugin.getServer().getServicesManager().load(IntegrationService.class);
        if (integrations == null) {
            return false;
        }
        integrations.withPolicies().builder()
                .enable(api -> {
                    api.actions().registry().register(NAME, new ActionFactory(plugin, service));
                    api.conditions().registry().register(NAME, new ConditionFactory(service));
                })
                .integrate(plugin);
        return true;
    }

    private record ActionFactory(Plugin plugin, GhostBlockService service) implements PlayerActionFactory {

        @Override
        public PlayerAction parsePlayer(final Instruction instruction) throws QuestException {
            final Argument<Operation> operation = instruction.enumeration(Operation.class).get();
            final Argument<String> groupId = instruction.string().get();
            return new OnlineActionAdapter(profile -> {
                final GhostGroup group = group(service, groupId.getValue(profile));
                final Operation op = operation.getValue(profile);
                final Player player = profile.getPlayer();
                // BetonQuest may run actions off the player's thread; visibility is written on it.
                player.getScheduler().run(plugin, task -> service.setVisible(player, group, switch (op) {
                    case SHOW -> true;
                    case HIDE -> false;
                    case TOGGLE -> !service.isVisible(player, group);
                }), null);
            });
        }
    }

    private record ConditionFactory(GhostBlockService service) implements PlayerConditionFactory {

        @Override
        public PlayerCondition parsePlayer(final Instruction instruction) throws QuestException {
            final Argument<String> groupId = instruction.string().get();
            return new OnlineConditionAdapter(profile ->
                    service.isVisible(profile.getPlayer(), group(service, groupId.getValue(profile))));
        }
    }

    private static GhostGroup group(final GhostBlockService service, final String id) throws QuestException {
        final GhostGroup group = service.group(id);
        if (group == null) {
            throw new QuestException("No ghost block group '" + id + "'");
        }
        return group;
    }
}
