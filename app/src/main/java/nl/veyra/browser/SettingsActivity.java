package nl.veyra.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Full-screen Material You preferences; changes retain the existing preference keys. */
public final class SettingsActivity extends Activity {
    public static final String ACTION = "settings_action";
    private SharedPreferences preferences;
    private ScrollView scroll;
    private int primary, onSurface, muted;
    private int scrollPosition;

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("veyra", MODE_PRIVATE);
        ThemeSupport.apply(this, preferences);
        super.onCreate(state);
        setResult(RESULT_OK);
        primary = color(com.google.android.material.R.attr.colorPrimary);
        onSurface = color(com.google.android.material.R.attr.colorOnSurface);
        muted = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
        scrollPosition = state == null ? 0 : state.getInt("scroll");
        buildPage();
    }

    private void buildPage() {
        LinearLayout root = column();
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Instellingen");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription("Terug naar de browser");
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        scroll = new ScrollView(this);
        scroll.setId(R.id.settings_scroll);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout hero = card(content, true);
        TextView eyebrow = label("MAAK HET JOUW BROWSER", 11, color(com.google.android.material.R.attr.colorOnPrimaryContainer));
        eyebrow.setLetterSpacing(.12f);
        hero.addView(eyebrow);
        TextView title = label("Jouw Veyra.", 36, color(com.google.android.material.R.attr.colorOnPrimaryContainer));
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        add(hero, title, 8);
        add(hero, label("Een rustig web, helemaal in jouw kleuren.", 15,
                color(com.google.android.material.R.attr.colorOnPrimaryContainer)), 8);
        TextView preview = label("Aa     Het web. Op jouw tempo.", 17, onSurface);
        preview.setPadding(dp(18), dp(20), dp(18), dp(20));
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(color(com.google.android.material.R.attr.colorSurface));
        surface.setCornerRadius(dp(24));
        preview.setBackground(surface);
        add(hero, preview, 20);

        section(content, "Uitstraling", R.drawable.ic_palette);
        LinearLayout appearance = card(content, false);
        appearance.addView(label("Kleurthema", 16, onSurface));
        add(appearance, label("Kies je eigen ritme, of volg je telefoon.", 13, muted), 4);
        choices(appearance, "theme", new String[]{"Licht", "Donker", "Systeem"},
                new String[]{"light", "dark", "system"}, "dark", true);
        MaterialSwitch dynamic = toggle(appearance, "Kleuren van je achtergrond",
                DynamicColors.isDynamicColorAvailable() ? "Material You · past zich aan je telefoon aan"
                        : "Beschikbaar vanaf Android 12", "dynamic_colors", true);
        dynamic.setEnabled(DynamicColors.isDynamicColorAvailable());
        dynamic.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean("dynamic_colors", checked).apply(); refreshTheme();
        });
        add(appearance, label("Eigen accentkleur", 16, onSurface), 18);
        String[] accents = {"mint", "violet", "blue", "peach"};
        String[] names = {"Mint", "Lavendel", "Blauw", "Perzik"};
        int[] colors = {0xFFB4F5CC, 0xFFCCB6FF, 0xFFA3CCFF, 0xFFFFBC9E};
        LinearLayout swatches = row();
        boolean wallpaper = DynamicColors.isDynamicColorAvailable() && preferences.getBoolean("dynamic_colors", true);
        for (int i = 0; i < accents.length; i++) {
            final String accent = accents[i];
            MaterialButton swatch = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            swatch.setText(names[i]); swatch.setTextSize(11); swatch.setMinWidth(0); swatch.setMinHeight(dp(56));
            swatch.setMinimumWidth(0); swatch.setPadding(dp(2), 0, dp(2), 0);
            swatch.setCornerRadius(dp(20));
            swatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[i]));
            swatch.setTextColor(0xFF19251F);
            boolean selected = accent.equals(preferences.getString("accent", "mint")) && !wallpaper;
            swatch.setStrokeWidth(dp(selected ? 3 : 0));
            swatch.setStrokeColor(android.content.res.ColorStateList.valueOf(onSurface));
            swatch.setContentDescription(names[i] + (selected ? ", geselecteerd" : ""));
            swatch.setOnClickListener(v -> {
                preferences.edit().putString("accent", accent).putBoolean("dynamic_colors", false).apply(); refreshTheme();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) params.setMarginStart(dp(6));
            swatches.addView(swatch, params);
        }
        add(appearance, swatches, 8);

        section(content, "Startpagina", R.drawable.ic_home);
        LinearLayout home = card(content, false);
        toggle(home, "Compacte startpagina", "Meer ruimte voor je favoriete websites", "compact_home", false);
        navigation(home, "Jouw naam", preferences.getString("nickname", "").isEmpty()
                ? "Voeg een persoonlijke begroeting toe" : preferences.getString("nickname", ""), v -> editName());
        navigation(home, "Snelkoppelingen", "Beheer je favoriete websites", v -> browserAction("shortcuts"));

        section(content, "Lezen en zoeken", R.drawable.ic_search);
        LinearLayout reading = card(content, false);
        TextView zoom = label("Tekstgrootte · " + preferences.getInt("text_zoom", 100) + "%", 16, onSurface);
        reading.addView(zoom);
        Slider slider = new Slider(this);
        slider.setContentDescription("Tekstgrootte op websites");
        slider.setValueFrom(85); slider.setValueTo(150); slider.setStepSize(5);
        slider.setValue(Math.max(85, Math.min(150, preferences.getInt("text_zoom", 100))));
        slider.setLabelFormatter(value -> Math.round(value) + "%");
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            int size = Math.round(value);
            preferences.edit().putInt("text_zoom", size).apply(); zoom.setText(getString(R.string.settings_text_size, size));
        });
        add(reading, slider, 8);
        TextView sample = label("Het web, prettig leesbaar.", 16, muted);
        add(reading, sample, 0);
        sample.setTextSize(16 * slider.getValue() / 100);
        slider.addOnChangeListener((s, value, fromUser) -> sample.setTextSize(16 * value / 100));
        String[] engines = {"DuckDuckGo", "Google", "Brave Search", "Ecosia"};
        String[] keys = {"duckduckgo", "google", "brave", "ecosia"};
        int index = Arrays.asList(keys).indexOf(preferences.getString("search", "duckduckgo"));
        navigation(reading, "Zoekmachine", engines[Math.max(0, index)], v -> {
            new MaterialAlertDialogBuilder(this).setTitle("Zoekmachine")
                    .setSingleChoiceItems(engines, Math.max(0, index), (dialog, which) -> {
                        preferences.edit().putString("search", keys[which]).apply();
                        dialog.dismiss(); rebuild();
                    }).setNegativeButton("Annuleren", null).show();
        });

        section(content, "Privacy en bescherming", R.drawable.ic_shield);
        LinearLayout protection = card(content, false);
        toggle(protection, "Advertenties en trackers blokkeren", "Lokale bescherming, zonder account", "blocking", true);
        navigation(protection, "Uitzonderingen voor websites",
                preferences.getStringSet("ad_exceptions", java.util.Collections.emptySet()).size() + " websites", v -> editExceptions());
        toggle(protection, "Alleen HTTPS", "Blokkeer onversleutelde pagina’s en verzoeken", "https_only", false);
        navigation(protection, "Wachtwoorden en passkeys", "Gebruik je eigen wachtwoordmanager", v -> browserAction("credentials"));

        section(content, "Extensies", R.drawable.ic_extension);
        LinearLayout extensions = card(content, false);
        navigation(extensions, "Firefox-extensies", "Ontdekken en beheren", v ->
                startActivity(new Intent(this, ExtensionsActivity.class)));
        add(extensions, label("Maak Veyra slimmer met extensies voor Firefox op Android.", 13, muted), 8);
        TextView footer = label("veyra  ·  Het web. Op jouw tempo.", 12, muted);
        footer.setGravity(Gravity.CENTER);
        add(content, footer, 28);
        setContentView(root);
        boolean light = ThemeSupport.isLight(this, preferences);
        getWindow().getDecorView().setSystemUiVisibility(light
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        getWindow().setStatusBarColor(color(com.google.android.material.R.attr.colorSurface));
        getWindow().setNavigationBarColor(color(com.google.android.material.R.attr.colorSurface));
        scroll.post(() -> scroll.scrollTo(0, scrollPosition));
    }

    private void refreshTheme() { scrollPosition = scroll.getScrollY(); recreate(); }
    private void rebuild() { scrollPosition = scroll.getScrollY(); buildPage(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("scroll", scroll.getScrollY()); super.onSaveInstanceState(state);
    }
    private void browserAction(String action) {
        setResult(RESULT_OK, new Intent().putExtra(ACTION, action)); finish();
    }
    private void editName() {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("Hoe mogen we je noemen?");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText field = new TextInputEditText(layout.getContext());
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        field.setText(preferences.getString("nickname", "")); layout.addView(field);
        LinearLayout container = column(); container.setPadding(dp(24), dp(8), dp(24), 0); container.addView(layout);
        new MaterialAlertDialogBuilder(this).setTitle("Jouw naam").setView(container)
                .setPositiveButton("Bewaren", (d, w) -> {
                    preferences.edit().putString("nickname", field.getText() == null ? "" : field.getText().toString().trim()).apply(); rebuild();
                }).setNegativeButton("Annuleren", null).show();
    }
    private void editExceptions() {
        Set<String> saved = preferences.getStringSet("ad_exceptions", java.util.Collections.emptySet());
        String[] sites = saved.toArray(new String[0]); Arrays.sort(sites);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this).setTitle("Uitzonderingen");
        if (sites.length == 0) builder.setMessage("Er zijn nog geen uitzonderingen. Open een website en kies ‘Adblocker voor deze website’ in het browsermenu.");
        else builder.setItems(sites, (d, which) -> {
            new MaterialAlertDialogBuilder(this).setTitle("Blokkering herstellen?").setMessage(sites[which])
                    .setPositiveButton("Uitzondering verwijderen", (again, w) -> {
                        Set<String> updated = new HashSet<>(saved); updated.remove(sites[which]);
                        preferences.edit().putStringSet("ad_exceptions", updated).apply(); rebuild();
                    }).setNegativeButton("Annuleren", null).show();
        });
        builder.setNegativeButton("Sluiten", null).show();
    }
    private void choices(LinearLayout parent, String key, String[] labels, String[] values, String fallback, boolean refresh) {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this);
        group.setSingleSelection(true); group.setSelectionRequired(true);
        for (int i = 0; i < values.length; i++) {
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId()); button.setText(labels[i]); button.setTextSize(13);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(52)); button.setPadding(dp(4), dp(8), dp(4), dp(8));
            button.setTag(values[i]); group.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
            if (values[i].equals(preferences.getString(key, fallback))) group.check(button.getId());
        }
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            preferences.edit().putString(key, (String) g.findViewById(id).getTag()).apply();
            if (refresh) refreshTheme();
        });
        add(parent, group, 16);
    }
    private MaterialSwitch toggle(LinearLayout parent, String title, String detail, String key, boolean fallback) {
        LinearLayout row = row(); row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout labels = column(); labels.addView(label(title, 16, onSurface)); add(labels, label(detail, 13, muted), 4);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch control = new MaterialSwitch(this);
        control.setContentDescription(title); control.setChecked(preferences.getBoolean(key, fallback));
        control.setMinHeight(dp(48)); control.setMinWidth(dp(48));
        control.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean(key, checked).apply());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginStart(dp(12));
        row.addView(control, params); row.setOnClickListener(v -> { if (control.isEnabled()) control.toggle(); });
        parent.addView(row); return control;
    }
    private void navigation(LinearLayout parent, String title, String subtitle, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        button.setText(getString(R.string.settings_navigation_label, title, subtitle)); button.setAllCaps(false);
        button.setSingleLine(false); button.setMaxLines(5); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextColor(onSurface); button.setTextSize(14); button.setMinHeight(dp(72));
        button.setPadding(0, dp(10), dp(4), dp(10)); button.setIconResource(R.drawable.ic_chevron_right);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_END); button.setIconTint(android.content.res.ColorStateList.valueOf(muted));
        button.setOnClickListener(listener); parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private void section(LinearLayout parent, String title, int icon) {
        TextView text = label(title, 14, primary); text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(text, android.content.res.ColorStateList.valueOf(primary));
        text.setCompoundDrawablePadding(dp(12)); add(parent, text, 28);
    }
    private LinearLayout card(LinearLayout parent, boolean hero) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(hero ? 32 : 24));
        card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(color(hero ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout inside = column(); inside.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(inside); add(parent, card, hero ? 0 : 12); return inside;
    }
    private int color(int attr) { return ThemeSupport.color(this, attr); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1); return v;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); parent.addView(child, params);
    }
}
