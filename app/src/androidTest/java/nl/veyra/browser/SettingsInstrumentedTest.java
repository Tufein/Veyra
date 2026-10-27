package nl.veyra.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
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
    @Test public void fullScreenSettingsUseMaterialComponentsAndStoredValues() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertNotNull(findText(root, "Jouw Veyra."));
                assertTrue(count(root, MaterialCardView.class) >= 5);
                assertEquals(1, count(root, MaterialButtonToggleGroup.class));
                assertEquals(1, count(root, Slider.class));
                assertTrue(count(root, MaterialSwitch.class) >= 4);
                MaterialSwitch control = findSwitch(root, "Compacte startpagina");
                assertNotNull(control); assertEquals(compact, control.isChecked());
                control.performClick();
                assertEquals(!compact, preferences.getBoolean("compact_home", compact));
            });
            screenshot("settings-dark.png");
        }
    }
    @Test public void themeSelectionSurvivesRecreation() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> findText(activity.getWindow().getDecorView(), "Licht").performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("light", preferences.getString("theme", ""));
                assertTrue(ThemeSupport.isLight(activity, preferences));
                assertNotNull(findText(activity.getWindow().getDecorView(), "Jouw Veyra."));
            });
            screenshot("settings-light.png");
        }
    }
    private static void screenshot(String name) throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        android.graphics.Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Settings screenshot unavailable", image);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(
                instrumentation.getTargetContext().getCacheDir(), name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static MaterialSwitch findSwitch(View view, String description) {
        if (view instanceof MaterialSwitch && description.contentEquals(view.getContentDescription())) return (MaterialSwitch) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            MaterialSwitch found = findSwitch(((ViewGroup) view).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }
    private static int count(View view, Class<?> type) {
        int total = type.isInstance(view) ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) total += count(((ViewGroup) view).getChildAt(i), type);
        return total;
    }
}
