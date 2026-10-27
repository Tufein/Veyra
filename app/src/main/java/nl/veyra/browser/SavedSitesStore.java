package nl.veyra.browser;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Legacy JSON storage with URL-based links and atomic bookmark/start-page edits. */
final class SavedSitesStore {
    static final int LIMIT = 8;
    static final String DEFAULTS = "[{\"title\":\"Wikipedia\",\"url\":\"https://nl.wikipedia.org/\"},"
            + "{\"title\":\"NOS\",\"url\":\"https://nos.nl/\"},{\"title\":\"YouTube\",\"url\":\"https://m.youtube.com/\"},"
            + "{\"title\":\"Zoeken\",\"url\":\"https://duckduckgo.com/\"}]";
    static final class Site {
        final String title, url;
        Site(String title, String url) { this.title = title; this.url = url; }
    }
    private final SharedPreferences preferences;
    SavedSitesStore(SharedPreferences preferences) { this.preferences = preferences; }
    List<Site> bookmarks() { return read("bookmarks", "[]"); }
    List<Site> shortcuts() { return read("shortcuts", DEFAULTS); }
    private List<Site> read(String key, String fallback) {
        List<Site> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key, fallback));
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.optJSONObject(i); if (value == null) continue;
                String title = value.optString("title").trim(), url = UrlPolicy.normalizeWebUrl(value.optString("url"));
                if (!title.isEmpty() && url != null && indexOf(result, url) < 0
                        && (!key.equals("shortcuts") || result.size() < LIMIT)) result.add(new Site(title, url));
            }
        } catch (JSONException invalid) { /* A read must never overwrite damaged legacy data. */ }
        return result;
    }
    boolean isPinned(String url) { return indexOf(shortcuts(), url) >= 0; }
    boolean isBookmarked(String url) { return indexOf(bookmarks(), url) >= 0; }
    void saveBookmark(int index, String title, String url) { save(false, index, title, url); }
    void saveShortcut(int index, String title, String url) { save(true, index, title, url); }
    private void save(boolean shortcut, int index, String title, String raw) {
        Site value = validate(title, raw);
        List<Site> entries = shortcut ? shortcuts() : bookmarks();
        if (index < -1 || index >= entries.size()) throw new IllegalArgumentException("index");
        int duplicate = indexOf(entries, value.url);
        if (duplicate >= 0 && duplicate != index) throw new IllegalArgumentException("duplicate");
        if (index == -1 && shortcut && entries.size() >= LIMIT) throw new IllegalArgumentException("limit");
        String oldUrl = index < 0 ? value.url : entries.get(index).url;
        List<Site> linked = shortcut ? bookmarks() : shortcuts();
        int linkedIndex = indexOf(linked, oldUrl);
        int linkedCollision = indexOf(linked, value.url);
        if (linkedIndex >= 0 && linkedCollision >= 0 && linkedCollision != linkedIndex)
            throw new IllegalArgumentException("duplicate");
        if (linkedIndex < 0) linkedIndex = linkedCollision;
        if (index == -1) entries.add(value); else entries.set(index, value);
        SharedPreferences.Editor editor = preferences.edit().putString(shortcut ? "shortcuts" : "bookmarks", encode(entries).toString());
        if (linkedIndex >= 0) {
            linked.set(linkedIndex, value);
            editor.putString(shortcut ? "bookmarks" : "shortcuts", encode(linked).toString());
        }
        editor.apply();
    }
    void pin(Site site) {
        List<Site> pins = shortcuts(), saved = bookmarks();
        int bookmark = indexOf(saved, site.url);
        Site value = bookmark >= 0 ? saved.get(bookmark) : validate(site.title, site.url);
        int pin = indexOf(pins, value.url);
        if (pin < 0 && pins.size() >= LIMIT) throw new IllegalArgumentException("limit");
        if (bookmark >= 0) value = saved.get(bookmark); else saved.add(value);
        if (pin >= 0) pins.set(pin, value); else pins.add(value);
        preferences.edit().putString("shortcuts", encode(pins).toString()).putString("bookmarks", encode(saved).toString()).apply();
    }
    void unpin(String url) {
        List<Site> pins = shortcuts(); int i = indexOf(pins, url);
        if (i >= 0) { pins.remove(i); preferences.edit().putString("shortcuts", encode(pins).toString()).apply(); }
    }
    void removeBookmark(String url) {
        List<Site> saved = bookmarks(); int i = indexOf(saved, url);
        if (i < 0) return;
        saved.remove(i); List<Site> pins = shortcuts(); int pin = indexOf(pins, url);
        SharedPreferences.Editor editor = preferences.edit().putString("bookmarks", encode(saved).toString());
        if (pin >= 0) { pins.remove(pin); editor.putString("shortcuts", encode(pins).toString()); }
        editor.apply();
    }
    static int indexOf(List<Site> values, String url) {
        String normalized = UrlPolicy.normalizeWebUrl(url);
        for (int i = 0; i < values.size(); i++) if (values.get(i).url.equals(normalized)) return i;
        return -1;
    }
    static Site validate(String title, String address) {
        title = title.trim(); String raw = address.trim();
        if (title.isEmpty() || title.length() > 40) throw new IllegalArgumentException("title");
        String url = UrlPolicy.normalizeWebUrl(raw);
        boolean hostPort = raw.matches("(?i)^(localhost|[a-z0-9.-]+\\.[a-z0-9-]+):[0-9]+([/?#].*)?$");
        if (url == null && (hostPort || !raw.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) url = UrlPolicy.normalizeWebUrl("https://" + raw);
        if (url == null) throw new IllegalArgumentException("url");
        return new Site(title, url);
    }
    static JSONArray encode(List<Site> entries) {
        JSONArray array = new JSONArray();
        for (Site site : entries) {
            JSONObject value = new JSONObject();
            try { value.put("title", site.title); value.put("url", site.url); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            array.put(value);
        }
        return array;
    }
}
