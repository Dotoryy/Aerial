package cc.aerial.client.accountmanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.User;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class RefreshTokenAuth {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Map<Long, String> XSTS_ERRORS = new HashMap<>();

    static {
        XSTS_ERRORS.put(2148916227L, "The account is banned from Xbox");
        XSTS_ERRORS.put(2148916233L, "The account doesn't have an Xbox account (never signed in)");
        XSTS_ERRORS.put(2148916235L, "The account is from a country where Xbox Live is not available/banned");
        XSTS_ERRORS.put(2148916236L, "The account needs adult verification on Xbox page. (South Korea)");
        XSTS_ERRORS.put(2148916237L, "The account needs adult verification on Xbox page. (South Korea)");
        XSTS_ERRORS.put(2148916238L, "The account is a child (under 18) and cannot proceed unless the account is added to a Family by an adult");
        XSTS_ERRORS.put(2148916262L, "Unknown error");
    }

    private RefreshTokenAuth() {
    }

    public static CompletableFuture<Account> authenticate(String refreshToken, Executor executor) {
        MicrosoftAuth.LOGGER.info("Refresh-token login: starting (token length {})",
                refreshToken == null ? 0 : refreshToken.length());
        return refreshMicrosoftToken(refreshToken, executor)
                .thenComposeAsync(tokens -> acquireXboxLiveToken((String) tokens.get("access_token"), executor)
                        .thenComposeAsync(xbl -> acquireXstsToken((String) xbl.get("Token"), executor)
                                .thenComposeAsync(xsts -> MicrosoftAuth.acquireMCAccessToken((String) xsts.get("Token"), (String) xbl.get("uhs"), executor)
                                        .thenComposeAsync(mcAccessToken -> MicrosoftAuth.login(mcAccessToken, executor)
                                                .thenApply(user -> {
                                                    MicrosoftAuth.LOGGER.info("Refresh-token login: signed in as {}", user.getName());
                                                    return new Account((String) tokens.get("refresh_token"), mcAccessToken,
                                                            user.getName(), user.getProfileId().toString(), 0L, AccountType.REFRESH);
                                                }), executor), executor), executor), executor)
                .whenComplete((account, error) -> {
                    if (error != null) {
                        MicrosoftAuth.LOGGER.error("Refresh-token login failed: {}", describeChain(error));
                    }
                });
    }

    private static CompletableFuture<Map<String, String>> refreshMicrosoftToken(String refreshToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MicrosoftAuth.LOGGER.info("Refresh-token login: exchanging the refresh token with login.live.com");
                String body = MicrosoftAuth.formEncode(Map.of(
                        "client_id", CLIENT_ID,
                        "grant_type", "refresh_token",
                        "redirect_uri", REDIRECT_URI,
                        "refresh_token", refreshToken,
                        "scope", SCOPE
                ));
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://login.live.com/oauth20_token.srf"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                JsonObject json = MicrosoftAuth.sendJson(request);
                if (json.has("error")) {
                    String description = json.has("error_description") ? json.get("error_description").getAsString() : json.get("error").getAsString();
                    throw new IOException(json.get("error").getAsString() + ": " + description);
                }
                String accessToken = require(json, "access_token", "Microsoft access token missing from refresh response.");
                String newRefreshToken = Optional.ofNullable(json.get("refresh_token")).map(e -> e.getAsString())
                        .filter(s -> !s.isBlank()).orElse(refreshToken);
                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", newRefreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Refresh token exchange was cancelled.");
            } catch (Exception e) {
                throw new CompletionException("Unable to refresh Microsoft OAuth token.", e);
            }
        }, executor);
    }

    private static CompletableFuture<Map<String, String>> acquireXboxLiveToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MicrosoftAuth.LOGGER.info("Refresh-token login: acquiring the Xbox Live token");
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", "t=" + accessToken);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();
                JsonObject json = MicrosoftAuth.sendJson(request);
                String token = require(json, "Token", "Xbox Live token missing from response.");
                String uhs = json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
                Map<String, String> result = new HashMap<>();
                result.put("Token", token);
                result.put("uhs", uhs);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live token acquisition was cancelled.");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live token.", e);
            }
        }, executor);
    }

    private static CompletableFuture<Map<String, String>> acquireXstsToken(String xboxToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MicrosoftAuth.LOGGER.info("Refresh-token login: acquiring the XSTS token");
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
                        .POST(HttpRequest.BodyPublishers.ofString(entity.toString()))
                        .build();
                JsonObject json = MicrosoftAuth.sendJson(request);
                if (json.has("XErr")) {
                    long errorCode = json.get("XErr").getAsLong();
                    throw new IOException(XSTS_ERRORS.getOrDefault(errorCode, "Unknown Xbox error (" + errorCode + ")"));
                }
                String token = require(json, "Token", "XSTS token missing from response.");
                Map<String, String> result = new HashMap<>();
                result.put("Token", token);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("XSTS token acquisition was cancelled.");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire XSTS token.", e);
            }
        }, executor);
    }

    static String describeChain(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (text.length() > 0) {
                text.append(" <- ");
            }
            String message = current.getMessage();
            text.append(current.getClass().getSimpleName())
                    .append(message == null ? "" : ": " + message);
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
            depth++;
        }
        return text.toString();
    }

    private static String require(JsonObject json, String field, String errorMessage) throws IOException {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            String value = json.get(field).getAsString();
            if (!value.isBlank()) {
                return value;
            }
        }
        throw new IOException(errorMessage);
    }
}
