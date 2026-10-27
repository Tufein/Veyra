package nl.veyra.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;
import java.util.Locale;

/** One library for saved websites and their explicit start-page pins. */
public final class LibraryActivity extends Activity {
    static final String EXTRA_START_PAGE = "start_page", EXTRA_RETURN_URL = "return_url", EXTRA_URL = "open_url";
    private SavedSitesStore store;
    private SettingsViews ui;
    private LinearLayout root, content, list;
    private boolean pins, editing;
    private String query = "", originalUrl, draftTitle = "", draftUrl = "";
    private TextInputEditText nameInput, urlInput;
    private int scrollPosition;
    private ScrollView scroll;

    @Override public void onCreate(Bundle state) {
        ThemeSupport.apply(this, getSharedPreferences("veyra", MODE_PRIVATE)); super.onCreate(state);
        store = new SavedSitesStore(getSharedPreferences("veyra", MODE_PRIVATE));
        pins = state == null ? getIntent().getBooleanExtra(EXTRA_START_PAGE, false) : state.getBoolean("pins");
        if (state != null) {
            query = state.getString("query", ""); editing = state.getBoolean("editing"); originalUrl = state.getString("original");
            draftTitle = state.getString("title", ""); draftUrl = state.getString("url", ""); scrollPosition = state.getInt("scroll");
        }
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::back);
        render();
    }
    private void render() {
        ui = new SettingsViews(this); root = ui.column(); root.setBackgroundColor(ui.surface);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(editing ? originalUrl == null ? R.string.library_add : R.string.library_edit : R.string.library_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back); toolbar.setNavigationContentDescription(R.string.library_back);
        toolbar.setNavigationOnClickListener(v -> back()); toolbar.setBackgroundColor(ui.surface);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, ui.dp(60)));
        if (!editing) {
            TabLayout tabs = new TabLayout(this); tabs.setId(R.id.library_tabs); tabs.setBackgroundColor(ui.surface);
            tabs.addTab(tabs.newTab().setText(R.string.library_bookmarks), !pins);
            tabs.addTab(tabs.newTab().setText(R.string.library_start_page), pins);
            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) { pins = tab.getPosition() == 1; query = ""; scrollPosition = 0; render(); }
                @Override public void onTabUnselected(TabLayout.Tab tab) { }
                @Override public void onTabReselected(TabLayout.Tab tab) { }
            }); root.addView(tabs);
        }
        scroll = new ScrollView(this); scroll.setId(R.id.library_scroll); scroll.setFillViewport(true);
        FrameLayout centered = new FrameLayout(this); content = ui.column(); content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(28));
        centered.addView(content, new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels, ui.dp(720)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(centered); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (editing) editor(); else overview();
        setContentView(root); getWindow().setStatusBarColor(ui.surface); getWindow().setNavigationBarColor(ui.surface);
        boolean light = ThemeSupport.isLight(this, getSharedPreferences("veyra", MODE_PRIVATE));
        getWindow().getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        if (!editing) scroll.post(() -> scroll.scrollTo(0, scrollPosition));
    }
    private void overview() {
        ui.add(content, ui.text(pins ? R.string.library_pins_body : R.string.library_bookmarks_body, 14, ui.muted), 8);
        TextInputLayout search = field(R.string.library_search); search.setStartIconDrawable(R.drawable.ic_search);
        search.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText input = (TextInputEditText) search.getEditText(); input.setId(R.id.library_search); input.setText(query);
        ui.add(content, search, 18); list = ui.column(); list.setId(R.id.library_list); ui.add(content, list, 12); populate();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { query = s.toString(); populate(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        MaterialButton add = ui.button(R.string.library_add, R.drawable.ic_add, v -> edit(null)); add.setId(R.id.library_add);
        add.setEnabled(!pins || store.shortcuts().size() < SavedSitesStore.LIMIT); ui.add(content, add, 18);
        if (pins && store.shortcuts().size() >= SavedSitesStore.LIMIT) ui.add(content, ui.text(R.string.shortcut_limit, 13, ui.muted), 8);
    }
    private void populate() {
        list.removeAllViews(); List<SavedSitesStore.Site> entries = pins ? store.shortcuts() : store.bookmarks();
        int count = 0; String needle = query.trim().toLowerCase(Locale.ROOT);
        for (SavedSitesStore.Site site : entries) {
            if (!(site.title + " " + site.url).toLowerCase(Locale.ROOT).contains(needle)) continue;
            ui.site(list, site.title, site.url, pins ? (store.isBookmarked(site.url) ? getString(R.string.library_also_bookmarked) : null)
                    : store.isPinned(site.url) ? getString(R.string.library_pinned) : null, v -> actions(site)); count++;
        }
        if (count == 0) {
            LinearLayout empty = ui.card(list, false);
            empty.addView(ui.text(query.isEmpty() ? R.string.library_empty : R.string.library_no_results, 20, ui.ink));
            ui.add(empty, ui.text(query.isEmpty() ? R.string.library_empty_body : R.string.library_search_help, 14, ui.muted), 8);
        }
    }
    private void actions(SavedSitesStore.Site site) {
        BrowserSheets.Sheet sheet = new BrowserSheets.Sheet(this, site.title, SettingsViews.host(site.url));
        BrowserSheets.action(sheet, R.drawable.ic_link, R.string.library_open, true, () -> openSite(site.url));
        boolean pinned = store.isPinned(site.url);
        BrowserSheets.action(sheet, pinned ? R.drawable.ic_check : R.drawable.ic_home,
                pinned ? R.string.library_unpin : R.string.library_pin, true, () -> {
                    try { if (pinned) store.unpin(site.url); else store.pin(site); refresh(); }
                    catch (IllegalArgumentException full) { android.widget.Toast.makeText(this, R.string.shortcut_limit, android.widget.Toast.LENGTH_SHORT).show(); }
                });
        if (!store.isBookmarked(site.url)) BrowserSheets.action(sheet, R.drawable.ic_bookmark, R.string.library_save_bookmark, true, () -> {
            store.saveBookmark(-1, site.title, site.url); refresh();
        });
        BrowserSheets.action(sheet, R.drawable.ic_tune, R.string.library_edit, true, () -> edit(site));
        if (store.isBookmarked(site.url)) BrowserSheets.action(sheet, R.drawable.ic_delete, R.string.library_delete, true, () -> confirmDelete(site));
        sheet.show();
    }
    private void refresh() { scrollPosition = scroll.getScrollY(); render(); }
    private void openSite(String url) {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_URL, false)) setResult(RESULT_OK, new Intent().putExtra(EXTRA_URL, url));
        else startActivity(new Intent(this, BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private void confirmDelete(SavedSitesStore.Site site) {
        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.library_delete_title, site.title))
                .setMessage(R.string.library_delete_body).setNegativeButton(R.string.shortcut_cancel, null)
                .setPositiveButton(R.string.library_delete, (d, w) -> { store.removeBookmark(site.url); refresh(); }).show();
    }
    private void edit(SavedSitesStore.Site site) {
        scrollPosition = scroll.getScrollY(); originalUrl = site == null ? null : site.url;
        draftTitle = site == null ? "" : site.title; draftUrl = site == null ? "" : site.url; editing = true; render();
    }
    private void editor() {
        ui.add(content, ui.text(R.string.library_editor_body, 14, ui.muted), 8);
        TextInputLayout name = field(R.string.shortcut_name), url = field(R.string.shortcut_url);
        nameInput = (TextInputEditText) name.getEditText(); nameInput.setId(R.id.library_name); nameInput.setText(draftTitle);
        nameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        urlInput = (TextInputEditText) url.getEditText(); urlInput.setId(R.id.library_url); urlInput.setText(draftUrl);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        ui.add(content, name, 22); ui.add(content, url, 12);
        MaterialButton save = ui.button(R.string.library_save, R.drawable.ic_check, v -> {
            name.setError(null); url.setError(null);
            try {
                List<SavedSitesStore.Site> entries = pins ? store.shortcuts() : store.bookmarks();
                int index = originalUrl == null ? -1 : SavedSitesStore.indexOf(entries, originalUrl);
                if (originalUrl != null && index < 0) throw new IllegalArgumentException("index");
                if (pins && index == -1) store.pin(SavedSitesStore.validate(nameInput.getText().toString(), urlInput.getText().toString()));
                else if (pins) store.saveShortcut(index, nameInput.getText().toString(), urlInput.getText().toString());
                else store.saveBookmark(index, nameInput.getText().toString(), urlInput.getText().toString());
                editing = false; render();
            } catch (IllegalArgumentException invalid) {
                if ("title".equals(invalid.getMessage())) name.setError(getString(R.string.shortcut_invalid_name));
                else url.setError(getString("duplicate".equals(invalid.getMessage()) ? R.string.library_duplicate
                        : "limit".equals(invalid.getMessage()) ? R.string.shortcut_limit : R.string.shortcut_invalid_url));
            }
        }); save.setId(R.id.library_save); ui.add(content, save, 20);
    }
    private TextInputLayout field(int hint) {
        TextInputLayout field = new TextInputLayout(this); field.setHint(hint); field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setBoxCornerRadii(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        TextInputEditText input = new TextInputEditText(field.getContext()); input.setSingleLine(true);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); field.addView(input, new LinearLayout.LayoutParams(-1, -2)); return field;
    }
    void back() { if (editing) { editing = false; render(); } else finish(); }
    @Override public void onBackPressed() { back(); }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("pins", pins); state.putBoolean("editing", editing); state.putString("query", query); state.putString("original", originalUrl);
        state.putString("title", editing ? nameInput.getText().toString() : draftTitle); state.putString("url", editing ? urlInput.getText().toString() : draftUrl);
        state.putInt("scroll", editing ? scrollPosition : scroll.getScrollY()); super.onSaveInstanceState(state);
    }
}
