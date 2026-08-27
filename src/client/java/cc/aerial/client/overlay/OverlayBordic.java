package cc.aerial.client.overlay;

import cc.aerial.client.features.impl.utility.OverlayModule;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayBordic {
    private static final String SESSIONS = "https://api.bordic.xyz/v4/sessions/daily?key=";
    private static final String WINSTREAK = "https://bordic.xyz/api/v2/resources/winstreak?uuid=";

    private static final int BATCH = 10;

    public record Session(boolean present, float fkdr, float wlr, int starsGained,
                          int finalKills, int wins) {
    }

    public static final Session MISSING = new Session(false, 0.0f, 0.0f, 0, 0, 0);

    private static final Map<UUID, Session> SESSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WINSTREAK_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private OverlayBordic() {
    }

    public static Session session(UUID uuid) {
        Session cached = SESSION_CACHE.get(uuid);
        return cached == null ? MISSING : cached;
    }

    public static int winstreak(UUID uuid) {
        Integer cached = WINSTREAK_CACHE.get(uuid);
        return cached == null ? -1 : cached;
    }

    public static void clear() {
        SESSION_CACHE.clear();
        WINSTREAK_CACHE.clear();
        PENDING.clear();
    }

    public static void request(List<BedwarsStats> roster) {
        String key = OverlayModule.INSTANCE.getBordicKey();
        if (key.isEmpty() || roster.isEmpty()) {
            return;
        }

        List<UUID> needSession = new ArrayList<>();
        for (BedwarsStats stats : roster) {
            UUID uuid = stats.getUuid();
            if (SESSION_CACHE.containsKey(uuid) || !PENDING.add(uuid)) {
                continue;
            }
            needSession.add(uuid);

            if (stats.isLoaded() && !stats.isWinstreakKnown()) {
                UUID target = uuid;
                OverlayHttp.POOL.submit(() -> fetchWinstreak(target));
            }
        }

        for (int start = 0; start < needSession.size(); start += BATCH) {
            List<UUID> batch = needSession.subList(start, Math.min(start + BATCH, needSession.size()));
            List<UUID> copy = List.copyOf(batch);
            OverlayHttp.POOL.submit(() -> fetchSessions(copy, key));
        }
    }

    private static void fetchSessions(List<UUID> batch, String key) {
        try {
            StringBuilder body = new StringBuilder("{\"uuids\":[");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) {
                    body.append(',');
                }
                body.append('"').append(OverlayHttp.undashed(batch.get(i))).append('"');
            }
            body.append("]}");

            OverlayHttp.Response response =
                    OverlayHttp.post(SESSIONS + OverlayHttp.encode(key), null, body.toString());
            if (response.ok() && OverlayHttp.bool(response.json(), "success")
                    && response.json().has("sessions") && response.json().get("sessions").isJsonArray()) {
                for (JsonElement element : response.json().getAsJsonArray("sessions")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject session = element.getAsJsonObject();
                    UUID uuid = parseUuid(OverlayHttp.string(session, "uuid"));
                    if (uuid != null) {
                        SESSION_CACHE.put(uuid, parseSession(session));
                    }
                }
            }
        } finally {
            for (UUID uuid : batch) {
                SESSION_CACHE.putIfAbsent(uuid, MISSING);
                PENDING.remove(uuid);
            }
        }
    }

    private static Session parseSession(JsonObject session) {
        if (!OverlayHttp.bool(session, "success")) {
            return MISSING;
        }
        JsonObject delta = OverlayHttp.object(session, "delta");
        int finalKills = OverlayHttp.integer(delta, "final_kills_bedwars");
        int finalDeaths = OverlayHttp.integer(delta, "final_deaths_bedwars");
        int wins = OverlayHttp.integer(delta, "wins_bedwars");
        int losses = OverlayHttp.integer(delta, "losses_bedwars");

        int starsGained = 0;
        JsonObject before = OverlayHttp.object(OverlayHttp.object(session, "historical"), "value");
        JsonObject after = OverlayHttp.object(OverlayHttp.object(session, "current"), "value");
        if (before != null && after != null) {
            starsGained = BedwarsLevel.fromExperience(experience(after))
                    - BedwarsLevel.fromExperience(experience(before));
        }

        return new Session(true, ratio(finalKills, finalDeaths), ratio(wins, losses),
                starsGained, finalKills, wins);
    }

    private static long experience(JsonObject bedwars) {
        int value = OverlayHttp.integer(bedwars, "Experience");
        return value != 0 ? value : OverlayHttp.integer(bedwars, "experience");
    }

    private static float ratio(int numerator, int denominator) {
        return denominator > 0 ? (float) numerator / denominator : numerator;
    }

    private static void fetchWinstreak(UUID uuid) {
        OverlayHttp.Response response =
                OverlayHttp.get(WINSTREAK + OverlayHttp.undashed(uuid), null);
        if (!response.ok() || !OverlayHttp.bool(response.json(), "success")) {
            return;
        }
        JsonObject data = OverlayHttp.object(response.json(), "data");
        if (data == null) {
            return;
        }
        WINSTREAK_CACHE.put(uuid, OverlayHttp.integer(data, "winstreak"));
    }

    private static UUID parseUuid(String undashed) {
        String cleaned = undashed.replace("-", "");
        if (cleaned.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(cleaned.substring(0, 8) + "-" + cleaned.substring(8, 12) + "-"
                    + cleaned.substring(12, 16) + "-" + cleaned.substring(16, 20) + "-"
                    + cleaned.substring(20));
        } catch (Exception exception) {
            return null;
        }
    }
}
