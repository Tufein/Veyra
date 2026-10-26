package nl.veyra.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationPolicyTest {
    @Test public void resolvesAddressesAndSearches() {
        assertEquals("https://example.com/", UrlPolicy.resolve(" example.com "));
        assertEquals("https://example.com/a?q=a%20b#part", UrlPolicy.resolve("https://EXAMPLE.com/a?q=a%20b#part"));
        assertEquals("http://localhost:8080/", UrlPolicy.resolve("http://localhost:8080"));
        assertEquals("https://example.com:8443/a", UrlPolicy.resolve("example.com:8443/a"));
        assertEquals("https://duckduckgo.com/?q=weer+in+Amsterdam", UrlPolicy.resolve("weer in Amsterdam"));
        assertEquals("https://xn--bcher-kva.de/", UrlPolicy.resolve("https://bücher.de"));
        assertEquals("https://[::1]:8443/", UrlPolicy.resolve("https://[::1]:8443"));
    }

    @Test public void rejectsDangerousOrMisleadingNavigation() {
        for (String input : new String[] { "javascript:alert(1)", "data:text/html,hi", "file:///etc/passwd",
                "content://contacts/1", "intent://foo", "https://trusted.example@evil.example/",
                "https://example.com\\@evil.example", "https://example.com:99999", "https://",
                "https://example.com/\nspoof", "https://%65xample.com/" }) {
            assertNull(input, UrlPolicy.resolve(input));
        }
        assertNull(UrlPolicy.normalizeWebUrl("about:blank"));
        assertNull(UrlPolicy.resolve("  "));
    }

    @Test public void blockerUsesDomainBoundariesAndPreservesFirstParty() {
        assertTrue(TrackerBlocker.blocks("ads.doubleclick.net", "news.example"));
        assertTrue(TrackerBlocker.blocks("DOUBLECLICK.NET.", "news.example"));
        assertFalse(TrackerBlocker.blocks("notdoubleclick.net", "news.example"));
        assertFalse(TrackerBlocker.blocks("doubleclick.net.evil.example", "news.example"));
        assertFalse(TrackerBlocker.blocks("ads.doubleclick.net", "doubleclick.net"));
        assertFalse(TrackerBlocker.blocks("doubleclick.net", "ads.doubleclick.net"));
        assertFalse(TrackerBlocker.blocks(null, "news.example"));
        assertFalse(TrackerBlocker.blocks("doubleclick.net", null));
    }

    @Test public void enforcesHttpsAndPreservesUrlDuringExplicitProtocolChoice() {
        assertTrue(UrlPolicy.allowed("http://example.com/path", false));
        assertFalse(UrlPolicy.allowed("http://example.com/path", true));
        assertTrue(UrlPolicy.allowed("https://example.com/path", true));
        assertFalse(UrlPolicy.allowed("file:///private", false));
        assertEquals("https://example.com/a?q=x%20y#z", UrlPolicy.withScheme("http://example.com:80/a?q=x%20y#z", true));
        assertEquals("http://example.com/a", UrlPolicy.withScheme("https://example.com:443/a", false));
        assertEquals("https://localhost:8080/a", UrlPolicy.withScheme("http://localhost:8080/a", true));
        assertNull(UrlPolicy.resolve("https://example.com:-1/"));
        assertNull(UrlPolicy.resolve("https://example.com:/"));
    }

    @Test public void changesOnlySearchQueriesWhenSearchProviderChanges() {
        assertEquals("https://www.google.com/search?q=weer+vandaag", UrlPolicy.resolve("weer vandaag", "https://www.google.com/search?q="));
        assertEquals("https://example.com/", UrlPolicy.resolve("example.com", "https://www.google.com/search?q="));
        assertEquals("https://duckduckgo.com/?q=test", UrlPolicy.resolve("https://duckduckgo.com/?q=test", "https://www.google.com/search?q="));
    }

    @Test public void loadsHostsSnapshotWithoutTreatingCommentsOrLocalhostAsRules() throws Exception {
        TrackerBlocker.load(new java.io.StringReader("# license comment\n127.0.0.1 localhost\n0.0.0.0 ads.fixture.example\n0.0.0.0 telemetry.fixture.example # inline comment\n0.0.0.0 localhost\n"));
        try {
            assertTrue(TrackerBlocker.blocks("sub.ads.fixture.example", "publisher.example"));
            assertTrue(TrackerBlocker.blocks("telemetry.fixture.example", "publisher.example"));
            assertFalse(TrackerBlocker.blocks("notads.fixture.example", "publisher.example"));
            assertFalse(TrackerBlocker.blocks("ads.fixture.example.evil.test", "publisher.example"));
            assertFalse(TrackerBlocker.blocks("sub.ads.fixture.example", "ads.fixture.example"));
            assertFalse(TrackerBlocker.blocks("localhost", "publisher.example"));
        } finally { TrackerBlocker.load(new java.io.StringReader("")); }
    }

    @Test public void bundledListContainsRealRulesAndRetainsSourceAttribution() throws Exception {
        java.nio.file.Path asset = java.nio.file.Path.of("src/main/assets/adblock-hosts.txt");
        String content = new String(java.nio.file.Files.readAllBytes(asset), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("# Start adaway.org"));
        assertTrue(content.contains("# Start KADhosts"));
        assertFalse(content.contains("# Start mvps.org"));
        assertFalse(content.contains("# Start someonewhocares.org"));
        TrackerBlocker.load(new java.io.StringReader(content));
        try {
            assertTrue(TrackerBlocker.size() > 50000);
            assertTrue(TrackerBlocker.blocks("analytics.163.com", "publisher.example"));
            assertFalse(TrackerBlocker.blocks("analytics.163.com.safe.example", "publisher.example"));
        } finally { TrackerBlocker.load(new java.io.StringReader("")); }
    }
}
