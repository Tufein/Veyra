package nl.veyra.browser;

import java.util.Arrays;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SyncMergeTest {
    private static SavedSitesStore.Site site(String title){return new SavedSitesStore.Site(title,"https://example.org/");}
    private static byte[] state(String title){return SyncMerger.encode(title==null?Collections.emptyList():Arrays.asList(site(title)),Collections.emptyList());}
    private static String title(byte[] bytes){BackupCodec.Preview parsed=BackupCodec.parse(bytes);return parsed.bookmarks.isEmpty()?null:parsed.bookmarks.get(0).title;}
    @Test public void remoteEditReplacesUnchangedLocalTitle(){SyncMerger.Result result=SyncMerger.merge(state("Old"),state("New"),state("Old"),true);assertEquals("New",title(result.bytes));assertEquals(0,result.conflicts);}
    @Test public void localEditIsSentWhenRemoteDidNotChange(){assertEquals("New",title(SyncMerger.merge(state("New"),state("Old"),state("Old"),true).bytes));}
    @Test public void remoteDeletionRemovesUnchangedLocalBookmark(){SyncMerger.Result result=SyncMerger.merge(state("Old"),state(null),state("Old"),true);assertNull(title(result.bytes));assertEquals(1,result.removedLocal);assertEquals(0,result.conflicts);}
    @Test public void localDeletionIsPropagatedWithoutResurrectingServerBookmark(){assertNull(title(SyncMerger.merge(state(null),state("Old"),state("Old"),true).bytes));}
    @Test public void concurrentEditsRequireExplicitVersionChoice(){
        SyncMerger.Result local=SyncMerger.merge(state("Local"),state("Remote"),state("Old"),true),remote=SyncMerger.merge(state("Local"),state("Remote"),state("Old"),false);
        assertTrue(local.conflicts>0);assertEquals("Local",title(local.bytes));assertEquals("Remote",title(remote.bytes));
    }
    @Test public void deletionVersusEditIsAConflict(){
        SyncMerger.Result local=SyncMerger.merge(state(null),state("Edited"),state("Old"),true),remote=SyncMerger.merge(state(null),state("Edited"),state("Old"),false);
        assertTrue(local.conflicts>0);assertNull(title(local.bytes));assertEquals("Edited",title(remote.bytes));
    }
    @Test public void independentAdditionsAreRetainedOnFirstPairing(){
        byte[] other=SyncMerger.encode(Arrays.asList(new SavedSitesStore.Site("Other","https://other.example/")),Collections.emptyList());
        SyncMerger.Result result=SyncMerger.merge(state("Local"),other,null,true);assertEquals(2,BackupCodec.parse(result.bytes).bookmarks.size());assertEquals(0,result.conflicts);
    }
    @Test public void remoteOrderingIsAppliedWhenLocalOrderIsUnchanged(){
        SavedSitesStore.Site one=site("One"),two=new SavedSitesStore.Site("Two","https://two.example/");
        byte[] base=SyncMerger.encode(Arrays.asList(one,two),Collections.emptyList()),remote=SyncMerger.encode(Arrays.asList(two,one),Collections.emptyList());
        assertEquals("Two",BackupCodec.parse(SyncMerger.merge(base,remote,base,true).bytes).bookmarks.get(0).title);
    }
    @Test public void linkedPinNamesFollowMergedBookmarks(){
        byte[] base=SyncMerger.encode(Arrays.asList(site("Old")),Arrays.asList(site("Old")));
        byte[] remote=SyncMerger.encode(Arrays.asList(site("New")),Arrays.asList(site("New")));
        BackupCodec.Preview merged=BackupCodec.parse(SyncMerger.merge(base,remote,base,true).bytes);assertEquals("New",merged.bookmarks.get(0).title);assertEquals("New",merged.shortcuts.get(0).title);
    }
    @Test public void syncPayloadNeverCarriesImportedTabsOrPreferences()throws Exception{
        JSONObject payload=new JSONObject(new String(state("Saved"),StandardCharsets.UTF_8));payload.put("preferences",new JSONObject().put("theme","light"));
        payload.put("tabs",new JSONArray().put(new JSONObject().put("url","https://tab.example/").put("home",false)));
        byte[] bytes=SyncMerger.sitesOnly(payload.toString().getBytes(StandardCharsets.UTF_8));
        assertFalse(new String(bytes,StandardCharsets.UTF_8).contains("tab.example"));assertEquals(0,BackupCodec.parse(bytes).settings.length());
    }
}
