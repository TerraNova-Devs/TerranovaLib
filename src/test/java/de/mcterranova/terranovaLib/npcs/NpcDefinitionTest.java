package de.mcterranova.terranovaLib.npcs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDefinitionTest {

    @Test
    void readsAFullDefinitionAndAppliesDefaults() throws InvalidConfigurationException {
        final NpcDefinition full = NpcDefinition.parse("rotbart", section("""
                name: "<red>Rotbart"
                type: villager
                location: "100.5;64;-200.5;world;90;0"
                skin:
                  name: Notch
                look-close: false
                """));
        assertEquals(EntityType.VILLAGER, full.type());
        assertEquals("Notch", full.skinName());
        assertEquals(false, full.lookClose());
        assertTrue(full.protect());

        final NpcDefinition minimal = NpcDefinition.parse("wache", section("location: \"1;2;3;world\""));
        assertEquals(EntityType.PLAYER, minimal.type());
        assertEquals("wache", minimal.name());
        assertNull(minimal.replaces());
    }

    @Test
    void readsTheOriginalItReplaces() {
        final NpcDefinition friedolin = NpcDefinition.parse("friedolin", section("""
                location: "1;2;3;world"
                replaces: e8cfe600-1ef7-450f-959c-19578ef3a3eb
                """));
        assertEquals(UUID.fromString("e8cfe600-1ef7-450f-959c-19578ef3a3eb"), friedolin.replaces());
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("location: \"1;2;3;world\"\nreplaces: 3")));
    }

    @Test
    void rejectsWhatWouldBreakLater() {
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("Rotbart", section("location: \"1;2;3;world\"")));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("type: PLAYER")));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("location: \"1;2;world\"")));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("location: \"1;x;3;world\"")));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("location: \"1;2;3;world\"\ntype: ARROW")));
        assertThrows(IllegalArgumentException.class, () -> NpcDefinition.parse("a", section("location: \"1;2;3;world\"\nskin:\n  texture: abc")));
    }

    private static ConfigurationSection section(final String yaml) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (final InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return config;
    }
}
