package cc.aerial.client.overlay;

import cc.aerial.client.features.impl.utility.OverlayModule;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayTags {
    private static final String URCHIN_V3 = "https://api.urchin.gg/v3/player/tags?player=";
    private static final String URCHIN_LEGACY = "https://urchin.ws/player/";
    private static final String SERAPH = "https://api.seraph.si/";

    private static final Map<UUID, PlayerTag> CACHE = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();

    private OverlayTags() {
    }

    public static PlayerTag get(String name, UUID uuid) {
        PlayerTag cached = CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }
        OverlayModule module = OverlayModule.INSTANCE;
        String urchinKey = module.getUrchinKey();
        String seraphKey = module.getSeraphKey();
        if (urchinKey.isEmpty() && seraphKey.isEmpty()) {
            return PlayerTag.NONE;
        }
        if (PENDING.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return PlayerTag.NONE;
        }
        OverlayHttp.POOL.submit(() -> {
            try {
                PlayerTag tag = PlayerTag.NONE;
                if (!urchinKey.isEmpty()) {
                    tag = PlayerTag.worst(tag, fetchUrchin(name, urchinKey));
                }
                if (!seraphKey.isEmpty()) {
                    tag = PlayerTag.worst(tag, fetchSeraph(uuid, seraphKey));
                }
                CACHE.put(uuid, tag);
            } finally {
                PENDING.remove(uuid);
            }
        });
        return PlayerTag.NONE;
    }

    public static void clear() {
        CACHE.clear();
        PENDING.clear();
    }

    private static PlayerTag fetchUrchin(String name, String key) {
        String url = URCHIN_V3 + OverlayHttp.encode(name);
        OverlayHttp.Response response = OverlayHttp.get(url, Map.of("X-API-Key", key));
        if (response.unauthorized()) {
            response = OverlayHttp.get(url + "&key=" + OverlayHttp.encode(key), null);
        }
        if (response.ok()) {
            PlayerTag tag = parseUrchin(response.json());
            if (tag.exists()) {
                return tag;
            }
        }
        return fetchLegacyUrchin(name, key);
    }

    private static PlayerTag fetchLegacyUrchin(String name, String key) {
        String url = URCHIN_LEGACY + OverlayHttp.encode(name)
                + "?key=" + OverlayHttp.encode(key) + "&sources=GAME";
        OverlayHttp.Response response = OverlayHttp.get(url, null);

        return response.ok() ? parseUrchin(response.json()) : PlayerTag.NONE;
    }

    private static PlayerTag parseUrchin(JsonObject root) {
        if (root == null || !root.has("tags") || !root.get("tags").isJsonArray()) {
            return PlayerTag.NONE;
        }
        JsonArray tags = root.getAsJsonArray("tags");
        PlayerTag best = PlayerTag.NONE;
        for (JsonElement element : tags) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject tag = element.getAsJsonObject();
            String type = OverlayHttp.string(tag, "tag_type");
            if (type.isEmpty()) {
                type = OverlayHttp.string(tag, "type");
            }
            String reason = OverlayHttp.string(tag, "reason");
            if (type.isEmpty() || isNotice(type, reason)) {
                continue;
            }
            best = PlayerTag.worst(best, new PlayerTag(PlayerTag.Source.URCHIN, type, reason));
        }
        return best;
    }

    private static boolean isNotice(String type, String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        if (lower.contains("urchin api is deprecated") || lower.contains("notice for the developer")) {
            return true;
        }
        return type.toLowerCase(Locale.ROOT).replace(' ', '_').equals("caution")
                && lower.contains("migrate to the new api");
    }

    private static PlayerTag fetchSeraph(UUID uuid, String key) {
        String url = SERAPH + OverlayHttp.undashed(uuid) + "/blacklist";
        OverlayHttp.Response response = OverlayHttp.get(url, Map.of("seraph-api-key", key));
        if (!response.ok() || !OverlayHttp.bool(response.json(), "success")) {
            return PlayerTag.NONE;
        }
        JsonObject blacklist = OverlayHttp.object(OverlayHttp.object(response.json(), "data"), "blacklist");
        if (blacklist == null || !OverlayHttp.bool(blacklist, "tagged")) {
            return PlayerTag.NONE;
        }
        String type = OverlayHttp.string(blacklist, "report_type");
        String reason = OverlayHttp.string(blacklist, "reason");
        if (reason.isEmpty()) {
            reason = OverlayHttp.string(blacklist, "tooltip");
        }
        if (type.isEmpty() && reason.isEmpty()) {
            return PlayerTag.NONE;
        }

        return new PlayerTag(PlayerTag.Source.SERAPH, type.isEmpty() ? "blacklisted" : type, reason);
    }

    public static Map<UUID, PlayerTag> snapshot(List<UUID> uuids) {
        Map<UUID, PlayerTag> out = new HashMap<>();
        for (UUID uuid : uuids) {
            PlayerTag tag = CACHE.get(uuid);
            if (tag != null && tag.exists()) {
                out.put(uuid, tag);
            }
        }
        return out;
    }
}
