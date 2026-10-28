package nl.veyra.browser;

import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/** Versioned, all-or-nothing local import; no cookies, history, permissions, or credentials. */
final class BackupCodec {
    static final int MAX_BYTES=2*1024*1024, MAX_BOOKMARKS=5000;
    private static final String[] BOOLEANS={"dynamic_colors","compact_home","blocking","https_only"};
    static final class Preview {
        final List<SavedSitesStore.Site> bookmarks,shortcuts;
        final JSONObject settings;
        List<TabStateStore.Entry> tabs = new ArrayList<>();
        Preview(List<SavedSitesStore.Site> bookmarks,List<SavedSitesStore.Site> shortcuts,JSONObject settings) {
            this.bookmarks=bookmarks;this.shortcuts=shortcuts;this.settings=settings;
        }
    }
    private BackupCodec() { }
    static byte[] export(SharedPreferences preferences,boolean settings) { return export(preferences,settings,false); }
    static byte[] export(SharedPreferences preferences,boolean settings,boolean tabs) {
        SavedSitesStore store=new SavedSitesStore(preferences);
        try {
            JSONObject root=new JSONObject().put("format","veyra-backup").put("version",1)
                    .put("bookmarks",SavedSitesStore.encode(store.bookmarks())).put("shortcuts",SavedSitesStore.encode(store.shortcuts()));
            JSONObject values=new JSONObject();
            if(settings) {
                for(String key:BOOLEANS)if(preferences.contains(key))values.put(key,preferences.getBoolean(key,false));
                for(String key:new String[]{"theme","accent","nickname","search","custom_search"})
                    if(preferences.contains(key) && (!key.equals("custom_search") || SearchProvider.valid(preferences.getString(key,""))))values.put(key,preferences.getString(key,""));
                if(preferences.contains("text_zoom"))values.put("text_zoom",preferences.getInt("text_zoom",100));
                if(preferences.contains("resident_limit"))values.put("resident_limit",preferences.getInt("resident_limit",3));
                if(preferences.contains("ad_exceptions"))values.put("ad_exceptions",new JSONArray(preferences.getStringSet("ad_exceptions",java.util.Collections.emptySet())));
            }
            root.put("preferences",values);
            if(tabs)root.put("tabs",new JSONArray(TabStateStore.encode(TabStateStore.read(preferences.getString("tabs","[]")))));
            byte[] bytes=root.toString(2).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Het bestand is groter dan 2 MB.");return bytes;
        } catch(JSONException invalid){throw new IllegalArgumentException("De gegevens konden niet worden geëxporteerd.",invalid);}
    }
    static Preview parse(byte[] bytes) {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Het bestand is groter dan 2 MB.");
        try {
            JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"veyra-backup".equals(root.optString("format")) || root.getInt("version")!=1)
                throw new IllegalArgumentException("Onbekend backupformaat of niet-ondersteunde versie.");
            List<SavedSitesStore.Site> bookmarks=sites(root.getJSONArray("bookmarks"),MAX_BOOKMARKS);
            List<SavedSitesStore.Site> shortcuts=sites(root.getJSONArray("shortcuts"),SavedSitesStore.LIMIT);
            JSONObject settings=root.optJSONObject("preferences"); if(settings==null)settings=new JSONObject();
            validateSettings(settings);
            Preview result=new Preview(bookmarks,shortcuts,settings);
            if(root.has("tabs")) {
                JSONArray tabs=root.getJSONArray("tabs");if(tabs.length()>TabStateStore.LIMIT)throw new IllegalArgumentException("Te veel tabadressen in de backup.");
                for(int i=0;i<tabs.length();i++) {
                    JSONObject tab=tabs.getJSONObject(i);String url=tab.getString("url");boolean home=tab.optBoolean("home",false);
                    if(tab.optBoolean("private",false) || (!home && UrlPolicy.normalizeWebUrl(url)==null))throw new IllegalArgumentException("Ongeldige of privé-tab in de backup.");
                }
                result.tabs=TabStateStore.read(tabs.toString());
            }
            return result;
        } catch(JSONException invalid){throw new IllegalArgumentException("Het backupbestand is ongeldig. Er is niets gewijzigd.",invalid);}
    }
    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1) {
            if(result.size()+count>MAX_BYTES)throw new IOException("Het bestand is groter dan 2 MB.");
            result.write(buffer,0,count);
        }
        return result.toByteArray();
    }
    static void merge(SharedPreferences preferences,Preview preview,boolean settings) { merge(preferences,preview,settings,false); }
    static void merge(SharedPreferences preferences,Preview preview,boolean settings,boolean restoreTabs) {
        SavedSitesStore store=new SavedSitesStore(preferences);
        List<SavedSitesStore.Site> bookmarks=new ArrayList<>(store.bookmarks()),pins=new ArrayList<>(store.shortcuts());
        for(SavedSitesStore.Site site:preview.bookmarks)if(SavedSitesStore.indexOf(bookmarks,site.url)<0)bookmarks.add(site);
        for(SavedSitesStore.Site site:preview.shortcuts)if(SavedSitesStore.indexOf(pins,site.url)<0)pins.add(site);
        if(bookmarks.size()>MAX_BOOKMARKS || pins.size()>SavedSitesStore.LIMIT)
            throw new IllegalArgumentException("De samengevoegde backup heeft te veel websites. Maak eerst ruimte op de startpagina.");
        // The existing bookmark is authoritative for linked names, including legacy titles.
        for(int i=0;i<pins.size();i++) { int b=SavedSitesStore.indexOf(bookmarks,pins.get(i).url);if(b>=0)pins.set(i,bookmarks.get(b)); }
        if(settings)validateSettings(preview.settings);
        SharedPreferences.Editor editor=preferences.edit().putString("bookmarks",SavedSitesStore.encode(bookmarks).toString())
                .putString("shortcuts",SavedSitesStore.encode(pins).toString());
        if(settings)for(java.util.Iterator<String> keys=preview.settings.keys();keys.hasNext();) {
            String key=keys.next();Object value=preview.settings.opt(key);
            if(value instanceof JSONArray) { java.util.Set<String> hosts=new java.util.HashSet<>();JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++)hosts.add(list.optString(i));editor.putStringSet(key,hosts); }
            else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Number)editor.putInt(key,((Number)value).intValue());
            else editor.putString(key,(String)value);
        }
        if(restoreTabs && !preview.tabs.isEmpty()) {
            List<TabStateStore.Entry> pending=new ArrayList<>(TabStateStore.read(preferences.getString("tabs_import","[]")));
            if(pending.size()+preview.tabs.size()>TabStateStore.LIMIT)throw new IllegalArgumentException("Te veel tabadressen in de backup.");
            pending.addAll(preview.tabs);editor.putString("tabs_import",TabStateStore.encode(pending));
        }
        editor.apply();
    }
    static void replaceSites(SharedPreferences preferences,Preview preview) {
        // Revalidate even in-memory previews before replacing user data.
        Preview valid=parse(SyncMerger.encode(preview.bookmarks,preview.shortcuts));
        preferences.edit().putString("bookmarks",SavedSitesStore.encode(valid.bookmarks).toString())
                .putString("shortcuts",SavedSitesStore.encode(valid.shortcuts).toString()).apply();
    }
    static byte[] mergeBytes(byte[] local,Preview remote) {
        Preview current=parse(local);if(remote==null)return local;
        List<SavedSitesStore.Site> bookmarks=new ArrayList<>(current.bookmarks),pins=new ArrayList<>(current.shortcuts);
        for(SavedSitesStore.Site site:remote.bookmarks)if(SavedSitesStore.indexOf(bookmarks,site.url)<0)bookmarks.add(site);
        for(SavedSitesStore.Site site:remote.shortcuts)if(SavedSitesStore.indexOf(pins,site.url)<0)pins.add(site);
        if(bookmarks.size()>MAX_BOOKMARKS||pins.size()>SavedSitesStore.LIMIT)throw new IllegalArgumentException("Te veel websites na samenvoegen. Maak eerst ruimte op je startpagina.");
        for(int i=0;i<pins.size();i++){int b=SavedSitesStore.indexOf(bookmarks,pins.get(i).url);if(b>=0)pins.set(i,bookmarks.get(b));}
        try{return new JSONObject().put("format","veyra-backup").put("version",1).put("bookmarks",SavedSitesStore.encode(bookmarks))
            .put("shortcuts",SavedSitesStore.encode(pins)).put("preferences",new JSONObject()).toString().getBytes(StandardCharsets.UTF_8);}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    private static List<SavedSitesStore.Site> sites(JSONArray array,int limit) throws JSONException {
        if(array.length()>limit)throw new IllegalArgumentException("De backup bevat te veel websites.");
        List<SavedSitesStore.Site> result=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.getJSONObject(i);String title=item.getString("title").trim(),url=UrlPolicy.normalizeWebUrl(item.getString("url"));
            if(title.isEmpty() || title.length()>512 || url==null)throw new IllegalArgumentException("Ongeldige website in de backup. Er is niets gewijzigd.");
            if(SavedSitesStore.indexOf(result,url)<0)result.add(new SavedSitesStore.Site(title,url));
        }
        return result;
    }
    private static void validateSettings(JSONObject values) {
        for(java.util.Iterator<String> keys=values.keys();keys.hasNext();) {
            String key=keys.next();Object value=values.opt(key);boolean valid=false;
            for(String booleanKey:BOOLEANS)if(key.equals(booleanKey))valid=value instanceof Boolean;
            if(key.equals("theme"))valid=value instanceof String && java.util.Arrays.asList("light","dark","system").contains(value);
            if(key.equals("accent"))valid=value instanceof String && java.util.Arrays.asList("mint","blue","violet","peach").contains(value);
            if(key.equals("search"))valid=value instanceof String && java.util.Arrays.asList("duckduckgo","google","brave","ecosia","custom").contains(value);
            if(key.equals("custom_search"))valid=value instanceof String && SearchProvider.valid((String)value);
            if(key.equals("nickname"))valid=value instanceof String && ((String)value).length()<=24;
            if(key.equals("resident_limit"))valid=value instanceof Number && ((Number)value).doubleValue()==((Number)value).intValue() && ((Number)value).intValue()>=2 && ((Number)value).intValue()<=4;
            if(key.equals("ad_exceptions") && value instanceof JSONArray) { JSONArray hosts=(JSONArray)value;valid=hosts.length()<=1000;for(int i=0;i<hosts.length();i++)valid&=hosts.opt(i) instanceof String && hosts.optString(i).matches("(?i)^[a-z0-9.-]{1,253}$"); }
            if(key.equals("text_zoom"))valid=value instanceof Number && ((Number)value).doubleValue()==((Number)value).intValue()
                    && ((Number)value).intValue()>=85 && ((Number)value).intValue()<=150 && ((Number)value).intValue()%5==0;
            if(!valid)throw new IllegalArgumentException("Niet-ondersteunde of ongeldige instelling in de backup: "+key);
        }
    }
}
