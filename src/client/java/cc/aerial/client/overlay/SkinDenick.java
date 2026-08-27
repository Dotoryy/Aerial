package cc.aerial.client.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinDenick {
    private static final Map<UUID, String> CACHE = new ConcurrentHashMap<>();
    private static final String MISS = "";

    private SkinDenick() {
    }

    public static String resolve(PlayerInfo info) {
        UUID uuid = info.getProfile().id();
        String cached = CACHE.get(uuid);
        if (cached != null) {
            return cached.equals(MISS) ? null : cached;
        }
        String resolved = decode(info);
        CACHE.put(uuid, resolved == null ? MISS : resolved);
        return resolved;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static String decode(PlayerInfo info) {
        try {
            Property textures = info.getProfile().properties().get("textures").stream()
                    .findFirst().orElse(null);
            if (textures == null) {
                return null;
            }
            String json = new String(Base64.getDecoder().decode(textures.value()), StandardCharsets.UTF_8);
            JsonObject blob = JsonParser.parseString(json).getAsJsonObject();
            if (!blob.has("profileName")) {
                return null;
            }
            String profileName = blob.get("profileName").getAsString();
            if (profileName == null || profileName.isEmpty()) {
                return null;
            }

            return profileName.equalsIgnoreCase(info.getProfile().name()) ? null : profileName;
        } catch (Exception exception) {
            return null;
        }
    }
}
