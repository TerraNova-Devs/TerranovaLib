package de.mcterranova.terranovaLib.ghostblocks;

import org.bukkit.Chunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostGroupTest {

    @Test
    void chunkKeysDecodeBackToTheirCoordinates() {
        final int[][] chunks = {{0, 0}, {-1, -1}, {5, -7}, {-1875000, 1874999}, {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (final int[] chunk : chunks) {
            final long key = Chunk.getChunkKey(chunk[0], chunk[1]);
            assertEquals(chunk[0], GhostGroup.chunkX(key));
            assertEquals(chunk[1], GhostGroup.chunkZ(key));
        }
    }
}
