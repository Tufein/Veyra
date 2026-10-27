package nl.veyra.browser;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

/** Mozilla catalog plus persistent, signed add-on installation and management. */
public final class ExtensionsActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Set<String> installedIds = new HashSet<>();
    private VeyraRuntime engine;
    private ExtensionUi extensionUi;
    private LinearLayout installed, catalog;
    private TextInputEditText search;
    private ProgressBar progress;
    private TextView status;
    private int request;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        ThemeSupport.apply(this, getSharedPreferences("veyra", MODE_PRIVATE));
        super.onCreate(state);
        engine = VeyraRuntime.get(this); extensionUi = new ExtensionUi(this, null);
        buildPage(); loadInstalled(); searchCatalog();
    }
    @Override protected void onResume() {
        super.onResume(); engine.setPromptActivity(this); extensionUi.attach();
    }
    @Override protected void onPause() { engine.clearPromptActivity(this); extensionUi.suspend(); super.onPause(); }
    @Override protected void onDestroy() {
        request++; network.shutdownNow(); extensionUi.destroy(); super.onDestroy();
    }
    private void buildPage() {
        LinearLayout root = column();
        int surface = color(com.google.android.material.R.attr.colorSurface);
        root.setBackgroundColor(surface);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(getString(R.string.ui_extensions_8966f));
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back); toolbar.setNavigationContentDescription(getString(R.string.ui_back_ebf6c));
        toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar);
        ScrollView scroll = new ScrollView(this); LinearLayout content = column();
        content.setPadding(dp(20), dp(12), dp(20), dp(32)); scroll.addView(content);
        TextView headline = text(getString(R.string.ui_a_little_extra_veyra_1b81b), 30); content.addView(headline);
        add(content, text(getString(R.string.ui_discover_extensions_from_mozilla_add_ons_for_firefox_o_9f892), 15), 12);
        add(content, button(getString(R.string.ui_disable_all_extra_extensions_ab3a3),v -> new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_disable_extensions_d0ffd))
                .setMessage(getString(R.string.ui_your_websites_and_browser_data_are_retained_built_in_p_dba35))
                .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_disable_257f2),(d,w)->operation(engine.disableUserExtensions(),getString(R.string.ui_extra_extensions_disabled_56aa3))).show()),16);
        add(content, button(getString(R.string.ui_compatibility_and_test_status_27405),v -> new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_extension_compatibility_24da9))
                .setMessage(getString(R.string.ui_ublock_origin_device_validation_pending_dark_reader_de_2e380))
                .setPositiveButton(getString(R.string.ui_done_6fb77),null).show()),4);
        add(content, text(getString(R.string.ui_installed_7ddcb), 18), 28);
        installed = column(); add(content, installed, 12);
        add(content, text(getString(R.string.ui_discover_100e7), 18), 28);
        TextInputLayout input = new TextInputLayout(this); input.setHint(getString(R.string.ui_search_firefox_extensions_91332));
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        search = new TextInputEditText(input.getContext()); search.setSingleLine(); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { searchCatalog(); return true; } return false; });
        input.addView(search); add(content, input, 16);
        MaterialButton find = button(getString(R.string.ui_search_5d33a), v -> searchCatalog()); add(content, find, 8);
        progress = new ProgressBar(this); progress.setVisibility(View.GONE); add(content, progress, 12);
        status = text("", 14); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(content, status, 8);
        catalog = column(); add(content, catalog, 12);
        add(content, text(getString(R.string.ui_geckoview_verifies_downloads_and_signatures_new_permis_6e116), 12), 20);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        getWindow().setStatusBarColor(surface); getWindow().setNavigationBarColor(surface);
        if (ThemeSupport.isLight(this, getSharedPreferences("veyra", MODE_PRIVATE)))
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void loadInstalled() {
        engine.runtime.getWebExtensionController().list().accept(list -> {
            if (isDestroyed()) return;
            installed.removeAllViews(); installedIds.clear();
            for (WebExtension extension : list) {
                if (extension.isBuiltIn) continue;
                installedIds.add(extension.id); extensionUi.bind(extension);
                LinearLayout card = card(installed);
                card.addView(text(VeyraRuntime.name(extension), 18));
                if (extension.metaData != null) add(card, text(extension.metaData.version, 12), 4);
                MaterialSwitch enabled = new MaterialSwitch(this); enabled.setText(R.string.extension_enabled);
                enabled.setChecked(extension.metaData != null && extension.metaData.enabled);
                enabled.setMinHeight(dp(48)); card.addView(enabled);
                enabled.setOnCheckedChangeListener((v, checked) -> {
                    if (busy) { loadInstalled(); return; }
                    WebExtensionController controller = engine.runtime.getWebExtensionController();
                    operation(checked ? controller.enable(extension, WebExtensionController.EnableSource.USER)
                            : controller.disable(extension, WebExtensionController.EnableSource.USER), getString(R.string.ui_extension_changed_12817));
                });
                MaterialButton open = button(getString(R.string.ui_open_5b1e5), v -> extensionUi.open(extension));
                open.setEnabled(extension.metaData != null && extension.metaData.enabled);
                add(card, open, 4);
                if (extension.metaData != null && extension.metaData.optionsPageUrl != null)
                    add(card, button(getString(R.string.ui_settings_27018), v -> extensionUi.options(extension)), 0);
                add(card, button(getString(R.string.ui_check_for_updates_bbfdb), v -> {
                    if (!busy) operation(engine.runtime.getWebExtensionController().update(extension), getString(R.string.ui_update_check_completed_cb124));
                }), 0);
                add(card, button(getString(R.string.ui_remove_d6c44), v -> {
                    if (busy) return;
                    new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_remove_extension_57080))
                            .setMessage(VeyraRuntime.name(extension) + getString(R.string.ui_and_its_extension_data_will_be_removed_75496))
                            .setPositiveButton(getString(R.string.ui_remove_d6c44), (d, w) -> operation(engine.runtime.getWebExtensionController().uninstall(extension), getString(R.string.ui_extension_removed_9eec3)))
                            .setNegativeButton(getString(R.string.ui_cancel_c6a8d), null).show();
                }), 0);
            }
            if (installedIds.isEmpty()) installed.addView(text(getString(R.string.ui_no_extensions_yet_discover_what_works_for_you_below_98814), 14));
        }, failure -> { if (!isDestroyed()) installed.addView(text(getString(R.string.ui_installed_extensions_could_not_be_loaded_reopen_this_s_20b49), 14)); });
    }
    private void searchCatalog() {
        String query = search.getText() == null ? "" : search.getText().toString();
        int generation = ++request;
        progress.setVisibility(View.VISIBLE); status.setText(R.string.extension_catalog_loading); catalog.removeAllViews();
        network.submit(() -> {
            try {
                java.util.List<AddonRepository.Addon> results = new AddonRepository().search(query);
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != request) return;
                    progress.setVisibility(busy ? View.VISIBLE : View.GONE);
                    status.setText(results.isEmpty() ? R.string.extension_catalog_empty : R.string.extension_catalog_source);
                    for (AddonRepository.Addon addon : results) {
                        LinearLayout card = card(catalog); card.addView(text(addon.name, 18));
                        add(card, text(Html.fromHtml(addon.summary, Html.FROM_HTML_MODE_COMPACT).toString(), 14), 8);
                        add(card, text(getString(R.string.ui_version_0d39b) + addon.version, 12), 8);
                        MaterialButton install = button(installedIds.contains(addon.id) ? getString(R.string.ui_installed_7ddcb) : getString(R.string.ui_add_to_veyra_55ca7), v -> {
                            if (busy || installedIds.contains(addon.id)) return;
                            new MaterialAlertDialogBuilder(this).setTitle(addon.name)
                                    .setMessage(getString(R.string.ui_version_0d39b) + addon.version + getString(R.string.ui_source_mozilla_add_ons_firefox_on_android_8c202) + addon.permissions
                                            + getString(R.string.ui_geckoview_verifies_the_signature_and_then_requests_con_c9fb8))
                                    .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_continue_65c33),(d,w) -> operation(engine.runtime.getWebExtensionController().install(addon.downloadUrl),getString(R.string.ui_extension_added_f937f))).show();
                        });
                        install.setEnabled(!installedIds.contains(addon.id)); add(card, install, 12);
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != request) return;
                    progress.setVisibility(busy ? View.VISIBLE : View.GONE);
                    status.setText(R.string.extension_catalog_failed);
                });
            }
        });
    }
    private <T> void operation(GeckoResult<T> result, String success) {
        busy = true; progress.setVisibility(View.VISIBLE); status.setText(R.string.extension_busy);
        result.accept(value -> {
            if (isDestroyed()) return;
            busy = false; progress.setVisibility(View.GONE); status.setText(success); loadInstalled(); searchCatalog();
        }, failure -> {
            if (isDestroyed()) return;
            busy = false; progress.setVisibility(View.GONE);
            String message = getString(R.string.ui_the_change_was_not_applied_check_your_connection_and_w_ed8b4);
            if (failure instanceof WebExtension.InstallException) {
                int code = ((WebExtension.InstallException) failure).code;
                message = getString(R.string.ui_installation_cancelled_or_rejected_code_c18ec) + code + getString(R.string.ui_the_extension_must_be_compatible_and_have_a_valid_mozi_0084f);
            }
            status.setText(message); loadInstalled();
        });
    }
    private MaterialButton button(String label, View.OnClickListener listener) {
        MaterialButton v = new MaterialButton(this); v.setText(label); v.setAllCaps(false); v.setMinHeight(dp(48)); v.setOnClickListener(listener); return v;
    }
    private LinearLayout card(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(24)); card.setStrokeWidth(0); card.setCardElevation(0);
        card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout content = column(); content.setPadding(dp(20), dp(16), dp(20), dp(16)); card.addView(content); add(parent, card, 12); return content;
    }
    private int color(int attr) { return ThemeSupport.color(this, attr); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String label, int size) { TextView v = new TextView(this); v.setText(label); v.setTextSize(size); v.setTextColor(color(com.google.android.material.R.attr.colorOnSurface)); return v; }
    private void add(LinearLayout parent, View view, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); parent.addView(view, p); }
}
