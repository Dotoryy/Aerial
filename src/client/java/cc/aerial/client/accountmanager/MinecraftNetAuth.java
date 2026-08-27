package cc.aerial.client.accountmanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class MinecraftNetAuth {
    private static final String[] LOGIN_ENTRY_URLS = {
            "https://www.minecraft.net/msaproxy/login/signin?returnUrl=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Fprofile",
            "https://www.minecraft.net/en-us/login",
            "https://login.live.com/oauth20_authorize.srf?client_id=000000004C12AE8F&redirect_uri=https%3A%2F%2Fwww.minecraft.net%2Flogin&response_type=code&scope=XboxLive.Signin%20XboxLive.offline_access&prompt=none",
            "https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&response_type=code&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&prompt=none"
    };
    private static final String[] PROFILE_URLS = {
            "https://www.minecraft.net/en-us/profile",
            "https://www.minecraft.net/en-us/msaprofile/mygames/editprofile"
    };

    private MinecraftNetAuth() {
    }

    static String loginForMinecraftToken(CookieStore store) throws Exception {
        CookieHttpClient client = new CookieHttpClient(store);
        Exception lastError = null;

        for (String entryUrl : LOGIN_ENTRY_URLS) {
            try {
                client.followRedirects(entryUrl, 20);
                String token = extractMinecraftAccessToken(store);
                if (token != null) {
                    return token;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        for (String profileUrl : PROFILE_URLS) {
            try {
                client.followRedirects(profileUrl, 10);
                String token = extractMinecraftAccessToken(store);
                if (token != null) {
                    return token;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    static String extractMinecraftAccessToken(CookieStore store) {
        String bearerToken = store.findMinecraftNetValue("bearer_token");
        if (looksLikeJwt(bearerToken)) {
            return bearerToken;
        }
        String accessTokenCookie = store.findMinecraftNetValue("access_token");
        if (accessTokenCookie == null || accessTokenCookie.isBlank()) {
            return null;
        }
        String decoded = accessTokenCookie;
        try {
            decoded = URLDecoder.decode(accessTokenCookie, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        if (looksLikeJwt(decoded)) {
            return decoded;
        }
        try {
            JsonElement rootElement = JsonParser.parseString(decoded);
            if (!rootElement.isJsonObject()) {
                return null;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("user") && root.get("user").isJsonObject()) {
                JsonObject user = root.getAsJsonObject("user");
                if (user.has("accessToken")) {
                    String token = user.get("accessToken").getAsString();
                    if (looksLikeJwt(token)) {
                        return token;
                    }
                }
            }
            if (root.has("accessToken")) {
                String token = root.get("accessToken").getAsString();
                if (looksLikeJwt(token)) {
                    return token;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean looksLikeJwt(String value) {
        return value != null && value.startsWith("eyJ") && value.split("\\.").length == 3;
    }
}
