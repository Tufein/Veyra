package nl.veyra.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
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
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.ContextThemeWrapper;
import android.view.autofill.AutofillManager;
import android.provider.Settings;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Native browser chrome; the installed Android System WebView supplies the engine. */
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

    private final List<Tab> tabs = new ArrayList<>();
    private final List<WebView> pendingPopups = new ArrayList<>();
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
    private boolean engineInitialized;

    private static final class Tab {
        volatile String url = "";
        volatile String pageHost = "";
        String title = "Nieuw tabblad";
        boolean home = true;
        String error;
        WebView web;
        Bundle savedWebState;
        long used;
        final AtomicInteger blocked = new AtomicInteger();
    }

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("veyra", MODE_PRIVATE);
        readSettings();
        super.onCreate(state);
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
        root.requestFocus();
    }

    private void readSettings() {
        String theme = preferences.getString("theme", "dark");
        light = "light".equals(theme) || ("system".equals(theme)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES);
        BG = light ? 0xFFF5F6F2 : 0xFF0D1212;
        SURFACE = light ? Color.WHITE : 0xFF192222;
        BORDER = light ? 0xFFE1E6DE : 0xFF2A3836;
        TEXT = light ? 0xFF182421 : 0xFFF0F6F1;
        MUTED = light ? 0xFF5B6D65 : 0xFF9AAFA5;
        String accent = preferences.getString("accent", "mint");
        switch (accent) {
            case "violet": ACCENT = light ? 0xFF6C46BC : 0xFFCCB6FF; break;
            case "blue": ACCENT = light ? 0xFF245FA7 : 0xFFA3CCFF; break;
            case "peach": ACCENT = light ? 0xFFA74426 : 0xFFFFBC9E; break;
            default: ACCENT = light ? 0xFF28714E : 0xFFB4F5CC; break;
        }
        ACCENT_INK = light ? Color.WHITE : 0xFF10251B;
        blocking = preferences.getBoolean("blocking", true);
        httpsOnly = preferences.getBoolean("https_only", false);
        adExceptions = Collections.unmodifiableSet(new HashSet<>(preferences.getStringSet("ad_exceptions", Collections.emptySet())));
        switch (preferences.getString("search", "duckduckgo")) {
            case "google": searchPrefix = "https://www.google.com/search?q="; break;
            case "brave": searchPrefix = "https://search.brave.com/search?q="; break;
            case "ecosia": searchPrefix = "https://www.ecosia.org/search?q="; break;
            default: searchPrefix = "https://duckduckgo.com/?q="; break;
        }
        setTheme(light ? R.style.Theme_Veyra_Light : R.style.Theme_Veyra);
    }

    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(new ContextThemeWrapper(this, light
                ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert));
    }

    private void settingsChanged() {
        boolean previousPolicy = httpsOnly;
        readSettings();
        for (Tab tab : tabs) {
            if (previousPolicy != httpsOnly) {
                dispose(tab, false);
                tab.savedWebState = null;
            } else if (tab.web != null) {
                tab.web.getSettings().setTextZoom(preferences.getInt("text_zoom", 100));
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
        heading.addView(icon("tune", "Veyra aanpassen", v -> showSettings()),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(heading);

        String nickname = preferences.getString("nickname", "").trim();
        TextView greeting = text(nickname.isEmpty() ? "EEN NIEUWE DAG. EEN NIEUWE ONTDEKKING." : "FIJN DAT JE ER BENT, " + nickname.toUpperCase(java.util.Locale.ROOT), 10, MUTED);
        greeting.setLetterSpacing(0.10f);
        addSpaced(content, greeting, 28);

        boolean compact = preferences.getBoolean("compact_home", false);
        LinearLayout hero = column();
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { light ? 0xFFE2EDE5 : 0xFF243C33, SURFACE });
        gradient.setCornerRadius(dp(28));
        gradient.setStroke(dp(1), BORDER);
        hero.setBackground(gradient);
        LinearLayout heroTop = row();
        TextView tag = text("RUIMTE VOOR JOU", 10, ACCENT);
        tag.setLetterSpacing(0.17f);
        heroTop.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        TextView spark = text("✳", 38, ACCENT);
        heroTop.addView(spark);
        hero.addView(heroTop);
        TextView title = text("Het web.\nOp jouw tempo.", compact ? 29 : 40, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(-0.055f);
        title.setLineSpacing(0, 0.98f);
        addSpaced(hero, title, compact ? 2 : 12);
        if (!compact) {
            TextView sub = text("Van een kleine vraag naar iets groots.\nWaar wil je vandaag naartoe?", 14, MUTED);
            sub.setLineSpacing(dp(4), 1);
            addSpaced(hero, sub, 17);
        }
        TextView start = action("Zoek iets nieuws   ↗", v -> focusAddress());
        addSpaced(hero, start, compact ? 16 : 24);
        addSpaced(content, hero, 17);

        LinearLayout linksHeading = row();
        TextView quickLabel = text("FAVORIETE PLEKKEN", 10, MUTED);
        quickLabel.setLetterSpacing(0.13f);
        linksHeading.addView(quickLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = text("Bewerken", 12, ACCENT);
        edit.setGravity(Gravity.CENTER);
        edit.setContentDescription("Snelkoppelingen bewerken");
        edit.setOnClickListener(v -> showShortcuts());
        linksHeading.addView(edit, new LinearLayout.LayoutParams(dp(88), dp(48)));
        addSpaced(content, linksHeading, 19);
        JSONArray links = shortcuts();
        for (int startIndex = 0; startIndex < links.length(); startIndex += 4) {
            LinearLayout shortcutsRow = row();
            for (int i = startIndex; i < Math.min(startIndex + 4, links.length()); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) continue;
                String label = item.optString("title", "Website");
                addShortcut(shortcutsRow, label.isEmpty() ? "↗" : label.substring(0, label.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT),
                        label, item.optString("url"), ACCENT);
            }
            for (int i = Math.min(4, links.length() - startIndex); i < 4; i++)
                shortcutsRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
            content.addView(shortcutsRow, new LinearLayout.LayoutParams(-1, dp(96)));
        }

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

    private void addShortcut(LinearLayout parent, String letter, String label, String url, int color) {
        LinearLayout shortcut = column();
        shortcut.setGravity(Gravity.CENTER_HORIZONTAL);
        shortcut.setContentDescription(label + " openen");
        TextView tile = text(letter, 23, color);
        tile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(shape(SURFACE, 18, BORDER));
        shortcut.addView(tile, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView caption = text(label, 11, MUTED);
        caption.setGravity(Gravity.CENTER);
        addSpaced(shortcut, caption, 9);
        shortcut.setOnClickListener(v -> navigate(url));
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
        tab.blocked.set(0);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void ensureWeb(Tab tab) {
        if (tab.web != null) return;
        WebView web = new WebView(this);
        engineInitialized = true;
        tab.web = web;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(preferences.getInt("text_zoom", 100));
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String valid = UrlPolicy.normalizeWebUrl(url);
                if (valid != null) {
                    if (request.isForMainFrame() && !UrlPolicy.allowed(valid, httpsOnly)) {
                        tab.url = valid;
                        tab.pageHost = host(valid);
                        tab.error = HTTPS_ERROR;
                        if (tab == current()) { renderPage(); updateChrome(); }
                        return true;
                    }
                    if (request.isForMainFrame()) {
                        tab.url = valid;
                        tab.pageHost = host(valid);
                        tab.error = null;
                        if (tab == current()) updateChrome();
                    }
                    return false;
                }
                if (request.isForMainFrame() && request.hasGesture()) openExternal(request.getUrl());
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (UrlPolicy.normalizeWebUrl(url) != null && !UrlPolicy.allowed(url, httpsOnly)) {
                    view.stopLoading(); tab.url = url; tab.error = HTTPS_ERROR;
                    if (tab == current()) { renderPage(); updateChrome(); }
                    return;
                }
                if (UrlPolicy.normalizeWebUrl(url) != null) {
                    tab.url = url;
                    tab.pageHost = host(url);
                }
                tab.error = null;
                if (tab == current() && !tab.home) { renderPage(); updateChrome(); }
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (blockingFor(tab) && tab.error == null) hideAdContainers(view);
                if (tab == current()) { updateChrome(); updateBlockedSummary(); }
                persistTabs();
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (httpsOnly && "http".equalsIgnoreCase(request.getUrl().getScheme())) {
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "HTTPS Required",
                            Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                if (blockingFor(tab) && !request.isForMainFrame()
                        && TrackerBlocker.blocks(request.getUrl().getHost(), tab.pageHost)) {
                    tab.blocked.incrementAndGet();
                    return new WebResourceResponse("text/plain", "UTF-8", 200, "OK",
                            java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && httpsOnly && "http".equalsIgnoreCase(request.getUrl().getScheme())) {
                    tab.error = HTTPS_ERROR;
                    if (tab == current()) { renderPage(); updateChrome(); }
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError issue) {
                if (!request.isForMainFrame()) return;
                tab.error = httpsOnly && "http".equalsIgnoreCase(request.getUrl().getScheme())
                        ? HTTPS_ERROR : "Controleer je verbinding of probeer het webadres opnieuw.";
                if (tab == current()) { renderPage(); updateChrome(); }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // A renderer can be shared by several tabs; Android calls each affected view.
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy();
                tab.web = null;
                tab.savedWebState = null;
                tab.error = "Android heeft deze pagina gesloten. Tik op opnieuw proberen.";
                if (!destroyed && tab == current()) { renderPage(); updateChrome(); }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (tab == current() && !tab.home) {
                    progress.setProgress(value);
                    progress.setVisibility(value < 100 && tab.error == null ? View.VISIBLE : View.INVISIBLE);
                }
            }
            @Override public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.isEmpty()) tab.title = title;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (tab != current()) return false;
                cancelFilePicker();
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER);
                    return true;
                } catch (ActivityNotFoundException unavailable) {
                    cancelFilePicker();
                    toast("Geen bestandskiezer beschikbaar.");
                    return true;
                }
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, Message message) {
                if (!gesture || tabs.size() >= MAX_TABS) return false;
                // A temporary view receives target=_blank, then hands its URL to a normal tab.
                WebView popup = new WebView(BrowserActivity.this);
                pendingPopups.add(popup);
                popup.getSettings().setAllowFileAccess(false);
                popup.getSettings().setAllowContentAccess(false);
                popup.setWebViewClient(new WebViewClient() {
                    private boolean handled;
                    private boolean capture(String url) {
                        if (handled) return true;
                        handled = true;
                        String valid = UrlPolicy.normalizeWebUrl(url);
                        popup.stopLoading();
                        new android.os.Handler(getMainLooper()).post(() -> {
                            if (!pendingPopups.remove(popup)) return;
                            popup.destroy();
                            if (valid != null && !destroyed && tabs.size() < MAX_TABS) { newTab(); navigate(valid); }
                        });
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return capture(request.getUrl().toString());
                    }
                    @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                        if (!"about:blank".equals(url)) capture(url);
                    }
                });
                ((WebView.WebViewTransport) message.obj).setWebView(popup);
                message.sendToTarget();
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    if (pendingPopups.remove(popup)) { popup.stopLoading(); popup.destroy(); }
                }, 15000);
                return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> toast(
                "Bestanden downloaden komt in een volgende versie."));
        if (tab.savedWebState != null) {
            if (web.restoreState(tab.savedWebState) == null && !tab.url.isEmpty()) web.loadUrl(tab.url);
            tab.savedWebState = null;
        }
    }

    private void showSelected() {
        for (Tab tab : tabs) if (tab.web != null && tab != current()) tab.web.onPause();
        Tab tab = current();
        tab.used = ++clock;
        if (!tab.home) {
            if (!UrlPolicy.allowed(tab.url, httpsOnly)) {
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
        int total = 0;
        for (Tab tab : tabs) total += tab.blocked.get();
        blockedSummary.setText(blocking ? total + (total == 1 ? " verzoek geblokkeerd in open tabs" : " verzoeken geblokkeerd in open tabs")
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
        LinearLayout list = column();
        list.setPadding(dp(16), dp(4), dp(16), dp(8));
        AlertDialog dialog = dialogBuilder().setTitle("Jouw tabbladen")
                .setView(wrapScroll(list)).setPositiveButton("Nieuw tabblad", (d, which) -> newTab())
                .setNegativeButton("Gereed", null).create();
        for (int index = 0; index < tabs.size(); index++) {
            final int position = index;
            Tab tab = tabs.get(index);
            LinearLayout item = row();
            item.setPadding(dp(12), dp(6), 0, dp(6));
            item.setBackground(shape(index == selected ? SURFACE : BG, 12, index == selected ? ACCENT : BORDER));
            LinearLayout labels = column();
            TextView title = text((index == selected ? "•  " : "") + (tab.home ? "Nieuw tabblad" : tab.title), 15, TEXT);
            title.setSingleLine();
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(title);
            TextView subtitle = text(tab.home ? "Veyra startpagina" : host(tab.url), 12, MUTED);
            addSpaced(labels, subtitle, 5);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            labels.setPadding(0, dp(8), 0, dp(8));
            labels.setOnClickListener(v -> {
                cancelFilePicker(); selected = position; dialog.dismiss(); showSelected();
            });
            item.addView(icon("close", "Tabblad sluiten", v -> {
                dialog.dismiss(); closeTab(position); showTabs();
            }), new LinearLayout.LayoutParams(dp(48), dp(52)));
            addSpaced(list, item, 8);
        }
        dialog.show();
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
        String[] items = { "Nieuw tabblad", "Bladwijzer toevoegen", "Bladwijzers", "Pagina delen",
                "Zoeken op pagina", "Adblocker voor deze website", "HTTPS en verbinding",
                "Veyra aanpassen", "Wachtwoorden en passkeys", "Over Veyra" };
        dialogBuilder().setTitle("Jouw Veyra").setItems(items, (dialog, which) -> {
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
                case 9:
                    dialogBuilder().setTitle("Veyra · 0.2.0")
                            .setMessage("Het web. Op jouw tempo.\n\nMet een eigen thema, lokale advertentieblokkering en Android Autofill. "
                                    + "Tabadressen, instellingen en bladwijzers blijven op je telefoon. Je zoekopdrachten gaan naar de gekozen zoekmachine. "
                                    + "Websites en de WebView-provider verwerken hun eigen verkeer.\n\n"
                                    + "Veyra heeft geen eigen wachtwoordkluis. Externe passkey-logins openen in een andere browser. "
                                    + "Een snelheidsvoordeel tegenover Samsung Internet is nog niet gemeten.")
                            .setPositiveButton("Gereed", null).show();
                    break;
                default: break;
            }
        }).show();
    }

    private boolean blockingFor(Tab tab) {
        return blocking && !adExceptions.contains(tab.pageHost.toLowerCase(java.util.Locale.ROOT));
    }

    private void hideAdContainers(WebView web) {
        web.evaluateJavascript("(function(){if(document.getElementById('veyra-adblock-style'))return;"
                + "var s=document.createElement('style');s.id='veyra-adblock-style';"
                + "s.textContent='.adsbygoogle,.ad-slot,.advertisement,.advertentie,[data-ad-slot],iframe[id^=google_ads_iframe]"
                + "{display:none!important}';(document.head||document.documentElement).appendChild(s);})()", null);
    }

    private void showBlockingInfo() {
        Tab tab = current();
        String site = tab.pageHost.toLowerCase(java.util.Locale.ROOT);
        AlertDialog.Builder builder = dialogBuilder().setTitle("Meer rust op het web")
                .setMessage("De adblocker staat " + (blocking ? "aan" : "uit") + ".\n\n"
                        + TrackerBlocker.size() + " lokale domeinregels en het verbergen van bekende advertentievakken. "
                        + "De hoofdpagina zelf wordt niet geblokkeerd. Dit blokkeert niet elke advertentie, zoals veel videoadvertenties.\n\n"
                        + (tab.home ? "Open een website om een uitzondering toe te voegen."
                        : site + "\n" + tab.blocked.get() + " verzoeken geblokkeerd in dit tabblad.\n"
                        + (adExceptions.contains(site) ? "Blokkering staat uit voor deze website." : "Blokkering volgt je algemene instelling.")))
                .setNegativeButton("Sluiten", null)
                .setNeutralButton("Instellingen", (d, which) -> showSettings());
        if (!tab.home && !site.isEmpty()) {
            builder.setPositiveButton(adExceptions.contains(site) ? "Uitzondering wissen" : "Hier uitzetten", (d, which) -> {
                Set<String> exceptions = new HashSet<>(adExceptions);
                if (!exceptions.remove(site)) exceptions.add(site);
                preferences.edit().putStringSet("ad_exceptions", exceptions).apply();
                readSettings();
                if (!blocking && !exceptions.contains(site)) toast("De algemene adblocker staat nog uit.");
                if (tab.web != null) { tab.error = null; tab.blocked.set(0); tab.web.reload(); }
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

    private void showSettings() {
        LinearLayout list = column();
        list.setPadding(dp(20), dp(4), dp(20), dp(12));
        AlertDialog settings = dialogBuilder().setTitle("Maak Veyra van jou")
                .setView(wrapScroll(list)).setPositiveButton("Gereed", null).create();
        section(list, "UITSTRALING");
        setting(list, "Kleurthema", themeLabel(), v -> {
            settings.dismiss(); choose("Kleurthema", "theme", new String[] { "Donker", "Licht", "Volg Android" }, new String[] { "dark", "light", "system" }, "dark");
        });
        setting(list, "Accentkleur", accentLabel(), v -> {
            settings.dismiss(); choose("Accentkleur", "accent", new String[] { "Mint", "Lavendel", "Hemelsblauw", "Perzik" }, new String[] { "mint", "violet", "blue", "peach" }, "mint");
        });
        setting(list, "Startpagina", preferences.getBoolean("compact_home", false) ? "Compact" : "Ruimtelijk", v -> {
            preferences.edit().putBoolean("compact_home", !preferences.getBoolean("compact_home", false)).apply();
            settings.dismiss(); settingsChanged(); showSettings();
        });
        setting(list, "Jouw naam", preferences.getString("nickname", "").isEmpty() ? "Voeg een persoonlijke begroeting toe" : preferences.getString("nickname", ""), v -> {
            settings.dismiss(); EditText field = new EditText(this);
            field.setSingleLine(true); field.setText(preferences.getString("nickname", ""));
            field.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(24) });
            field.setHint("Hoe mogen we je noemen?");
            dialogBuilder().setTitle("Jouw naam").setView(field).setPositiveButton("Bewaren", (d, w) -> {
                preferences.edit().putString("nickname", field.getText().toString().trim()).apply(); settingsChanged(); showSettings();
            }).setNegativeButton("Annuleren", (d, w) -> showSettings()).show();
        });
        setting(list, "Snelkoppelingen", "Voeg je eigen websites toe", v -> { settings.dismiss(); showShortcuts(); });
        section(list, "LEZEN EN ZOEKEN");
        setting(list, "Tekstgrootte op websites", preferences.getInt("text_zoom", 100) + "%", v -> {
            settings.dismiss(); String[] sizes = { "85%", "100%", "115%", "130%", "150%" };
            int[] values = { 85, 100, 115, 130, 150 };
            int selectedSize = 1;
            for (int i = 0; i < values.length; i++) if (values[i] == preferences.getInt("text_zoom", 100)) selectedSize = i;
            dialogBuilder().setTitle("Tekstgrootte").setSingleChoiceItems(sizes, selectedSize, (d, which) -> {
                preferences.edit().putInt("text_zoom", values[which]).apply(); d.dismiss(); settingsChanged(); showSettings();
            }).setNegativeButton("Sluiten", null).show();
        });
        setting(list, "Zoekmachine", searchLabel(), v -> {
            settings.dismiss(); choose("Zoekmachine", "search", new String[] { "DuckDuckGo", "Google", "Brave Search", "Ecosia" },
                    new String[] { "duckduckgo", "google", "brave", "ecosia" }, "duckduckgo");
        });
        section(list, "BESCHERMING");
        setting(list, "Advertenties en trackers", blocking ? "Aan · " + TrackerBlocker.size() + " lokale domeinregels" : "Uit", v -> {
            preferences.edit().putBoolean("blocking", !blocking).apply();
            settings.dismiss();
            for (Tab open : tabs) { dispose(open, false); open.savedWebState = null; }
            settingsChanged(); showSettings();
        });
        setting(list, "Uitzonderingen voor websites", adExceptions.size() + " websites", v -> {
            settings.dismiss(); showAdExceptions();
        });
        setting(list, "HTTPS", httpsOnly ? "Alleen HTTPS" : "Voorkeur HTTPS", v -> { settings.dismiss(); showHttpsChoice(); });
        setting(list, "Wachtwoorden en passkeys", "Invullen via je eigen wachtwoordmanager", v -> { settings.dismiss(); showCredentials(); });
        settings.show();
    }

    private void choose(String title, String key, String[] labels, String[] values, String fallback) {
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(preferences.getString(key, fallback))) checked = i;
        dialogBuilder().setTitle(title).setSingleChoiceItems(labels, checked, (d, which) -> {
            preferences.edit().putString(key, values[which]).apply(); d.dismiss(); settingsChanged(); showSettings();
        }).setNegativeButton("Sluiten", null).show();
    }

    private void section(LinearLayout parent, String label) {
        TextView view = text(label, 10, ACCENT); view.setLetterSpacing(0.14f);
        addSpaced(parent, view, 24);
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

    private String themeLabel() {
        switch (preferences.getString("theme", "dark")) { case "light": return "Licht"; case "system": return "Volg Android"; default: return "Donker"; }
    }
    private String accentLabel() {
        switch (preferences.getString("accent", "mint")) { case "violet": return "Lavendel"; case "blue": return "Hemelsblauw"; case "peach": return "Perzik"; default: return "Mint"; }
    }
    private String searchLabel() {
        switch (preferences.getString("search", "duckduckgo")) { case "google": return "Google"; case "brave": return "Brave Search"; case "ecosia": return "Ecosia"; default: return "DuckDuckGo"; }
    }

    private void showAdExceptions() {
        if (adExceptions.isEmpty()) { dialogBuilder().setTitle("Geen uitzonderingen")
                .setMessage("Open een website en kies in het menu ‘Adblocker voor deze website’ om blokkering daar uit te zetten.")
                .setPositiveButton("Gereed", null).show(); return; }
        String[] sites = adExceptions.toArray(new String[0]); java.util.Arrays.sort(sites);
        dialogBuilder().setTitle("Tik om een uitzondering te verwijderen").setItems(sites, (d, which) -> {
            Set<String> updated = new HashSet<>(adExceptions); updated.remove(sites[which]);
            preferences.edit().putStringSet("ad_exceptions", updated).apply();
            readSettings();
            for (Tab tab : tabs) if (tab.web != null && tab.pageHost.equalsIgnoreCase(sites[which])) tab.web.reload();
            showAdExceptions();
        }).setNegativeButton("Sluiten", null).show();
    }

    private JSONArray shortcuts() {
        try {
            return new JSONArray(preferences.getString("shortcuts", "["
                    + "{\"title\":\"Wikipedia\",\"url\":\"https://nl.wikipedia.org/\"},"
                    + "{\"title\":\"NOS\",\"url\":\"https://nos.nl/\"},"
                    + "{\"title\":\"YouTube\",\"url\":\"https://m.youtube.com/\"},"
                    + "{\"title\":\"Zoeken\",\"url\":\"https://duckduckgo.com/\"}]"));
        } catch (Exception invalid) { return new JSONArray(); }
    }

    private void showShortcuts() {
        JSONArray saved = shortcuts();
        String[] titles = new String[saved.length()];
        for (int i = 0; i < titles.length; i++) titles[i] = saved.optJSONObject(i).optString("title");
        dialogBuilder().setTitle("Jouw snelkoppelingen").setItems(titles, (d, which) ->
                dialogBuilder().setTitle(titles[which]).setItems(new String[] { "Bewerken", "Verwijderen" }, (action, item) -> {
                    if (item == 0) editShortcut(which);
                    else { saved.remove(which); preferences.edit().putString("shortcuts", saved.toString()).apply();
                        home = null; renderPage(); showShortcuts(); }
                }).show())
                .setPositiveButton("Toevoegen", (d, which) -> {
                    if (saved.length() >= 8) toast("Maximaal acht snelkoppelingen."); else editShortcut(-1);
                }).setNegativeButton("Gereed", null).show();
    }

    private void editShortcut(int position) {
        JSONArray saved = shortcuts();
        LinearLayout fields = column(); fields.setPadding(dp(24), dp(8), dp(24), 0);
        EditText title = new EditText(this); title.setHint("Naam"); title.setSingleLine(true);
        title.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(18) });
        EditText url = new EditText(this); url.setHint("https://voorbeeld.nl"); url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (position >= 0) { title.setText(saved.optJSONObject(position).optString("title")); url.setText(saved.optJSONObject(position).optString("url")); }
        fields.addView(title); fields.addView(url);
        AlertDialog editor = dialogBuilder().setTitle(position < 0 ? "Snelkoppeling toevoegen" : "Snelkoppeling bewerken")
                .setView(fields).setPositiveButton("Bewaren", null).setNegativeButton("Annuleren", null).create();
        editor.setOnShowListener(d -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = title.getText().toString().trim();
            String typed = url.getText().toString().trim();
            String valid = UrlPolicy.normalizeWebUrl(typed.contains("://") ? typed : "https://" + typed);
            if (label.isEmpty()) { title.setError("Geef een naam op"); return; }
            if (valid == null) { url.setError("Voer een geldig HTTP- of HTTPS-adres in"); return; }
            try {
                JSONObject item = new JSONObject(); item.put("title", label); item.put("url", valid);
                if (position < 0) saved.put(item); else saved.put(position, item);
                preferences.edit().putString("shortcuts", saved.toString()).apply();
                home = null; renderPage(); editor.dismiss(); showShortcuts();
            } catch (Exception failure) { toast("Bewaren is niet gelukt."); }
        }));
        editor.show();
    }

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
                        .setMessage("Ga in Android naar ‘Wachtwoorden, passkeys en automatisch invullen’ en kies je manager. De naam van dit menu verschilt per telefoon.")
                        .setPositiveButton("Android-instellingen", (d, w) -> openPasswordSettings()).setNegativeButton("Sluiten", null).show();
            } else { tab.web.requestFocus(); manager.requestAutofill(tab.web); }
        });
        setting(list, "Je wachtwoordmanager instellen", "Open de Android-instellingen", v -> { dialog.dismiss(); openPasswordSettings(); });
        setting(list, "Inloggen met een externe passkey", "Open deze website in een ondersteunde browser", v -> { dialog.dismiss(); externalPasskeyLogin(); });
        TextView note = text("Voor passkeys op willekeurige websites is extra browsergoedkeuring nodig. Deze versie gebruikt daarvoor een externe browser. "
                + "De login blijft in die browser; de sessie wordt niet naar Veyra overgezet. Automatisch invullen hangt af van je manager en de website.", 12, MUTED);
        addSpaced(list, note, 18);
        dialog.show();
    }

    private void openPasswordSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (ActivityNotFoundException unavailable) { toast("Open Android-instellingen en zoek ‘automatisch invullen’."); }
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

    private JSONArray bookmarks() {
        try { return new JSONArray(preferences.getString("bookmarks", "[]")); }
        catch (Exception invalid) { return new JSONArray(); }
    }

    private void addBookmark() {
        Tab tab = current();
        if (tab.home || UrlPolicy.normalizeWebUrl(tab.url) == null) { toast("Open eerst een website."); return; }
        JSONArray saved = bookmarks();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item != null && tab.url.equals(item.optString("url"))) { toast("Deze bladwijzer bestaat al."); return; }
        }
        try {
            JSONObject item = new JSONObject();
            item.put("title", tab.title);
            item.put("url", tab.url);
            saved.put(item);
            preferences.edit().putString("bookmarks", saved.toString()).apply();
            toast("Bladwijzer opgeslagen");
        } catch (Exception failure) { toast("Opslaan is niet gelukt."); }
    }

    private void showBookmarks() {
        JSONArray saved = bookmarks();
        if (saved.length() == 0) { toast("Je hebt nog geen bladwijzers."); return; }
        LinearLayout list = column();
        list.setPadding(dp(16), 0, dp(16), dp(8));
        AlertDialog dialog = dialogBuilder().setTitle("Bladwijzers")
                .setView(wrapScroll(list)).setNegativeButton("Sluiten", null).create();
        for (int index = 0; index < saved.length(); index++) {
            int position = index;
            JSONObject item = saved.optJSONObject(index);
            if (item == null) continue;
            String url = item.optString("url");
            LinearLayout line = row();
            TextView link = text(item.optString("title", host(url)) + "\n" + host(url), 14, TEXT);
            link.setPadding(dp(8), dp(16), dp(8), dp(16));
            link.setMaxLines(3);
            link.setOnClickListener(v -> { dialog.dismiss(); navigate(url); });
            line.addView(link, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(icon("close", "Bladwijzer verwijderen", v -> {
                saved.remove(position);
                preferences.edit().putString("bookmarks", saved.toString()).apply();
                dialog.dismiss(); showBookmarks();
            }), new LinearLayout.LayoutParams(dp(48), dp(48)));
            list.addView(line);
        }
        dialog.show();
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
                    tab.web.setFindListener((index, count, done) -> {
                        if (!done || destroyed || tab != current() || tab.web == null) return;
                        tab.web.setFindListener(null);
                        if (count == 0) { toast("Geen resultaten"); return; }
                        AlertDialog results = dialogBuilder().setTitle(count + " resultaten")
                                .setPositiveButton("Volgende", null).setNeutralButton("Vorige", null)
                                .setNegativeButton("Gereed", null).create();
                        results.setOnShowListener(d -> {
                            results.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                                if (tab.web != null) tab.web.findNext(true);
                            });
                            results.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                                if (tab.web != null) tab.web.findNext(false);
                            });
                        });
                        results.setOnDismissListener(d -> { if (tab.web != null) tab.web.clearMatches(); });
                        results.show();
                    });
                    tab.web.findAllAsync(term);
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
        if (request == FILE_PICKER && fileCallback != null) {
            ValueCallback<Uri[]> callback = fileCallback;
            fileCallback = null;
            callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
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
        if (engineInitialized) CookieManager.getInstance().flush();
    }

    @Override protected void onResume() {
        super.onResume();
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
        for (WebView popup : pendingPopups) popup.destroy();
        pendingPopups.clear();
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
        tab.web.stopLoading();
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
        button.setBackground(shape(ACCENT, 18, Color.TRANSPARENT)); button.setOnClickListener(listener); return button;
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
