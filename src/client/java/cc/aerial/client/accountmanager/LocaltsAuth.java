package cc.aerial.client.accountmanager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class LocaltsAuth {
    private static final String XBOX_LOGIN_ENTRY =
            "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d"
                    + "&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254";

    private static final String XBL_LOGIN_WITH_XBOX =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    private static final String XBL_TOKEN_PREFIX = "\"rp://api.minecraftservices.com/\",";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private LocaltsAuth() {
    }

    public static Account authenticate(String cookieContent) throws IOException, InterruptedException {
        String cookie = normalizeCookie(cookieContent);
        if (cookie.isEmpty() || !cookie.contains("=")) {
            throw new IOException("Localts content is not a cookie assignment");
        }

        String hop1 = followLocation(XBOX_LOGIN_ENTRY, null);
        if (hop1 == null) {
            throw new IOException("Xbox entry did not redirect");
        }
        String hop2 = followLocation(escapeSpaces(hop1), cookie);
        if (hop2 == null) {
            throw new IOException("MSAAUTH cookie was not accepted (no redirect back to Xbox)");
        }
        String hop3 = followLocation(hop2, cookie);
        if (hop3 == null) {
            throw new IOException("Xbox did not return an access token URL");
        }

        String accessTokenParam = extractQuery(hop3, "accessToken");
        if (accessTokenParam.isEmpty()) {
            throw new IOException("Final redirect has no accessToken -- session likely expired");
        }

        byte[] decoded = decodeBase64(accessTokenParam);
        String body = new String(decoded, StandardCharsets.UTF_8);
        int prefixAt = body.indexOf(XBL_TOKEN_PREFIX);
        if (prefixAt < 0) {
            throw new IOException("Xbox token blob missing relying-party segment");
        }
        String segment = body.substring(prefixAt + XBL_TOKEN_PREFIX.length());
        String xstsToken = extractJsonString(segment, "Token");
        String userHash = extractJsonString(segment, "uhs");
        if (xstsToken.isEmpty() || userHash.isEmpty()) {
            throw new IOException("Xbox token blob missing Token or uhs");
        }

        String mcToken = loginWithXbox(xstsToken, userHash);
        JsonObject profile = fetchProfile(mcToken);
        String uuid = profile.get("id").getAsString();
        String username = profile.get("name").getAsString();

        return new Account(username, mcToken, uuid);
    }

    private static String normalizeCookie(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int semicolon = s.indexOf(';');
        return semicolon >= 0 ? s.substring(0, semicolon).trim() : s;
    }

    private static String escapeSpaces(String url) {
        return url.contains(" ") ? url.replace(" ", "%20") : url;
    }

    private static String followLocation(String url, String cookieHeader) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", "Mozilla/5.0")
                .GET();
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        HttpResponse<Void> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        return response.headers().firstValue("Location").orElse(null);
    }

    private static String extractQuery(String url, String name) {
        int at = url.indexOf(name + "=");
        if (at < 0) return "";
        String tail = url.substring(at + name.length() + 1);
        int amp = tail.indexOf('&');
        String value = amp >= 0 ? tail.substring(0, amp) : tail;
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(value);
        }
    }

    private static String extractJsonString(String body, String name) {
        String needle = "\"" + name + "\":\"";
        int at = body.indexOf(needle);
        if (at < 0) return "";
        int start = at + needle.length();
        int end = body.indexOf('"', start);
        return end < 0 ? "" : body.substring(start, end);
    }

    private static String loginWithXbox(String xstsToken, String userHash) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        payload.addProperty("ensureLegacyEnabled", true);
        HttpRequest request = HttpRequest.newBuilder(URI.create(XBL_LOGIN_WITH_XBOX))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("login_with_xbox HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("access_token")) {
            throw new IOException("login_with_xbox response missing access_token");
        }
        return json.get("access_token").getAsString();
    }

    private static JsonObject fetchProfile(String mcToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_PROFILE))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + mcToken)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("/minecraft/profile HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (json.has("error")) {
            throw new IOException(json.get("error").getAsString());
        }
        if (!json.has("id") || !json.has("name")) {
            throw new IOException("Profile missing id or name -- the account may not own Minecraft");
        }
        return json;
    }
}
