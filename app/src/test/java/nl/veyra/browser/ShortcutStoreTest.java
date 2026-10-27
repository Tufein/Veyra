package nl.veyra.browser;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ShortcutStoreTest {
    private SharedPreferences preferences;
    private ShortcutStore store;
    @Before public void setup() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("veyra", 0);
        preferences.edit().clear().commit(); store = new ShortcutStore(preferences);
    }
    @Test public void defaultsAreSharedWithoutWritingPreferences() {
        assertEquals(4, store.read().size()); assertEquals("Wikipedia", store.read().get(0).title);
        assertFalse(preferences.contains("shortcuts"));
    }
    @Test public void addEditAndRemoveKeepLegacyStorageAndUnrelatedData() {
        preferences.edit().putString("bookmarks", "keep").commit();
        store.save(-1, "Example", "example.com"); assertEquals(5, store.read().size());
        store.save(4, "Other", "https://other.example/path");
        assertEquals("Other", store.read().get(4).title); assertEquals("https://other.example/path", store.read().get(4).url);
        store.remove(4); assertEquals(4, store.read().size()); assertEquals("keep", preferences.getString("bookmarks", ""));
    }
    @Test public void explicitHostPortsAreAcceptedWithoutTreatingThemAsSchemes() {
        store.save(-1, "Local", "localhost:8080/test");
        assertEquals("https://localhost:8080/test", store.read().get(4).url);
    }
    @Test public void unsafeAndMalformedUrlsDoNotChangeStorage() {
        for (String url : new String[]{"javascript:alert(1)", "file:///tmp/test", "https://user:pass@example.org/", "not a url"}) {
            try { store.save(-1, "Invalid", url); fail(url); }
            catch (IllegalArgumentException expected) { assertEquals("url", expected.getMessage()); }
        }
        assertFalse(preferences.contains("shortcuts"));
    }
    @Test public void limitAndInvalidIndicesDoNotOverwriteExistingShortcuts() {
        for (int i = 0; i < 4; i++) store.save(-1, "Site " + i, "https://example.org/" + i);
        String saved = preferences.getString("shortcuts", "");
        try { store.save(-1, "Extra", "https://example.org"); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("limit", expected.getMessage()); }
        try { store.remove(99); fail(); } catch (IllegalArgumentException expected) { assertEquals("index", expected.getMessage()); }
        assertEquals(saved, preferences.getString("shortcuts", ""));
    }
    @Test public void readingMalformedStorageDoesNotEraseIt() {
        preferences.edit().putString("shortcuts", "broken json").commit();
        assertTrue(store.read().isEmpty()); assertEquals("broken json", preferences.getString("shortcuts", ""));
    }
}
