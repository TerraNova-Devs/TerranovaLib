package de.mcterranova.terranovaLib.crops;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stages of rice in Nexo: reis_saat (0), reis_stufe1 … reis_stufe7, and the chains of the other ripe looks,
 * reis_b_stufe0 … 7 and reis_c_stufe0 … 7. Kept free of Nexo, so it can be tested.
 */
final class RiceStages {

    static final int RIPE = 7;

    private static final Pattern STAGE = Pattern.compile("(reis_(?:[bc]_)?stufe)(\\d)");

    private RiceStages() {
    }

    /** The stage {@code steps} on from {@code id} in its own chain, at most ripe; null if ripe or no rice stage. */
    static String grown(String id, int steps) {
        String chain;
        int stage;
        if (id.equals("reis_saat")) {
            chain = "reis_stufe";
            stage = 0;
        } else {
            Matcher m = STAGE.matcher(id);
            if (!m.matches()) return null;
            chain = m.group(1);
            stage = Integer.parseInt(m.group(2));
        }
        return stage >= RIPE ? null : chain + Math.min(RIPE, stage + steps);
    }
}
