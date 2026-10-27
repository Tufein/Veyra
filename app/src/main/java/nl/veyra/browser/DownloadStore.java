package nl.veyra.browser;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent transfer status; private response addresses are never written. */
final class DownloadStore {
    static final String RUNNING="running",COMPLETE="complete",FAILED="failed",CANCELLED="cancelled";
    static final class Entry {
        final String id,name,url,uri,mime,status,error;final long bytes,total,time;
        Entry(String id,String name,String url,String uri,String mime,String status,String error,long bytes,long total,long time){
            this.id=id;this.name=name;this.url=url;this.uri=uri;this.mime=mime;this.status=status;this.error=error;this.bytes=bytes;this.total=total;this.time=time;
        }
        Entry progress(String state,String error,long bytes){return new Entry(id,name,url,uri,mime,state,error,bytes,total,time);}
    }
    private final SharedPreferences preferences;
    DownloadStore(SharedPreferences preferences){this.preferences=preferences;}
    List<Entry> read(){synchronized(DownloadStore.class){
        List<Entry> entries=new ArrayList<>();
        try {JSONArray array=new JSONArray(preferences.getString("downloads","[]"));for(int i=0;i<array.length();i++){
            JSONObject v=array.getJSONObject(i);entries.add(new Entry(v.getString("id"),v.getString("name"),v.optString("url"),v.getString("uri"),
                    v.optString("mime","application/octet-stream"),v.getString("status"),v.optString("error"),v.optLong("bytes"),v.optLong("total",-1),v.optLong("time")));
        }}catch(org.json.JSONException invalid){/* Preserve malformed data on read. */}return entries;
    }}
    void save(Entry entry){synchronized(DownloadStore.class){List<Entry> entries=read();int at=-1;
        for(int i=0;i<entries.size();i++)if(entries.get(i).id.equals(entry.id)){at=i;break;}
        if(at<0)entries.add(0,entry);else entries.set(at,entry);
        while(entries.size()>100){int remove=-1;for(int i=entries.size()-1;i>=0;i--)if(!RUNNING.equals(entries.get(i).status)){remove=i;break;}if(remove<0)break;entries.remove(remove);}
        write(entries);
    }}
    void remove(String id){synchronized(DownloadStore.class){List<Entry> entries=read();entries.removeIf(e->e.id.equals(id)&&!RUNNING.equals(e.status));write(entries);}}
    void recover(java.util.Set<String> active){synchronized(DownloadStore.class){List<Entry> entries=read();boolean changed=false;
        for(int i=0;i<entries.size();i++){Entry e=entries.get(i);if(RUNNING.equals(e.status)&&!active.contains(e.id)){
            entries.set(i,e.progress(FAILED,"Onderbroken doordat Veyra werd afgesloten. Kies opnieuw aanvragen; controleer het gedeeltelijke bestand.",e.bytes));changed=true;
        }}if(changed)write(entries);
    }}
    private void write(List<Entry> entries){JSONArray array=new JSONArray();try{for(Entry e:entries)array.put(new JSONObject().put("id",e.id).put("name",e.name).put("url",e.url)
            .put("uri",e.uri).put("mime",e.mime).put("status",e.status).put("error",e.error).put("bytes",e.bytes).put("total",e.total).put("time",e.time));}
        catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}preferences.edit().putString("downloads",array.toString()).apply();}
    static String filename(String disposition,String url){
        String candidate=null;
        if(disposition!=null){
            java.util.regex.Matcher utf=java.util.regex.Pattern.compile("(?i)filename\\*=UTF-8''([^;]+)").matcher(disposition);
            if(utf.find())try{candidate=java.net.URLDecoder.decode(utf.group(1).trim().replace("+","%2B"),"UTF-8");}catch(Exception invalid){/* Use plain name. */}
            if(candidate==null){java.util.regex.Matcher plain=java.util.regex.Pattern.compile("(?i)filename=\\s*(?:\"([^\"]*)\"|([^;]*))").matcher(disposition);
                if(plain.find())candidate=plain.group(1)!=null?plain.group(1):plain.group(2).trim();}
        }
        if(candidate==null||candidate.isEmpty())try{String path=java.net.URI.create(url).getPath();candidate=path.substring(path.lastIndexOf('/')+1);}catch(Exception invalid){candidate="download";}
        candidate=candidate.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|\\u202A-\\u202E\\u2066-\\u2069]","_").trim().replaceAll("^\\.+","");
        if(candidate.isEmpty())candidate="download";
        if(candidate.length()>120)candidate=candidate.substring(0,Character.isHighSurrogate(candidate.charAt(119))?119:120);
        return candidate;
    }
}
