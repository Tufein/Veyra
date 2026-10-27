package nl.veyra.browser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.json.JSONObject;

/** Three-way merge relative to the last successful sync; deletions never rely on timestamps. */
final class SyncMerger {
    static final class Result {
        final byte[] bytes; final int conflicts, removedLocal;
        Result(byte[] bytes,int conflicts,int removedLocal){this.bytes=bytes;this.conflicts=conflicts;this.removedLocal=removedLocal;}
    }
    private static final class Collection {
        final List<SavedSitesStore.Site> entries;final int conflicts;
        Collection(List<SavedSitesStore.Site> entries,int conflicts){this.entries=entries;this.conflicts=conflicts;}
    }
    private SyncMerger() { }
    static Result merge(byte[] local,byte[] remote,byte[] baseline,boolean preferLocal) {
        BackupCodec.Preview left=BackupCodec.parse(local);
        if(remote==null)return new Result(local,0,0);
        BackupCodec.Preview right=BackupCodec.parse(remote),base=baseline==null?null:BackupCodec.parse(baseline);
        Collection bookmarks=mergeCollection(left.bookmarks,right.bookmarks,base==null?null:base.bookmarks,preferLocal);
        Collection pins=mergeCollection(left.shortcuts,right.shortcuts,base==null?null:base.shortcuts,preferLocal);
        if(bookmarks.entries.size()>BackupCodec.MAX_BOOKMARKS||pins.entries.size()>SavedSitesStore.LIMIT)
            throw new IllegalArgumentException("Te veel websites na samenvoegen. Maak eerst ruimte op je startpagina.");
        Map<String,SavedSitesStore.Site> names=map(bookmarks.entries);
        for(int i=0;i<pins.entries.size();i++){SavedSitesStore.Site linked=names.get(pins.entries.get(i).url);if(linked!=null)pins.entries.set(i,linked);}
        int removed=0;for(SavedSitesStore.Site site:left.bookmarks)if(!names.containsKey(site.url))removed++;
        Map<String,SavedSitesStore.Site> pinned=map(pins.entries);for(SavedSitesStore.Site site:left.shortcuts)if(!pinned.containsKey(site.url))removed++;
        return new Result(encode(bookmarks.entries,pins.entries),bookmarks.conflicts+pins.conflicts,removed);
    }
    static byte[] sitesOnly(byte[] bytes) {BackupCodec.Preview parsed=BackupCodec.parse(bytes);return encode(parsed.bookmarks,parsed.shortcuts);}
    private static Collection mergeCollection(List<SavedSitesStore.Site> left,List<SavedSitesStore.Site> right,List<SavedSitesStore.Site> baseline,boolean preferLocal){
        Map<String,SavedSitesStore.Site> local=map(left),remote=map(right),base=baseline==null?new LinkedHashMap<>():map(baseline);
        Set<String> urls=new LinkedHashSet<>(local.keySet());urls.addAll(remote.keySet());urls.addAll(base.keySet());
        Map<String,SavedSitesStore.Site> selected=new LinkedHashMap<>();int conflicts=0;
        for(String url:urls){SavedSitesStore.Site l=local.get(url),r=remote.get(url),b=base.get(url),choice;
            if(same(l,r))choice=l;
            else if(baseline==null){if(l==null)choice=r;else if(r==null)choice=l;else{conflicts++;choice=preferLocal?l:r;}}
            else if(same(l,b))choice=r;
            else if(same(r,b))choice=l;
            else{conflicts++;choice=preferLocal?l:r;}
            if(choice!=null)selected.put(url,choice);
        }
        List<String> localOrder=new ArrayList<>(local.keySet()),remoteOrder=new ArrayList<>(remote.keySet()),baseOrder=new ArrayList<>(base.keySet());
        boolean localChanged=baseline!=null&&!localOrder.equals(baseOrder),remoteChanged=baseline!=null&&!remoteOrder.equals(baseOrder);
        List<String> preferred=localOrder;
        if(remoteChanged&&!localChanged)preferred=remoteOrder;
        else if(localChanged&&remoteChanged&&!localOrder.equals(remoteOrder)){conflicts++;preferred=preferLocal?localOrder:remoteOrder;}
        List<SavedSitesStore.Site> output=new ArrayList<>();for(String url:preferred){SavedSitesStore.Site site=selected.remove(url);if(site!=null)output.add(site);}
        output.addAll(selected.values());return new Collection(output,conflicts);
    }
    private static Map<String,SavedSitesStore.Site> map(List<SavedSitesStore.Site> entries){
        Map<String,SavedSitesStore.Site> result=new LinkedHashMap<>();for(SavedSitesStore.Site entry:entries)result.put(entry.url,entry);return result;
    }
    private static boolean same(SavedSitesStore.Site a,SavedSitesStore.Site b){return a==b || (a!=null&&b!=null&&Objects.equals(a.title,b.title)&&Objects.equals(a.url,b.url));}
    static byte[] encode(List<SavedSitesStore.Site> bookmarks,List<SavedSitesStore.Site> pins){
        try{return new JSONObject().put("format","veyra-backup").put("version",1).put("bookmarks",SavedSitesStore.encode(bookmarks))
                .put("shortcuts",SavedSitesStore.encode(pins)).put("preferences",new JSONObject()).toString().getBytes(StandardCharsets.UTF_8);}
        catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
    }
}
