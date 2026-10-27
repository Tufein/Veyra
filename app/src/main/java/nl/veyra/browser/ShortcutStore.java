package nl.veyra.browser;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Shared start-page storage; reading preferences never rewrites existing user data. */
final class ShortcutStore {
    static final int LIMIT = 8;
    private static final String DEFAULTS = "["
            + "{\"title\":\"Wikipedia\",\"url\":\"https://nl.wikipedia.org/\"},"
            + "{\"title\":\"NOS\",\"url\":\"https://nos.nl/\"},"
            + "{\"title\":\"YouTube\",\"url\":\"https://m.youtube.com/\"},"
            + "{\"title\":\"Zoeken\",\"url\":\"https://duckduckgo.com/\"}]";
    static final class Shortcut {
        final String title, url;
        Shortcut(String title, String url) { this.title = title; this.url = url; }
    }
    private final SharedPreferences preferences;
    ShortcutStore(SharedPreferences preferences) { this.preferences = preferences; }
    List<Shortcut> read() {
        List<Shortcut> list = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(preferences.getString("shortcuts", DEFAULTS));
            for (int i = 0; i < saved.length() && list.size() < LIMIT; i++) {
                JSONObject value = saved.optJSONObject(i);
                if (value == null) continue;
                String title = value.optString("title").trim();
                String url = UrlPolicy.normalizeWebUrl(value.optString("url"));
                if (!title.isEmpty() && url != null) list.add(new Shortcut(title, url));
            }
        } catch (JSONException invalid) { /* Leave damaged storage untouched until an explicit edit. */ }
        return list;
    }
    void save(int index, String title, String address) {
        List<Shortcut> list = read();
        title = title.trim();
        String url = UrlPolicy.normalizeWebUrl(address.trim());
        String raw = address.trim();
        boolean hostPort = raw.matches("(?i)^(localhost|[a-z0-9.-]+\\.[a-z0-9-]+):[0-9]+([/?#].*)?$");
        if (url == null && (hostPort || !raw.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")))
            url = UrlPolicy.normalizeWebUrl("https://" + raw);
        if (title.isEmpty() || title.length() > 40) throw new IllegalArgumentException("title");
        if (url == null) throw new IllegalArgumentException("url");
        if (index < -1 || index >= list.size()) throw new IllegalArgumentException("index");
        Shortcut shortcut = new Shortcut(title, url);
        if (index == -1) {
            if (list.size() >= LIMIT) throw new IllegalArgumentException("limit");
            list.add(shortcut);
        } else list.set(index, shortcut);
        write(list);
    }
    void remove(int index) {
        List<Shortcut> list = read();
        if (index < 0 || index >= list.size()) throw new IllegalArgumentException("index");
        list.remove(index); write(list);
    }
    JSONArray toJson() { return encode(read()); }
    private void write(List<Shortcut> list) { preferences.edit().putString("shortcuts", encode(list).toString()).apply(); }
    private static JSONArray encode(List<Shortcut> list) {
        JSONArray result = new JSONArray();
        for (Shortcut shortcut : list) {
            JSONObject value = new JSONObject();
            try { value.put("title", shortcut.title); value.put("url", shortcut.url); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            result.put(value);
        }
        return result;
    }
}
