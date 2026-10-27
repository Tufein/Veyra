package nl.veyra.browser;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Disk representation intentionally excludes private tabs and web form state. */
final class TabStateStore {
    static final int LIMIT=12;
    static final class Entry {
        final String url,title;final boolean home,privateMode;
        Entry(String url,String title,boolean home,boolean privateMode){this.url=url;this.title=title;this.home=home;this.privateMode=privateMode;}
    }
    static List<Entry> read(String raw) {
        List<Entry> result=new ArrayList<>();
        try {
            JSONArray saved=new JSONArray(raw);
            for(int i=0;i<saved.length()&&result.size()<LIMIT;i++) {
                JSONObject item=saved.optJSONObject(i);if(item==null||item.optBoolean("private",false))continue;
                String url=UrlPolicy.normalizeWebUrl(item.optString("url"));boolean home=item.optBoolean("home",true)||url==null;
                result.add(new Entry(url==null?"":url,item.optString("title","Tabblad"),home,false));
            }
        }catch(org.json.JSONException invalid){/* Start with an empty home, without rewriting damaged storage. */}
        return result;
    }
    static String encode(List<Entry> entries) {
        JSONArray array=new JSONArray();
        try { for(Entry entry:entries)if(!entry.privateMode)array.put(new JSONObject().put("url",entry.url)
                .put("title",entry.title).put("home",entry.home)); }
        catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
        return array.toString();
    }
    static int selected(List<Entry> entries,int selected) {
        int visible=0,last=0;
        for(int i=0;i<entries.size();i++)if(!entries.get(i).privateMode){if(i<=selected)last=visible;visible++;}
        return Math.max(0,Math.min(last,visible-1));
    }
}
