package cc.aerial.client.accountmanager;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.google.gson.JsonParseException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.User;

import java.util.Locale;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class MicrosoftAuth {
    static final Logger LOGGER = LoggerFactory.getLogger("Aerial/Auth");

    public static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static final int PORT = 25575;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String CALLBACK_PAGE = "<html><head><title>Aerial Account Manager</title></head>"
            + "<body style=\"font-family:sans-serif;text-align:center;margin-top:15%\">"
            + "<h2>You can close this window now.</h2></body></html>";

    private MicrosoftAuth() {
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public static URI getMSAuthLink(String state) {
        String redirect = encode(String.format("http://localhost:%d/callback", PORT));
        return URI.create("https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri=" + redirect
                + "&scope=" + encode("XboxLive.signin XboxLive.offline_access")
                + "&state=" + state
                + "&prompt=select_account");
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 0);
            } catch (IOException e) {
                throw new CompletionException("Unable to start local auth server!", e);
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> authCode = new AtomicReference<>(null);
            AtomicReference<String> errorMsg = new AtomicReference<>(null);
            server.createContext("/callback", exchange -> {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (!state.equals(query.get("state"))) {
                    errorMsg.set(String.format("State mismatch! Expected '%s' but got '%s'.", state, query.get("state")));
                } else if (query.containsKey("code")) {
                    authCode.set(query.get("code"));
                } else if (query.containsKey("error")) {
                    errorMsg.set(String.format("%s: %s", query.get("error"), query.get("error_description")));
                }

                byte[] response = CALLBACK_PAGE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.getResponseBody().close();
                latch.countDown();
            });

            try {
                server.start();
                latch.await();
                String code = Optional.ofNullable(authCode.get())
                        .filter(c -> !c.isBlank())
                        .orElseThrow(() -> new Exception(Optional.ofNullable(errorMsg.get())
                                .orElse("There was no auth code or error description present.")));
                server.stop(2);
                return code;
            } catch (InterruptedException e) {
                server.stop(2);
                throw new CancellationException("Microsoft auth code acquisition was cancelled!");
            } catch (Exception e) {
                server.stop(2);
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return exchangeToken(Map.of(
                "client_id", CLIENT_ID,
                "grant_type", "authorization_code",
                "code", authCode,
                "redirect_uri", String.format("http://localhost:%d/callback", PORT)
        ), executor);
    }

    public static CompletableFuture<Map<String, String>> refreshMSAccessTokens(String msToken, Executor executor) {
        return exchangeToken(Map.of(
                "client_id", CLIENT_ID,
                "grant_type", "refresh_token",
                "refresh_token", msToken,
                "redirect_uri", String.format("http://localhost:%d/callback", PORT)
        ), executor);
    }

    private static CompletableFuture<Map<String, String>> exchangeToken(Map<String, String> form, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = formEncode(form);
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.live.com/oauth20_token.srf"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                JsonObject json = sendJson(request);
                String accessToken = requireField(json, "access_token", "error_description");
                String refreshToken = requireField(json, "refresh_token", "error_description");
                Map<String, String> result = new LinkedHashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft token exchange was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to exchange Microsoft OAuth token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject entity = xblRequestBody("d=" + accessToken, "http://auth.xboxlive.com");
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();
                JsonObject json = sendJson(request);
                return requireField(json, "Token", "Message");
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("SandboxId", "RETAIL");
                var userTokens = new com.google.gson.JsonArray();
                userTokens.add(accessToken);
                properties.add("UserTokens", userTokens);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();
                JsonObject json = sendJson(request);
                String token = requireField(json, "Token", "Message");
                String uhs = json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
                Map<String, String> result = new LinkedHashMap<>();
                result.put("Token", token);
                result.put("uhs", uhs);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live XSTS token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live XSTS token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String payload = String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken);
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                JsonObject json = sendJson(request);
                return requireField(json, "access_token", "errorMessage");
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Minecraft access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<User> login(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + mcToken)
                        .GET()
                        .build();
                JsonObject json = sendJson(request);
                if (json.has("error")) {
                    throw new IOException(json.get("error").getAsString() + ": " + optString(json, "errorMessage"));
                }
                String uuid = Optional.ofNullable(json.get("id")).map(e -> e.getAsString()).filter(s -> !s.isBlank())
                        .orElseThrow(() -> new IOException("Minecraft profile ID (UUID) was missing from the response."));
                String name = json.get("name").getAsString();
                return SessionManager.createUser(name, uuid, mcToken);
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft profile fetching was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch Minecraft profile!", e);
            }
        }, executor);
    }

    public static CompletableFuture<User> login(String accessToken, String username, String uuid, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            if (accessToken == null || accessToken.isBlank() || username == null || username.isBlank() || uuid == null || uuid.isBlank()) {
                throw new IllegalArgumentException("Access Token, Username, and UUID cannot be empty for direct login.");
            }
            return SessionManager.createUser(username, uuid, accessToken);
        }, executor);
    }

    private static JsonObject xblRequestBody(String rpsTicket, String relyingParty) {
        JsonObject entity = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", rpsTicket);
        entity.add("Properties", properties);
        entity.addProperty("RelyingParty", relyingParty);
        entity.addProperty("TokenType", "JWT");
        return entity;
    }

    static JsonObject sendJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        String host = request.uri().getHost();
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();

        if (status < 200 || status >= 300) {
            LOGGER.warn("{} -> HTTP {}: {}", host, status, redact(body));
            throw new IOException(explainFailure(host, status, body));
        }

        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("{} -> HTTP {} but the body is not a JSON object: {}", host, status, redact(body));
                throw new IOException("Unexpected reply from " + host + summarise(body));
            }
            LOGGER.info("{} -> HTTP {} ok", host, status);
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            LOGGER.warn("{} -> HTTP {} with an unparseable body: {}", host, status, redact(body));
            throw new IOException("Unreadable reply from " + host + summarise(body), e);
        }
    }

    private static String explainFailure(String host, int status, String body) {
        String description = "";
        String code = "";
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonObject json = parsed.getAsJsonObject();
                code = optString(json, "error");
                description = optString(json, "error_description");
            }
        } catch (JsonParseException ignored) {
        }

        String lower = description.toLowerCase(Locale.ROOT);
        if (lower.contains("compromised") || lower.contains("security interrupt")) {
            return "Microsoft has locked this account pending identity verification. "
                    + "Sign in at account.live.com, complete the check, then add the account again.";
        }
        if (lower.contains("consent") || lower.contains("aadsts65001")) {
            return "This account has not granted the sign-in permission. Add it again with the "
                    + "interactive Microsoft login.";
        }
        if (lower.contains("expired") || "invalid_grant".equals(code)) {
            return "The saved refresh token is no longer valid -- sign in again to get a new one.";
        }
        if (!description.isBlank()) {
            return description;
        }
        return "HTTP " + status + " from " + host + summarise(body);
    }

    private static String summarise(String body) {
        String clean = redact(body).replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return "";
        }
        return " -- " + (clean.length() > 160 ? clean.substring(0, 160) + "..." : clean);
    }

    static String redact(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return body.replaceAll("(?i)(\"(?:access_token|refresh_token|id_token|Token|RpsTicket)\"\\s*:\\s*\")[^\"]*",
                "$1<redacted>");
    }

    private static String requireField(JsonObject json, String field, String errorField) throws IOException {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            String value = json.get(field).getAsString();
            if (!value.isBlank()) {
                return value;
            }
        }
        if (json.has("error")) {
            throw new IOException(json.get("error").getAsString() + ": " + optString(json, errorField));
        }
        if (json.has("XErr")) {
            throw new IOException("Xbox error " + json.get("XErr").getAsString() + ": " + optString(json, "Message"));
        }
        throw new IOException("There was no '" + field + "' or error description present.");
    }

    private static String optString(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull() ? json.get(field).getAsString() : "";
    }

    static String formEncode(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}
