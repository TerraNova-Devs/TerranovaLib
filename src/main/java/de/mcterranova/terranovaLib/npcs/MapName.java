package de.mcterranova.terranovaLib.npcs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * The name of an NPC as plain text for a pin of HeroicMap: Citizens names can carry MiniMessage tags and
 * legacy colour codes, and a pin name has at most 64 characters.
 */
public final class MapName {

    static final int MAX_LENGTH = 64;

    // §a, &a, &#RRGGBB and the §x§R§R§G§G§B§B form of hex colours
    private static final Pattern LEGACY = Pattern.compile("[§&](?:#[0-9a-fA-F]{6}|[0-9a-fk-orxA-FK-ORX])");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private MapName() {
    }

    /** The plain name, cut to 64 characters, or null if nothing is left. */
    public static @Nullable String of(@Nullable final String name) {
        if (name == null) {
            return null;
        }
        String plain = MiniMessage.miniMessage().stripTags(name);
        plain = LEGACY.matcher(plain).replaceAll("");
        plain = CONTROL.matcher(plain).replaceAll("");
        plain = SPACES.matcher(plain).replaceAll(" ").trim();
        if (plain.length() > MAX_LENGTH) {
            // never split a surrogate pair
            final int end = Character.isHighSurrogate(plain.charAt(MAX_LENGTH - 1)) ? MAX_LENGTH - 1 : MAX_LENGTH;
            plain = plain.substring(0, end).trim();
        }
        return plain.isEmpty() ? null : plain;
    }
}
