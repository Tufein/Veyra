package nl.veyra.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SettingsInstrumentedTest {
    private SharedPreferences preferences;
    private String theme;
    private boolean dynamic, compact;

    @Before public void prepare() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("veyra", Context.MODE_PRIVATE);
        theme = preferences.getString("theme", "dark"); dynamic = preferences.getBoolean("dynamic_colors", true);
        compact = preferences.getBoolean("compact_home", false);
        preferences.edit().putString("theme", "dark").putBoolean("dynamic_colors", false).apply();
    }
    @After public void restore() {
        preferences.edit().putString("theme", theme).putBoolean("dynamic_colors", dynamic).putBoolean("compact_home", compact).apply();
    }
    @Test public void settingsHubOpensIndependentDetailScreenAndReturns() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.settings_title), ((MaterialToolbar) activity.findViewById(R.id.settings_toolbar)).getTitle());
                assertNull(activity.findViewById(R.id.settings_theme_group));
                clickCategory(activity.getWindow().getDecorView(), activity.getString(R.string.settings_home));
                MaterialSwitch control = activity.findViewById(R.id.settings_compact_switch);
                assertNotNull(control); assertEquals(compact, control.isChecked()); control.performClick();
                assertEquals(!compact, preferences.getBoolean("compact_home", compact));
                activity.navigateUp(); assertFalse(activity.isFinishing());
                assertEquals(activity.getString(R.string.settings_title), ((MaterialToolbar) activity.findViewById(R.id.settings_toolbar)).getTitle());
            });
            screenshot("settings-dark.png");
        }
    }
    @Test public void themeSelectionAndDetailScreenSurviveRecreation() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                clickCategory(activity.getWindow().getDecorView(), activity.getString(R.string.settings_appearance));
                activity.findViewById(R.id.settings_theme_light).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("light", preferences.getString("theme", ""));
                assertTrue(ThemeSupport.isLight(activity, preferences));
                assertEquals(activity.getString(R.string.settings_appearance), ((MaterialToolbar) activity.findViewById(R.id.settings_toolbar)).getTitle());
                MaterialButtonToggleGroup group = activity.findViewById(R.id.settings_theme_group);
                assertEquals(R.id.settings_theme_light, group.getCheckedButtonId());
            });
            screenshot("settings-light.png");
        }
    }
    private static void screenshot(String name) throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation(); instrumentation.waitForIdleSync();
        android.graphics.Bitmap image = instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(image);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(instrumentation.getTargetContext().getCacheDir(), name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }
    private static void clickCategory(View root, String label) {
        View target = findText(root, label); assertNotNull(target);
        while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
        assertTrue(target.performClick());
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
