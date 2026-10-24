package de.mcterranova.terranovaLib.dialoguehud;

import de.mcterranova.terranovaLib.utils.PluginHooks;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.betonquest.betonquest.api.common.component.ComponentLineWrapper;
import org.betonquest.betonquest.api.common.component.font.Font;
import org.betonquest.betonquest.api.common.component.font.FontRegistry;
import org.betonquest.betonquest.api.integration.IntegrationService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Shows BetonQuest conversations in the actionbar instead of the chat. Settings in dialogue-hud.yml.
 */
public final class DialogueHud {

    private static final String CONFIG = "dialogue-hud.yml";

    @Nullable
    private static DialogueHud instance;

    private final JavaPlugin plugin;

    /** At most one per player: a new conversation replaces what an old one left on screen. */
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    /** Fonts as BetonQuest had them before glyph widths were added, so a reload does not stack them. */
    private final Map<Key, Font> originalFonts = new ConcurrentHashMap<>();

    private volatile HudConfig hudConfig;

    @Nullable
    private volatile FontRegistry fonts;

    @Nullable
    private volatile GlyphIntegration glyphs;

    private DialogueHud(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.hudConfig = loadConfig();
    }

    /**
     * Whether a player currently has a dialogue in the actionbar. Other plugins that use the
     * actionbar can check this and hold their own messages back, since the actionbar shows only
     * one message at a time and the last sender wins.
     */
    public static boolean isInDialogue(final Player player) {
        final DialogueHud hud = instance;
        return hud != null && hud.sessions.containsKey(player.getUniqueId());
    }

    /**
     * Registers the input listener and /dialoguehud now, and the conversation IO once BetonQuest
     * enables. Call only where BetonQuest is installed: these classes name BetonQuest's.
     */
    public static DialogueHud enable(final JavaPlugin plugin) {
        final DialogueHud hud = new DialogueHud(plugin);
        instance = hud;
        // BetonQuest enables after the lib and takes integrations until all plugins are enabled, so
        // "conversationIO: terranova_hud" resolves when it parses its packages a tick later.
        PluginHooks.whenEnabled(plugin, "BetonQuest", () -> {
            final IntegrationService integrations = plugin.getServer().getServicesManager().load(IntegrationService.class);
            if (integrations == null) {
                plugin.getLogger().severe("BetonQuest's IntegrationService is not available; the dialogue HUD stays off.");
                return;
            }
            integrations.withPolicies().builder()
                    .enable(api -> {
                        hud.fonts = api.fonts();
                        ConversationIntegration.register(hud, api);
                    })
                    // Nexo has registered its glyphs by now.
                    .postEnable(api -> hud.loadGlyphs())
                    .integrate(plugin);
        });

        plugin.getServer().getPluginManager().registerEvents(new DialogueInputHandler(hud), plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("dialoguehud", "Reloads or previews the dialogue HUD.",
                        List.of("dhud"), hud.new HudCommand()));
        return hud;
    }

    public void disable() {
        List.copyOf(sessions.values()).forEach(DialogueSession::dispose);
        instance = null;
    }

    Plugin plugin() {
        return plugin;
    }

    Logger getLogger() {
        return plugin.getLogger();
    }

    HudConfig hudConfig() {
        return hudConfig;
    }

    @Nullable
    GlyphIntegration glyphs() {
        return glyphs;
    }

    @Nullable
    DialogueSession session(final Player player) {
        return sessions.get(player.getUniqueId());
    }

    void replaceSession(final DialogueSession session) {
        final DialogueSession old = sessions.put(session.player().getUniqueId(), session);
        if (old != null && old != session) {
            old.dispose();
        }
    }

    void removeSession(final DialogueSession session) {
        sessions.remove(session.player().getUniqueId(), session);
    }

    /** dialogue-hud.yml in the data folder, written from the default in the jar the first time. */
    private HudConfig loadConfig() {
        final File file = new File(plugin.getDataFolder(), CONFIG);
        if (!file.exists()) {
            plugin.saveResource(CONFIG, false);
        }
        return HudConfig.from(YamlConfiguration.loadConfiguration(file));
    }

    private void loadGlyphs() {
        final FontRegistry registry = fonts;
        glyphs = registry == null ? null : GlyphIntegration.load(hudConfig, registry, originalFonts, getLogger());
    }

    private final class HudCommand implements BasicCommand {

        @Override
        public void execute(final CommandSourceStack source, final String[] args) {
            final String sub = args.length == 0 ? "" : args[0];
            switch (sub) {
                case "reload" -> {
                    hudConfig = loadConfig();
                    loadGlyphs();
                    source.getSender().sendRichMessage(glyphs == null
                            ? "<yellow>Reloaded. No usable frame glyphs, see the console."
                            : "<green>Reloaded with frame glyphs.");
                }
                case "preview" -> preview(source, args);
                default -> source.getSender().sendRichMessage("<gray>/dialoguehud reload | preview [text]");
            }
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
            return args.length <= 1 ? List.of("reload", "preview") : List.of();
        }

        @Override
        public String permission() {
            return "dialoguehud.admin";
        }

        /**
         * Shows the first page of a text in the caller's actionbar without a conversation, to check
         * the frame's position and size in game at different GUI scales.
         */
        private void preview(final CommandSourceStack source, final String[] args) {
            final FontRegistry registry = fonts;
            if (!(source.getExecutor() instanceof final Player player) || registry == null) {
                source.getSender().sendRichMessage("<red>Run this as a player, after BetonQuest has started.");
                return;
            }
            final String text = args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length))
                    : "Die See birgt viele Geheimnisse, und nicht alle davon wollen gefunden werden. Wer hinausfährt,"
                    + " sollte wissen, wohin, und wer zurückkehrt, bringt mehr mit als Fisch. Frag die alten Kapitäne"
                    + " am Hafen: Jeder von ihnen hat eine Geschichte, und keine gleicht der anderen.";
            final ActionbarRenderer renderer = new ActionbarRenderer(hudConfig, new ComponentLineWrapper(registry), glyphs);
            final Component prefix = renderer.prefix(Component.text("Friedolin"), "default");
            final List<List<Component>> pages = renderer.paginate(prefix, Component.text(text));
            final List<Component> lines = renderer.readingLines(pages.getFirst(), true, 1, pages.size());
            final int width = lines.stream().mapToInt(renderer::width).max().orElse(0);
            player.sendActionBar(renderer.finish(lines, renderer.frameRows(lines.size()), width));
            source.getSender().sendRichMessage("<gray>" + pages.size() + " page(s), first page " + lines.size()
                    + " line(s), " + width + " px wide, " + (glyphs == null ? "plain text." : "with frame."));
        }
    }
}
