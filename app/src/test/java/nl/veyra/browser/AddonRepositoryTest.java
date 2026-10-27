package nl.veyra.browser;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AddonRepositoryTest {
    private JSONObject addon() throws Exception {
        return new JSONObject("{\"guid\":\"addon@example.org\",\"type\":\"extension\","
                + "\"name\":{\"en-US\":\"Test extension\"},\"summary\":{\"en-US\":\"A test\"},"
                + "\"current_version\":{\"version\":\"1.0\",\"compatibility\":{\"android\":{\"min\":\"128.0\",\"max\":\"*\"}},"
                + "\"file\":{\"status\":\"public\",\"url\":\"https://addons.mozilla.org/firefox/downloads/file/123/example.xpi\"}}}");
    }
    private String response(JSONObject addon) { return "{\"results\":[" + addon + "]}"; }

    @Test public void readsCompatibleAndroidCatalogEntry() throws Exception {
        java.util.List<AddonRepository.Addon> list = AddonRepository.parse(response(addon()));
        assertEquals(1, list.size()); assertEquals("addon@example.org", list.get(0).id);
        assertEquals("Test extension", list.get(0).name); assertEquals("1.0", list.get(0).version);
    }
    @Test public void excludesDesktopOnlyAndObsoleteAndroidVersions() throws Exception {
        JSONObject a = addon(); a.getJSONObject("current_version").getJSONObject("compatibility").remove("android");
        assertTrue(AddonRepository.parse(response(a)).isEmpty());
        a = addon(); a.getJSONObject("current_version").getJSONObject("compatibility").getJSONObject("android").put("max", "68.*");
        assertTrue(AddonRepository.parse(response(a)).isEmpty());
    }
    @Test public void excludesUnavailableFilesAndNonExtensions() throws Exception {
        JSONObject a = addon(); a.getJSONObject("current_version").getJSONObject("file").put("status", "disabled");
        assertTrue(AddonRepository.parse(response(a)).isEmpty());
        a = addon(); a.put("type", "statictheme"); assertTrue(AddonRepository.parse(response(a)).isEmpty());
    }
    @Test public void rejectsUntrustedDownloadLocations() {
        assertFalse(AddonRepository.isMozillaDownload("http://addons.mozilla.org/firefox/downloads/file/1/a.xpi"));
        assertFalse(AddonRepository.isMozillaDownload("https://addons.mozilla.org.evil.test/firefox/downloads/file/1/a.xpi"));
        assertFalse(AddonRepository.isMozillaDownload("https://evil.test@addons.mozilla.org/firefox/downloads/file/1/a.xpi"));
        assertFalse(AddonRepository.isMozillaDownload("https://addons.mozilla.org:8443/firefox/downloads/file/1/a.xpi"));
        assertFalse(AddonRepository.isMozillaDownload("file:///tmp/a.xpi"));
        assertFalse(AddonRepository.isMozillaDownload("https://addons.mozilla.org/a.xpi"));
        assertTrue(AddonRepository.isMozillaDownload("https://addons.mozilla.org/firefox/downloads/file/1/a.xpi"));
    }
    @Test public void excludesTooNewOrMalformedCompatibilityRanges() {
        assertFalse(AddonRepository.compatible("200.0", "*"));
        assertFalse(AddonRepository.compatible("158.1", "*"));
        assertFalse(AddonRepository.compatible("128.0", "140.*"));
        assertFalse(AddonRepository.compatible("invalid", "*"));
        assertTrue(AddonRepository.compatible("128.0", "*"));
        assertTrue(AddonRepository.compatible("158.0", "158.*"));
    }
}
