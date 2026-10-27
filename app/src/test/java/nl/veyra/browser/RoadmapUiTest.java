package nl.veyra.browser;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="en-rUS-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class RoadmapUiTest {
    private ActivityController<? extends android.app.Activity> controller;
    @Before public void setup(){
        for(String file:new String[]{"veyra","veyra_history","veyra_downloads","veyra_sync_secret"})RuntimeEnvironment.getApplication().getSharedPreferences(file,0).edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().putString("theme","dark").putBoolean("dynamic_colors",false).commit();
    }
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    private <T extends android.app.Activity>T launch(Class<T> type){ActivityController<T> created=Robolectric.buildActivity(type).setup().visible();controller=created;idle();return created.get();}
    private static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));}
    private View root(){return controller.get().getWindow().getDecorView();}
    @Test public void historyHasEnglishLabelsAndPreservesWebsiteTitles()throws Exception{
        HistoryStore history=new HistoryStore(RuntimeEnvironment.getApplication().getSharedPreferences("veyra_history",0));history.record("https://example.org/","Geschiedenis",1700000000000L,false);
        launch(HistoryActivity.class);assertNotNull(LibraryActivityTest.find(root(),"History"));assertNotNull(LibraryActivityTest.find(root(),"Geschiedenis"));
        LibraryActivityTest.capture(root(),"history-english-dark");
    }
    @Test public void backupOpensTheSystemDocumentCreatorInsteadOfRequestingBroadStorageAccess(){
        launch(BackupActivity.class);View button=LibraryActivityTest.find(root(),"Export backup");assertNotNull(button);button.performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(controller.get()).getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,request.intent.getAction());assertEquals("application/json",request.intent.getType());
    }
    @Test public void syncFormShowsRecoveryRulesAndRejectsShortPhraseBeforeNetwork()throws Exception{
        launch(SyncActivity.class);assertNotNull(LibraryActivityTest.find(root(),"Encrypted sync"));
        View button=LibraryActivityTest.find(root(),"Merge and sync");assertNotNull(button);button.performClick();
        assertNotNull(LibraryActivityTest.find(root(),"Not synced yet."));assertTrue(RuntimeEnvironment.getApplication().getSharedPreferences("veyra_sync_secret",0).getAll().isEmpty());
        LibraryActivityTest.capture(root(),"sync-english-dark");
    }
    @Test public void privacyPageHasSeparateChoicesAndNoDestructiveDefault()throws Exception{
        launch(DataSettingsActivity.class);assertNotNull(LibraryActivityTest.find(root(),"Privacy and data"));
        View button=LibraryActivityTest.find(root(),"Clear selected data");button.performClick();
        assertEquals("Choose what to clear first.",org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        LibraryActivityTest.capture(root(),"privacy-english-dark");
    }
    @Test @Config(qualifiers="nl-rNL-w1000dp-h900dp-mdpi") public void tabletSettingsUsesTwoPaneNavigation()throws Exception{
        controller=Robolectric.buildActivity(SettingsActivity.class,SettingsActivity.intent(RuntimeEnvironment.getApplication(),SettingsSection.APPEARANCE)).setup().visible();idle();
        assertNotNull(LibraryActivityTest.find(root(),"Browser en gegevens"));assertNotNull(LibraryActivityTest.find(root(),"Snelkoppelingen"));
        LibraryActivityTest.capture(root(),"settings-tablet-dark");
    }
    @Test @Config(qualifiers="en-rUS-ldrtl-w320dp-h900dp-mdpi") public void largeTextAndRtlHistoryRemainScrollable()throws Exception{
        android.content.res.Configuration config=new android.content.res.Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());config.fontScale=2f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        launch(HistoryActivity.class);LibraryActivityTest.capture(root(),"history-rtl-large-font");assertNotNull(LibraryActivityTest.find(root(),"History"));
    }
    @Test public void returningFromImportRefreshesTheLibrary(){
        launch(LibraryActivity.class);controller.pause();
        new SavedSitesStore(RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0)).saveBookmark(-1,"Imported website","https://example.org/imported");
        controller.resume();idle();assertNotNull(LibraryActivityTest.find(root(),"Imported website"));
    }
    @Test public void largeLibraryCreatesOnlyOnePageUntilRequested(){
        org.json.JSONArray entries=new org.json.JSONArray();
        try{for(int i=0;i<125;i++)entries.put(new org.json.JSONObject().put("title","Saved "+i).put("url","https://example.org/"+i));}catch(org.json.JSONException invalid){throw new AssertionError(invalid);}
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().putString("bookmarks",entries.toString()).commit();
        launch(LibraryActivity.class);assertNotNull(LibraryActivityTest.find(root(),"Saved 99"));assertNull(LibraryActivityTest.find(root(),"Saved 124"));
        LibraryActivityTest.find(root(),"Show more").performClick();assertNotNull(LibraryActivityTest.find(root(),"Saved 124"));
    }
    @Test public void englishLibraryDoesNotTranslateSavedDutchWebsiteNames(){
        new SavedSitesStore(RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0)).saveBookmark(-1,"Geschiedenis","https://example.org/");
        launch(LibraryActivity.class);assertNotNull(LibraryActivityTest.find(root(),"Library"));assertNotNull(LibraryActivityTest.find(root(),"Geschiedenis"));
    }
}
