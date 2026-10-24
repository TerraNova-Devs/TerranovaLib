package de.mcterranova.terranovaLib.npcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Reads the texture hash from the base64 textures value of a skin, the part of textures.SKIN.url after
 * /texture/. That hash is all HeroicMap takes for a head; it fetches the skin itself.
 */
public final class SkinHash {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{1,64}");

    private SkinHash() {
    }

    /** The hash, or null if the value is not a skin on textures.minecraft.net. */
    public static @Nullable String fromTexture(@Nullable final String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            final String json = new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
            final JsonElement url = JsonParser.parseString(json).getAsJsonObject()
                    .getAsJsonObject("textures").getAsJsonObject("SKIN").get("url");
            final URI uri = new URI(url.getAsString());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || !"textures.minecraft.net".equals(uri.getHost())
                    || uri.getPath() == null || !uri.getPath().startsWith("/texture/")) {
                return null;
            }
            final String hash = uri.getPath().substring("/texture/".length());
            return HASH.matcher(hash).matches() ? hash : null;
        } catch (RuntimeException | java.net.URISyntaxException e) {
            // broken base64 or JSON, or no SKIN in it
            return null;
        }
    }
}
