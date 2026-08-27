package cc.aerial.client.accountmanager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class LocaltsService {
    private static final String BASE_URL = "https://localts.store/v1";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(3);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();

    private static String apiKey = loadApiKey();

    private LocaltsService() {
    }

    public static boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public static String getApiKey() {
        return apiKey == null ? "" : apiKey;
    }

    public static void setApiKey(String key) {
        apiKey = key == null ? "" : key.trim();
        File file = apiKeyFile();
        try {
            if (apiKey.isEmpty()) {
                if (file.exists()) {
                    file.delete();
                }
                return;
            }
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), apiKey, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Localts] Could not save API key: " + e.getMessage());
        }
    }

    private static File apiKeyFile() {
        return new File(Minecraft.getInstance().gameDirectory,
                "aerial" + File.separator + "localts_apikey.txt");
    }

    private static String loadApiKey() {
        File file = apiKeyFile();
        if (!file.exists()) {
            return "";
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    public record Account(String username, long balance) {
    }

    public record Product(String id, String name, String description, String category,
                          long priceInCredits, int stock, String type,
                          Map<Integer, Integer> quantityDiscounts) {
    }

    public enum OrderStatus { PENDING, PACKAGING, PACKAGED }

    public static CompletableFuture<Account> me(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject json = requireSuccess(get("/me", true));
            return new Account(json.get("username").getAsString(),
                    json.get("balance").getAsLong());
        }, executor);
    }

    public static CompletableFuture<List<Product>> products(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject json = requireSuccess(get("/products", false));
            JsonArray array = json.getAsJsonArray("products");
            List<Product> out = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                out.add(parseProduct(element.getAsJsonObject()));
            }
            return out;
        }, executor);
    }

    public static CompletableFuture<cc.aerial.client.accountmanager.Account> purchaseAndImport(
            String productId, Executor executor, java.util.function.Consumer<String> status) {
        return CompletableFuture.supplyAsync(() -> {
            status.accept("Placing order...");
            JsonObject purchase = requireSuccess(post("/products/" + productId + "/purchase?amount=1"));
            String orderId = purchase.get("orderId").getAsString();

            long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
            JsonObject order;
            while (true) {
                order = requireSuccess(get("/orders/get-order?id="
                        + URLEncoder.encode(orderId, StandardCharsets.UTF_8), true));
                String statusText = order.get("status").getAsString();
                OrderStatus orderStatus = OrderStatus.valueOf(statusText);
                if (orderStatus == OrderStatus.PACKAGED) {
                    break;
                }
                status.accept("Waiting for order (" + statusText.toLowerCase(Locale.ROOT) + ")...");
                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException("Order " + orderId + " timed out at " + statusText);
                }
                try {
                    Thread.sleep(POLL_INTERVAL.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for order", e);
                }
            }

            JsonArray items = order.getAsJsonArray("items");
            if (items == null || items.isEmpty()) {
                throw new RuntimeException("Order packaged but has no items");
            }
            return items.get(0).getAsJsonObject().get("content").getAsString();
        }, executor).thenCompose(msaauth -> {
            status.accept("Signing in with the cookie...");
            return importCookieAccount(msaauth, executor);
        });
    }

    private static CompletableFuture<cc.aerial.client.accountmanager.Account> importCookieAccount(
            String cookieContent, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return LocaltsAuth.authenticate(cookieContent);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException(e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage(), e);
            }
        }, executor).thenApply(account -> {
            account.setType(AccountType.COOKIE);
            Minecraft.getInstance().execute(() -> {
                AccountManager.accounts.add(account);
                AccountManager.save();
            });
            return account;
        });
    }

    private static JsonObject get(String path, boolean withAuth) {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(REQUEST_TIMEOUT)
                .GET(), withAuth);
    }

    private static JsonObject post(String path) {
        return send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody()), true);
    }

    private static JsonObject send(HttpRequest.Builder builder, boolean withAuth) {
        if (withAuth) {
            if (!hasApiKey()) {
                throw new RuntimeException("Set an API key first");
            }
            builder.header("X-API-Key", apiKey);
        }
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new RuntimeException("API key rejected");
            }
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("HTTP " + response.statusCode());
            }
            JsonElement element = JsonParser.parseString(response.body());
            if (!element.isJsonObject()) {
                throw new RuntimeException("Unexpected response");
            }
            return element.getAsJsonObject();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
        }
    }

    private static JsonObject requireSuccess(JsonObject json) {
        JsonElement success = json.get("success");
        if (success != null && success.isJsonPrimitive() && success.getAsJsonPrimitive().isBoolean()
                && !success.getAsBoolean()) {
            String error = json.has("error") ? json.get("error").getAsString() : "Unknown error";
            throw new RuntimeException(error);
        }
        return json;
    }

    private static Product parseProduct(JsonObject json) {
        Map<Integer, Integer> discounts = new TreeMap<>();
        if (json.has("quantityDiscounts") && json.get("quantityDiscounts").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("quantityDiscounts").entrySet()) {
                try {
                    discounts.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new Product(
                json.get("id").getAsString(),
                json.get("name").getAsString(),
                json.has("description") ? json.get("description").getAsString() : "",
                json.has("category") ? json.get("category").getAsString() : "",
                json.get("priceInCredits").getAsLong(),
                json.has("stock") ? json.get("stock").getAsInt() : 0,
                json.has("type") ? json.get("type").getAsString() : "",
                discounts);
    }
}
