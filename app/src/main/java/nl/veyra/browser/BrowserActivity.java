package nl.veyra.browser;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Native Material browser chrome backed by GeckoView and signed WebExtensions. */
public final class BrowserActivity extends Activity {
    private int BG, SURFACE, BORDER, TEXT, MUTED, ACCENT, ACCENT_INK;
    private boolean light;
    private volatile boolean httpsOnly;
    private volatile Set<String> adExceptions = Collections.emptySet();
    private String searchPrefix = "https://duckduckgo.com/?q=";
    private static final String HTTPS_ERROR = "Alleen HTTPS staat aan. Deze HTTP-pagina is geblokkeerd. Open het verbindingsmenu om HTTPS te proberen.";
    private static final int MAX_TABS = 12;
    private static final int MAX_RESIDENT = 3;
    private static final int FILE_PICKER = 41;
    private static final int SETTINGS_PAGE = 42;
    private static final int LIBRARY_PAGE = 43;

    private final List<Tab> tabs = new ArrayList<>();
    private int selected;
    private long clock;
    private SharedPreferences preferences;
    private volatile boolean blocking = true;
    private FrameLayout page;
    private EditText address;
    private TextView connection;
    private TextView tabCount;
    private IconButton back;
    private IconButton forward;
    private ProgressBar progress;
    private View home;
    private View error;
    private TextView blockedSummary;
    private ValueCallback<Uri[]> fileCallback;
    private boolean destroyed;
    private VeyraRuntime engine;
    private ExtensionUi extensionUi;
    private final Runnable blockedListener = this::updateBlockedSummary;

