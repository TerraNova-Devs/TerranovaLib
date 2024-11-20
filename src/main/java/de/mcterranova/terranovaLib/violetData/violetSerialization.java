package de.mcterranova.terranovaLib.violetData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class violetSerialization {
    public static String databaseInstantSE(Instant instant) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm.ss.SSSSSSSSSX").withZone(ZoneId.systemDefault()).format(instant);
    }

    public static Instant databaseInstantRE(String instant) {
        return Instant.parse(instant);
    }
}
