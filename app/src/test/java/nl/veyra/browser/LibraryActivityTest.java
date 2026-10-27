package nl.veyra.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="nl-rNL-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class LibraryActivityTest {
    private ActivityController<LibraryActivity> controller;
    private SharedPreferences preferences;
    @Before public void prepare() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("veyra", 0);
        preferences.edit().clear().putString("theme", "dark").putBoolean("dynamic_colors", false).commit();
    }
    @After public void close() { if (controller != null) controller.pause().stop().destroy(); }
    private LibraryActivity launch(boolean pins) {
        controller=Robolectric.buildActivity(LibraryActivity.class,new Intent(RuntimeEnvironment.getApplication(),LibraryActivity.class)
                .putExtra(LibraryActivity.EXTRA_START_PAGE,pins).putExtra(LibraryActivity.EXTRA_RETURN_URL,true)).setup().visible();
        idle(); return controller.get();
    }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250)); }
    private View root() { return controller.get().getWindow().getDecorView(); }
    private void click(int label) {
        View target=find(root(),controller.get().getString(label)); assertNotNull(target);
        while(!target.isClickable() && target.getParent() instanceof View) target=(View)target.getParent();
        assertTrue(target.performClick()); idle();
    }
    @Test public void emptyLibraryHasSearchAndAddInsteadOfClosingWithAToast() throws Exception {
        launch(false); assertNotNull(find(root(),controller.get().getString(R.string.library_empty)));
        assertFalse(controller.get().isFinishing()); capture(root(), "library-empty-dark");
    }
    @Test public void pinsAndBookmarksUseOneLibraryAndShowSharedStatus() throws Exception {
        SavedSitesStore store=new SavedSitesStore(preferences); store.pin(new SavedSitesStore.Site("Design reference","https://example.org/"));
        launch(false); assertNotNull(find(root(),"Design reference")); assertNotNull(find(root(),controller.get().getString(R.string.library_pinned)));
        capture(root(),"library-bookmarks-dark");
        ((TabLayout)controller.get().findViewById(R.id.library_tabs)).getTabAt(1).select(); idle();
        assertNotNull(find(root(),"Wikipedia")); capture(root(),"library-startpage-dark");
    }
    @Test public void editorValidatesRestoresDraftAndSavesBookmark() {
        LibraryActivity activity=launch(false); activity.findViewById(R.id.library_add).performClick(); idle();
        ((TextInputEditText)activity.findViewById(R.id.library_name)).setText("Example");
        ((TextInputEditText)activity.findViewById(R.id.library_url)).setText("javascript:alert(1)");
        activity.findViewById(R.id.library_save).performClick(); assertFalse(preferences.contains("bookmarks"));
        ((TextInputEditText)activity.findViewById(R.id.library_url)).setText("example.org"); controller.recreate(); idle();
        assertEquals("Example",((TextInputEditText)controller.get().findViewById(R.id.library_name)).getText().toString());
        controller.get().findViewById(R.id.library_save).performClick(); idle();
        assertTrue(new SavedSitesStore(preferences).isBookmarked("https://example.org/")); assertFalse(controller.get().isFinishing());
    }
    @Test public void searchAndSelectedTabSurviveRecreation() {
        launch(true); ((TextInputEditText)controller.get().findViewById(R.id.library_search)).setText("Wiki"); idle();
        assertNotNull(find(root(),"Wikipedia")); assertNull(find(root(),"YouTube"));
        controller.recreate(); idle(); assertEquals(1,((TabLayout)controller.get().findViewById(R.id.library_tabs)).getSelectedTabPosition());
        assertEquals("Wiki",((TextInputEditText)controller.get().findViewById(R.id.library_search)).getText().toString());
    }
    @Test public void actionOpensWebsiteAsValidatedResultAndUnpinKeepsBookmark() {
        SavedSitesStore store=new SavedSitesStore(preferences); store.pin(new SavedSitesStore.Site("Example","https://example.org/"));
        launch(false); clickSite("Example");
        android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View unpin=find(dialog.getWindow().getDecorView(),controller.get().getString(R.string.library_unpin));
        while(!unpin.isClickable()) unpin=(View)unpin.getParent(); unpin.performClick(); idle();
        assertTrue(store.isBookmarked("https://example.org/")); assertFalse(store.isPinned("https://example.org/"));
        clickSite("Example"); dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View open=find(dialog.getWindow().getDecorView(),controller.get().getString(R.string.library_open));
        while(!open.isClickable()) open=(View)open.getParent(); open.performClick(); idle();
        assertEquals(Activity.RESULT_OK,Shadows.shadowOf(controller.get()).getResultCode());
        assertEquals("https://example.org/",Shadows.shadowOf(controller.get()).getResultIntent().getStringExtra(LibraryActivity.EXTRA_URL));
    }
    @Test public void lightListRendersWithTheSelectedPalette() throws Exception {
        preferences.edit().putString("theme","light").commit(); launch(true); capture(root(),"library-startpage-light");
    }
    @Test public void deleteConfirmationCanBeCancelledAndThenRemovesBothCopies() {
        SavedSitesStore store=new SavedSitesStore(preferences); store.pin(new SavedSitesStore.Site("Example","https://example.org/"));
        launch(false); clickSite("Example"); clickDialogAction(R.string.library_delete);
        androidx.appcompat.app.AlertDialog confirmation=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        confirmation.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick(); idle();
        assertTrue(store.isBookmarked("https://example.org/")); assertTrue(store.isPinned("https://example.org/"));
        clickSite("Example"); clickDialogAction(R.string.library_delete);
        confirmation=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        confirmation.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick(); idle();
        assertFalse(store.isBookmarked("https://example.org/")); assertFalse(store.isPinned("https://example.org/"));
    }
    @Test @Config(qualifiers="nl-rNL-w320dp-h740dp-mdpi") public void largeTextLibraryKeepsFullWidthRowsAndScrollableContent() throws Exception {
        android.content.res.Configuration config=new android.content.res.Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        config.fontScale=1.6f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        launch(true); capture(root(),"library-small-large-font");
        View list=controller.get().findViewById(R.id.library_list);
        assertTrue(list.getWidth()<=controller.get().getResources().getDisplayMetrics().widthPixels);
        assertNotNull(controller.get().findViewById(R.id.library_scroll));
    }
    private void clickDialogAction(int resource) {
        android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View action=find(dialog.getWindow().getDecorView(),controller.get().getString(resource)); assertNotNull(action);
        while(!action.isClickable()) action=(View)action.getParent(); action.performClick(); idle();
    }
    @Test public void libraryOpenedFromSettingsRoutesToExistingBrowserInstance() {
        SavedSitesStore store=new SavedSitesStore(preferences); store.saveBookmark(-1,"Example","https://example.org/");
        controller=Robolectric.buildActivity(LibraryActivity.class,new Intent(RuntimeEnvironment.getApplication(),LibraryActivity.class)).setup().visible();
        idle(); clickSite("Example"); clickDialogAction(R.string.library_open);
        Intent intent=Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertEquals(BrowserActivity.class.getName(),intent.getComponent().getClassName());
        assertEquals(Intent.ACTION_VIEW,intent.getAction()); assertEquals("https://example.org/",intent.getDataString());
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP,
                intent.getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    private void clickSite(String name) {
        View v=find(root(),name); while(!v.isClickable()) v=(View)v.getParent(); v.performClick(); idle();
    }
    static TextView find(View view,String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText())) return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView result=find(((ViewGroup)view).getChildAt(i),text); if(result!=null)return result;
        }
        return null;
    }
    static void capture(View view,String name) throws Exception {
        idle(); int width=view.getResources().getDisplayMetrics().widthPixels,height=view.getResources().getDisplayMetrics().heightPixels;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height); Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap));
        File directory=new File("build/reports/settings-ui"); assertTrue(directory.isDirectory() || directory.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(directory,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)); }
        finally { bitmap.recycle(); }
    }
}
