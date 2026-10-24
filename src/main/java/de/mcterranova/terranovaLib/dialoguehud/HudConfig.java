package de.mcterranova.terranovaLib.dialoguehud;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The parsed config.yml. Immutable, so a running conversation keeps the settings it started with
 * across /dialoguehud reload.
 */
record HudConfig(
        int updateInterval,
        int keepalive,
        int maxTextWidth,
        int maxLines,
        int minLines,
        boolean typing,
        int typingCharsPerUpdate,
        int epilogueTicks,
        String prefixFormat,
        String nextFormat,
        String progressFormat,
        String optionFormat,
        String optionIdleFormat,
        String chooseFormat,
        String selectorFormat,
        boolean glyphsEnabled,
        String frameLeft,
        String frameRight,
        List<String> frameMiddle,
        String rowFont,
        int padding,
        double paddingRatio,
        String selectorGlyph,
        Map<String, String> npcIcons,
        Set<Input> next,
        Set<Input> confirm,
        Set<Input> cancel,
        boolean scroll,
        long debounceMs,
        int maxHudOptions,
        String noResourcePack,
        String noGlyphs,
        String longOptionList
) {

    /** The fallback value that keeps this HUD, only without glyphs. */
    static final String PLAIN = "plain";

    /** Vanilla inputs the server reliably receives during a conversation. */
    enum Input { RIGHT_CLICK, LEFT_CLICK, SWAP_HANDS, JUMP, SNEAK }

    static HudConfig from(final FileConfiguration config) {
        final Map<String, String> icons = new HashMap<>();
        final ConfigurationSection iconSection = config.getConfigurationSection("glyphs.npc-icons");
        if (iconSection != null) {
            iconSection.getKeys(false).forEach(key -> icons.put(key, iconSection.getString(key, "")));
        }
        return new HudConfig(
                Math.max(1, config.getInt("hud.update-interval-ticks", 2)),
                Math.max(1, config.getInt("hud.keepalive-ticks", 30)),
                Math.max(60, config.getInt("hud.max-text-width", 300)),
                Math.max(1, config.getInt("hud.max-lines", 6)),
                Math.max(1, config.getInt("hud.min-lines", 2)),
                config.getBoolean("hud.typing-effect", true),
                Math.max(1, config.getInt("hud.typing-chars-per-update", 2)),
                Math.max(0, config.getInt("hud.epilogue-ticks", 60)),
                config.getString("format.prefix", "<icon><gold><name></gold> <dark_gray>│</dark_gray> "),
                config.getString("format.next-page", " <gray>▶</gray>"),
                config.getString("format.progress", " <dark_gray><page>/<pages></dark_gray>"),
                config.getString("format.option", "<selector><yellow><option></yellow>"),
                config.getString("format.option-idle", "<gray>   <option></gray>"),
                config.getString("format.choose", "<gray>Antworte:</gray> <dark_gray>(<index>/<count>)</dark_gray>"),
                config.getString("format.selector", "<white>» </white>"),
                config.getBoolean("glyphs.enabled", true),
                config.getString("glyphs.frame-left", ""),
                config.getString("glyphs.frame-right", ""),
                List.copyOf(config.getStringList("glyphs.frame-middle")),
                config.getString("glyphs.row-font", "terranova:dialogue_row{row}"),
                Math.max(0, config.getInt("glyphs.padding", 8)),
                Math.max(0, config.getDouble("glyphs.padding-ratio", 0.05)),
                config.getString("glyphs.selector", ""),
                Map.copyOf(icons),
                inputs(config.getStringList("input.next")),
                inputs(config.getStringList("input.confirm")),
                inputs(config.getStringList("input.cancel")),
                config.getBoolean("input.scroll", true),
                Math.max(0, config.getLong("input.debounce-ms", 150)),
                Math.max(1, config.getInt("selection.max-hud-options", 6)),
                config.getString("fallback.no-resourcepack", "dialog"),
                config.getString("fallback.no-glyphs", PLAIN),
                config.getString("fallback.long-option-list", "dialog"));
    }

    private static Set<Input> inputs(final List<String> names) {
        final Set<Input> inputs = EnumSet.noneOf(Input.class);
        for (final String name : names) {
            // An unknown name is a typo in config.yml; failing loudly beats a silently dead key.
            inputs.add(Input.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        }
        return inputs;
    }
}
