package de.mcterranova.terranovaLib.npcs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One NPC as written in a definition file.
 *
 * @param location BetonQuest's format {@code x;y;z;world[;yaw;pitch]}; the world is resolved late,
 *                 so definitions for a world that loads after startup still parse
 * @param replaces Citizens UUID of the NPC that existed before this definition (e.g. on Prod). It is
 *                 taken over instead of creating a new one; UUIDs are unique across servers, so a
 *                 server without it simply gets a new NPC
 */
record NpcDefinition(String id, String name, EntityType type, String location, @Nullable String skinName,
                     @Nullable String skinTexture, @Nullable String skinSignature, boolean lookClose,
                     boolean protect, @Nullable UUID replaces) {

    static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    static NpcDefinition parse(final String id, final ConfigurationSection section) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id '" + id + "' may only use a-z, 0-9, _ and -");
        }
        final String location = section.getString("location");
        if (location == null) {
            throw new IllegalArgumentException("'" + id + "' has no location");
        }
        final EntityType type = EntityType.valueOf(section.getString("type", "PLAYER").toUpperCase(Locale.ROOT));
        if (!type.isAlive()) {
            throw new IllegalArgumentException("'" + id + "' has type " + type + ", which is not a living entity");
        }
        final String texture = section.getString("skin.texture");
        final String signature = section.getString("skin.signature");
        if (texture != null && signature == null) {
            throw new IllegalArgumentException("'" + id + "' has a skin texture without its signature");
        }
        final NpcDefinition definition = new NpcDefinition(id, section.getString("name", id), type, location,
                section.getString("skin.name"), texture, signature,
                section.getBoolean("look-close", true), section.getBoolean("protected", true),
                replaces(id, section.getString("replaces")));
        definition.coordinates();
        return definition;
    }

    @Nullable
    private static UUID replaces(final String id, @Nullable final String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + id + "' replaces '" + value + "', which is not a Citizens NPC UUID", e);
        }
    }

    /**
     * @return the location, or {@code null} while its world is not loaded
     */
    @Nullable
    Location resolveLocation() {
        final String[] parts = location.split(";");
        final World world = Bukkit.getWorld(parts[3]);
        if (world == null) {
            return null;
        }
        final double[] c = coordinates();
        return new Location(world, c[0], c[1], c[2], (float) c[3], (float) c[4]);
    }

    /**
     * x, y, z, yaw, pitch. Throws on a malformed location, which {@link #parse} uses to reject it early.
     */
    private double[] coordinates() {
        final String[] parts = location.split(";");
        if (parts.length != 4 && parts.length != 6) {
            throw new IllegalArgumentException("'" + id + "' location must be x;y;z;world or x;y;z;world;yaw;pitch");
        }
        try {
            return new double[]{
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    parts.length == 6 ? Double.parseDouble(parts[4]) : 0, parts.length == 6 ? Double.parseDouble(parts[5]) : 0};
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("'" + id + "' location has a non-number: " + location, e);
        }
    }

    /**
     * Writes a location in the format {@link #parse} reads.
     */
    static String format(final Location location) {
        return String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%s;%.1f;%.1f", location.getX(), location.getY(),
                location.getZ(), location.getWorld().getName(), location.getYaw(), location.getPitch());
    }
}
