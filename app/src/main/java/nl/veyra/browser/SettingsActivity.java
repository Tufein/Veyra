package nl.veyra.browser;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Independent settings hub and detail screens. Only explicit browser Back exits this activity. */
public final class SettingsActivity extends Activity {
    static final String EXTRA_SECTION = "settings_section";
    private SharedPreferences preferences;
    private ShortcutStore shortcuts;
    private SettingsViews ui;
    private SettingsSection section;
    private final ArrayList<SettingsSection> history = new ArrayList<>();
    private final int[] positions = new int[SettingsSection.values().length];
    private ScrollView scroll;
    private LinearLayout content, categories;
    private String query = "";
    private int editing = -1;
    private TextInputEditText nameInput, shortcutName, shortcutUrl;
    private String nameDraft, titleDraft, urlDraft;

    static Intent intent(Context context, SettingsSection section) {
        return new Intent(context, SettingsActivity.class).putExtra(EXTRA_SECTION, section.name());
    }

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("veyra", MODE_PRIVATE);
        ThemeSupport.apply(this, preferences);
        super.onCreate(state);
        shortcuts = new ShortcutStore(preferences);
        setResult(RESULT_OK);
        if (state == null) {
            section = SettingsSection.restore(getIntent().getStringExtra(EXTRA_SECTION));
            if (section == SettingsSection.SHORTCUT_EDITOR || section == SettingsSection.EXTENSIONS) section = SettingsSection.OVERVIEW;
            if (section != SettingsSection.OVERVIEW) history.add(SettingsSection.OVERVIEW);
        } else {
            section = SettingsSection.restore(state.getString("section")); query = state.getString("query", "");
            int[] saved = state.getIntArray("positions");
            if (saved != null) System.arraycopy(saved, 0, positions, 0, Math.min(saved.length, positions.length));
            ArrayList<String> stack = state.getStringArrayList("history");
            if (stack != null) for (String value : stack) history.add(SettingsSection.restore(value));
            editing = state.getInt("editing", -1); nameDraft = state.getString("nameDraft");
            titleDraft = state.getString("titleDraft"); urlDraft = state.getString("urlDraft");
        }
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateUp);
        render(false);
    }

    @Override protected void onResume() {
        super.onResume();
        // Provider availability may change while Android's settings screen is open.
        if (section == SettingsSection.PASSWORDS && scroll != null) { remember(); render(false); }
    }

    private void render(boolean animate) {
        ui = new SettingsViews(this);
        nameInput = null; shortcutName = null; shortcutUrl = null;
        LinearLayout root = ui.column(); root.setBackgroundColor(ui.surface);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setId(R.id.settings_toolbar);
        toolbar.setTitle(section == SettingsSection.SHORTCUT_EDITOR
                ? (editing == -1 ? R.string.shortcut_new : R.string.shortcut_edit) : section.title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(section == SettingsSection.OVERVIEW
                ? R.string.settings_back_browser : history.isEmpty() || history.get(history.size() - 1) == SettingsSection.OVERVIEW
                ? R.string.settings_back_overview : R.string.settings_back_previous);
        toolbar.setNavigationOnClickListener(v -> navigateUp()); toolbar.setBackgroundColor(ui.surface);
        if (section == SettingsSection.OVERVIEW) {
            toolbar.getMenu().add(R.string.settings_about).setIcon(R.drawable.ic_info)
                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
            toolbar.setOnMenuItemClickListener(item -> { open(SettingsSection.ABOUT); return true; });
        }
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, ui.dp(64)));
        scroll = new ScrollView(this); scroll.setId(R.id.settings_scroll); scroll.setFillViewport(true);
        scroll.setClipToPadding(false); scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        FrameLayout centered = new FrameLayout(this);
        content = ui.column(); content.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(32));
        int available = getResources().getDisplayMetrics().widthPixels;
        centered.addView(content, new FrameLayout.LayoutParams(Math.min(available, ui.dp(720)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(centered); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        switch (section) {
            case OVERVIEW: overview(); break;
            case APPEARANCE: appearance(); break;
            case HOME: home(); break;
            case READING: reading(); break;
            case PRIVACY: privacy(); break;
            case SHORTCUTS: shortcutList(); break;
            case SHORTCUT_EDITOR: shortcutEditor(); break;
            case PASSWORDS: passwords(); break;
            case ABOUT: about(); break;
            default: break;
        }
        TextView footer = ui.text(R.string.settings_footer, 12, ui.muted); footer.setGravity(Gravity.CENTER);
        ui.add(content, footer, 30);
        setContentView(root);
        boolean light = ThemeSupport.isLight(this, preferences);
        getWindow().getDecorView().setSystemUiVisibility(light
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        getWindow().setStatusBarColor(ui.surface); getWindow().setNavigationBarColor(ui.surface);
        scroll.post(() -> scroll.scrollTo(0, positions[section.ordinal()]));
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            content.setAlpha(0); content.setTranslationY(ui.dp(8));
            content.animate().alpha(1).translationY(0).setDuration(180).start();
        }
    }

    private void overview() {
        TextView eyebrow = ui.text(R.string.settings_eyebrow, 10, ui.primary); eyebrow.setLetterSpacing(.13f);
        ui.add(content, eyebrow, 14);
        TextView headline = ui.text(R.string.settings_headline, 38, ui.ink);
        headline.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headline.setLetterSpacing(-.045f); headline.setLineSpacing(0, 1); headline.setAccessibilityHeading(true);
        ui.add(content, headline, 10); ui.add(content, ui.text(R.string.settings_description, 14, ui.muted), 10);
        TextInputLayout search = field(R.string.settings_search, 0);
        search.setStartIconDrawable(R.drawable.ic_search); search.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        search.setEndIconContentDescription(R.string.settings_search_clear);
        TextInputEditText input = (TextInputEditText) search.getEditText(); input.setId(R.id.settings_search);
        input.setSingleLine(true); input.setText(query); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        ui.add(content, search, 24);
        categories = ui.column(); categories.setId(R.id.settings_categories); ui.add(content, categories, 0);
        populateCategories();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); populateCategories(); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void populateCategories() {
        categories.removeAllViews();
        SettingsSection[] destinations = {SettingsSection.APPEARANCE, SettingsSection.HOME, SettingsSection.SHORTCUTS,
                SettingsSection.READING, SettingsSection.PRIVACY, SettingsSection.PASSWORDS, SettingsSection.EXTENSIONS, SettingsSection.ABOUT};
        int count = 0;
        for (SettingsSection destination : destinations) {
            String title = getString(destination.title), summary = summary(destination);
            if (!destination.matches(query, title, getString(destination.summary))) continue;
            if (query.trim().isEmpty()) {
                if (destination == SettingsSection.APPEARANCE) ui.section(categories, R.string.settings_personal);
                else if (destination == SettingsSection.READING) ui.section(categories, R.string.settings_everyday);
                else if (destination == SettingsSection.ABOUT) ui.section(categories, R.string.settings_other);
            }
            ui.category(categories, destination.icon, title, summary, v -> open(destination)); count++;
        }
        if (count == 0) {
            LinearLayout empty = ui.card(categories, false); empty.setId(R.id.settings_empty);
            empty.addView(ui.text(R.string.settings_search_empty, 18, ui.ink));
            ui.add(empty, ui.text(R.string.settings_search_empty_body, 14, ui.muted), 8);
        }
    }

    private String summary(SettingsSection destination) {
        if (destination == SettingsSection.APPEARANCE) return getString(R.string.settings_current_theme, themeLabel(), accentLabel());
        if (destination == SettingsSection.READING) return getString(R.string.settings_current_theme, searchLabel(), getString(R.string.settings_text_size_percent, preferences.getInt("text_zoom", 100)));
        if (destination == SettingsSection.PRIVACY) return getString(preferences.getBoolean("blocking", true)
                ? R.string.settings_protection_status_on : R.string.settings_protection_status_off);
        if (destination == SettingsSection.SHORTCUTS) return shortcutCount();
        return getString(destination.summary);
    }

    private void appearance() {
        ui.heading(content, R.string.settings_appearance, R.string.settings_theme_description);
        LinearLayout preview = ui.card(content, true); preview.setId(R.id.settings_preview);
        TextView eyebrow = ui.text(R.string.settings_preview_label, 10, ui.accentInk); eyebrow.setLetterSpacing(.1f); preview.addView(eyebrow);
        TextView title = ui.text(R.string.settings_preview_title, 30, ui.accentInk); title.setLetterSpacing(-.04f); ui.add(preview, title, 12);
        LinearLayout address = ui.row(); address.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        address.setBackground(ui.shape(ui.surface, 26));
        address.addView(ui.image(R.drawable.ic_search, ui.primary), new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        TextView caption = ui.text(R.string.settings_preview_search, 13, ui.muted); caption.setPadding(ui.dp(10), 0, 0, 0);
        address.addView(caption); ui.add(preview, address, 20);
        preview.setContentDescription(getString(R.string.settings_preview_caption));
        ui.section(content, R.string.settings_theme);
        LinearLayout theme = ui.card(content, false);
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this); group.setId(R.id.settings_theme_group);
        group.setSingleSelection(true); group.setSelectionRequired(true);
        boolean stacked = getResources().getConfiguration().fontScale >= 1.3f
                && getResources().getDisplayMetrics().widthPixels < ui.dp(600);
        group.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int[] ids = {R.id.settings_theme_light, R.id.settings_theme_dark, R.id.settings_theme_system};
        String[] keys = {"light", "dark", "system"}; int[] labels = {R.string.settings_theme_light, R.string.settings_theme_dark, R.string.settings_theme_system};
        for (int i = 0; i < keys.length; i++) {
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(ids[i]); button.setTag(keys[i]); button.setText(labels[i]); button.setTextSize(13);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(ui.dp(52)); button.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8));
            button.setSingleLine(false); button.setEllipsize(null);
            group.addView(button, stacked ? new LinearLayout.LayoutParams(-1, -2)
                    : new LinearLayout.LayoutParams(0, -2, 1));
            if (keys[i].equals(preferences.getString("theme", "dark"))) group.check(button.getId());
        }
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            preferences.edit().putString("theme", (String) g.findViewById(id).getTag()).apply(); refreshTheme();
        }); theme.addView(group);
        boolean dynamicAvailable = DynamicColors.isDynamicColorAvailable();
        com.google.android.material.materialswitch.MaterialSwitch dynamic = ui.toggle(theme, R.string.settings_wallpaper,
                dynamicAvailable ? R.string.settings_wallpaper_available : R.string.settings_wallpaper_unavailable,
                dynamicAvailable && preferences.getBoolean("dynamic_colors", true), (button, checked) -> {
                    preferences.edit().putBoolean("dynamic_colors", checked).apply(); refreshTheme();
                });
        dynamic.setId(R.id.settings_dynamic_switch); dynamic.setEnabled(dynamicAvailable);
        ui.section(content, R.string.settings_accents); ui.add(content, ui.text(R.string.settings_accents_description, 13, ui.muted), 8);
        GridLayout swatches = new GridLayout(this); swatches.setColumnCount(2); swatches.setUseDefaultMargins(false);
        String[] seeds = {"mint", "violet", "blue", "peach"};
        int[] names = {R.string.settings_accent_mint, R.string.settings_accent_violet, R.string.settings_accent_blue, R.string.settings_accent_peach};
        int[] lights = {0xFFB4F5CC, 0xFFEBDDFF, 0xFFD3E4FF, 0xFFFFDBCC};
        int[] darks = {0xFF145136, 0xFF50367C, 0xFF164976, 0xFF75351D};
        for (int i = 0; i < seeds.length; i++) {
            String seed = seeds[i]; boolean selected = seed.equals(preferences.getString("accent", "mint"))
                    && !(dynamicAvailable && preferences.getBoolean("dynamic_colors", true));
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(names[i]); button.setTextSize(13); button.setCheckable(true); button.setChecked(selected);
            button.setMinHeight(ui.dp(72)); button.setMinWidth(0); button.setMinimumWidth(0); button.setSingleLine(false);
            button.setPadding(ui.dp(12), ui.dp(8), ui.dp(8), ui.dp(8)); button.setCornerRadius(ui.dp(24));
            button.setIcon(new SettingsViews.PaletteIcon(lights[i], darks[i])); button.setIconTint(null); button.setIconSize(ui.dp(32));
            button.setTextColor(ui.ink); button.setBackgroundTintList(ColorStateList.valueOf(selected ? ui.high : ui.low));
            button.setStrokeWidth(ui.dp(selected ? 2 : 0)); button.setStrokeColor(ColorStateList.valueOf(ui.primary));
            button.setContentDescription(selected ? getString(R.string.settings_selected, getString(names[i])) : getString(names[i]));
            button.setOnClickListener(v -> { preferences.edit().putString("accent", seed).putBoolean("dynamic_colors", false).apply(); refreshTheme(); });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0;
            params.columnSpec = GridLayout.spec(i % 2, 1f); params.rowSpec = GridLayout.spec(i / 2);
            params.setMargins(i % 2 == 0 ? 0 : ui.dp(6), ui.dp(6), i % 2 == 0 ? ui.dp(6) : 0, 0);
            swatches.addView(button, params);
        }
        ui.add(content, swatches, 8);
    }

    private void home() {
        ui.heading(content, R.string.settings_home, R.string.settings_home_summary);
        LinearLayout card = ui.card(content, false);
        com.google.android.material.materialswitch.MaterialSwitch compact = ui.toggle(card, R.string.settings_compact,
                R.string.settings_compact_summary, preferences.getBoolean("compact_home", false),
                (button, checked) -> preferences.edit().putBoolean("compact_home", checked).apply());
        compact.setId(R.id.settings_compact_switch);
        ui.section(content, R.string.settings_name); ui.add(content, ui.text(R.string.settings_name_description, 14, ui.muted), 8);
        TextInputLayout name = field(R.string.settings_name_hint, 24); nameInput = (TextInputEditText) name.getEditText();
        nameInput.setId(R.id.settings_name_field); nameInput.setText(nameDraft == null ? preferences.getString("nickname", "") : nameDraft);
        ui.add(content, name, 16);
        MaterialButton save = ui.button(R.string.settings_save_name, R.drawable.ic_check, v -> {
            preferences.edit().putString("nickname", value(nameInput).trim()).apply(); nameDraft = null;
            hideKeyboard(); announce(R.string.settings_name_saved);
        }); save.setId(R.id.settings_name_save); ui.add(content, save, 12);
        LinearLayout shortcutsCard = ui.card(content, false);
        ui.navigation(shortcutsCard, R.string.settings_shortcuts, shortcutCount(), v -> open(SettingsSection.SHORTCUTS));
    }

    private void reading() {
        ui.heading(content, R.string.settings_reading, R.string.settings_reading_description);
        LinearLayout card = ui.card(content, false);
        int saved = preferences.getInt("text_zoom", 100);
        int zoom = Math.max(85, Math.min(150, Math.round(saved / 5f) * 5));
        TextView label = ui.text(getString(R.string.settings_text_size, zoom), 17, ui.ink); card.addView(label);
        Slider slider = new Slider(this); slider.setId(R.id.settings_zoom_slider); slider.setContentDescription(getString(R.string.settings_reading_slider));
        slider.setValueFrom(85); slider.setValueTo(150); slider.setStepSize(5); slider.setValue(zoom);
        slider.setLabelFormatter(number -> getString(R.string.settings_text_size_percent, Math.round(number)));
        ui.add(card, slider, 16);
        LinearLayout preview = ui.card(content, true);
        TextView sample = ui.text(R.string.settings_reading_preview, 24, ui.accentInk); preview.addView(sample);
        TextView body = ui.text(R.string.settings_reading_sample, 15, ui.accentInk); ui.add(preview, body, 12);
        sample.setTextSize(24 * zoom / 100f); body.setTextSize(15 * zoom / 100f);
        slider.addOnChangeListener((s, number, fromUser) -> {
            int size = Math.round(number); label.setText(getString(R.string.settings_text_size, size));
            sample.setTextSize(24 * size / 100f); body.setTextSize(15 * size / 100f);
            if (fromUser) preferences.edit().putInt("text_zoom", size).apply();
        });
        ui.section(content, R.string.settings_search_engine);
        ui.add(content, ui.text(R.string.settings_search_engine_description, 13, ui.muted), 8);
        String[] values = {"duckduckgo", "google", "brave", "ecosia"};
        String[] labels = {"DuckDuckGo", "Google", "Brave Search", "Ecosia"};
        for (int i = 0; i < values.length; i++) {
            String key = values[i]; boolean selected = key.equals(preferences.getString("search", "duckduckgo"));
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(labels[i]); button.setAllCaps(false); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setMinHeight(ui.dp(56)); button.setCornerRadius(ui.dp(20)); button.setCheckable(true); button.setChecked(selected);
            if (selected) button.setIconResource(R.drawable.ic_check);
            button.setOnClickListener(v -> { preferences.edit().putString("search", key).apply(); remember(); render(false); });
            ui.add(content, button, 8);
        }
    }

    private void privacy() {
        ui.heading(content, R.string.settings_privacy, R.string.settings_privacy_summary);
        LinearLayout card = ui.card(content, false);
        ui.toggle(card, R.string.settings_blocking, R.string.settings_blocking_summary, preferences.getBoolean("blocking", true),
                (button, checked) -> preferences.edit().putBoolean("blocking", checked).apply());
        ui.toggle(card, R.string.settings_https, R.string.settings_https_summary, preferences.getBoolean("https_only", false),
                (button, checked) -> preferences.edit().putBoolean("https_only", checked).apply());
        ui.add(content, ui.text(R.string.settings_privacy_note, 13, ui.muted), 14);
        ui.section(content, R.string.settings_exceptions);
        Set<String> saved = preferences.getStringSet("ad_exceptions", java.util.Collections.emptySet());
        if (saved.isEmpty()) {
            LinearLayout empty = ui.card(content, false); empty.addView(ui.text(R.string.settings_exceptions_none, 17, ui.ink));
            ui.add(empty, ui.text(R.string.settings_exceptions_none_body, 14, ui.muted), 8);
        } else {
            ui.add(content, ui.text(R.string.settings_exception_description, 13, ui.muted), 8);
            String[] sites = saved.toArray(new String[0]); Arrays.sort(sites);
            for (String site : sites) {
                MaterialButton remove = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                remove.setText(site); remove.setAllCaps(false); remove.setMinHeight(ui.dp(56)); remove.setIconResource(R.drawable.ic_shield);
                remove.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.settings_exception_remove, site))
                        .setPositiveButton(R.string.settings_exception_restore, (d, w) -> {
                            Set<String> updated = new HashSet<>(preferences.getStringSet("ad_exceptions", java.util.Collections.emptySet()));
                            updated.remove(site); preferences.edit().putStringSet("ad_exceptions", updated).apply(); remember(); render(false);
                        }).setNegativeButton(R.string.shortcut_cancel, null).show());
                ui.add(content, remove, 8);
            }
        }
    }

    private void shortcutList() {
        ui.heading(content, R.string.settings_shortcuts, R.string.settings_shortcuts_summary);
        List<ShortcutStore.Shortcut> saved = shortcuts.read();
        ui.add(content, ui.text(shortcutCount(), 13, ui.primary), 14);
        if (saved.isEmpty()) {
            LinearLayout empty = ui.card(content, true); empty.addView(ui.text(R.string.settings_shortcut_empty, 22, ui.accentInk));
            ui.add(empty, ui.text(R.string.settings_shortcut_empty_body, 14, ui.accentInk), 10);
        }
        for (int i = 0; i < saved.size(); i++) {
            final int index = i; ShortcutStore.Shortcut shortcut = saved.get(i);
            ui.category(content, R.drawable.ic_link, shortcut.title, shortcut.url, v -> editShortcut(index));
        }
        MaterialButton add = ui.button(R.string.shortcut_add, R.drawable.ic_add, v -> editShortcut(-1));
        add.setEnabled(saved.size() < ShortcutStore.LIMIT); ui.add(content, add, 20);
        if (saved.size() >= ShortcutStore.LIMIT) ui.add(content, ui.text(R.string.shortcut_limit, 13, ui.muted), 10);
    }

    private void editShortcut(int index) { editing = index; titleDraft = null; urlDraft = null; open(SettingsSection.SHORTCUT_EDITOR); }
    private void shortcutEditor() {
        List<ShortcutStore.Shortcut> saved = shortcuts.read();
        if (editing >= saved.size()) { editing = -1; announce(R.string.shortcut_unavailable); }
        ui.heading(content, editing == -1 ? R.string.shortcut_new : R.string.shortcut_edit, R.string.shortcut_editor_summary);
        TextInputLayout name = field(R.string.shortcut_name, 40); shortcutName = (TextInputEditText) name.getEditText();
        shortcutName.setId(R.id.settings_shortcut_name); shortcutName.setText(titleDraft != null ? titleDraft : editing < 0 ? "" : saved.get(editing).title);
        TextInputLayout url = field(R.string.shortcut_url, 0); url.setPlaceholderText(getString(R.string.shortcut_url_hint));
        shortcutUrl = (TextInputEditText) url.getEditText(); shortcutUrl.setId(R.id.settings_shortcut_url);
        shortcutUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        shortcutUrl.setText(urlDraft != null ? urlDraft : editing < 0 ? "" : saved.get(editing).url);
        ui.add(content, name, 24); ui.add(content, url, 14);
        MaterialButton save = ui.button(R.string.shortcut_save, R.drawable.ic_check, v -> {
            name.setError(null); url.setError(null);
            try {
                shortcuts.save(editing, value(shortcutName), value(shortcutUrl));
                titleDraft = null; urlDraft = null; announce(R.string.shortcut_saved); navigateUp();
            } catch (IllegalArgumentException invalid) {
                if ("title".equals(invalid.getMessage())) name.setError(getString(R.string.shortcut_invalid_name));
                else if ("url".equals(invalid.getMessage())) url.setError(getString(R.string.shortcut_invalid_url));
                else announce("limit".equals(invalid.getMessage()) ? R.string.shortcut_limit : R.string.shortcut_unavailable);
            }
        }); save.setId(R.id.settings_shortcut_save); ui.add(content, save, 20);
        if (editing >= 0) {
            MaterialButton remove = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(R.string.shortcut_delete); remove.setIconResource(R.drawable.ic_delete); remove.setMinHeight(ui.dp(56));
            remove.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.shortcut_delete_confirm, saved.get(editing).title))
                    .setPositiveButton(R.string.shortcut_delete_action, (d, w) -> {
                        try { shortcuts.remove(editing); titleDraft = null; urlDraft = null; announce(R.string.shortcut_deleted); navigateUp(); }
                        catch (IllegalArgumentException changed) { announce(R.string.shortcut_unavailable); navigateUp(); }
                    }).setNegativeButton(R.string.shortcut_cancel, null).show());
            ui.add(content, remove, 8);
        }
    }

    private void passwords() {
        ui.heading(content, R.string.settings_passwords, R.string.settings_passwords_description);
        LinearLayout card = ui.card(content, true);
        AutofillManager manager = getSystemService(AutofillManager.class);
        card.addView(ui.text(manager != null && manager.isEnabled() ? R.string.settings_passwords_status_on
                : R.string.settings_passwords_status_off, 20, ui.accentInk));
        ui.add(card, ui.text(R.string.settings_passwords_instructions, 14, ui.accentInk), 10);
        ui.add(content, ui.button(R.string.settings_passwords_open, R.drawable.ic_key, v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException unavailable) { announce(R.string.settings_passwords_unavailable); }
        }), 20);
        ui.section(content, R.string.settings_passkeys_title); ui.add(content, ui.text(R.string.settings_passkeys_body, 14, ui.muted), 10);
    }

    private void about() {
        ui.heading(content, R.string.settings_about, R.string.settings_about_body);
        LinearLayout card = ui.card(content, true);
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException impossible) { version = ""; }
        card.addView(ui.text(getString(R.string.settings_version, version), 21, ui.accentInk));
        ui.add(card, ui.text(R.string.settings_about_alpha, 14, ui.accentInk), 10);
        ui.section(content, R.string.settings_privacy); ui.add(content, ui.text(R.string.settings_about_privacy, 14, ui.muted), 10);
        ui.add(content, ui.text(R.string.settings_about_roadmap, 13, ui.muted), 20);
    }

    private TextInputLayout field(int hint, int maxLength) {
        TextInputLayout layout = new TextInputLayout(this); layout.setHint(getString(hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        float radius = ui.dp(20); layout.setBoxCornerRadii(radius, radius, radius, radius);
        TextInputEditText input = new TextInputEditText(layout.getContext()); input.setSingleLine(true);
        if (maxLength > 0) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        layout.addView(input); return layout;
    }
    private void open(SettingsSection next) {
        hideKeyboard(); remember();
        if (next == SettingsSection.EXTENSIONS) { startActivity(new Intent(this, ExtensionsActivity.class)); return; }
        if (next == section) return;
        history.add(section); section = next; render(true);
    }
    void navigateUp() {
        hideKeyboard(); remember();
        if (!history.isEmpty()) { section = history.remove(history.size() - 1); render(true); }
        else if (section != SettingsSection.OVERVIEW) { section = SettingsSection.OVERVIEW; render(true); }
        else finish();
    }
    @Override public void onBackPressed() { navigateUp(); }
    private void remember() {
        if (scroll != null) positions[section.ordinal()] = scroll.getScrollY();
        if (nameInput != null) nameDraft = value(nameInput);
        if (shortcutName != null) titleDraft = value(shortcutName);
        if (shortcutUrl != null) urlDraft = value(shortcutUrl);
    }
    private void refreshTheme() { remember(); recreate(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        remember(); state.putString("section", section.name()); state.putString("query", query);
        state.putIntArray("positions", positions); state.putInt("editing", editing);
        ArrayList<String> stack = new ArrayList<>(); for (SettingsSection item : history) stack.add(item.name());
        state.putStringArrayList("history", stack); state.putString("nameDraft", nameDraft);
        state.putString("titleDraft", titleDraft); state.putString("urlDraft", urlDraft);
        super.onSaveInstanceState(state);
    }
    private void hideKeyboard() {
        View focus = getCurrentFocus(); if (focus == null) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0); focus.clearFocus();
    }
    private void announce(int resource) { Toast.makeText(this, resource, Toast.LENGTH_SHORT).show(); }
    private static String value(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private String shortcutCount() {
        int count = shortcuts.read().size();
        return getResources().getQuantityString(R.plurals.settings_shortcut_count, count, count, ShortcutStore.LIMIT);
    }
    private String themeLabel() {
        String value = preferences.getString("theme", "dark");
        return getString("light".equals(value) ? R.string.settings_theme_light : "system".equals(value) ? R.string.settings_theme_system : R.string.settings_theme_dark);
    }
    private String accentLabel() {
        if (DynamicColors.isDynamicColorAvailable() && preferences.getBoolean("dynamic_colors", true)) return getString(R.string.settings_wallpaper);
        switch (preferences.getString("accent", "mint")) {
            case "violet": return getString(R.string.settings_accent_violet);
            case "blue": return getString(R.string.settings_accent_blue);
            case "peach": return getString(R.string.settings_accent_peach);
            default: return getString(R.string.settings_accent_mint);
        }
    }
    private String searchLabel() {
        switch (preferences.getString("search", "duckduckgo")) {
            case "google": return "Google"; case "brave": return "Brave Search"; case "ecosia": return "Ecosia"; default: return "DuckDuckGo";
        }
    }
}
