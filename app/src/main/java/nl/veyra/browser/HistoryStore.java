package nl.veyra.browser;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local visit history; callers must pass the session's real privacy mode. */
final class HistoryStore {
    static final int LIMIT = 2000;
    static final class Visit {
        final String url, title; final long time;
        Visit(String url, String title, long time) { this.url=url; this.title=title; this.time=time; }
    }
    private final SharedPreferences preferences;
    HistoryStore(SharedPreferences preferences) { this.preferences=preferences; }
    List<Visit> read(String query) {
        List<Visit> result=new ArrayList<>(); String needle=query.trim().toLowerCase(Locale.ROOT);
        try {
            JSONArray saved=new JSONArray(preferences.getString("history", "[]"));
            for(int i=0;i<saved.length() && result.size()<LIMIT;i++) {
                JSONObject item=saved.optJSONObject(i); if(item==null)continue;
                String url=UrlPolicy.normalizeWebUrl(item.optString("url")), title=item.optString("title");
                if(url!=null && (title+" "+url).toLowerCase(Locale.ROOT).contains(needle))
                    result.add(new Visit(url,title.isEmpty()?SettingsViews.host(url):title,item.optLong("time")));
            }
        } catch(org.json.JSONException invalid) { /* Reading does not destroy damaged data. */ }
        return result;
    }
    void record(String url,String title,long time,boolean privateMode) {
        if(privateMode || (url=UrlPolicy.normalizeWebUrl(url))==null)return;
        List<Visit> entries=read(""); final String address=url; entries.removeIf(v->v.url.equals(address));
        String label=title==null||title.trim().isEmpty()?SettingsViews.host(url):title.trim();
        entries.add(0,new Visit(url,label.substring(0,Math.min(label.length(),512)),time));
        if(entries.size()>LIMIT)entries=entries.subList(0,LIMIT); write(entries);
    }
    void remove(String url) { RecentlyClosedTabs.historyDeleted(); List<Visit> entries=read(""); entries.removeIf(v->v.url.equals(url)); write(entries); }
    void clear() { RecentlyClosedTabs.historyDeleted(); preferences.edit().remove("history").apply(); }
    private void write(List<Visit> entries) {
        JSONArray array=new JSONArray();
        try { for(Visit v:entries)array.put(new JSONObject().put("url",v.url).put("title",v.title).put("time",v.time)); }
        catch(org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        preferences.edit().putString("history",array.toString()).apply();
    }
}
