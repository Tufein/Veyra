package nl.veyra.browser;

import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public final class RoadmapStorageTest {
    private SharedPreferences preferences;
    @Before public void setup(){preferences=RuntimeEnvironment.getApplication().getSharedPreferences("roadmap-test",0);preferences.edit().clear().commit();}
    @Test public void privateVisitsNeverReachHistoryOrSelectedTabSnapshot(){
        HistoryStore history=new HistoryStore(preferences);history.record("https://private.example/secret","Secret",1,true);assertTrue(preferences.getAll().isEmpty());
        java.util.List<TabStateStore.Entry> tabs=Arrays.asList(new TabStateStore.Entry("https://one.example/","One",false,false),
                new TabStateStore.Entry("https://private.example/secret","Secret",false,true),new TabStateStore.Entry("https://two.example/","Two",false,false));
        String saved=TabStateStore.encode(tabs);assertFalse(saved.contains("private.example"));assertFalse(saved.contains("Secret"));
        assertEquals(0,TabStateStore.selected(tabs,1));assertEquals(1,TabStateStore.selected(tabs,2));assertEquals(2,TabStateStore.read(saved).size());
    }
    @Test public void historySearchDeleteAndRecencyAreLocalAndBounded(){
        HistoryStore history=new HistoryStore(preferences);history.record("https://example.org/","Example",10,false);history.record("https://other.example/","Other",20,false);
        history.record("https://EXAMPLE.org","Updated",30,false);assertEquals(2,history.read("").size());assertEquals("Updated",history.read("").get(0).title);
        assertEquals(1,history.read("other").size());history.remove("https://example.org/");assertEquals(1,history.read("").size());history.clear();assertTrue(history.read("").isEmpty());
    }
    @Test public void damagedHistoryIsNotRewrittenOnRead(){preferences.edit().putString("history","damaged").commit();assertTrue(new HistoryStore(preferences).read("").isEmpty());assertEquals("damaged",preferences.getString("history",""));}
    @Test public void backupRoundTripPreservesLegacyTitlesAndOnlyWhitelistedSettings(){
        String title="A legacy bookmark title longer than forty characters, retained intact";
        preferences.edit().putString("bookmarks","[{\"title\":\""+title+"\",\"url\":\"https://example.org/\"}]")
                .putString("theme","light").putInt("text_zoom",115).putString("tabs","private should not export").putString("password","never export").commit();
        byte[] data=BackupCodec.export(preferences,true);String serialized=new String(data,StandardCharsets.UTF_8);
        assertFalse(serialized.contains("never export"));assertFalse(serialized.contains("private should"));
        BackupCodec.Preview preview=BackupCodec.parse(data);assertEquals(title,preview.bookmarks.get(0).title);
        SharedPreferences destination=RuntimeEnvironment.getApplication().getSharedPreferences("import-target",0);destination.edit().clear().commit();
        BackupCodec.merge(destination,preview,true);assertEquals(title,new SavedSitesStore(destination).bookmarks().get(0).title);assertEquals(115,destination.getInt("text_zoom",0));
    }
    @Test public void malformedOrFutureBackupNeverTouchesExistingData()throws Exception{
        preferences.edit().putString("bookmarks","keep").commit();
        for(String invalid:new String[]{"not json","{\"format\":\"veyra-backup\",\"version\":99}","{\"format\":\"veyra-backup\",\"version\":1,\"bookmarks\":[{\"title\":\"Bad\",\"url\":\"javascript:alert(1)\"}],\"shortcuts\":[]}"}){
            try{BackupCodec.merge(preferences,BackupCodec.parse(invalid.getBytes(StandardCharsets.UTF_8)),true);fail();}catch(IllegalArgumentException expected){}
            assertEquals("keep",preferences.getString("bookmarks",""));
        }
    }
    @Test public void backupCapacityFailureDoesNotPartiallyWrite(){
        SavedSitesStore sites=new SavedSitesStore(preferences);for(int i=0;i<4;i++)sites.pin(new SavedSitesStore.Site("Extra"+i,"https://example.org/"+i));
        String before=preferences.getAll().toString();
        BackupCodec.Preview preview=new BackupCodec.Preview(Arrays.asList(new SavedSitesStore.Site("New","https://new.example/")),
                Arrays.asList(new SavedSitesStore.Site("New","https://new.example/")),new JSONObject());
        try{BackupCodec.merge(preferences,preview,false);fail();}catch(IllegalArgumentException expected){}
        assertEquals(before,preferences.getAll().toString());
    }
    @Test public void importingSettingsIsExplicitAndCannotImportPermissionGrants()throws Exception{
        JSONObject root=new JSONObject(new String(BackupCodec.export(preferences,false),StandardCharsets.UTF_8));root.put("preferences",new JSONObject().put("theme","light"));
        preferences.edit().putString("theme","dark").commit();BackupCodec.merge(preferences,BackupCodec.parse(root.toString().getBytes(StandardCharsets.UTF_8)),false);assertEquals("dark",preferences.getString("theme",""));
        root.put("preferences",new JSONObject().put("camera",true));try{BackupCodec.parse(root.toString().getBytes(StandardCharsets.UTF_8));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void syncMergeKeepsLocalNamesAndDoesNotWritePreferences(){
        SavedSitesStore sites=new SavedSitesStore(preferences);sites.saveBookmark(-1,"Local","https://example.org/");String before=preferences.getAll().toString();
        BackupCodec.Preview remote=new BackupCodec.Preview(Arrays.asList(new SavedSitesStore.Site("Remote","https://example.org/"),new SavedSitesStore.Site("New","https://new.example/")),java.util.Collections.emptyList(),new JSONObject());
        BackupCodec.Preview merged=BackupCodec.parse(BackupCodec.mergeBytes(BackupCodec.export(preferences,false),remote));
        assertEquals(2,merged.bookmarks.size());assertEquals("Local",merged.bookmarks.get(0).title);assertEquals(before,preferences.getAll().toString());
    }
    @Test public void customSearchRejectsUnsafeOrMisplacedPlaceholders(){
        assertTrue(SearchProvider.valid("https://search.example/find?q=%s&lang=nl"));
        for(String invalid:new String[]{"http://example.org/?q=%s","javascript:%s","https://%s.example/?q=x","https://example.org/?q=%s&other=%s","https://example.org/?q=veyra#%s","https://user:pass@example.org/?q=%s"})assertFalse(invalid,SearchProvider.valid(invalid));
        preferences.edit().putString("search","custom").putString("custom_search","https://search.example/?q=%s&lang=nl").commit();
        assertEquals("https://search.example/?q=hello+world&lang=nl",SearchProvider.resolve("hello world",preferences));
        assertEquals("https://duckduckgo.com/?q=real",SearchProvider.resolve("duckduckgo.com/?q=real",preferences));
    }
    @Test public void sitePermissionsAreOriginScopedAndNeverRememberPrivateChoices(){
        SitePermissions sites=new SitePermissions(preferences);sites.set("https://EXAMPLE.org/path",SitePermissions.CAMERA,SitePermissions.ALLOW,false);
        assertEquals(SitePermissions.ALLOW,sites.get("https://example.org:443/else",SitePermissions.CAMERA,false));
        assertEquals(SitePermissions.ASK,sites.get("https://sub.example.org/",SitePermissions.CAMERA,false));
        assertEquals(SitePermissions.BLOCK,sites.get("http://example.org/",SitePermissions.CAMERA,false));
        String before=preferences.getAll().toString();sites.set("https://secret.example/",SitePermissions.CAMERA,SitePermissions.ALLOW,true);
        assertEquals(before,preferences.getAll().toString());assertEquals(SitePermissions.ASK,sites.get("https://example.org/",SitePermissions.CAMERA,true));
        sites.reset("https://example.org/");assertEquals(SitePermissions.ASK,sites.get("https://example.org/",SitePermissions.CAMERA,false));
    }
    @Test public void downloadNamesCannotTraversePathsOrSpoofDirection(){
        assertEquals("report.pdf",DownloadStore.filename("attachment; filename=\"report.pdf\"","https://example.org/"));
        String unsafe=DownloadStore.filename("attachment; filename=\"../../secret\u202E.txt\"","https://example.org/");assertFalse(unsafe.contains("/"));assertFalse(unsafe.startsWith("."));assertFalse(unsafe.contains("\u202E"));
        assertEquals("hello world.pdf",DownloadStore.filename("attachment; filename*=UTF-8''hello%20world.pdf","https://example.org/"));
    }
    @Test public void interruptedDownloadsBecomeRetryableAndActiveOnesStayRunning(){
        DownloadStore store=new DownloadStore(preferences);store.save(new DownloadStore.Entry("one","file","https://example.org/","content://test/file","application/pdf",DownloadStore.RUNNING,"",10,100,1));
        store.recover(java.util.Collections.singleton("one"));assertEquals(DownloadStore.RUNNING,store.read().get(0).status);
        store.recover(java.util.Collections.emptySet());assertEquals(DownloadStore.FAILED,store.read().get(0).status);assertEquals(10,store.read().get(0).bytes);
    }
    @Test public void bookmarkOrderingLeavesLinkedContentIntact(){
        SavedSitesStore store=new SavedSitesStore(preferences);store.pin(new SavedSitesStore.Site("One","https://one.example/"));store.pin(new SavedSitesStore.Site("Two","https://two.example/"));
        store.move(false,"https://two.example/",-1);assertEquals("Two",store.bookmarks().get(0).title);assertEquals("One",store.shortcuts().get(4).title);
        store.move(true,"https://two.example/",-1);assertEquals("Two",store.shortcuts().get(4).title);
    }
    @Test public void optionalTabBackupRejectsPrivateTabsAndQueuesNormalAddresses()throws Exception {
        preferences.edit().putString("tabs",TabStateStore.encode(Arrays.asList(new TabStateStore.Entry("https://normal.example/","Normal",false,false),
                new TabStateStore.Entry("https://private.example/","Private",false,true)))).commit();
        BackupCodec.Preview preview=BackupCodec.parse(BackupCodec.export(preferences,false,true));
        assertEquals(1,preview.tabs.size());assertEquals("https://normal.example/",preview.tabs.get(0).url);
        BackupCodec.merge(preferences,preview,false);assertTrue(preferences.getString("tabs_import","").contains("normal.example"));
        assertFalse(preferences.getString("tabs_import","").contains("private.example"));
        JSONObject unsafe=new JSONObject(new String(BackupCodec.export(preferences,false),StandardCharsets.UTF_8));
        unsafe.put("tabs",new JSONArray().put(new JSONObject().put("url","https://private.example/").put("private",true)));
        try{BackupCodec.parse(unsafe.toString().getBytes(StandardCharsets.UTF_8));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void explicitPreferencesBackupKeepsBlockingExceptionsWithoutGrantingSitePermissions(){
        preferences.edit().putStringSet("ad_exceptions",new java.util.HashSet<>(Arrays.asList("example.org"))).putInt("resident_limit",2).commit();
        BackupCodec.Preview preview=BackupCodec.parse(BackupCodec.export(preferences,true));
        SharedPreferences destination=RuntimeEnvironment.getApplication().getSharedPreferences("exceptions-import",0);destination.edit().clear().commit();
        BackupCodec.merge(destination,preview,true);assertTrue(destination.getStringSet("ad_exceptions",java.util.Collections.emptySet()).contains("example.org"));
        assertEquals(2,destination.getInt("resident_limit",0));assertFalse(destination.contains("camera"));
    }

}
