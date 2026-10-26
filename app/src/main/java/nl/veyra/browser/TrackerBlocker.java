package nl.veyra.browser;

import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.IOException;

/** Immutable host index, populated off the UI thread from the bundled hosts snapshot. */
public final class TrackerBlocker {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagservices.com", "adservice.google.com",
            "adnxs.com", "adsrvr.org", "criteo.com", "criteo.net", "outbrain.com",
            "taboola.com", "scorecardresearch.com", "quantserve.com", "hotjar.com",
            "hotjar.io", "ads-twitter.com", "amazon-adsystem.com", "pubmatic.com",
            "rubiconproject.com", "openx.net", "casalemedia.com", "demdex.net"
    ));

    private TrackerBlocker() { }

    private static volatile Set<String> loaded = Collections.emptySet();

    public static void load(Reader source) throws IOException {
        Set<String> parsed = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+", 3);
                if (parts.length < 2 || !"0.0.0.0".equals(parts[0])) continue;
                String host = canonical(parts[1]);
                if (host.contains(".") && !host.equals("0.0.0.0") && host.matches("[a-z0-9.-]+")) parsed.add(host);
            }
        }
        loaded = Collections.unmodifiableSet(parsed);
    }

    public static int size() {
        Set<String> all = new HashSet<>(loaded);
        all.addAll(HOSTS);
        return all.size();
    }

    public static boolean blocks(String requestHost, String pageHost) {
        if (requestHost == null || pageHost == null) return false;
        String host = canonical(requestHost);
        String page = canonical(pageHost);
        // Don't block a navigation target or its own subdomains.
        if (host.equals(page) || host.endsWith("." + page)) return false;
        Set<String> snapshot = loaded;
        String candidate = host;
        while (true) {
            if (HOSTS.contains(candidate) || snapshot.contains(candidate)) {
                return !page.equals(candidate) && !page.endsWith("." + candidate);
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) return false;
            candidate = candidate.substring(dot + 1);
        }
    }

    private static String canonical(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
