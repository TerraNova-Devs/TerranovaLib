package de.mcterranova.terranovaLib.npcs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MapNameTest {

    @Test
    void stripsTagsAndColourCodes() {
        assertEquals("Friedolin", MapName.of("Friedolin"));
        assertEquals("Händlerin Mara", MapName.of("<gradient:#ff0000:#00ff00>Händlerin</gradient> <b>Mara</b>"));
        assertEquals("Wache", MapName.of("§6§lWache"));
        assertEquals("Wache", MapName.of("&6Wa&#12ab34che"));
        assertEquals("Wache", MapName.of("§x§1§2§a§b§3§4Wache"));
        assertEquals("Zwei Zeilen", MapName.of("Zwei\n  Zeilen\t"));
    }

    @Test
    void cutsToTheLimitOfAPin() {
        assertEquals("a".repeat(MapName.MAX_LENGTH), MapName.of("a".repeat(100)));
        // an emoji across the limit is dropped, not split
        assertEquals("a".repeat(MapName.MAX_LENGTH - 1), MapName.of("a".repeat(MapName.MAX_LENGTH - 1) + "😀b"));
    }

    @Test
    void nothingLeftIsNoName() {
        assertNull(MapName.of(null));
        assertNull(MapName.of(""));
        assertNull(MapName.of("  "));
        assertNull(MapName.of("<red></red>§a"));
    }
}
