package cc.aerial.client.accountmanager;

import java.net.URI;
import java.util.Locale;

final class StoredCookie {
    final String domain;
    final String path;
    final String name;
    final String value;
    final boolean secure;

    StoredCookie(String domain, String path, String name, String value, boolean secure) {
        this.domain = normalizeDomain(domain);
        this.path = normalizePath(path);
        this.name = name;
        this.value = value;
        this.secure = secure;
    }

    boolean matches(URI uri) {
        if (uri == null || name == null || name.isEmpty()) {
            return false;
        }
        if (secure && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!domainMatches(host)) {
            return false;
        }
        String requestPath = uri.getPath();
        if (requestPath == null || requestPath.isEmpty()) {
            requestPath = "/";
        }
        return requestPath.startsWith(path);
    }

    private boolean domainMatches(String host) {
        if (domain == null || domain.isEmpty()) {
            return true;
        }
        String normalized = domain.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            return host.equals(normalized);
        }
        String bare = normalized.substring(1);
        return host.equals(bare) || host.endsWith(normalized);
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        domain = domain.trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            return "";
        }
        return !domain.startsWith(".") && domain.contains(".") ? "." + domain : domain;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
