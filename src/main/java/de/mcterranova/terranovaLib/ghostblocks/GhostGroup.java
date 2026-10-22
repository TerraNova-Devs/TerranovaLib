package de.mcterranova.terranovaLib.ghostblocks;

import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A named set of blocks in one world, indexed by chunk so a chunk sent to a player costs one lookup.
 *
 * @param byChunk chunk key ({@link Chunk#getChunkKey(int, int)}) to the group's blocks in that chunk
 */
record GhostGroup(String id, String world, boolean defaultVisible, Map<Long, Map<BlockPosition, BlockData>> byChunk) {

    static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    /** Keeps a typo in a fill from flooding every client with millions of block changes. */
    static final int MAX_BLOCKS = 65_536;

    static GhostGroup parse(final String id, final YamlConfiguration yaml) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("group id '" + id + "' may only use a-z, 0-9, _ and -");
        }
        final String world = yaml.getString("world");
        if (world == null) {
            throw new IllegalArgumentException("group '" + id + "' has no world");
        }
        final Map<Long, Map<BlockPosition, BlockData>> byChunk = new HashMap<>();
        final int[] count = {0};
        for (final String line : yaml.getStringList("blocks")) {
            final String[] parts = line.trim().split("\\s+", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("group '" + id + "': '" + line + "' is not \"x y z block\"");
            }
            put(byChunk, count, id, integer(id, parts[0]), integer(id, parts[1]), integer(id, parts[2]), blockData(id, parts[3]));
        }
        for (final Map<?, ?> fill : yaml.getMapList("fills")) {
            final int[] from = coordinates(id, String.valueOf(fill.get("from")));
            final int[] to = coordinates(id, String.valueOf(fill.get("to")));
            final BlockData data = blockData(id, String.valueOf(fill.get("block")));
            for (int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
                for (int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
                    for (int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
                        put(byChunk, count, id, x, y, z, data);
                    }
                }
            }
        }
        byChunk.replaceAll((chunk, blocks) -> Map.copyOf(blocks));
        return new GhostGroup(id, world, yaml.getBoolean("default-visible", false), Map.copyOf(byChunk));
    }

    /**
     * Writes blocks in the format {@link #parse} reads.
     */
    static YamlConfiguration toYaml(final String world, final boolean defaultVisible, final List<String> blocks) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", world);
        yaml.set("default-visible", defaultVisible);
        yaml.set("blocks", blocks);
        return yaml;
    }

    /** Inverse of {@link Chunk#getChunkKey(int, int)}. */
    static int chunkX(final long chunkKey) {
        return (int) chunkKey;
    }

    /** Inverse of {@link Chunk#getChunkKey(int, int)}. */
    static int chunkZ(final long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    static String line(final int x, final int y, final int z, final BlockData data) {
        return x + " " + y + " " + z + " " + data.getAsString();
    }

    private static void put(final Map<Long, Map<BlockPosition, BlockData>> byChunk, final int[] count, final String id,
                            final int x, final int y, final int z, final BlockData data) {
        if (++count[0] > MAX_BLOCKS) {
            throw new IllegalArgumentException("group '" + id + "' has more than " + MAX_BLOCKS + " blocks");
        }
        byChunk.computeIfAbsent(Chunk.getChunkKey(x >> 4, z >> 4), key -> new HashMap<>())
                .put(Position.block(x, y, z), data);
    }

    private static int[] coordinates(final String id, final String value) {
        final String[] parts = value.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("group '" + id + "': '" + value + "' is not \"x y z\"");
        }
        return new int[]{integer(id, parts[0]), integer(id, parts[1]), integer(id, parts[2])};
    }

    private static int integer(final String id, final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("group '" + id + "': '" + value + "' is not a whole number", e);
        }
    }

    private static BlockData blockData(final String id, final String value) {
        try {
            return Bukkit.createBlockData(value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("group '" + id + "': '" + value + "' is not a block state", e);
        }
    }
}
