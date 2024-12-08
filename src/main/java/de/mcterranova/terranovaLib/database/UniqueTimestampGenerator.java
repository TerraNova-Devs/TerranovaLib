package de.mcterranova.terranovaLib.database;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UniqueTimestampGenerator {

    // Map to hold the last timestamp for each UUID
    private final Map<UUID, Long> lastTimestamps = new HashMap<>();

    /**
     * Generates a unique timestamp for a specific UUID. Ensures that timestamps are unique
     * for each UUID but can overlap across different UUIDs. (Exact up to nanoseconds)
     *
     * @param id The UUID for which the timestamp is generated.
     * @return A unique Timestamp.
     */
    public synchronized Timestamp generate(UUID id) {
        // Get the current time in milliseconds since epoch
        long currentTimeMillis = System.currentTimeMillis();

        // Get nanoseconds for precision
        long currentNanoTime = System.nanoTime();
        long nanoTimestamp = currentTimeMillis * 1_000_000 + (currentNanoTime % 1_000_000);

        // Ensure uniqueness for the given UUID
        long lastNanoTimestamp = lastTimestamps.getOrDefault(id, 0L);
        if (nanoTimestamp <= lastNanoTimestamp) {
            nanoTimestamp = lastNanoTimestamp + 1;
        }

        // Update the last timestamp for this UUID
        lastTimestamps.put(id, nanoTimestamp);

        // Convert nanoseconds to a Timestamp
        return Timestamp.from(Instant.ofEpochMilli(nanoTimestamp / 1_000_000)
                .plusNanos(nanoTimestamp % 1_000_000));
    }
}
