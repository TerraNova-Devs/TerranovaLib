package de.mcterranova.terranovaLib.npcs;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinHashTest {

    private static final String HASH = "a116e69a845e227f7ca1fdde8c357c8c821ebd4ba619382ea4a1f87d4ae94";

    private static String texture(final String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String skin(final String url) {
        return texture("{\"timestamp\":1,\"profileName\":\"x\",\"textures\":{\"SKIN\":{\"url\":\"" + url
                + "\",\"metadata\":{\"model\":\"slim\"}}}}");
    }

    @Test
    void readsTheHashOverHttpAndHttps() {
        assertEquals(HASH, SkinHash.fromTexture(skin("http://textures.minecraft.net/texture/" + HASH)));
        assertEquals(HASH, SkinHash.fromTexture(skin("https://textures.minecraft.net/texture/" + HASH)));
    }

    @Test
    void rejectsWhatIsNotAHashOnTheSkinHost() {
        assertNull(SkinHash.fromTexture(skin("https://textures.minecraft.net/texture/" + HASH.toUpperCase())));
        assertNull(SkinHash.fromTexture(skin("https://evil.example/texture/" + HASH)));
        assertNull(SkinHash.fromTexture(skin("https://textures.minecraft.net/texture/" + HASH + "/x")));
        assertNull(SkinHash.fromTexture(skin("https://textures.minecraft.net/texture/")));
        assertNull(SkinHash.fromTexture(skin("https://textures.minecraft.net/texture/" + "a".repeat(65))));
        assertNull(SkinHash.fromTexture(skin("https://textures.minecraft.net/other/" + HASH)));
    }

    @Test
    void rejectsBrokenOrMissingValues() {
        assertNull(SkinHash.fromTexture(null));
        assertNull(SkinHash.fromTexture(""));
        assertNull(SkinHash.fromTexture("not base64!"));
        assertNull(SkinHash.fromTexture(texture("not json")));
        assertNull(SkinHash.fromTexture(texture("{\"textures\":{}}")));
        assertNull(SkinHash.fromTexture(texture("{\"textures\":{\"CAPE\":{\"url\":\"x\"}}}")));
    }
}
