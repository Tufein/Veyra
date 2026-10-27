package nl.veyra.browser;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/** Compatibility facade: home and settings use the same linked saved-site storage. */
final class ShortcutStore {
    static final int LIMIT = SavedSitesStore.LIMIT;
    static final class Shortcut {
        final String title, url;
        Shortcut(String title, String url) { this.title = title; this.url = url; }
    }
    private final SavedSitesStore sites;
    ShortcutStore(SharedPreferences preferences) { sites = new SavedSitesStore(preferences); }
    List<Shortcut> read() {
        List<Shortcut> result = new ArrayList<>();
        for (SavedSitesStore.Site site : sites.shortcuts()) result.add(new Shortcut(site.title, site.url));
        return result;
    }
    void save(int index, String title, String address) { sites.saveShortcut(index, title, address); }
    void remove(int index) {
        List<Shortcut> values = read();
        if (index < 0 || index >= values.size()) throw new IllegalArgumentException("index");
        sites.unpin(values.get(index).url);
    }
    JSONArray toJson() { return SavedSitesStore.encode(sites.shortcuts()); }
}
