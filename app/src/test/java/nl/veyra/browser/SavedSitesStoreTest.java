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
public final class SavedSitesStoreTest {
    private SharedPreferences preferences;
    private SavedSitesStore store;
    @Before public void setup() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("veyra", 0);
        preferences.edit().clear().commit(); store = new SavedSitesStore(preferences);
    }
    @Test public void oldBookmarksAndDefaultPinsAreReadWithoutMigrationWrites() {
        String legacy = "[{\"title\":\"Saved\",\"url\":\"HTTPS://EXAMPLE.ORG\"}]";
        preferences.edit().putString("bookmarks", legacy).putString("tabs", "keep").commit();
        assertEquals("https://example.org/", store.bookmarks().get(0).url);
        assertEquals(4, store.shortcuts().size()); assertFalse(preferences.contains("shortcuts"));
        assertEquals(legacy, preferences.getString("bookmarks", "")); assertEquals("keep", preferences.getString("tabs", ""));
    }
    @Test public void pinningCreatesOneBookmarkAndUnpinningKeepsIt() {
        SavedSitesStore.Site site = new SavedSitesStore.Site("Example", "https://example.org/");
        store.pin(site); store.pin(site);
        assertEquals(1, store.bookmarks().size()); assertEquals(5, store.shortcuts().size());
        store.unpin(site.url); assertFalse(store.isPinned(site.url)); assertTrue(store.isBookmarked(site.url));
    }
    @Test public void editsFromEitherPlaceUpdateTheLinkedWebsite() {
        store.pin(new SavedSitesStore.Site("Original", "https://example.org/"));
        store.saveBookmark(0, "Renamed", "https://other.example/path?q=1");
        assertEquals("Renamed", store.shortcuts().get(4).title);
        new ShortcutStore(preferences).save(4, "Final", "https://final.example/");
        assertEquals("Final", store.bookmarks().get(0).title); assertEquals("https://final.example/", store.bookmarks().get(0).url);
        assertFalse(store.isBookmarked("https://example.org/"));
    }
    @Test public void deletingBookmarkRemovesItsPinAndPreservesOtherWebsites() {
        store.pin(new SavedSitesStore.Site("Example", "https://example.org/"));
        store.removeBookmark("https://example.org/");
        assertTrue(store.bookmarks().isEmpty()); assertEquals(4, store.shortcuts().size());
    }
    @Test public void savingExistingDefaultAsBookmarkLinksWithoutDuplicatePin() {
        store.saveBookmark(-1, "My encyclopedia", "https://nl.wikipedia.org/");
        assertEquals(4, store.shortcuts().size()); assertEquals("My encyclopedia", store.shortcuts().get(0).title);
    }
    @Test public void fullStartPageDoesNotCreateAnOrphanBookmark() {
        for (int i=0;i<4;i++) store.pin(new SavedSitesStore.Site("Site "+i, "https://example.org/"+i));
        String before = preferences.getString("bookmarks", "");
        try { store.pin(new SavedSitesStore.Site("Overflow", "https://overflow.example/")); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("limit", expected.getMessage()); }
        assertEquals(before, preferences.getString("bookmarks", "")); assertEquals(8, store.shortcuts().size());
    }
    @Test public void duplicateOrLinkedCollisionLeavesBothCollectionsUntouched() {
        store.pin(new SavedSitesStore.Site("One", "https://one.example/"));
        store.saveBookmark(-1, "Two", "https://two.example/");
        String pins = preferences.getString("shortcuts", ""), saved = preferences.getString("bookmarks", "");
        try { store.saveBookmark(-1, "Again", "HTTPS://ONE.EXAMPLE"); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("duplicate", expected.getMessage()); }
        try { store.saveShortcut(4, "Collision", "https://two.example/"); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("duplicate", expected.getMessage()); }
        assertEquals(pins, preferences.getString("shortcuts", "")); assertEquals(saved, preferences.getString("bookmarks", ""));
    }
    @Test public void unsafeInputNeverWritesEitherCollection() {
        for (String url : new String[]{"javascript:alert(1)", "file:///tmp/a", "https://user:pass@example.org/"}) {
            try { store.pin(new SavedSitesStore.Site("Unsafe", url)); fail(); }
            catch (IllegalArgumentException expected) { assertEquals("url", expected.getMessage()); }
        }
        assertFalse(preferences.contains("bookmarks")); assertFalse(preferences.contains("shortcuts"));
    }
    @Test public void queryAndPathRemainDistinctWhileRootAddressesNormalize() {
        store.saveBookmark(-1, "Root", "https://EXAMPLE.org");
        store.saveBookmark(-1, "Path", "https://example.org/path");
        store.saveBookmark(-1, "Query", "https://example.org/?q=1");
        assertEquals(3, store.bookmarks().size()); assertTrue(store.isBookmarked("https://example.org/"));
    }
    @Test public void malformedBookmarksRemainUntouchedOnReadOrUnrelatedShortcutEdit() {
        preferences.edit().putString("bookmarks", "damaged").commit();
        assertTrue(store.bookmarks().isEmpty()); store.saveShortcut(0, "Wiki", "https://nl.wikipedia.org/");
        assertEquals("damaged", preferences.getString("bookmarks", ""));
    }
    @Test public void changingAnUnpinnedBookmarkToAPinnedAddressUpdatesTheSharedTitle() {
        store.saveBookmark(-1, "My news", "https://example.org/");
        store.saveBookmark(0, "Daily news", "https://nos.nl/");
        assertEquals("Daily news", store.shortcuts().get(1).title);
        assertEquals("https://nos.nl/", store.bookmarks().get(0).url);
    }
    @Test public void pinningALegacyBookmarkPreservesItsLongTitle() {
        String title="An existing page title longer than the current editor limit of forty characters";
        preferences.edit().putString("bookmarks", "[{\"title\":\""+title+"\",\"url\":\"https://example.org/\"}]").commit();
        store.pin(store.bookmarks().get(0));
        assertEquals(title, store.bookmarks().get(0).title); assertEquals(title, store.shortcuts().get(4).title);
    }

}
