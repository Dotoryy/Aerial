package cc.aerial.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OverlayHttp {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aerial/Overlay");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    static final ExecutorService POOL = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "Aerial Overlay API");
        thread.setDaemon(true);
        return thread;
    });

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private OverlayHttp() {
    }

    public static void submit(Runnable task) {
        POOL.execute(task);
    }

    public record Response(int code, String body, JsonObject json) {
        public boolean ok() {
            return code == 200 && json != null;
        }

        public boolean unauthorized() {
            return code == 401 || code == 403;
        }
    }

    public static Response get(String url, Map<String, String> headers) {
        return send(builder(url, headers).GET().build());
    }

    public static Response post(String url, Map<String, String> headers, String body) {
        return post(url, headers, body, "application/json");
    }

    public static Response post(String url, Map<String, String> headers, String body, String contentType) {
        HttpRequest.Builder builder = builder(url, headers);
        if (headers == null || !headers.containsKey("Content-Type")) {
            builder.header("Content-Type", contentType);
        }
        return send(builder
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private static HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Aerial")
                .timeout(TIMEOUT);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder;
    }

    private static Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), parse(response.body()));
        } catch (Exception exception) {
            LOGGER.warn("Overlay: request to {} failed", request.uri(), exception);

            return new Response(0, "", null);
        }
    }

    private static JsonObject parse(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String undashed(java.util.UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : "";
    }

    public static int integer(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                    ? object.get(key).getAsInt() : 0;
        } catch (Exception exception) {
            return 0;
        }
    }

    public static boolean bool(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                    && object.get(key).getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }

    public static JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
                ? root.getAsJsonObject(key) : null;
    }
}
