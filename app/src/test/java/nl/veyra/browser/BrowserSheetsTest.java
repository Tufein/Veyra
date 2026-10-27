package nl.veyra.browser;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="nl-rNL-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class BrowserSheetsTest {
    public static class Host extends Activity {
        @Override public void onCreate(Bundle state) {
            ThemeSupport.apply(this,getSharedPreferences("veyra",0)); super.onCreate(state);
            setContentView(new android.widget.FrameLayout(this));
        }
    }
    private ActivityController<Host> controller;
    private BrowserSheets.Sheet sheet;
    @Before public void prepare() {
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().clear()
                .putString("theme","dark").putBoolean("dynamic_colors",false).commit();
    }
    private Host host() { controller=Robolectric.buildActivity(Host.class).setup().visible(); return controller.get(); }
    @After public void close() { if(sheet!=null)sheet.dialog.dismiss(); if(controller!=null)controller.pause().stop().destroy(); }
    @Test public void homeMenuDisablesPageActionsAndRoutesLibrary() throws Exception {
        AtomicInteger route=new AtomicInteger(-1); sheet=BrowserSheets.menu(host(),false,false,"",route::set); sheet.show();
        View bookmark=LibraryActivityTest.find(sheet.root,"Bewaren"); assertNotNull(bookmark); assertFalse(bookmark.isEnabled());
        View share=LibraryActivityTest.find(sheet.root,"Pagina delen"); assertFalse(((View)share.getParent()).isEnabled());
        LibraryActivityTest.capture(sheet.dialog.getWindow().getDecorView(),"menu-dark");
        LibraryActivityTest.find(sheet.root,"Bibliotheek").performClick(); assertEquals(2,route.get()); assertFalse(sheet.dialog.isShowing());
    }
    @Test public void savedPageShowsStateAndKeepsSettingsAccessible() throws Exception {
        AtomicInteger route=new AtomicInteger(-1); sheet=BrowserSheets.menu(host(),true,true,"example.org",route::set); sheet.show();
        assertFalse(LibraryActivityTest.find(sheet.root,"Opgeslagen").isEnabled());
        LibraryActivityTest.find(sheet.root,"Instellingen").performClick(); assertEquals(7,route.get());
    }
    @Test public void tabsHaveIndependentSelectionAndCloseTargets() throws Exception {
        AtomicInteger selected=new AtomicInteger(-1),closed=new AtomicInteger(-1);
        List<BrowserSheets.TabItem> tabs=Arrays.asList(new BrowserSheets.TabItem("Nieuw tabblad","",true),
                new BrowserSheets.TabItem("Een lange paginatitel die netjes over twee regels mag lopen","https://example.org/",false));
        sheet=BrowserSheets.tabs(host(),tabs,1,12,selected::set,closed::set,()->{}); sheet.show();
        assertNotNull(LibraryActivityTest.find(sheet.root,"Huidig tabblad"));
        LibraryActivityTest.capture(sheet.dialog.getWindow().getDecorView(),"tabs-dark");
        View close=findDescription(sheet.root,"Een lange paginatitel die netjes over twee regels mag lopen sluiten");
        assertNotNull(close); close.performClick(); assertEquals(1,closed.get()); assertEquals(-1,selected.get());
    }
    @Test public void selectingTabRoutesCorrectIndexAndFullListDisablesAdd() {
        List<BrowserSheets.TabItem> tabs=new ArrayList<>(); for(int i=0;i<12;i++)tabs.add(new BrowserSheets.TabItem("Tab "+i,"https://example.org/"+i,false));
        AtomicInteger selected=new AtomicInteger(-1); sheet=BrowserSheets.tabs(host(),tabs,5,12,selected::set,i->{},()->fail("tab limit")); sheet.show();
        View add=LibraryActivityTest.find(sheet.root,"Nieuw tabblad"); assertTrue(add instanceof MaterialButton); assertFalse(add.isEnabled());
        View target=LibraryActivityTest.find(sheet.root,"Tab 2"); while(!target.isClickable())target=(View)target.getParent();
        target.performClick(); assertEquals(2,selected.get());
    }
    @Test @Config(qualifiers="nl-rNL-w320dp-h740dp-mdpi") public void smallScreenLargeTextMenuRemainsScrollable() throws Exception {
        android.content.res.Configuration config=new android.content.res.Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        config.fontScale=1.6f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        sheet=BrowserSheets.menu(host(),true,false,"example.org",i->{}); sheet.show();
        LibraryActivityTest.capture(sheet.dialog.getWindow().getDecorView(),"menu-small-large-font");
        assertTrue(sheet.root.getHeight()<=controller.get().getResources().getDisplayMetrics().heightPixels);
        assertTrue(hasScroll(sheet.root));
    }
    @Test public void lightMenuRendersWithTheSelectedPalette() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().putString("theme","light").putString("accent","blue").commit();
        sheet=BrowserSheets.menu(host(),true,false,"example.org",i->{}); sheet.show();
        LibraryActivityTest.capture(sheet.dialog.getWindow().getDecorView(),"menu-light");
    }
    private static boolean hasScroll(View v) {
        if(v instanceof android.widget.ScrollView)return true;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasScroll(((ViewGroup)v).getChildAt(i)))return true;
        return false;
    }
    private static View findDescription(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=findDescription(((ViewGroup)view).getChildAt(i),description); if(found!=null)return found;
        }
        return null;
    }
}
