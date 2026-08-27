package cc.aerial.client.accountmanager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CookieAuth {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final List<String> COOKIE_ORDER_JSHP = Arrays.asList("__Host-MSAAUTH", "__Host-MSAAUTHP", "JSHP", "JSH",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");
    private static final List<String> COOKIE_ORDER_JSH = Arrays.asList("__Host-MSAAUTH", "__Host-MSAAUTHP", "JSH", "JSHP",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "MSPOK", "MSPShared", "MSPPre", "MSPCID",
            "MSPOAuthVis", "AMCSecAuth", "NAP", "ANON", "OParams", "PPLState", "WLSSC", "uaid", "pres", "LOpt");

    private CookieAuth() {
    }

    public static CompletableFuture<Boolean> addAccountFromCookieFile(File cookieFile, Consumer<String> status) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        EXECUTOR.execute(() -> {
            try {
                status.accept("&fReading cookie file...&r");
                CookieStore cookieStore = parseCookieFile(cookieFile);
                if (cookieStore.isEmpty()) {
                    status.accept("&cNo valid Microsoft cookies found in file&r");
                    future.complete(false);
                    return;
                }
                if (!cookieStore.hasRequiredAuthCookies()) {
                    status.accept("&cMissing auth cookies (need __Host-MSAAUTH, JSH, or JSHP)&r");
                    future.complete(false);
                    return;
                }
                status.accept("&fAuthenticating with Microsoft...&r");
                authenticateWithCookies(cookieStore, status).whenComplete((result, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        System.err.println("[CookieAuth] Authentication failed: " + cause.getMessage());
                        status.accept("&cAuthentication failed: " + cause.getMessage() + "&r");
                        future.complete(false);
                    } else {
                        future.complete(result);
                    }
                });
            } catch (Exception e) {
                status.accept("&cError processing cookie file: " + e.getMessage() + "&r");
                future.complete(false);
            }
        });
        return future;
    }

    private static CookieStore parseCookieFile(File cookieFile) throws IOException {
        String content = readFile(cookieFile);
        if (content.trim().isEmpty()) {
            return new CookieStore();
        }
        if (content.trim().startsWith("[")) {
            return parseJsonCookies(content);
        }
        CookieStore cookies = parseNetscapeCookies(content);
        return !cookies.isEmpty() ? cookies : parseLooseCookies(content);
    }

    private static String readFile(File cookieFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cookieFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static CookieStore parseJsonCookies(String content) {
        CookieStore store = new CookieStore();
        try {
            JsonElement root = JsonParser.parseString(content);
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("cookies")) {
                array = root.getAsJsonObject().getAsJsonArray("cookies");
            } else {
                return store;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("name") || !obj.has("value")) {
                    continue;
                }
                if (obj.has("expirationDate")) {
                    double expiration = obj.get("expirationDate").getAsDouble();
                    if (expiration > 0.0 && expiration < System.currentTimeMillis() / 1000.0) {
                        continue;
                    }
                }
                String domain = obj.has("domain") ? obj.get("domain").getAsString() : obj.has("host") ? obj.get("host").getAsString() : "";
                String path = obj.has("path") ? obj.get("path").getAsString() : "/";
                String name = obj.get("name").getAsString().trim();
                String value = obj.get("value").getAsString().trim();
                boolean secure = !obj.has("secure") || obj.get("secure").getAsBoolean();
                if (CookieStore.isRelevantDomain(domain) && !value.isEmpty()) {
                    store.put(domain, path, name, value, secure);
                }
            }
        } catch (Exception e) {
            System.err.println("[CookieAuth] Failed to parse JSON cookies: " + e.getMessage());
        }
        return store;
    }

    private static CookieStore parseNetscapeCookies(String content) {
        CookieStore store = new CookieStore();
        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", 7);
            if (parts.length >= 7) {
                String domain = parts[0].trim();
                String path = parts[2].trim();
                String name = parts[5].trim();
                String value = parts[6].trim();
                boolean secure = "TRUE".equalsIgnoreCase(parts[3].trim());
                if (CookieStore.isRelevantDomain(domain) && !value.isEmpty()) {
                    store.put(domain, path, name, value, secure);
                }
            }
        }
        return store;
    }

    private static CookieStore parseLooseCookies(String content) {
        CookieStore store = new CookieStore();
        String normalized = content.replace("\n", "").replace("\r", "");
        for (String segment : normalized.split(";")) {
            segment = segment.trim();
            if (segment.contains("=")) {
                int equals = segment.indexOf('=');
                String name = segment.substring(0, equals).trim();
                String value = segment.substring(equals + 1).trim();
                if (!value.isEmpty()) {
                    store.put("", "/", name, value, true);
                }
            }
        }
        if (store.isEmpty()) {
            for (String line : content.split("\\r?\\n")) {
                line = line.trim();
                if (line.contains("=")) {
                    int equals = line.indexOf('=');
                    String name = line.substring(0, equals).trim();
                    String value = line.substring(equals + 1).trim();
                    if (!value.isEmpty()) {
                        store.put("", "/", name, value, true);
                    }
                }
            }
        }
        return store;
    }

    public static CompletableFuture<Account> loginWithStoredCookies(String serializedCookies, Executor executor) {
        CookieStore store = CookieStore.deserialize(serializedCookies);
        if (store.isEmpty()) {
            CompletableFuture<Account> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("No saved cookie data"));
            return failed;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return authenticateWithStore(store);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static Account authenticateWithStore(CookieStore store) throws Exception {
        Exception lastError = null;
        try {
            String msAccessToken = getMicrosoftAccessToken(store);
            if (msAccessToken != null) {
                return finishMicrosoftTokenLogin(store, msAccessToken);
            }
        } catch (Exception e) {
            lastError = e;
            System.err.println("[CookieAuth] Xbox OAuth login failed: " + e.getMessage());
        }
        try {
            String mcAccessToken = MinecraftNetAuth.loginForMinecraftToken(store);
            if (mcAccessToken != null) {
                return finishMinecraftTokenLogin(store, mcAccessToken);
            }
        } catch (Exception e) {
            lastError = e;
            System.err.println("[CookieAuth] minecraft.net fallback failed: " + e.getMessage());
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Failed to authenticate with cookies (cookies may be expired)");
    }

    private static String getMicrosoftAccessToken(CookieStore store) throws Exception {
        CookieHttpClient client = new CookieHttpClient(store);
        Exception lastError = null;
        String[] oauthUrls = {
                "https://login.live.com/oauth20_authorize.srf?redirect_uri=https://sisu.xboxlive.com/connect/oauth/XboxLive&response_type=token&client_id=000000004420578E&scope=XboxLive.Signin%20XboxLive.offline_access&prompt=none",
                "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&response_type=token&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&prompt=none"
        };
        List<List<String>> orderings = new ArrayList<>();
        orderings.add(COOKIE_ORDER_JSHP);
        orderings.add(COOKIE_ORDER_JSH);
        for (String oauthUrl : oauthUrls) {
            for (List<String> ordering : orderings) {
                try {
                    String token = client.followOAuthRedirects(oauthUrl, 12, ordering);
                    if (token != null) {
                        return token;
                    }
                } catch (Exception e) {
                    lastError = e;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private static Account finishMicrosoftTokenLogin(CookieStore store, String msAccessToken) throws Exception {
        var xbl = acquireXboxLiveToken(msAccessToken);
        String xstsToken = acquireXstsToken(xbl.get("Token"));
        String xblToken = "XBL3.0 x=" + xbl.get("uhs") + ";" + xstsToken;
        McResponse mcResponse = postMinecraftLogin(xblToken);
        if (mcResponse != null && mcResponse.access_token != null) {
            ProfileResponse profile = getMinecraftProfile(mcResponse.access_token);
            if (profile != null && profile.name != null) {
                return new Account(store.serialize(), mcResponse.access_token, profile.name, profile.id, 0L, AccountType.COOKIE);
            }
            throw new IOException("Failed to get Minecraft profile");
        }
        throw new IOException("Failed to get Minecraft access token");
    }

    private static Account finishMinecraftTokenLogin(CookieStore store, String mcAccessToken) throws Exception {
        ProfileResponse profile = getMinecraftProfile(mcAccessToken);
        if (profile != null && profile.name != null) {
            return new Account(store.serialize(), mcAccessToken, profile.name, profile.id, 0L, AccountType.COOKIE);
        }
        throw new IOException("Failed to get Minecraft profile");
    }

    private static CompletableFuture<Boolean> authenticateWithCookies(CookieStore store, Consumer<String> status) {
        status.accept("&fAuthenticating with cookies...&r");
        return loginWithCookies(store, EXECUTOR).thenApply(account -> {
            User user = SessionManager.createUser(account.getUsername(), account.getUuid(), account.getAccessToken());
            AccountManager.accounts.add(account);
            AccountManager.save();
            SessionManager.set(user);
            status.accept("&aSuccessfully logged in as " + user.getName() + "&r");
            return true;
        }).exceptionally(error -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            System.err.println("[CookieAuth] Authentication failed: " + cause.getMessage());
            status.accept("&cAuthentication failed: " + cause.getMessage() + "&r");
            return false;
        });
    }

    private static CompletableFuture<Account> loginWithCookies(CookieStore store, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return authenticateWithStore(store);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static java.util.Map<String, String> acquireXboxLiveToken(String accessToken) throws Exception {
        Exception lastError = null;
        for (String ticketPrefix : new String[]{"t=", "d="}) {
            try {
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", ticketPrefix + accessToken);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Go-http-client/1.1")
                        .header("X-Xbl-Contract-Version", "0")
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString(), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Xbox Live authentication failed (" + response.statusCode() + "): " + response.body());
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
                result.put("Token", json.get("Token").getAsString());
                result.put("uhs", json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString());
                return result;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Xbox Live authentication failed");
    }

    private static String acquireXstsToken(String xboxToken) throws Exception {
        JsonObject entity = new JsonObject();
        JsonObject properties = new JsonObject();
        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);
        entity.add("Properties", properties);
        entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        entity.addProperty("TokenType", "JWT");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Go-http-client/1.1")
                .header("X-Xbl-Contract-Version", "0")
                .POST(HttpRequest.BodyPublishers.ofString(entity.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("XSTS authentication failed (" + response.statusCode() + "): " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (json.has("XErr")) {
            throw new IOException("XSTS error: " + json.get("XErr").getAsString());
        }
        return json.get("Token").getAsString();
    }

    public static McResponse postMinecraftLogin(String xblToken) throws Exception {
        String payload = "{\"identityToken\":\"" + xblToken + "\",\"ensureLegacyEnabled\":true}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Minecraft login failed (" + response.statusCode() + "): " + response.body());
        }
        return GSON.fromJson(response.body(), McResponse.class);
    }

    public static ProfileResponse getMinecraftProfile(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Minecraft profile request failed (" + response.statusCode() + "): " + response.body());
        }
        return GSON.fromJson(response.body(), ProfileResponse.class);
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    public static class ProfileResponse {
        public String name;
        public String id;
    }

    public static class McResponse {
        public String access_token;
    }
}
