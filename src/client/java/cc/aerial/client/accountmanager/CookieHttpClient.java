package cc.aerial.client.accountmanager;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

final class CookieHttpClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private final CookieStore store;

    CookieHttpClient(CookieStore store) {
        this.store = store;
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    void followRedirects(String startUrl, int maxRedirects) throws Exception {
        HttpClient client = client();
        String currentUrl = startUrl;
        for (int hop = 0; hop < maxRedirects; hop++) {
            URI uri = URI.create(currentUrl);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET();
            String cookieHeader = store.buildCookieHeader(uri);
            if (cookieHeader != null && !cookieHeader.isBlank()) {
                builder.header("Cookie", cookieHeader);
            }
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            mergeResponseCookies(response, uri);
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Request failed (" + response.statusCode() + ") at " + currentUrl);
                }
                return;
            }
            currentUrl = uri.resolve(location).toString();
        }
        throw new IOException("Too many redirects while requesting " + startUrl);
    }

    String followOAuthRedirects(String startUrl, int maxRedirects, List<String> preferredOrder) throws Exception {
        HttpClient client = client();
        String currentUrl = startUrl;
        for (int hop = 0; hop < maxRedirects; hop++) {
            URI uri = URI.create(currentUrl);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET();
            String cookieHeader = preferredOrder == null ? store.buildCookieHeader(uri) : store.buildCookieHeader(uri, preferredOrder);
            if (cookieHeader != null && !cookieHeader.isBlank()) {
                builder.header("Cookie", cookieHeader);
            }
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            mergeResponseCookies(response, uri);
            String location = response.headers().firstValue("Location").orElse(null);
            if (location != null) {
                String oauthError = extractOAuthError(location);
                if (oauthError != null) {
                    throw new IOException(oauthError);
                }
                String token = extractAccessToken(location);
                if (token != null) {
                    return token;
                }
                if (statusCode == 302 || statusCode == 303 || statusCode == 301 || statusCode == 307) {
                    currentUrl = uri.resolve(location).toString();
                    continue;
                }
            }
            return statusCode == 200 ? null : null;
        }
        return null;
    }

    private void mergeResponseCookies(HttpResponse<?> response, URI requestUri) {
        for (String value : response.headers().allValues("Set-Cookie")) {
            parseSetCookie(value, requestUri);
        }
    }

    private void parseSetCookie(String headerValue, URI requestUri) {
        if (headerValue == null || headerValue.isBlank()) {
            return;
        }
        String[] parts = headerValue.split(";", -1);
        if (parts.length == 0) {
            return;
        }
        String nameValue = parts[0].trim();
        int equals = nameValue.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = nameValue.substring(0, equals).trim();
        String value = nameValue.substring(equals + 1).trim();
        String domain = requestUri.getHost();
        String path = "/";
        boolean secure = false;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            if (attribute.isEmpty()) {
                continue;
            }
            int attrEquals = attribute.indexOf('=');
            String key = attrEquals > 0 ? attribute.substring(0, attrEquals).trim().toLowerCase(Locale.ROOT) : attribute.toLowerCase(Locale.ROOT);
            String attrValue = attrEquals > 0 ? attribute.substring(attrEquals + 1).trim() : "";
            if ("domain".equals(key) && !attrValue.isEmpty()) {
                domain = attrValue;
            } else if ("path".equals(key) && !attrValue.isEmpty()) {
                path = attrValue;
            } else if ("secure".equals(key)) {
                secure = true;
            }
        }
        store.put(domain, path, name, value, secure);
    }

    private static String extractOAuthError(String location) {
        String query = location;
        if (location.contains("#")) {
            query = location.split("#", 2)[1];
        } else if (location.contains("?")) {
            query = location.split("\\?", 2)[1];
        }
        String error = null;
        String description = null;
        for (String param : query.split("&")) {
            if (param.startsWith("error=")) {
                error = param.substring("error=".length());
            } else if (param.startsWith("error_description=")) {
                description = param.substring("error_description=".length());
            }
        }
        if (error == null) {
            return null;
        }
        error = URLDecoder.decode(error, StandardCharsets.UTF_8);
        if (description != null) {
            description = URLDecoder.decode(description, StandardCharsets.UTF_8);
        }
        return description != null ? error + ": " + description : error;
    }

    private static String extractAccessToken(String location) {
        if (location.contains("#")) {
            String fragment = location.split("#", 2)[1];
            for (String param : fragment.split("&")) {
                if (param.startsWith("access_token=")) {
                    return URLDecoder.decode(param.substring("access_token=".length()), StandardCharsets.UTF_8);
                }
            }
        }
        if (location.contains("access_token=")) {
            int start = location.indexOf("access_token=") + "access_token=".length();
            int end = location.indexOf('&', start);
            String token = end == -1 ? location.substring(start) : location.substring(start, end);
            return URLDecoder.decode(token, StandardCharsets.UTF_8);
        }
        return null;
    }
}
