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
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Extensies");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back); toolbar.setNavigationContentDescription("Terug");
        toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar);
        ScrollView scroll = new ScrollView(this); LinearLayout content = column();
        content.setPadding(dp(20), dp(12), dp(20), dp(32)); scroll.addView(content);
        TextView headline = text("Een beetje extra Veyra.", 30); content.addView(headline);
        add(content, text("Ontdek extensies uit Mozilla Add-ons voor Firefox op Android. Compatibiliteit verschilt per extensie.", 15), 12);
        add(content, text("Geïnstalleerd", 18), 28);
        installed = column(); add(content, installed, 12);
        add(content, text("Ontdekken", 18), 28);
        TextInputLayout input = new TextInputLayout(this); input.setHint("Zoeken in Firefox-extensies");
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        search = new TextInputEditText(input.getContext()); search.setSingleLine(); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { searchCatalog(); return true; } return false; });
        input.addView(search); add(content, input, 16);
        MaterialButton find = button("Zoeken", v -> searchCatalog()); add(content, find, 8);
        progress = new ProgressBar(this); progress.setVisibility(View.GONE); add(content, progress, 12);
        status = text("", 14); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(content, status, 8);
        catalog = column(); add(content, catalog, 12);
        add(content, text("Downloads en handtekeningen worden gecontroleerd door GeckoView. Nieuwe rechten vragen altijd je toestemming. Updates start je zelf bij de geïnstalleerde extensie.", 12), 20);
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
                            : controller.disable(extension, WebExtensionController.EnableSource.USER), "Extensie aangepast");
                });
                MaterialButton open = button("Openen", v -> extensionUi.open(extension));
                open.setEnabled(extension.metaData != null && extension.metaData.enabled);
                add(card, open, 4);
                if (extension.metaData != null && extension.metaData.optionsPageUrl != null)
                    add(card, button("Instellingen", v -> extensionUi.options(extension)), 0);
                add(card, button("Controleren op updates", v -> {
                    if (!busy) operation(engine.runtime.getWebExtensionController().update(extension), "Updatecontrole voltooid");
                }), 0);
                add(card, button("Verwijderen", v -> {
                    if (busy) return;
                    new MaterialAlertDialogBuilder(this).setTitle("Extensie verwijderen?")
                            .setMessage(VeyraRuntime.name(extension) + " en de bijbehorende extensiegegevens worden verwijderd.")
                            .setPositiveButton("Verwijderen", (d, w) -> operation(engine.runtime.getWebExtensionController().uninstall(extension), "Extensie verwijderd"))
                            .setNegativeButton("Annuleren", null).show();
                }), 0);
            }
            if (installedIds.isEmpty()) installed.addView(text("Nog geen extensies. Ontdek hieronder wat bij je past.", 14));
        }, failure -> { if (!isDestroyed()) installed.addView(text("Geïnstalleerde extensies konden niet worden geladen. Open dit scherm opnieuw.", 14)); });
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
                        add(card, text("Versie " + addon.version, 12), 8);
                        MaterialButton install = button(installedIds.contains(addon.id) ? "Geïnstalleerd" : "Toevoegen aan Veyra", v -> {
                            if (busy || installedIds.contains(addon.id)) return;
                            operation(engine.runtime.getWebExtensionController().install(addon.downloadUrl), "Extensie toegevoegd");
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
            String message = "De wijziging is niet uitgevoerd. Controleer de verbinding en of de extensie deze browser ondersteunt.";
            if (failure instanceof WebExtension.InstallException) {
                int code = ((WebExtension.InstallException) failure).code;
                message = "Installatie geannuleerd of geweigerd (code " + code + "). De extensie moet compatibel zijn en een geldige Mozilla-handtekening hebben.";
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
