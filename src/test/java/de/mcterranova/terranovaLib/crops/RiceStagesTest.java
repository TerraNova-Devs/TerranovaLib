package de.mcterranova.terranovaLib.crops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RiceStagesTest {

    @Test
    void boneMealGrowsWithinTheChainUpToRipe() {
        // the seed is stage 0 of the first chain
        assertEquals("reis_stufe2", RiceStages.grown("reis_saat", 2));
        assertEquals("reis_stufe5", RiceStages.grown("reis_saat", 5));
        assertEquals("reis_stufe6", RiceStages.grown("reis_stufe3", 3));
        // the other chains stay in their chain
        assertEquals("reis_b_stufe5", RiceStages.grown("reis_b_stufe0", 5));
        assertEquals("reis_c_stufe4", RiceStages.grown("reis_c_stufe2", 2));
        // never past ripe
        assertEquals("reis_stufe7", RiceStages.grown("reis_stufe5", 5));
        assertEquals("reis_c_stufe7", RiceStages.grown("reis_c_stufe6", 2));
    }

    @Test
    void ripeOrOtherFurnitureNotAtAll() {
        assertNull(RiceStages.grown("reis_stufe7", 2));
        assertNull(RiceStages.grown("reis_b_stufe7", 3));
        assertNull(RiceStages.grown("weizen_stufe1", 2));
    }
}
