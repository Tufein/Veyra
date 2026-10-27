package nl.veyra.browser;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "nl-rNL-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SettingsActivityTest {
    private SharedPreferences preferences;
    private ActivityController<SettingsActivity> controller;

    @Before public void prepare() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("veyra", 0);
        preferences.edit().clear().putString("theme", "dark").putBoolean("dynamic_colors", false).commit();
    }
    @After public void close() { if (controller != null) controller.pause().stop().destroy(); }
    private SettingsActivity launch(SettingsSection section) {
        controller = Robolectric.buildActivity(SettingsActivity.class,
                SettingsActivity.intent(RuntimeEnvironment.getApplication(), section)).setup().visible();
        idle(); return controller.get();
    }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250)); }
    private SettingsActivity activity() { return controller.get(); }
    private View root() { return activity().getWindow().getDecorView(); }
    private void open(int label) {
        View target = findText(root(), activity().getString(label)); assertNotNull("Missing setting", target);
        while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
        assertTrue(target.performClick()); idle();
    }
    private String title() { return ((MaterialToolbar) activity().findViewById(R.id.settings_toolbar)).getTitle().toString(); }

    @Test public void hubHasCategoriesInsteadOfInlinePreferences() throws Exception {
        launch(SettingsSection.OVERVIEW);
        assertEquals("Instellingen", title());
        assertNotNull(findText(root(), "Uitstraling")); assertNotNull(findText(root(), "Privacy en bescherming"));
        assertNull(activity().findViewById(R.id.settings_zoom_slider));
        assertNull(activity().findViewById(R.id.settings_theme_group));
        screenshot("overview-dark");
        activity().navigateUp(); assertTrue(activity().isFinishing());
    }
    @Test public void detailBackReturnsToHubWithoutClosingSettings() {
        launch(SettingsSection.OVERVIEW); open(R.string.settings_home);
        assertEquals("Startpagina", title());
        MaterialSwitch compact = activity().findViewById(R.id.settings_compact_switch); compact.performClick();
        assertTrue(preferences.getBoolean("compact_home", false));
        activity().navigateUp(); idle();
        assertEquals("Instellingen", title()); assertFalse(activity().isFinishing());
    }
    @Test public void searchFiltersSynonymsAndHandlesNoResults() {
        launch(SettingsSection.OVERVIEW);
        TextInputEditText input = activity().findViewById(R.id.settings_search); input.setText("Firefox"); idle();
        assertNotNull(findText(root(), "Extensies")); assertNull(findText(root(), "Uitstraling"));
        input.setText("nietsdatbestaat"); idle(); assertNotNull(activity().findViewById(R.id.settings_empty));
        input.setText(""); idle(); assertNotNull(findText(root(), "Uitstraling"));
    }
    @Test public void extensionsOpenTheirManagerWithoutClosingSettings() {
        launch(SettingsSection.OVERVIEW); open(R.string.settings_extensions);
        android.content.Intent intent = Shadows.shadowOf(activity()).getNextStartedActivity();
        assertNotNull(intent); assertEquals(ExtensionsActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("Instellingen", title()); assertFalse(activity().isFinishing());
    }
    @Test public void returningFromAndroidSettingsRefreshesPasswordProviderStatus() {
        launch(SettingsSection.PASSWORDS);
        android.view.autofill.AutofillManager manager = activity().getSystemService(android.view.autofill.AutofillManager.class);
        Shadows.shadowOf(manager).setEnabled(false); controller.pause().resume(); idle();
        assertNotNull(findText(root(), activity().getString(R.string.settings_passwords_status_off)));
        open(R.string.settings_passwords_open);
        android.content.Intent intent = Shadows.shadowOf(activity()).getNextStartedActivity();
        assertEquals(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, intent.getAction());
        assertEquals("package:" + activity().getPackageName(), intent.getDataString());
        controller.pause(); Shadows.shadowOf(manager).setEnabled(true); controller.resume(); idle();
        assertEquals("Wachtwoorden en passkeys", title()); assertFalse(activity().isFinishing());
        assertNotNull(findText(root(), activity().getString(R.string.settings_passwords_status_on)));
    }
    @Test public void keyboardAdjustmentPersistsWebsiteTextSize() throws Exception {
        launch(SettingsSection.READING);
        com.google.android.material.slider.Slider slider = activity().findViewById(R.id.settings_zoom_slider);
        slider.requestFocus();
        slider.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(105, preferences.getInt("text_zoom", 100));
        screenshot("reading-dark");
    }
    @Test public void themeRecreationPreservesPageAndCheckedTheme() throws Exception {
        launch(SettingsSection.OVERVIEW); open(R.string.settings_appearance);
        activity().findViewById(R.id.settings_theme_light).performClick(); idle(); controller.recreate(); idle();
        assertEquals("light", preferences.getString("theme", "")); assertEquals("Uitstraling", title());
        MaterialButtonToggleGroup group = activity().findViewById(R.id.settings_theme_group);
        assertEquals(R.id.settings_theme_light, group.getCheckedButtonId());
        screenshot("appearance-light");
        activity().navigateUp(); idle(); assertEquals("Instellingen", title()); screenshot("overview-light");
    }
    @Test public void accentSelectionUpdatesPaletteAndDisablesWallpaper() throws Exception {
        launch(SettingsSection.APPEARANCE);
        int previous = ThemeSupport.color(activity(), com.google.android.material.R.attr.colorSurface);
        open(R.string.settings_accent_violet); controller.recreate(); idle();
        assertEquals("violet", preferences.getString("accent", ""));
        assertFalse(preferences.getBoolean("dynamic_colors", true));
        assertNotEquals(previous, ThemeSupport.color(activity(), com.google.android.material.R.attr.colorSurface));
        screenshot("appearance-dark");
    }
    @Test public void shortcutEditorStaysInSettingsAndRejectsScriptUrls() {
        launch(SettingsSection.HOME); open(R.string.settings_shortcuts); open(R.string.shortcut_add);
        TextInputEditText name = activity().findViewById(R.id.settings_shortcut_name);
        TextInputEditText url = activity().findViewById(R.id.settings_shortcut_url);
        name.setText("Mijn website"); url.setText("javascript:alert(1)");
        activity().findViewById(R.id.settings_shortcut_save).performClick(); idle();
        assertEquals("Nieuwe snelkoppeling", title()); assertFalse(preferences.contains("shortcuts"));
        url.setText("example.org"); activity().findViewById(R.id.settings_shortcut_save).performClick(); idle();
        assertEquals("Snelkoppelingen", title()); assertFalse(activity().isFinishing());
        ShortcutStore.Shortcut saved = new ShortcutStore(preferences).read().get(4);
        assertEquals("Mijn website", saved.title); assertEquals("https://example.org/", saved.url);
        assertTrue(new SavedSitesStore(preferences).isBookmarked(saved.url));
        activity().navigateUp(); idle(); assertEquals("Startpagina", title());
        activity().navigateUp(); idle(); assertEquals("Instellingen", title());
    }
    @Test public void unsavedNameSurvivesRecreationAndSavingPreservesBrowserState() {
        preferences.edit().putString("tabs", "existing browser state").commit();
        launch(SettingsSection.HOME);
        ((TextInputEditText) activity().findViewById(R.id.settings_name_field)).setText("Stefan");
        controller.recreate(); idle();
        assertEquals("Stefan", ((TextInputEditText) activity().findViewById(R.id.settings_name_field)).getText().toString());
        activity().findViewById(R.id.settings_name_save).performClick();
        assertEquals("Stefan", preferences.getString("nickname", ""));
        assertEquals("existing browser state", preferences.getString("tabs", ""));
    }
    @Test public void editorDraftAndParentStackSurviveRecreation() {
        launch(SettingsSection.HOME); open(R.string.settings_shortcuts); open(R.string.shortcut_add);
        ((TextInputEditText) activity().findViewById(R.id.settings_shortcut_name)).setText("Nog niet bewaard");
        ((TextInputEditText) activity().findViewById(R.id.settings_shortcut_url)).setText("example.net");
        controller.recreate(); idle();
        assertEquals("Nog niet bewaard", ((TextInputEditText) activity().findViewById(R.id.settings_shortcut_name)).getText().toString());
        assertFalse(preferences.contains("shortcuts"));
        activity().navigateUp(); idle(); assertEquals("Snelkoppelingen", title());
        activity().navigateUp(); idle(); assertEquals("Startpagina", title());
    }
    @Test public void largeFontAndSmallPhoneKeepSettingsScrollable() throws Exception {
        RuntimeEnvironment.setQualifiers("nl-rNL-w320dp-h640dp-mdpi");
        android.content.res.Configuration config = new android.content.res.Configuration(activityConfiguration());
        config.fontScale = 1.5f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(config,
                RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        launch(SettingsSection.APPEARANCE); screenshot("appearance-small-large-font");
        MaterialButtonToggleGroup group = activity().findViewById(R.id.settings_theme_group);
        assertNotNull(group); assertEquals(android.widget.LinearLayout.VERTICAL, group.getOrientation());
        assertFalse(activity().isFinishing());
    }
    @Test @Config(sdk = 28, qualifiers = "nl-rNL-w360dp-h740dp-mdpi")
    public void androidNineKeepsManualThemesAndDisablesWallpaperColors() {
        launch(SettingsSection.APPEARANCE);
        MaterialSwitch wallpaper = activity().findViewById(R.id.settings_dynamic_switch);
        assertNotNull(wallpaper); assertFalse(wallpaper.isEnabled());
        activity().findViewById(R.id.settings_theme_light).performClick(); controller.recreate(); idle();
        assertEquals("light", preferences.getString("theme", ""));
        assertEquals("Uitstraling", title());
    }
    @Test public void shortcutListUsesCompactSiteRowsAndLinksTheLibrary() throws Exception {
        launch(SettingsSection.SHORTCUTS); screenshot("shortcuts-dark");
        open(R.string.library_from_bookmarks);
        android.content.Intent intent = Shadows.shadowOf(activity()).getNextStartedActivity();
        assertEquals(LibraryActivity.class.getName(), intent.getComponent().getClassName());
        assertFalse(activity().isFinishing());
    }
    private android.content.res.Configuration activityConfiguration() {
        return RuntimeEnvironment.getApplication().getResources().getConfiguration();
    }
    private void screenshot(String name) throws Exception {
        idle();
        View decor = root(); int width = activity().getResources().getDisplayMetrics().widthPixels;
        int height = activity().getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(image));
        File directory = new File("build/reports/settings-ui"); assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
