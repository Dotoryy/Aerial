package cc.aerial.client.accountmanager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CookieStore {
    private static final Gson GSON = new Gson();
    private final List<StoredCookie> cookies = new ArrayList<>();

    void put(StoredCookie cookie) {
        if (cookie == null || cookie.name == null || cookie.name.isBlank() || cookie.value == null || cookie.value.isBlank()) {
            return;
        }
        if ("Disabled".equalsIgnoreCase(cookie.value)) {
            return;
        }
        for (int i = 0; i < cookies.size(); i++) {
            if (sameSlot(cookies.get(i), cookie)) {
                cookies.set(i, cookie);
                return;
            }
        }
        cookies.add(cookie);
    }

    void put(String domain, String path, String name, String value, boolean secure) {
        put(new StoredCookie(domain, path, name, value, secure));
    }

    boolean isEmpty() {
        return cookies.isEmpty();
    }

    String buildCookieHeader(URI uri, List<String> preferredOrder) {
        List<StoredCookie> matching = new ArrayList<>();
        for (StoredCookie cookie : cookies) {
            if (cookie.matches(uri)) {
                matching.add(cookie);
            }
        }
        if (matching.isEmpty()) {
            return "";
        }
        List<String> orderedNames = new ArrayList<>();
        if (preferredOrder != null) {
            orderedNames.addAll(preferredOrder);
        }
        for (StoredCookie cookie : matching) {
            if (!orderedNames.contains(cookie.name)) {
                orderedNames.add(cookie.name);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (StoredCookie cookie : matching) {
            values.put(cookie.name, cookie.value);
        }
        StringBuilder header = new StringBuilder();
        for (String name : orderedNames) {
            if (values.containsKey(name)) {
                if (!header.isEmpty()) {
                    header.append("; ");
                }
                header.append(name).append('=').append(values.get(name));
            }
        }
        return header.toString();
    }

    String buildCookieHeader(URI uri) {
        return buildCookieHeader(uri, null);
    }

    private Map<String, String> toFlatMap() {
        Map<String, String> flat = new LinkedHashMap<>();
        for (StoredCookie cookie : cookies) {
            flat.put(cookie.name, cookie.value);
        }
        return flat;
    }

    String findMinecraftNetValue(String cookieName) {
        for (int i = cookies.size() - 1; i >= 0; i--) {
            StoredCookie cookie = cookies.get(i);
            if (cookieName.equals(cookie.name)) {
                String domain = cookie.domain == null ? "" : cookie.domain.toLowerCase(Locale.ROOT);
                if (domain.contains("minecraft.net")) {
                    return cookie.value;
                }
            }
        }
        return null;
    }

    boolean hasRequiredAuthCookies() {
        Map<String, String> flat = toFlatMap();
        return flat.containsKey("__Host-MSAAUTH") || flat.containsKey("__Host-MSAAUTHP")
                || flat.containsKey("JSH") || flat.containsKey("JSHP");
    }

    String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("v", 2);
        JsonArray array = new JsonArray();
        for (StoredCookie cookie : cookies) {
            JsonObject entry = new JsonObject();
            entry.addProperty("domain", cookie.domain);
            entry.addProperty("path", cookie.path);
            entry.addProperty("name", cookie.name);
            entry.addProperty("value", cookie.value);
            entry.addProperty("secure", cookie.secure);
            array.add(entry);
        }
        root.add("cookies", array);
        return GSON.toJson(root);
    }

    static CookieStore deserialize(String serialized) {
        CookieStore store = new CookieStore();
        if (serialized == null || serialized.isBlank()) {
            return store;
        }
        try {
            JsonElement rootElement = JsonParser.parseString(serialized);
            if (!rootElement.isJsonObject()) {
                return store;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("cookies") && root.get("cookies").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("cookies")) {
                    if (element.isJsonObject()) {
                        JsonObject entry = element.getAsJsonObject();
                        String name = getString(entry, "name");
                        String value = getString(entry, "value");
                        if (!name.isBlank() && !value.isBlank()) {
                            store.put(getString(entry, "domain"), getString(entry, "path"), name, value,
                                    entry.has("secure") && entry.get("secure").getAsBoolean());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CookieStore] Failed to deserialize cookies: " + e.getMessage());
        }
        return store;
    }

    static boolean isRelevantDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return true;
        }
        domain = domain.toLowerCase(Locale.ROOT);
        return domain.contains("live.com") || domain.contains("microsoftonline.com") || domain.contains("microsoft.com")
                || domain.contains("xboxlive.com") || domain.contains("minecraft.net") || domain.contains("mojang.com");
    }

    private static boolean sameSlot(StoredCookie left, StoredCookie right) {
        return eq(left.domain, right.domain) && eq(left.path, right.path) && eq(left.name, right.name);
    }

    private static boolean eq(String left, String right) {
        return left != null ? left.equals(right) : (right == null || right.isEmpty());
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
