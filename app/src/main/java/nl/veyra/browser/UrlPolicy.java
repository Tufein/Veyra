package nl.veyra.browser;

import java.net.IDN;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/** Shared navigation policy for typed addresses, external intents and restored tabs. */
public final class UrlPolicy {
    private UrlPolicy() { }

    public static String resolve(String input) {
        return resolve(input, "https://duckduckgo.com/?q=");
    }

    public static String resolve(String input, String searchPrefix) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return null;
        if (value.regionMatches(true, 0, "https://", 0, 8)
                || value.regionMatches(true, 0, "http://", 0, 7)) {
            return normalizeWebUrl(value);
        }
        // A host:port address is valid input, but script/file/intent schemes never are.
        boolean hostPort = value.matches("(?i)^(localhost|[a-z0-9.-]+\\.[a-z0-9-]+):[0-9]+([/?#].*)?$");
        if (!hostPort && value.matches("(?s)^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return null;
        if (!value.matches("(?s).*\\s.*") && !value.contains("@")) {
            String host = value.split("[/?#]", 2)[0];
            if (host.contains(".") || host.equalsIgnoreCase("localhost") || hostPort) {
                String normalized = normalizeWebUrl("https://" + value);
                if (normalized != null) return normalized;
            }
        }
        try {
            return searchPrefix + URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static boolean allowed(String url, boolean httpsOnly) {
        String valid = normalizeWebUrl(url);
        return valid != null && (!httpsOnly || valid.startsWith("https://"));
    }

    public static String withScheme(String url, boolean https) {
        String valid = normalizeWebUrl(url);
        if (valid == null) return null;
        URI uri = URI.create(valid);
        int port = uri.getPort();
        if ((https && port == 80) || (!https && port == 443)) port = -1;
        return (https ? "https" : "http") + "://" + uri.getHost()
                + (port == -1 ? "" : ":" + port) + uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }

    public static String normalizeWebUrl(String value) {
        if (value == null || value.indexOf('\\') >= 0 || value.matches("(?s).*[\\x00-\\x20\\x7f].*")) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            if (uri.getRawUserInfo() != null || uri.getRawAuthority() == null) return null;
            String authority = uri.getRawAuthority();
            if (authority.contains("@") || authority.contains("%") || authority.endsWith(":")) return null;
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) {
                // URI.getHost() does not recognize Unicode domain names. Convert explicitly.
                String[] parts = authority.split(":", -1);
                if (parts.length > 2) return null;
                host = IDN.toASCII(parts[0], IDN.USE_STD3_ASCII_RULES);
                if (parts.length == 2) {
                    if (!parts[1].matches("[0-9]+")) return null;
                    port = Integer.parseInt(parts[1]);
                }
            }
            if (host.isEmpty() || port < -1 || port > 65535) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.isEmpty()) return null;
            String path = uri.getRawPath();
            return scheme.toLowerCase(Locale.ROOT) + "://" + host
                    + (port == -1 ? "" : ":" + port)
                    + (path == null || path.isEmpty() ? "/" : path)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                    + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (Exception invalid) {
            return null;
        }
    }
}
