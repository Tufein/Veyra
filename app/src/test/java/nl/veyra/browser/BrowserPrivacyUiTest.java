package nl.veyra.browser;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
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
public final class BrowserPrivacyUiTest {
    public static class Host extends Activity {
        @Override public void onCreate(Bundle state) {
            ThemeSupport.apply(this,getSharedPreferences("veyra",0)); super.onCreate(state);
            setContentView(new android.widget.FrameLayout(this));
        }
    }
    private ActivityController<Host> controller;
    @Before public void prepare() {
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().clear()
                .putString("theme","dark").putBoolean("dynamic_colors",false)
                .putString("nickname","Personal nickname").commit();
    }
    private Host host() { controller=Robolectric.buildActivity(Host.class).setup().visible(); return controller.get(); }
    @After public void close() { if(controller!=null)controller.pause().stop().destroy(); }
    @Test public void bannerExplainsActiveModeAndCountWithIndependentExitAction() {
        AtomicInteger info=new AtomicInteger(),close=new AtomicInteger();
        Host host=host(); BrowserPrivacyUi.Banner banner=new BrowserPrivacyUi.Banner(host,info::incrementAndGet,close::incrementAndGet);
        host.setContentView(banner.view); banner.bind(true,3);
        assertEquals(View.VISIBLE,banner.view.getVisibility());
        assertNotNull(LibraryActivityTest.find(banner.view,"InPrivate actief"));
        assertNotNull(LibraryActivityTest.find(banner.view,"3 privétabbladen"));
        ((View)LibraryActivityTest.find(banner.view,"InPrivate actief").getParent().getParent()).performClick();
        assertEquals(1,info.get()); assertEquals(0,close.get());
        banner.view.findViewById(R.id.private_close_all).performClick(); assertEquals(1,close.get());
        banner.bind(false,2); assertEquals(View.GONE,banner.view.getVisibility());
    }
    @Test public void privateHomeHasExplicitActionsWithoutPersonalGreeting() throws Exception {
        AtomicInteger search=new AtomicInteger(),normal=new AtomicInteger(),close=new AtomicInteger();
        Host host=host(); View home=BrowserPrivacyUi.home(host,search::incrementAndGet,normal::incrementAndGet,close::incrementAndGet);
        host.setContentView(home);
        assertNull(LibraryActivityTest.find(home,"Personal nickname"));
        LibraryActivityTest.find(home,host.getString(R.string.private_search)).performClick(); assertEquals(1,search.get());
        LibraryActivityTest.find(home,host.getString(R.string.normal_new)).performClick(); assertEquals(1,normal.get());
        LibraryActivityTest.find(home,host.getString(R.string.private_close_all)).performClick(); assertEquals(1,close.get());
        capture(host,"private-home-dark");
    }
    @Test public void dialogsInheritSecureWindowWithoutChangingNormalDialogs() {
        Host host=host(); AlertDialog normal=BrowserPrivacyUi.dialog(host).setTitle("Normal").create();
        assertEquals(0,normal.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE);
        host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        AlertDialog secure=BrowserPrivacyUi.dialog(host).setTitle("Private").create();
        secure.show(); assertTrue((secure.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0); secure.dismiss();
    }
    @Test public void privateHomeAndBannerRenderWithLightPalette() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("veyra",0).edit().putString("theme","light").commit();
        capture(host(),"private-home-light");
    }
    @Test @Config(qualifiers="nl-rNL-w320dp-h740dp-mdpi") public void largeTextPrivateActionsRemainScrollable() throws Exception {
        android.content.res.Configuration config=new android.content.res.Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        config.fontScale=2;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        Host host=host(); capture(host,"private-home-small-large-font");
        View root=host.findViewById(android.R.id.content);
        assertTrue(root.getHeight()<=host.getResources().getDisplayMetrics().heightPixels);
        View close=LibraryActivityTest.find(root,host.getString(R.string.private_close_all)); assertNotNull(close);
        android.widget.ScrollView scroll=(android.widget.ScrollView)close.getParent().getParent().getParent();
        scroll.fullScroll(View.FOCUS_DOWN); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.graphics.Rect visible=new android.graphics.Rect();assertTrue(close.getGlobalVisibleRect(visible));
        assertEquals(close.getHeight(),visible.height());
        LibraryActivityTest.capture(host.getWindow().getDecorView(),"private-home-small-large-font-bottom");
    }
    private void capture(Host host,String name) throws Exception {
        SettingsViews ui=new SettingsViews(host); LinearLayout root=ui.column();root.setBackgroundColor(ui.surface);
        BrowserPrivacyUi.Banner banner=new BrowserPrivacyUi.Banner(host,()->{},()->{}); banner.bind(true,2);
        root.addView(BrowserPrivacyUi.home(host,()->{},()->{},()->{}),new LinearLayout.LayoutParams(-1,0,1));
        root.addView(banner.view,new LinearLayout.LayoutParams(-1,-2));host.setContentView(root);
        LibraryActivityTest.capture(host.getWindow().getDecorView(),name);
    }
}
