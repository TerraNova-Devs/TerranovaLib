package de.mcterranova.terranovaLib.dialoguehud;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.BetonQuestApi;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.common.component.ComponentLineWrapper;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.conversation.Conversation;
import org.betonquest.betonquest.conversation.ConversationIO;
import org.betonquest.betonquest.conversation.ConversationIOFactory;
import org.betonquest.betonquest.kernel.registry.feature.ConversationIORegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Registers the {@value #NAME} conversation IO and decides, per conversation, whether the player
 * gets the HUD or a fallback.
 * <p>
 * BetonQuest 3.3 has no public API for conversation IOs yet (its docs list them as a future
 * feature). The IO is therefore registered the way BetonQuest's own integrations do it, through
 * the internal {@link ConversationIORegistry} of its component loader, from inside a public
 * {@code IntegrationService} integration so it is in place before conversations are parsed.
 * {@link Conversation}, {@link ConversationIO} and the registry are internal classes; verified
 * against BetonQuest 3.3.0-DEV-52 and expected to need updates when BetonQuest publishes the API.
 */
final class ConversationIntegration implements ConversationIOFactory {

    static final String NAME = "terranova_hud";

    private final DialogueHud hud;

    private final ConversationIORegistry registry;

    private final ComponentLineWrapper wrapper;

    private ConversationIntegration(final DialogueHud hud, final ConversationIORegistry registry,
                                    final ComponentLineWrapper wrapper) {
        this.hud = hud;
        this.registry = registry;
        this.wrapper = wrapper;
    }

    static void register(final DialogueHud hud, final BetonQuestApi api) {
        final ConversationIORegistry registry = BetonQuest.getInstance().getComponentLoader().get(ConversationIORegistry.class);
        registry.register(NAME, new ConversationIntegration(hud, registry, new ComponentLineWrapper(api.fonts())));
        hud.getLogger().info("Conversation IO '" + NAME + "' registered with BetonQuest.");
    }

    @Override
    public ConversationIO parse(final Conversation conversation, final OnlineProfile onlineProfile) throws QuestException {
        final Player player = onlineProfile.getPlayer();
        final HudConfig config = hud.hudConfig();
        if (packMissing(player.getResourcePackStatus())) {
            // Without the pack, glyphs would show up as boxes.
            return fallback(config.noResourcePack(), conversation, onlineProfile, config, null);
        }
        final GlyphIntegration glyphs = hud.glyphs();
        if (glyphs == null) {
            return fallback(config.noGlyphs(), conversation, onlineProfile, config, null);
        }
        return session(conversation, onlineProfile, config, glyphs);
    }

    /**
     * Whether the player is known to be without the server pack: declined, failed or still loading.
     * No status at all counts as loaded. Behind NexoProxy that is a server switch: the client kept
     * the pack, the proxy did not send it again, and this server never heard about it. That relies
     * on the pack being mandatory (Nexo's dispatch.mandatory), so nobody plays without it.
     */
    static boolean packMissing(@Nullable final PlayerResourcePackStatusEvent.Status status) {
        return status != null && status != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    private ConversationIO fallback(final String names, final Conversation conversation, final OnlineProfile onlineProfile,
                                    final HudConfig config, @Nullable final GlyphIntegration glyphs) throws QuestException {
        final List<String> ios = ioNames(names);
        if (ios.isEmpty() || HudConfig.PLAIN.equals(ios.getFirst())) {
            return session(conversation, onlineProfile, config, glyphs);
        }
        return registry.getFactory(ios).parse(conversation, onlineProfile);
    }

    private DialogueSession session(final Conversation conversation, final OnlineProfile onlineProfile,
                                    final HudConfig config, @Nullable final GlyphIntegration glyphs) {
        final List<String> longList = ioNames(config.longOptionList());
        return new DialogueSession(hud, conversation, onlineProfile.getPlayer(), config,
                new ActionbarRenderer(config, wrapper, glyphs),
                () -> {
                    if (longList.isEmpty() || HudConfig.PLAIN.equals(longList.getFirst())) {
                        return null;
                    }
                    try {
                        return registry.getFactory(longList).parse(conversation, onlineProfile);
                    } catch (final QuestException e) {
                        hud.getLogger().warning("No IO for long answer lists (" + config.longOptionList() + "): " + e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Splits a fallback list, dropping this IO so a fallback can never point back at itself.
     */
    private static List<String> ioNames(final String names) {
        return Arrays.stream(names.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty() && !NAME.equals(name))
                .toList();
    }
}
