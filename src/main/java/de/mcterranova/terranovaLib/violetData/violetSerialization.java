package de.mcterranova.terranovaLib.violetData;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class violetSerialization {
    //gut lesbarer 20 Zeichen String
    public static String databaseInstantSE(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    public static Instant databaseInstantRE(String instant) {
        return Instant.parse(instant);
    }

    public static Timestamp databaseTimestampSE(Instant instant) {
        return Timestamp.from(instant);
    }

    public static Instant databaseTimestampRE(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