    private static final class Tab {
        volatile String url = "";
        volatile String pageHost = "";
        String title = "Nieuw tabblad";
        boolean home = true;
        String error;
        BrowserView web;
        Bundle savedWebState;
        long used;
    }

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("veyra", MODE_PRIVATE);
        readSettings();
        super.onCreate(state);
        engine = VeyraRuntime.get(this);
        engine.addBlockedListener(blockedListener);
        extensionUi = new ExtensionUi(this, this::createExtensionTab);
        buildChrome();
        restoreTabs();
        Thread lists = new Thread(() -> {
            try {
                TrackerBlocker.load(new InputStreamReader(getAssets().open("adblock-hosts.txt"), StandardCharsets.UTF_8));
            } catch (java.io.IOException unavailable) { /* The built-in baseline remains available. */ }
        }, "veyra-adblock");
        lists.setDaemon(true);
        lists.start();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::goBack);
        }
        if (!openIntent(getIntent())) showSelected();
    }

    private void buildChrome() {
        if (page != null) page.removeAllViews();
        home = null;
        blockedSummary = null;
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BG));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout bottom = column();
        bottom.setPadding(dp(12), dp(9), dp(12), dp(4));
        LinearLayout omnibox = row();
        omnibox.setBackground(shape(SURFACE, 24, BORDER));
        omnibox.setElevation(dp(2));
        connection = text("V", 14, ACCENT);
        connection.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connection.setGravity(Gravity.CENTER);
        connection.setContentDescription("Verbindingsinformatie");
        connection.setOnClickListener(v -> showConnection());
        omnibox.addView(connection, new LinearLayout.LayoutParams(dp(52), dp(52)));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(15);
        address.setTextColor(TEXT);
        address.setHintTextColor(MUTED);
        address.setHint("Zoek of voer een webadres in");
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(0, 0, 0, 0);
        address.setSelectAllOnFocus(true);
        address.setContentDescription("Zoeken of webadres");
        address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        omnibox.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        IconButton reload = icon("reload", "Pagina vernieuwen", v -> {
            Tab tab = current();
            if (tab.home) focusAddress();
            else if (!UrlPolicy.allowed(tab.url, httpsOnly) || tab.web == null) navigate(tab.url);
            else { tab.error = null; tab.web.reload(); renderPage(); }
        });
        omnibox.addView(reload, new LinearLayout.LayoutParams(dp(48), dp(52)));
        bottom.addView(omnibox, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout navigation = row();
        back = icon("back", "Terug", v -> goBack());
        forward = icon("forward", "Vooruit", v -> {
            Tab tab = current();
            if (tab.web != null && tab.web.canGoForward()) {
                tab.home = false;
                tab.error = null;
                tab.web.goForward();
                renderPage();
            }
        });
        navigation.addView(back, weightedButton());
        navigation.addView(forward, weightedButton());
        navigation.addView(icon("home", "Startpagina", v -> {
            Tab tab = current();
            if (tab.web != null) { tab.web.stopLoading(); tab.web.onPause(); }
            tab.home = true;
            tab.error = null;
            showSelected();
        }), weightedButton());
        tabCount = text("1", 14, TEXT);
        tabCount.setGravity(Gravity.CENTER);
        tabCount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout tabButton = new FrameLayout(this);
        tabButton.setContentDescription("Tabbladen");
        TextView countShape = tabCount;
        countShape.setBackground(shape(Color.TRANSPARENT, 7, MUTED));
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(dp(25), dp(26), Gravity.CENTER);
        tabButton.addView(countShape, countParams);
        tabButton.setOnClickListener(v -> showTabs());
        navigation.addView(tabButton, weightedButton());
        navigation.addView(icon("menu", "Menu", v -> showMenu()), weightedButton());
        bottom.addView(navigation, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        int flags = light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        root.requestFocus();
    }

    private void readSettings() {
        light = ThemeSupport.isLight(this, preferences);
        blocking = preferences.getBoolean("blocking", true);
        httpsOnly = preferences.getBoolean("https_only", false);
        adExceptions = Collections.unmodifiableSet(new HashSet<>(preferences.getStringSet("ad_exceptions", Collections.emptySet())));
        switch (preferences.getString("search", "duckduckgo")) {
            case "google": searchPrefix = "https://www.google.com/search?q="; break;
            case "brave": searchPrefix = "https://search.brave.com/search?q="; break;
            case "ecosia": searchPrefix = "https://www.ecosia.org/search?q="; break;
            default: searchPrefix = "https://duckduckgo.com/?q="; break;
        }
        ThemeSupport.apply(this, preferences);
        BG = ThemeSupport.color(this, com.google.android.material.R.attr.colorSurface);
        SURFACE = ThemeSupport.color(this, com.google.android.material.R.attr.colorSurfaceContainer);
        BORDER = ThemeSupport.color(this, com.google.android.material.R.attr.colorOutlineVariant);
        TEXT = ThemeSupport.color(this, com.google.android.material.R.attr.colorOnSurface);
        MUTED = ThemeSupport.color(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        ACCENT = ThemeSupport.color(this, com.google.android.material.R.attr.colorPrimary);
        ACCENT_INK = ThemeSupport.color(this, com.google.android.material.R.attr.colorOnPrimary);
    }

    private AlertDialog.Builder dialogBuilder() {
        return new MaterialAlertDialogBuilder(this);
    }

    private void settingsChanged() {
        boolean previousPolicy = httpsOnly;
        readSettings();
        engine.updatePreferences();
        for (Tab tab : tabs) {
            if (previousPolicy != httpsOnly) {
                dispose(tab, false);
                tab.savedWebState = null;
            }
        }
        buildChrome();
        showSelected();
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(52), 1);
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(24), dp(18), dp(24), dp(24));
        LinearLayout heading = row();
        TextView brand = text("veyra", 29, TEXT);
        brand.setLetterSpacing(-0.065f);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(icon("tune", "Instellingen", v -> showSettings()),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(heading);

        String nickname = preferences.getString("nickname", "").trim();
        TextView greeting = text(nickname.isEmpty() ? "EEN NIEUWE DAG. EEN NIEUWE ONTDEKKING." : "FIJN DAT JE ER BENT, " + nickname.toUpperCase(java.util.Locale.ROOT), 10, MUTED);
        greeting.setLetterSpacing(0.10f);
        addSpaced(content, greeting, 12);

        boolean compact = preferences.getBoolean("compact_home", false);
        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { ThemeSupport.color(this, com.google.android.material.R.attr.colorPrimaryContainer), SURFACE });
        gradient.setCornerRadius(dp(28));
        gradient.setStroke(dp(1), BORDER);
        hero.setBackground(gradient);
        LinearLayout heroTop = row();
        TextView tag = text("RUIMTE VOOR JOU", 10, ACCENT);
        tag.setLetterSpacing(0.17f);
        heroTop.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        TextView spark = text("✳", 28, ACCENT);
        heroTop.addView(spark);
        hero.addView(heroTop);
        TextView title = text("Het web.\nOp jouw tempo.", compact ? 27 : 32, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(-0.055f);
        title.setLineSpacing(0, 0.98f);
        addSpaced(hero, title, compact ? 2 : 12);
        if (!compact) {
            TextView sub = text("Waar wil je vandaag naartoe?", 14, MUTED);
            sub.setLineSpacing(dp(4), 1);
            addSpaced(hero, sub, 10);
        }
        TextView start = action("Zoek iets nieuws   ↗", v -> focusAddress());
        addSpaced(hero, start, compact ? 12 : 16);
        addSpaced(content, hero, 17);

        LinearLayout linksHeading = row();
        TextView quickLabel = text(getString(R.string.home_shortcuts), 15, TEXT);
        quickLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        linksHeading.addView(quickLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = text("Bewerken", 12, ACCENT);
        edit.setGravity(Gravity.CENTER);
        edit.setContentDescription("Snelkoppelingen bewerken");
        edit.setOnClickListener(v -> showLibrary(true));
        linksHeading.addView(edit, new LinearLayout.LayoutParams(dp(80), dp(48)));
        addSpaced(content, linksHeading, 19);
        JSONArray links = shortcuts();
        for (int startIndex = 0; startIndex < links.length(); startIndex += 4) {
            LinearLayout shortcutsRow = row();
            shortcutsRow.setGravity(Gravity.TOP); shortcutsRow.setMinimumHeight(dp(96));
            for (int i = startIndex; i < Math.min(startIndex + 4, links.length()); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) continue;
                String label = item.optString("title", "Website");
                addShortcut(shortcutsRow, label, item.optString("url"));
            }
            for (int i = Math.min(4, links.length() - startIndex); i < 4; i++)
                shortcutsRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
            content.addView(shortcutsRow, new LinearLayout.LayoutParams(-1, -2));
        }

        SettingsViews siteUi = new SettingsViews(this);
        siteUi.category(content, R.drawable.ic_library, getString(R.string.library_title),
                getString(R.string.library_manage), v -> showBookmarks());

        LinearLayout protection = row();
        protection.setPadding(dp(13), dp(16), dp(16), dp(16));
        protection.setBackground(shape(SURFACE, 22, BORDER));
        IconButton shield = icon("shield", "Advertentieblokkering", v -> showBlockingInfo());
        shield.ink = ACCENT;
        protection.addView(shield, new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout copy = column();
        copy.setPadding(dp(12), 0, 0, 0);
        TextView label = text(blocking ? "Minder advertenties. Meer rust." : "Adblocker staat uit", 14, TEXT);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(label);
        blockedSummary = text("", 11, MUTED);
        addSpaced(copy, blockedSummary, 5);
        protection.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        protection.setOnClickListener(v -> showBlockingInfo());
        addSpaced(content, protection, 12);
        TextView https = text(httpsOnly ? "●  Alleen HTTPS ingeschakeld" : "●  HTTPS heeft de voorkeur", 12, MUTED);
        https.setPadding(dp(4), dp(16), dp(4), dp(8));
        https.setOnClickListener(v -> showHttpsChoice());
        content.addView(https);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private void addShortcut(LinearLayout parent, String label, String url) {
        LinearLayout shortcut = column();
        shortcut.setGravity(Gravity.CENTER_HORIZONTAL);
        shortcut.setContentDescription(label + " openen");
        TextView tile = new SettingsViews(this).monogram(label, url);
        shortcut.addView(tile, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView caption = text(label, 11, MUTED);
        caption.setMaxLines(2); caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER);
        addSpaced(shortcut, caption, 9);
        shortcut.setFocusable(true);
        shortcut.setOnClickListener(v -> navigate(url));
        shortcut.setOnLongClickListener(v -> { showLibrary(true); return true; });
        parent.addView(shortcut, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void navigate(String input) {
        String url = UrlPolicy.resolve(input, searchPrefix);
        if (url == null) { toast("Voer een geldig webadres of een zoekopdracht in."); return; }
        hideKeyboard();
        Tab tab = current();
        tab.home = false;
        tab.error = null;
        tab.url = url;
        tab.pageHost = host(url);
        if (!UrlPolicy.allowed(url, httpsOnly)) {
            if (tab.web != null) tab.web.stopLoading();
            tab.error = HTTPS_ERROR;
            renderPage(); updateChrome(); persistTabs();
            return;
        }
        ensureWeb(tab);
        tab.web.onResume();
        tab.web.loadUrl(url);
        renderPage();
        updateChrome();
        persistTabs();
    }

    private void ensureWeb(Tab tab) { ensureWeb(tab, false); }

    private void ensureWeb(Tab tab, boolean openedByEngine) {
        if (tab.web != null && tab.web.isClosed()) { dispose(tab, false); }
        if (tab.web != null) return;
        BrowserView web = new BrowserView(this, new BrowserView.Listener() {
            @Override public boolean allowNavigation(String url, boolean gesture) {
                String valid = UrlPolicy.normalizeWebUrl(url);
                if (valid == null) {
                    if ("about:blank".equals(url) || url.startsWith("moz-extension://")) return true;
                    if (gesture) openExternal(Uri.parse(url));
                    return false;
                }
                if (!UrlPolicy.allowed(valid, httpsOnly)) {
                    tab.url = valid; tab.pageHost = host(valid); tab.error = HTTPS_ERROR;
                    if (tab == current()) { renderPage(); updateChrome(); }
                    return false;
                }
                return true;
            }
            @Override public void locationChanged(String url) {
                if (UrlPolicy.normalizeWebUrl(url) == null && !url.startsWith("moz-extension://")) return;
                tab.url = url; tab.pageHost = host(url); tab.error = null;
                if (tab == current() && !tab.home) { renderPage(); updateChrome(); }
            }
            @Override public void progressChanged(int value) {
                if (tab != current() || tab.home) return;
                if (value >= 0) {
                    progress.setProgress(value);
                    progress.setVisibility(value < 100 && tab.error == null ? View.VISIBLE : View.INVISIBLE);
                }
                updateChrome();
            }
            @Override public void titleChanged(String title) { if (!title.isEmpty()) tab.title = title; }
            @Override public void finished() {
                if (tab == current()) { updateChrome(); updateBlockedSummary(); }
                persistTabs();
            }
            @Override public void failed(String message) {
                tab.error = message;
                if (tab == current()) { renderPage(); updateChrome(); }
            }
            @Override public GeckoSession newSession(String url) { return createExtensionTab(url); }
            @Override public void chooseFile(String[] types, boolean multiple, ValueCallback<Uri[]> callback) {
                cancelFilePicker(); fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
                if (types != null && types.length > 0) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
                try { startActivityForResult(intent, FILE_PICKER); }
                catch (ActivityNotFoundException unavailable) { cancelFilePicker(); toast("Geen bestandskiezer beschikbaar."); }
            }
        }, openedByEngine);
        tab.web = web;
        attachExtensions(web);
        if (tab.savedWebState != null) {
            if (!web.restoreState(tab.savedWebState) && !tab.url.isEmpty()) web.loadUrl(tab.url);
            tab.savedWebState = null;
        }
    }

    private GeckoSession createExtensionTab(String uri) {
        if (tabs.size() >= MAX_TABS) return null;
        if (uri != null && !uri.startsWith("moz-extension://") && !UrlPolicy.allowed(uri, httpsOnly)) return null;
        Tab added = new Tab(); added.home = false; added.url = uri == null ? "" : uri;
        for (Tab tab : tabs) if (tab.web != null) {
            tab.web.onPause(); engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false);
        }
        tabs.add(added); selected = tabs.size() - 1; ensureWeb(added, true);
        added.web.onResume(); trimResidentTabs(); renderPage(); updateChrome(); persistTabs();
        return added.web.session;
    }

    private void attachExtensions(BrowserView web) {
        engine.runtime.getWebExtensionController().list().accept(extensions -> {
            if (destroyed || web.isClosed()) return;
            for (WebExtension extension : extensions) {
                if (extension.isBuiltIn) continue;
                extensionUi.bind(extension);
                web.session.getWebExtensionController().setActionDelegate(extension, extensionUi.actions);
                web.session.getWebExtensionController().setTabDelegate(extension, new WebExtension.SessionTabDelegate() {
                    @Override public GeckoResult<AllowOrDeny> onCloseTab(WebExtension source, GeckoSession session) {
                        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).web == web) {
                            closeTab(i); return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY);
                    }
                    @Override public GeckoResult<AllowOrDeny> onUpdateTab(WebExtension source, GeckoSession session,
                            WebExtension.UpdateTabDetails details) {
                        if (Boolean.TRUE.equals(details.active)) {
                            for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).web == web) { selected = i; showSelected(); break; }
                        }
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                    }
                });
            }
        }, failure -> toast("Extensies konden niet worden gekoppeld."));
    }

    private void showSelected() {
        for (Tab tab : tabs) if (tab.web != null && tab != current()) {
            tab.web.onPause();
            engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false);
        }
        Tab tab = current();
        tab.used = ++clock;
        if (tab.home) {
            if (tab.web != null) { tab.web.onPause(); engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false); }
            engine.activeSession = null;
        }
        if (!tab.home) {
            if (!tab.url.startsWith("moz-extension://") && !UrlPolicy.allowed(tab.url, httpsOnly)) {
                tab.error = HTTPS_ERROR;
                renderPage(); updateChrome(); persistTabs(); return;
            }
            boolean fresh = tab.web == null && tab.savedWebState == null;
            ensureWeb(tab);
            tab.web.onResume();
            if (fresh && !tab.url.isEmpty()) tab.web.loadUrl(tab.url);
        }
        trimResidentTabs();
        renderPage();
        updateChrome();
        persistTabs();
    }

    private void renderPage() {
        Tab tab = current();
        View desired;
        if (tab.home) {
            if (home == null) home = buildHome();
            desired = home;
            updateBlockedSummary();
        } else if (tab.error != null) {
            LinearLayout message = column();
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(32), dp(24), dp(32), dp(24));
            boolean policyBlocked = HTTPS_ERROR.equals(tab.error);
            TextView title = text(policyBlocked ? "Deze pagina gebruikt HTTP." : "Even geen verbinding.", 28, TEXT);
            title.setGravity(Gravity.CENTER);
            message.addView(title);
            TextView body = text(tab.error, 15, MUTED);
            body.setGravity(Gravity.CENTER);
            addSpaced(message, body, 16);
            TextView retry = action(policyBlocked ? "Probeer met HTTPS" : "Opnieuw proberen", v -> {
                navigate(policyBlocked ? UrlPolicy.withScheme(tab.url, true) : tab.url);
            });
            addSpaced(message, retry, 24);
            error = message;
            desired = error;
        } else {
            desired = tab.web;
        }
        if (page.getChildCount() == 1 && page.getChildAt(0) == desired) return;
        page.removeAllViews();
        if (desired != null) {
            if (desired.getParent() instanceof ViewGroup) ((ViewGroup) desired.getParent()).removeView(desired);
            page.addView(desired, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private void updateChrome() {
        Tab tab = current();
        if (!address.hasFocus()) address.setText(tab.home ? "" : tab.url);
        connection.setText(tab.home ? "V" : (tab.error != null ? "!" : (tab.url.startsWith("https://") ? "●" : "HTTP")));
        connection.setTextColor(tab.home || tab.url.startsWith("https://") ? ACCENT : 0xFFFFC17A);
        tabCount.setText(String.valueOf(tabs.size()));
        tabCount.getParent().requestLayout();
        boolean canBack = !tab.home;
        back.setEnabled(canBack);
        back.setAlpha(canBack ? 1f : 0.35f);
        boolean canForward = tab.web != null && tab.web.canGoForward();
        forward.setEnabled(canForward);
        forward.setAlpha(canForward ? 1f : 0.35f);
        if (tab.home || tab.error != null) progress.setVisibility(View.INVISIBLE);
    }

    private void updateBlockedSummary() {
        if (blockedSummary == null || tabs.isEmpty()) return;
        int total = engine == null ? 0 : engine.blockedCount();
        blockedSummary.setText(blocking ? total + (total == 1 ? " verzoek geblokkeerd in deze sessie" : " verzoeken geblokkeerd in deze sessie")
                : "Via het menu kun je blokkering aanzetten");
    }

    private void newTab() {
        if (tabs.size() >= MAX_TABS) { toast("Maximaal 12 tabbladen. Sluit eerst een tabblad."); return; }
        cancelFilePicker();
        hideKeyboard();
        tabs.add(new Tab());
        selected = tabs.size() - 1;
        showSelected();
    }

    private void showTabs() {
        hideKeyboard();
        List<BrowserSheets.TabItem> items = new ArrayList<>();
        for (Tab tab : tabs) items.add(new BrowserSheets.TabItem(tab.title, tab.url, tab.home));
        BrowserSheets.tabs(this, items, selected, MAX_TABS, position -> {
            cancelFilePicker(); selected = position; showSelected();
        }, position -> { closeTab(position); showTabs(); }, this::newTab).show();
    }

    private void closeTab(int position) {
        cancelFilePicker();
        Tab tab = tabs.remove(position);
        dispose(tab, false);
        if (tabs.isEmpty()) tabs.add(new Tab());
        if (position < selected) selected--;
        selected = Math.min(selected, tabs.size() - 1);
        showSelected();
    }

    private void showMenu() {
        hideKeyboard(); Tab tab = current();
        boolean pageOpen = !tab.home && tab.error == null && UrlPolicy.normalizeWebUrl(tab.url) != null;
        BrowserSheets.menu(this, pageOpen, new SavedSitesStore(preferences).isBookmarked(tab.url), host(tab.url), which -> {
            switch (which) {
                case 0: newTab(); break;
                case 1: addBookmark(); break;
                case 2: showBookmarks(); break;
                case 3: sharePage(); break;
                case 4: findOnPage(); break;
                case 5: showBlockingInfo(); break;
                case 6: showConnection(); break;
                case 7: showSettings(); break;
                case 8: showCredentials(); break;
                case 9: startActivity(new Intent(this, ExtensionsActivity.class)); break;
                default: break;
            }
        }).show();
    }

    private void showBlockingInfo() {
        Tab tab = current();
        String site = tab.pageHost.toLowerCase(java.util.Locale.ROOT);
        AlertDialog.Builder builder = dialogBuilder().setTitle("Meer rust op het web")
                .setMessage("De adblocker staat " + (blocking ? "aan" : "uit") + ".\n\n"
                        + TrackerBlocker.size() + " lokale domeinregels en het verbergen van bekende advertentievakken. "
                        + "De hoofdpagina zelf wordt niet geblokkeerd. Dit blokkeert niet elke advertentie, zoals veel videoadvertenties.\n\n"
                        + (tab.home ? "Open een website om een uitzondering toe te voegen."
                        : site + "\n" + engine.blockedCount() + " verzoeken geblokkeerd in deze sessie.\n"
                        + (adExceptions.contains(site) ? "Blokkering staat uit voor deze website." : "Blokkering volgt je algemene instelling.")))
                .setNegativeButton("Sluiten", null)
                .setNeutralButton("Instellingen", (d, which) -> showSettings());
        if (!tab.home && !site.isEmpty()) {
            builder.setPositiveButton(adExceptions.contains(site) ? "Uitzondering wissen" : "Hier uitzetten", (d, which) -> {
                Set<String> exceptions = new HashSet<>(adExceptions);
                if (!exceptions.remove(site)) exceptions.add(site);
                preferences.edit().putStringSet("ad_exceptions", exceptions).apply();
                readSettings(); engine.updatePreferences();
                if (!blocking && !exceptions.contains(site)) toast("De algemene adblocker staat nog uit.");
                if (tab.web != null) { tab.error = null; tab.web.reload(); }
            });
        }
        builder.show();
    }

    private void showConnection() {
        Tab tab = current();
        if (tab.home) { showHttpsChoice(); return; }
        String message = tab.error != null ? "Deze pagina kon niet worden geladen.\n\n"
                : tab.url.startsWith("https://") ? "HTTPS versleutelt deze verbinding. Dat garandeert niet dat de website betrouwbaar is.\n\n"
                : "HTTP is niet versleuteld.\n\n";
        AlertDialog.Builder builder = dialogBuilder().setTitle(host(tab.url)).setMessage(message
                + (httpsOnly ? "Alleen HTTPS staat aan. Er is geen automatische terugval naar HTTP."
                : "HTTPS heeft de voorkeur. Je kunt HTTP zelf kiezen als een website dat nodig heeft."))
                .setNegativeButton("Sluiten", null)
                .setNeutralButton("Voorkeur", (d, which) -> showHttpsChoice());
        if (!tab.url.startsWith("https://")) builder.setPositiveButton("Probeer HTTPS", (d, which) -> navigate(UrlPolicy.withScheme(tab.url, true)));
        else if (!httpsOnly) builder.setPositiveButton("Kies HTTP", (d, which) ->
                dialogBuilder().setTitle("Openen zonder versleuteling?")
                        .setMessage("De verbinding wordt HTTP. Gebruik dit alleen als deze website dat nodig heeft.")
                        .setPositiveButton("Open met HTTP", (again, action) -> navigate(UrlPolicy.withScheme(tab.url, false)))
                        .setNegativeButton("Annuleren", null).show());
        builder.show();
    }

    private void showHttpsChoice() {
        dialogBuilder().setTitle("HTTPS-voorkeur")
                .setSingleChoiceItems(new String[] { "Voorkeur HTTPS · expliciete HTTP-links toegestaan", "Alleen HTTPS · HTTP blokkeren" }, httpsOnly ? 1 : 0, (dialog, which) -> {
                    preferences.edit().putBoolean("https_only", which == 1).apply();
                    dialog.dismiss(); settingsChanged();
                }).setNegativeButton("Sluiten", null).show();
    }

    private void showSettings() { showSettings(SettingsSection.OVERVIEW); }

    private void showSettings(SettingsSection section) {
        hideKeyboard();
        startActivityForResult(SettingsActivity.intent(this, section), SETTINGS_PAGE);
    }

    private void setting(LinearLayout parent, String label, String detail, View.OnClickListener listener) {
        LinearLayout item = row(); item.setPadding(dp(15), dp(14), dp(12), dp(14));
        item.setBackground(shape(SURFACE, 17, BORDER));
        LinearLayout copy = column();
        TextView title = text(label, 15, TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); copy.addView(title);
        TextView subtitle = text(detail, 12, MUTED); addSpaced(copy, subtitle, 5);
        item.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 24, ACCENT); arrow.setGravity(Gravity.CENTER);
        item.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(32)));
        item.setOnClickListener(listener); item.setFocusable(true);
        addSpaced(parent, item, 8);
    }

    private JSONArray shortcuts() { return new ShortcutStore(preferences).toJson(); }

    private void showCredentials() {
        LinearLayout list = column(); list.setPadding(dp(20), dp(4), dp(20), dp(12));
        TextView info = text("Gebruik de wachtwoordmanager die je in Android hebt ingesteld, bijvoorbeeld Google Password Manager, Bitwarden of 1Password. "
                + "Veyra bewaart zelf geen wachtwoorden of passkeys.", 14, MUTED);
        info.setLineSpacing(dp(4), 1); list.addView(info);
        AlertDialog dialog = dialogBuilder().setTitle("Wachtwoorden en passkeys").setView(wrapScroll(list))
                .setNegativeButton("Sluiten", null).create();
        setting(list, "Wachtwoord invullen", "Selecteer eerst een inlogveld op de website", v -> {
            dialog.dismiss(); Tab tab = current();
            if (tab.home || tab.web == null || tab.error != null) { toast("Open eerst een inlogpagina en selecteer een inlogveld."); return; }
            AutofillManager manager = getSystemService(AutofillManager.class);
            if (manager == null || !manager.isEnabled()) {
                dialogBuilder().setTitle("Kies je wachtwoordmanager")
                        .setMessage("Kies je wachtwoordmanager als dienst voor automatisch invullen in Android.")
                        .setPositiveButton("Autofill-instellingen", (d, w) -> openPasswordSettings()).setNegativeButton("Sluiten", null).show();
            } else { tab.web.requestFocus(); manager.requestAutofill(tab.web); }
        });
        setting(list, "Je wachtwoordmanager instellen", "Kies je dienst voor automatisch invullen", v -> { dialog.dismiss(); openPasswordSettings(); });
        setting(list, "Inloggen met een externe passkey", "Open deze website in een ondersteunde browser", v -> { dialog.dismiss(); externalPasskeyLogin(); });
        TextView note = text("Voor passkeys op willekeurige websites is extra browsergoedkeuring nodig. Deze versie gebruikt daarvoor een externe browser. "
                + "De login blijft in die browser; de sessie wordt niet naar Veyra overgezet. Automatisch invullen hangt af van je manager en de website.", 12, MUTED);
        addSpaced(list, note, 18);
        dialog.show();
    }

    private void openPasswordSettings() {
        if (!AutofillSettings.open(this)) toast(getString(R.string.settings_passwords_unavailable));
    }

    private void externalPasskeyLogin() {
        Tab tab = current();
        if (tab.home || tab.error != null || !tab.url.startsWith("https://")) { toast("Open eerst een HTTPS-inlogpagina."); return; }
        String url = UrlPolicy.normalizeWebUrl(tab.url);
        if (url == null) return;
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> browsers = new ArrayList<>();
        for (ResolveInfo match : getPackageManager().queryIntentActivities(probe, 0)) {
            if (match.activityInfo != null && match.activityInfo.exported
                    && !getPackageName().equals(match.activityInfo.packageName)) browsers.add(match);
        }
        if (browsers.isEmpty()) { toast("Geen andere browser gevonden. Installeer een browser met passkey-ondersteuning."); return; }
        String[] labels = new String[browsers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = browsers.get(i).loadLabel(getPackageManager()).toString();
        dialogBuilder().setTitle("Login openen in andere browser").setItems(labels, (dialog, which) -> {
            ResolveInfo browser = browsers.get(which);
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                    .setClassName(browser.activityInfo.packageName, browser.activityInfo.name);
            try { startActivity(open); } catch (ActivityNotFoundException | SecurityException unavailable) { toast("Deze browser is niet beschikbaar."); }
        }).setNegativeButton("Annuleren", null).show();
    }

    private void addBookmark() {
        Tab tab = current();
        if (tab.home || UrlPolicy.normalizeWebUrl(tab.url) == null) { toast("Open eerst een website."); return; }
        SavedSitesStore sites = new SavedSitesStore(preferences);
        if (sites.isBookmarked(tab.url)) { toast("Deze bladwijzer bestaat al."); return; }
        String title = tab.title == null || tab.title.trim().isEmpty() ? host(tab.url) : tab.title.trim();
        if (title.length() > 40) title = title.substring(0, Character.isHighSurrogate(title.charAt(39)) ? 39 : 40);
        try { sites.saveBookmark(-1, title, tab.url); toast("Bladwijzer opgeslagen"); home = null; }
        catch (IllegalArgumentException failure) { toast("Opslaan is niet gelukt."); }
    }

    private void showBookmarks() { showLibrary(false); }
    private void showLibrary(boolean startPage) {
        startActivityForResult(new Intent(this, LibraryActivity.class)
                .putExtra(LibraryActivity.EXTRA_START_PAGE, startPage).putExtra(LibraryActivity.EXTRA_RETURN_URL, true), LIBRARY_PAGE);
    }

    private void sharePage() {
        if (current().home) { toast("Open eerst een website."); return; }
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, current().url);
        startActivity(Intent.createChooser(share, "Pagina delen"));
    }

    private void findOnPage() {
        Tab tab = current();
        if (tab.home || tab.web == null) { toast("Open eerst een website."); return; }
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Welk woord zoek je?");
        dialogBuilder().setTitle("Zoeken op pagina").setView(query)
                .setPositiveButton("Zoeken", (dialog, which) -> {
                    String term = query.getText().toString();
                    if (term.isEmpty() || tab.web == null) return;
                    tab.web.session.getFinder().find(term, GeckoSession.FINDER_FIND_FORWARD).accept(found -> {
                        if (destroyed || tab != current() || tab.web == null) return;
                        if (!found.found) { toast("Geen resultaten"); return; }
                        AlertDialog results = dialogBuilder().setTitle(found.total < 0 ? "Zoeken op pagina" : found.total + " resultaten")
                                .setPositiveButton("Volgende", null).setNeutralButton("Vorige", null)
                                .setNegativeButton("Gereed", null).create();
                        results.setOnShowListener(d -> {
                            results.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                                if (tab.web != null) tab.web.session.getFinder().find(null, GeckoSession.FINDER_FIND_FORWARD);
                            });
                            results.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                                if (tab.web != null) tab.web.session.getFinder().find(null, GeckoSession.FINDER_FIND_BACKWARDS);
                            });
                        });
                        results.setOnDismissListener(d -> { if (tab.web != null) tab.web.session.getFinder().clear(); });
                        results.show();
                    }, failure -> toast("Zoeken is niet gelukt."));
                }).setNegativeButton("Annuleren", null).show();
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!"mailto".equalsIgnoreCase(scheme) && !"tel".equalsIgnoreCase(scheme) && !"geo".equalsIgnoreCase(scheme)) {
            toast("Dit type link wordt nog niet ondersteund."); return;
        }
        dialogBuilder().setTitle("Andere app openen?").setMessage(uri.toString())
                .setPositiveButton("Openen", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                    catch (ActivityNotFoundException unavailable) { toast("Geen geschikte app gevonden."); }
                }).setNegativeButton("Annuleren", null).show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openIntent(intent);
    }

    private boolean openIntent(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        String url = UrlPolicy.normalizeWebUrl(intent.getData().toString());
        if (url == null) return false;
        if (tabs.size() < MAX_TABS) newTab();
        navigate(url);
        return true;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == LIBRARY_PAGE) {
            home = null;
            if (result == RESULT_OK && data != null && UrlPolicy.normalizeWebUrl(data.getStringExtra(LibraryActivity.EXTRA_URL)) != null)
                navigate(data.getStringExtra(LibraryActivity.EXTRA_URL));
            else showSelected();
        }
        if (request == SETTINGS_PAGE) {
            settingsChanged();
        }
        if (request == FILE_PICKER && fileCallback != null) {
            ValueCallback<Uri[]> callback = fileCallback;
            fileCallback = null;
            List<Uri> selectedFiles = new ArrayList<>();
            if (result == RESULT_OK && data != null) {
                if (data.getData() != null) selectedFiles.add(data.getData());
                else if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) selectedFiles.add(data.getClipData().getItemAt(i).getUri());
                }
            }
            callback.onReceiveValue(selectedFiles.isEmpty() ? null : selectedFiles.toArray(new Uri[0]));
        }
    }

    private void cancelFilePicker() {
        if (fileCallback == null) return;
        fileCallback.onReceiveValue(null);
        fileCallback = null;
    }

    private void goBack() {
        if (address.hasFocus()) { hideKeyboard(); updateChrome(); return; }
        Tab tab = current();
        if (!tab.home && tab.web != null && tab.web.canGoBack()) {
            tab.error = null; tab.web.goBack(); renderPage();
        } else if (!tab.home) {
            if (tab.web != null) { tab.web.stopLoading(); tab.web.onPause(); }
            tab.home = true; showSelected();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onPause() {
        super.onPause();
        if (!tabs.isEmpty() && current().web != null) current().web.onPause();
        persistTabs();
        if (engine != null) engine.clearPromptActivity(this);
        if (extensionUi != null) extensionUi.suspend();
    }

    @Override protected void onResume() {
        super.onResume();
        if (engine != null) {
            engine.setPromptActivity(this); extensionUi.attach();
            for (Tab tab : tabs) if (tab.web != null) attachExtensions(tab.web);
        }
        if (!tabs.isEmpty() && current().web != null && !current().home) current().web.onResume();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            for (Tab tab : tabs) if (tab != current() && tab.web != null) dispose(tab, true);
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        cancelFilePicker();
        if (engine != null) engine.removeBlockedListener(blockedListener);
        if (extensionUi != null) extensionUi.destroy();
        for (Tab tab : tabs) dispose(tab, false);
        super.onDestroy();
    }

    private void trimResidentTabs() {
        while (true) {
            int resident = 0;
            Tab oldest = null;
            for (Tab tab : tabs) if (tab.web != null) {
                resident++;
                if (tab != current() && (oldest == null || tab.used < oldest.used)) oldest = tab;
            }
            if (resident <= MAX_RESIDENT || oldest == null) return;
            dispose(oldest, true);
        }
    }

    private void dispose(Tab tab, boolean saveState) {
        if (tab.web == null) return;
        if (saveState) {
            tab.savedWebState = new Bundle();
            tab.web.saveState(tab.savedWebState);
        } else tab.savedWebState = null;
        if (tab.web.getParent() instanceof ViewGroup) ((ViewGroup) tab.web.getParent()).removeView(tab.web);
        if (!tab.web.isClosed()) tab.web.stopLoading();
        tab.web.destroy();
        tab.web = null;
    }

    private void restoreTabs() {
        try {
            JSONArray saved = new JSONArray(preferences.getString("tabs", "[]"));
            for (int i = 0; i < Math.min(saved.length(), MAX_TABS); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                Tab tab = new Tab();
                String valid = UrlPolicy.normalizeWebUrl(item.optString("url"));
                tab.url = valid == null ? "" : valid;
                tab.pageHost = host(tab.url);
                tab.home = item.optBoolean("home", true) || valid == null;
                tab.title = item.optString("title", "Tabblad");
                tabs.add(tab);
            }
        } catch (Exception invalid) { tabs.clear(); }
        if (tabs.isEmpty()) tabs.add(new Tab());
        selected = Math.max(0, Math.min(preferences.getInt("selected", 0), tabs.size() - 1));
    }

    private void persistTabs() {
        if (destroyed) return;
        try {
            JSONArray saved = new JSONArray();
            for (Tab tab : tabs) {
                JSONObject item = new JSONObject();
                item.put("url", tab.url); item.put("title", tab.title); item.put("home", tab.home);
                saved.put(item);
            }
            preferences.edit().putString("tabs", saved.toString()).putInt("selected", selected).apply();
        } catch (Exception ignored) { /* A page must remain usable if a session cannot be saved. */ }
    }

    private Tab current() { return tabs.get(selected); }
    private String host(String url) {
        String value = Uri.parse(url).getHost();
        return value == null ? "" : value;
    }
    private void focusAddress() {
        address.requestFocus(); address.selectAll();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }
    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus(); page.setFocusableInTouchMode(true); page.requestFocus();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private void addSpaced(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin); parent.addView(child, params);
    }
    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private ScrollView wrapScroll(View content) {
        ScrollView scroll = new ScrollView(this); scroll.addView(content); return scroll;
    }
    private TextView action(String label, View.OnClickListener listener) {
        TextView button = text(label, 15, ACCENT_INK); button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), dp(16), dp(20), dp(16));
        button.setBackground(shape(ACCENT, 28, Color.TRANSPARENT)); button.setOnClickListener(listener); return button;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private IconButton icon(String kind, String description, View.OnClickListener listener) {
        IconButton button = new IconButton(kind); button.setContentDescription(description);
        button.setOnClickListener(listener); return button;
    }

    private final class IconButton extends View {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        int ink = TEXT;
        IconButton(String kind) {
            super(BrowserActivity.this); this.kind = kind; setFocusable(true); setClickable(true);
            setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33888888), null,
                    shape(Color.WHITE, 24, Color.TRANSPARENT)));
        }
        @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            float size = dp(23);
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(ink); paint.setStrokeWidth(1.8f); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch (kind) {
                case "back": path.moveTo(14, 5); path.lineTo(7, 12); path.lineTo(14, 19); break;
                case "forward": path.moveTo(10, 5); path.lineTo(17, 12); path.lineTo(10, 19); break;
                case "home":
                    path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10); path.lineTo(21, 21);
                    path.lineTo(15, 21); path.lineTo(15, 14); path.lineTo(9, 14); path.lineTo(9, 21);
                    path.lineTo(3, 21); path.close(); break;
                case "reload":
                    canvas.drawArc(4, 4, 20, 20, 35, 290, false, paint);
                    path.moveTo(20, 3); path.lineTo(20, 9); path.lineTo(14, 9); break;
                case "menu":
                    canvas.drawLine(4, 6, 20, 6, paint); canvas.drawLine(4, 12, 20, 12, paint);
                    canvas.drawLine(4, 18, 20, 18, paint); break;
                case "tune":
                    canvas.drawLine(3, 7, 21, 7, paint); canvas.drawLine(3, 17, 21, 17, paint);
                    paint.setStyle(Paint.Style.FILL); canvas.drawCircle(8, 7, 3, paint); canvas.drawCircle(16, 17, 3, paint); break;
                case "close":
                    path.moveTo(6, 6); path.lineTo(18, 18); path.moveTo(18, 6); path.lineTo(6, 18); break;
                case "shield":
                    path.moveTo(12, 2); path.lineTo(21, 6); path.lineTo(20, 15); path.quadTo(18, 20, 12, 23);
                    path.quadTo(6, 20, 4, 15); path.lineTo(3, 6); path.close();
                    path.moveTo(8, 12); path.lineTo(11, 15); path.lineTo(16, 9); break;
                default: break;
            }
            canvas.drawPath(path, paint); canvas.restore();
        }
    }
}
