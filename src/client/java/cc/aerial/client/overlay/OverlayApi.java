package cc.aerial.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OverlayApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aerial/Overlay");

    private static final String ENDPOINT = "https://api.hypixel.net/v2/player?uuid=";

    private static final String KEYLESS_ENDPOINT = "http://api.abyssoverlay.com/player?uuid=";
    private static final String KEYLESS_USER_AGENT = "node-ao/2.0.3";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ExecutorService POOL = OverlayHttp.POOL;
    private static final HttpClient CLIENT = OverlayHttp.CLIENT;

    private static final Map<UUID, BedwarsStats> CACHE = new ConcurrentHashMap<>();

    private OverlayApi() {
    }

    public static BedwarsStats get(String name, UUID uuid, String apiKey, boolean apiLess) {
        BedwarsStats cached = CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }
        BedwarsStats placeholder = BedwarsStats.loading(name, uuid);
        if (CACHE.putIfAbsent(uuid, placeholder) != null) {
            return CACHE.get(uuid);
        }
        POOL.submit(() -> CACHE.put(uuid, apiLess ? fetchKeyless(name, uuid) : fetch(name, uuid, apiKey)));
        return placeholder;
    }

    public static void forget(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static BedwarsStats fetch(String name, UUID uuid, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + uuid))
                .header("API-Key", apiKey)
                .header("User-Agent", "Aerial")
                .timeout(TIMEOUT)
                .GET()
                .build();
        return fetch(name, uuid, request, "Hypixel API");
    }

    private static BedwarsStats fetchKeyless(String name, UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(KEYLESS_ENDPOINT + uuid))
                .header("User-Agent", KEYLESS_USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();
        return fetch(name, uuid, request, "Abyss");
    }

    private static BedwarsStats fetch(String name, UUID uuid, HttpRequest request, String source) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.warn("Overlay: {} returned {} for {} ({}): {}",
                        source, response.statusCode(), name, uuid, response.body());
                return BedwarsStats.error(name, uuid);
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                LOGGER.warn("Overlay: {} returned a non-object body for {} ({}): {}", source, name, uuid, response.body());
                return BedwarsStats.error(name, uuid);
            }
            JsonObject body = parsed.getAsJsonObject();
            if (!optBoolean(body, "success")) {
                LOGGER.warn("Overlay: {} rejected the lookup for {} ({}): {}",
                        source, name, uuid, optString(body, "cause"));
                return BedwarsStats.error(name, uuid);
            }
            JsonElement player = body.get("player");

            if (player == null || player.isJsonNull()) {
                return BedwarsStats.nicked(name, uuid);
            }
            return parsePlayer(name, uuid, player.getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.warn("Overlay: lookup failed for {} ({}) via {}", name, uuid, source, exception);
            return BedwarsStats.error(name, uuid);
        }
    }

    private static BedwarsStats parsePlayer(String name, UUID uuid, JsonObject player) {
        JsonObject bedwars = path(player, "stats", "Bedwars");
        if (bedwars == null) {
            return BedwarsStats.loaded(name, uuid, 0, 0, 0, 0, 0, 0, 0, true, rankPrefix(player));
        }
        int star = BedwarsLevel.fromExperience(optLong(bedwars, "Experience"));
        int finalKills = optInt(bedwars, "final_kills_bedwars");
        int finalDeaths = optInt(bedwars, "final_deaths_bedwars");
        int wins = optInt(bedwars, "wins_bedwars");
        int losses = optInt(bedwars, "losses_bedwars");
        int beds = optInt(bedwars, "beds_broken_bedwars");

        boolean winstreakKnown = bedwars.has("winstreak") && !bedwars.get("winstreak").isJsonNull();
        int winstreak = winstreakKnown ? optInt(bedwars, "winstreak") : 0;

        return BedwarsStats.loaded(name, uuid, star, finalKills, finalDeaths, wins, losses, beds,
                winstreak, winstreakKnown, rankPrefix(player));
    }

    private static String rankPrefix(JsonObject player) {
        String prefix = optString(player, "prefix");
        if (!prefix.isEmpty()) {
            return prefix;
        }
        String rank = optString(player, "rank");
        if (!rank.isEmpty() && !rank.equals("NORMAL")) {
            return rank;
        }
        String monthly = optString(player, "monthlyPackageRank");
        if (monthly.equals("SUPERSTAR")) {
            return "MVP++";
        }
        String newRank = optString(player, "newPackageRank");
        if (!newRank.isEmpty() && !newRank.equals("NONE")) {
            return newRank.replace("_PLUS", "+");
        }
        return "";
    }

    private static JsonObject path(JsonObject root, String... keys) {
        JsonObject current = root;
        for (String key : keys) {
            if (current == null || !current.has(key) || !current.get(key).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(key);
        }
        return current;
    }

    private static int optInt(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : 0;
    }

    private static long optLong(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : 0L;
    }

    private static boolean optBoolean(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static String optString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public static boolean hasWorld() {
        return Minecraft.getInstance().level != null;
    }
}
