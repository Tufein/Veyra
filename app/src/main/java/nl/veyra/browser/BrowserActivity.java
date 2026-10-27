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
    private String httpsError() { return getString(R.string.ui_https_only_is_enabled_this_http_page_is_blocked_open_t_d00eb); }
    private static final int MAX_TABS = 12;
    private static final int MAX_RESIDENT = 3;
    private static final int FILE_PICKER = 41;
    private static final int SETTINGS_PAGE = 42;
    private static final int LIBRARY_PAGE = 43;
    private static final int DOWNLOAD_DESTINATION = 44;
    private org.mozilla.geckoview.WebResponse pendingDownload;
    private boolean pendingDownloadPrivate;
    private SitePermissionController sitePermissions;

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
    private View home, browserControls;
    private boolean fullscreen;
    private View error;
    private TextView blockedSummary, recordingNotice;
    private ValueCallback<Uri[]> fileCallback;
    private boolean destroyed, resumed;
    private VeyraRuntime engine;
    private ExtensionUi extensionUi;
    private final Runnable blockedListener = this::updateBlockedSummary;

    private static final class Tab {
        volatile String url = "";
        volatile String pageHost = "";
        String title = "";
        boolean home = true;
        boolean privateMode;
        boolean recording;
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
        sitePermissions = new SitePermissionController(this);
        engine.registerBrowser(this);
        engine.addBlockedListener(blockedListener);
        extensionUi = new ExtensionUi(this, new ExtensionUi.TabFactory() {
            public GeckoSession create(String uri) { return createExtensionTab(uri); }
            public GeckoSession create(WebExtension.CreateTabDetails details) {
                if(Boolean.TRUE.equals(details.pinned)||Boolean.TRUE.equals(details.openInReaderMode)||Boolean.TRUE.equals(details.discarded))return null;
                if(details.cookieStoreId!=null && !"firefox-default".equals(details.cookieStoreId))return null;
                return createContentTab(details.url,false,!Boolean.FALSE.equals(details.active),details.index);
            }
        });
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
        if (getIntent().getBooleanExtra("safe_mode",false)) recoverExtensions();
        else if (!openIntent(getIntent())) showSelected();
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
        recordingNotice = text("",13,ACCENT);recordingNotice.setGravity(Gravity.CENTER);recordingNotice.setPadding(dp(16),dp(10),dp(16),dp(10));
        recordingNotice.setMinHeight(dp(48));recordingNotice.setFocusable(true);recordingNotice.setVisibility(View.GONE);
        recordingNotice.setOnClickListener(v->{for(Tab tab:tabs)if(tab.recording){dispose(tab,false);tab.error=getString(R.string.recording_stopped);}renderPage();updateChrome();});
        root.addView(recordingNotice,new LinearLayout.LayoutParams(-1,-2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BG));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout bottom = column(); browserControls = bottom;
        bottom.setPadding(dp(12), dp(9), dp(12), dp(4));
        LinearLayout omnibox = row();
        omnibox.setBackground(shape(SURFACE, 24, BORDER));
        omnibox.setElevation(dp(2));
        connection = text("V", 14, ACCENT);
        connection.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connection.setGravity(Gravity.CENTER);
        connection.setContentDescription(getString(R.string.ui_connection_information_d437e));
        connection.setOnClickListener(v -> showConnection());
        omnibox.addView(connection, new LinearLayout.LayoutParams(dp(52), dp(52)));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(15);
        address.setTextColor(TEXT);
        address.setHintTextColor(MUTED);
        address.setHint(getString(R.string.ui_search_or_enter_a_web_address_6c60f));
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(0, 0, 0, 0);
        address.setSelectAllOnFocus(true);
        address.setContentDescription(getString(R.string.ui_search_or_web_address_45960));
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
        IconButton reload = icon("reload", getString(R.string.ui_reload_page_3974b), v -> {
            Tab tab = current();
            if (tab.home) focusAddress();
            else if (!UrlPolicy.allowed(tab.url, httpsOnly) || tab.web == null) navigate(tab.url);
            else { tab.error = null; tab.web.reload(); renderPage(); }
        });
        omnibox.addView(reload, new LinearLayout.LayoutParams(dp(48), dp(52)));
        bottom.addView(omnibox, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout navigation = row();
        back = icon("back", getString(R.string.ui_back_ebf6c), v -> goBack());
        forward = icon("forward", getString(R.string.ui_forward_9df38), v -> {
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
        navigation.addView(icon("home", getString(R.string.ui_start_page_a582e), v -> {
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
        tabButton.setContentDescription(getString(R.string.ui_tabs_c36a5));
        TextView countShape = tabCount;
        countShape.setBackground(shape(Color.TRANSPARENT, 7, MUTED));
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(dp(25), dp(26), Gravity.CENTER);
        tabButton.addView(countShape, countParams);
        SettingsViews.buttonRole(tabButton);tabButton.setFocusable(true);tabButton.setOnClickListener(v -> showTabs());
        navigation.addView(tabButton, weightedButton());
        navigation.addView(icon("menu", getString(R.string.ui_menu_99af6), v -> showMenu()), weightedButton());
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
        heading.addView(icon("tune", getString(R.string.ui_settings_27018), v -> showSettings()),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(heading);

        String nickname = preferences.getString("nickname", "").trim();
        TextView greeting = text(nickname.isEmpty() ? getString(R.string.ui_a_new_day_a_new_discovery_f9c79) : getString(R.string.ui_good_to_see_you_019a4) + nickname.toUpperCase(java.util.Locale.ROOT), 10, MUTED);
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
        TextView tag = text(getString(R.string.ui_space_for_you_cdeb2), 10, ACCENT);
        tag.setLetterSpacing(0.17f);
        heroTop.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        TextView spark = text("✳", 28, ACCENT);
        heroTop.addView(spark);
        hero.addView(heroTop);
        TextView title = text(getString(R.string.ui_the_web_at_your_pace_4dacc), compact ? 27 : 32, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(-0.055f);
        title.setLineSpacing(0, 0.98f);
        addSpaced(hero, title, compact ? 2 : 12);
        if (!compact) {
            TextView sub = text(getString(R.string.ui_where_would_you_like_to_go_today_a3a25), 14, MUTED);
            sub.setLineSpacing(dp(4), 1);
            addSpaced(hero, sub, 10);
        }
        TextView start = action(getString(R.string.ui_discover_something_new_526c5), v -> focusAddress());
        addSpaced(hero, start, compact ? 12 : 16);
        addSpaced(content, hero, 17);

        LinearLayout linksHeading = row();
        TextView quickLabel = text(getString(R.string.home_shortcuts), 15, TEXT);
        quickLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        linksHeading.addView(quickLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = text(getString(R.string.ui_edit_ea89b), 12, ACCENT);
        edit.setGravity(Gravity.CENTER);
        edit.setContentDescription(getString(R.string.ui_edit_shortcuts_d7fea));
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
        TextView label = text(blocking ? getString(R.string.ui_fewer_ads_more_calm_19b86) : getString(R.string.ui_ad_blocker_is_off_a6c8c), 14, TEXT);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(label);
        blockedSummary = text("", 11, MUTED);
        addSpaced(copy, blockedSummary, 5);
        protection.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        protection.setOnClickListener(v -> showBlockingInfo());
        addSpaced(content, protection, 12);
        TextView https = text(httpsOnly ? getString(R.string.ui_https_only_enabled_674fe) : getString(R.string.ui_https_preferred_7eb8e), 12, MUTED);
        https.setPadding(dp(4), dp(16), dp(4), dp(8));
        https.setOnClickListener(v -> showHttpsChoice());
        content.addView(https);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private void addShortcut(LinearLayout parent, String label, String url) {
        LinearLayout shortcut = column();
        shortcut.setGravity(Gravity.CENTER_HORIZONTAL);
        shortcut.setContentDescription(label + getString(R.string.ui_open_8679f));
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
        String url = SearchProvider.resolve(input, preferences);
        if (url == null) { toast(getString(R.string.ui_enter_a_valid_web_address_or_a_search_query_b4f3d)); return; }
        hideKeyboard();
        Tab tab = current();
        tab.home = false;
        tab.error = null;
        tab.url = url;
        tab.pageHost = host(url);
        if (!UrlPolicy.allowed(url, httpsOnly)) {
            if (tab.web != null) tab.web.stopLoading();
            tab.error = httpsError();
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
                    tab.url = valid; tab.pageHost = host(valid); tab.error = httpsError();
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
                if (!tab.home && tab.error == null) new HistoryStore(getSharedPreferences("veyra_history",MODE_PRIVATE))
                        .record(tab.url,tab.title,System.currentTimeMillis(),tab.privateMode);
                if (tab == current()) { updateChrome(); updateBlockedSummary(); }
                persistTabs();
            }
            @Override public void failed(String message) {
                tab.error = message;tab.recording=false;
                if (tab == current()) renderPage();updateChrome();
            }
            @Override public GeckoSession newSession(String url) { return createContentTab(url,tab.privateMode); }
            @Override public void download(org.mozilla.geckoview.WebResponse response) {
                if (tab != current()) { DownloadService.close(response.body); return; }
                beginDownload(response,tab.privateMode);
            }
            private boolean permissionActive() { return !destroyed && tab == current() && !tab.home && tab.web != null && !tab.web.isClosed(); }
            @Override public void androidPermissions(String[] permissions,GeckoSession.PermissionDelegate.Callback callback) {
                if(!resumed){callback.reject();return;}
                String top=SitePermissions.origin(tab.url);
                sitePermissions.androidPermissions(permissions,callback,()->permissionActive() && java.util.Objects.equals(top,SitePermissions.origin(tab.url)));
            }
            @Override public GeckoResult<Integer> contentPermission(GeckoSession.PermissionDelegate.ContentPermission permission) {
                String origin = SitePermissions.origin(permission.uri);
                return sitePermissions.content(permission,tab.privateMode,()->resumed && permissionActive() && origin != null && origin.equals(SitePermissions.origin(tab.url)));
            }
            @Override public void mediaPermission(String uri,GeckoSession.PermissionDelegate.MediaSource[] video,
                    GeckoSession.PermissionDelegate.MediaSource[] audio,GeckoSession.PermissionDelegate.MediaCallback callback) {
                String origin = SitePermissions.origin(uri),top = SitePermissions.origin(tab.url);
                sitePermissions.media(uri,video,audio,callback,tab.privateMode,()->resumed && permissionActive() && origin != null && top != null && top.equals(SitePermissions.origin(tab.url)));
            }
            @Override public void contextMenu(String link) { if(tab == current()) showLinkMenu(link,tab.privateMode); }
            @Override public void fullscreen(boolean enabled) { if(tab == current()) setFullscreen(enabled); }
            @Override public void recording(boolean camera,boolean microphone) { tab.recording=camera||microphone;updateChrome(); }
            @Override public void chooseFile(String[] types, boolean multiple, ValueCallback<Uri[]> callback) {
                cancelFilePicker(); fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
                if (types != null && types.length > 0) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
                try { startActivityForResult(intent, FILE_PICKER); }
                catch (ActivityNotFoundException unavailable) { cancelFilePicker(); toast(getString(R.string.ui_no_file_picker_is_available_7a3e5)); }
            }
        }, openedByEngine, tab.privateMode);
        tab.web = web;
        attachExtensions(web);
        if (tab.savedWebState != null) {
            if (!web.restoreState(tab.savedWebState) && !tab.url.isEmpty()) web.loadUrl(tab.url);
            tab.savedWebState = null;
        }
    }

    private GeckoSession createExtensionTab(String uri) { return createContentTab(uri,false); }
    private GeckoSession createContentTab(String uri,boolean privateMode) { return createContentTab(uri,privateMode,true,null); }
    private GeckoSession createContentTab(String uri,boolean privateMode,boolean activate,Integer requestedIndex) {
        if (tabs.size() >= MAX_TABS) return null;
        if (uri != null && !uri.startsWith("moz-extension://") && !UrlPolicy.allowed(uri, httpsOnly)) return null;
        Tab added = new Tab(); added.home = false; added.privateMode = privateMode;
        if(privateMode)engine.privateTabOpened(); added.url = uri == null ? "" : uri;
        if(activate)for (Tab tab : tabs) if (tab.web != null) {
            tab.web.onPause(); engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false);
        }
        int position=requestedIndex==null?tabs.size():Math.max(0,Math.min(requestedIndex,tabs.size()));
        tabs.add(position,added);if(activate)selected=position;else if(position<=selected)selected++;
        added.used=++clock;ensureWeb(added,true);
        if(activate)added.web.onResume();else added.web.onPause();
        GeckoSession result=added.web.session;trimResidentTabs();renderPage();updateChrome();persistTabs();return result;
    }

    private void attachExtensions(BrowserView web) {
        if (web.session.getSettings().getUsePrivateMode()) return;
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
                        if(Boolean.TRUE.equals(details.pinned)||details.muted!=null||Boolean.FALSE.equals(details.autoDiscardable))return GeckoResult.fromValue(AllowOrDeny.DENY);
                        if (details.url != null && !details.url.startsWith("moz-extension://") && !UrlPolicy.allowed(details.url,httpsOnly))
                            return GeckoResult.fromValue(AllowOrDeny.DENY);
                        if (Boolean.TRUE.equals(details.active) || Boolean.TRUE.equals(details.highlighted)) {
                            for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).web == web) { selected = i; showSelected(); break; }
                        }
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                    }
                });
            }
        }, failure -> toast(getString(R.string.ui_extensions_could_not_be_connected_e6821)));
    }

    private void showSelected() {
        for (Tab tab : tabs) if (tab.web != null && tab != current()) {
            tab.web.onPause();
            engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false);
        }
        Tab tab = current();
        updatePrivacyProtection();
        tab.used = ++clock;
        if (tab.home) {
            if (tab.web != null) { tab.web.onPause(); engine.runtime.getWebExtensionController().setTabActive(tab.web.session, false); }
            engine.activeSession = null;
        }
        if (!tab.home) {
            if (!tab.url.startsWith("moz-extension://") && !UrlPolicy.allowed(tab.url, httpsOnly)) {
                tab.error = httpsError();
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
            boolean policyBlocked = httpsError().equals(tab.error);
            TextView title = text(policyBlocked ? getString(R.string.ui_this_page_uses_http_037f4) : getString(R.string.ui_unable_to_connect_6733b), 28, TEXT);
            title.setGravity(Gravity.CENTER);
            message.addView(title);
            TextView body = text(tab.error, 15, MUTED);
            body.setGravity(Gravity.CENTER);
            addSpaced(message, body, 16);
            TextView retry = action(policyBlocked ? getString(R.string.ui_try_https_b3d5f) : getString(R.string.ui_try_again_57643), v -> {
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
        updatePrivacyProtection();
        boolean recording=tabs.stream().anyMatch(t -> t.recording);
        recordingNotice.setVisibility(recording?View.VISIBLE:View.GONE);recordingNotice.setText(R.string.recording_active);
        Tab tab = current();
        if (!address.hasFocus()) address.setText(tab.home ? "" : tab.url);
        connection.setText(tab.privateMode ? "P" : tab.home ? "V" : (tab.error != null ? "!" : (tab.url.startsWith("https://") ? "●" : "HTTP")));
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
        blockedSummary.setText(blocking ? total + (total == 1 ? getString(R.string.protection_request) : getString(R.string.protection_requests))
                : getString(R.string.ui_you_can_enable_blocking_from_the_menu_ec645));
    }

    private void newTab() { newTab(false); }
    private void newTab(boolean privateMode) {
        if (fullscreen && !tabs.isEmpty() && current().web != null) current().web.session.exitFullScreen();
        setFullscreen(false);
        if (tabs.size() >= MAX_TABS) { toast(getString(R.string.ui_maximum_of_12_tabs_close_a_tab_first_0c165)); return; }
        cancelFilePicker();
        hideKeyboard();
        Tab added = new Tab(); added.privateMode = privateMode; if(privateMode)engine.privateTabOpened(); tabs.add(added);
        selected = tabs.size() - 1;
        showSelected();
    }

    private void showTabs() {
        hideKeyboard();
        List<BrowserSheets.TabItem> items = new ArrayList<>();
        for (Tab tab : tabs) items.add(new BrowserSheets.TabItem(tab.title, tab.url, tab.home, tab.privateMode));
        BrowserSheets.tabs(this, items, selected, MAX_TABS, position -> {
            cancelFilePicker(); selected = position; showSelected();
        }, position -> { closeTab(position); showTabs(); }, this::newTab).show();
    }

    private void closeTab(int position) {
        cancelFilePicker();
        Tab tab = tabs.remove(position);
        dispose(tab, false);if(tab.privateMode)engine.privateTabClosed();
        if (tabs.isEmpty()) tabs.add(new Tab());
        if (position < selected) selected--;
        selected = Math.min(selected, tabs.size() - 1);
        consumeImportedTabs();showSelected();
    }

    private void showMenu() {
        hideKeyboard(); Tab tab = current();
        boolean pageOpen = !tab.home && tab.error == null && UrlPolicy.normalizeWebUrl(tab.url) != null;
        BrowserSheets.Sheet menu = BrowserSheets.menu(this, pageOpen, new SavedSitesStore(preferences).isBookmarked(tab.url), host(tab.url), which -> {
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
                case 10: newTab(true); break;
                case 11: startActivity(new Intent(this, HistoryActivity.class)); break;
                case 12: startActivity(new Intent(this, DownloadsActivity.class)); break;
                case 13: startActivityForResult(new Intent(this, SiteSettingsActivity.class).putExtra("url",tab.url), SETTINGS_PAGE); break;
                case 14: showDefaultBrowser(); break;
                case 15: startActivity(new Intent(this, DataSettingsActivity.class)); break;
                default: break;
            }
        });
        BrowserSheets.action(menu,R.drawable.ic_private,R.string.private_new,true,()->newTab(true));
        BrowserSheets.action(menu,R.drawable.ic_history,R.string.history_title,true,()->startActivity(new Intent(this,HistoryActivity.class)));
        BrowserSheets.action(menu,R.drawable.ic_download,R.string.downloads_title,true,()->startActivity(new Intent(this,DownloadsActivity.class)));
        BrowserSheets.action(menu,R.drawable.ic_tune,R.string.site_settings,pageOpen && !tab.privateMode,()->startActivityForResult(new Intent(this,SiteSettingsActivity.class).putExtra("url",tab.url),SETTINGS_PAGE));
        menu.show();
    }

    private void showBlockingInfo() {
        Tab tab = current();
        String site = tab.pageHost.toLowerCase(java.util.Locale.ROOT);
        AlertDialog.Builder builder = dialogBuilder().setTitle(getString(R.string.ui_a_calmer_web_f96df))
                .setMessage(getString(R.string.ui_the_ad_blocker_is_333d7) + (blocking ? getString(R.string.protection_on) : getString(R.string.protection_off)) + ".\n\n"
                        + TrackerBlocker.size() + getString(R.string.protection_rules)
                        + getString(R.string.ui_the_main_page_itself_is_not_blocked_this_does_not_bloc_8116e)
                        + (tab.home ? getString(R.string.ui_open_a_website_to_add_an_exception_d3ae5)
                        : site + "\n" + engine.blockedCount() + getString(R.string.protection_requests_detail)
                        + (engine.exception(site,tab.privateMode) ? getString(R.string.ui_blocking_is_off_for_this_website_7e3b1) : getString(R.string.ui_blocking_follows_your_general_preference_da263))))
                .setNegativeButton(getString(R.string.ui_close_5a468), null)
                .setNeutralButton(getString(R.string.ui_settings_27018), (d, which) -> showSettings());
        if (!tab.home && !site.isEmpty()) {
            builder.setPositiveButton(engine.exception(site,tab.privateMode) ? getString(R.string.ui_remove_exception_231e4) : getString(R.string.ui_turn_off_here_39996), (d, which) -> {
                Set<String> exceptions = new HashSet<>(adExceptions);
                if (!exceptions.remove(site)) exceptions.add(site);
                if(tab.privateMode) engine.togglePrivateException(site);
                else { preferences.edit().putStringSet("ad_exceptions", exceptions).apply(); readSettings(); engine.updatePreferences(); }
                if (!blocking && !exceptions.contains(site)) toast(getString(R.string.ui_the_general_ad_blocker_is_still_off_a1a9c));
                if (tab.web != null) { tab.error = null; tab.web.reload(); }
            });
        }
        builder.show();
    }

    private void showConnection() {
        Tab tab = current();
        if (tab.home) { showHttpsChoice(); return; }
        String message = tab.error != null ? getString(R.string.ui_this_page_could_not_be_loaded_893d2)
                : tab.url.startsWith("https://") ? getString(R.string.ui_https_encrypts_this_connection_it_does_not_guarantee_t_61f57)
                : getString(R.string.ui_http_is_not_encrypted_d2a0f);
        AlertDialog.Builder builder = dialogBuilder().setTitle(host(tab.url)).setMessage(message
                + (httpsOnly ? getString(R.string.ui_https_only_is_enabled_there_is_no_automatic_fallback_t_b6ef3)
                : getString(R.string.ui_https_is_preferred_you_can_choose_http_yourself_when_a_cf412)))
                .setNegativeButton(getString(R.string.ui_close_5a468), null)
                .setNeutralButton(getString(R.string.connection_preference), (d, which) -> showHttpsChoice());
        if (!tab.url.startsWith("https://")) builder.setPositiveButton(getString(R.string.ui_try_https_b3496), (d, which) -> navigate(UrlPolicy.withScheme(tab.url, true)));
        else if (!httpsOnly) builder.setPositiveButton(getString(R.string.ui_choose_http_0bc1d), (d, which) ->
                dialogBuilder().setTitle(getString(R.string.ui_open_without_encryption_6d547))
                        .setMessage(getString(R.string.ui_the_connection_will_use_http_use_this_only_when_this_w_a5b5c))
                        .setPositiveButton(getString(R.string.ui_open_with_http_012bc), (again, action) -> navigate(UrlPolicy.withScheme(tab.url, false)))
                        .setNegativeButton(getString(R.string.ui_cancel_c6a8d), null).show());
        builder.show();
    }

    private void showHttpsChoice() {
        dialogBuilder().setTitle("HTTPS-voorkeur")
                .setSingleChoiceItems(new String[] { getString(R.string.ui_prefer_https_explicit_http_links_are_allowed_8e11d), getString(R.string.ui_https_only_block_http_d8886) }, httpsOnly ? 1 : 0, (dialog, which) -> {
                    preferences.edit().putBoolean("https_only", which == 1).apply();
                    dialog.dismiss(); settingsChanged();
                }).setNegativeButton(getString(R.string.ui_close_5a468), null).show();
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
        TextView info = text(getString(R.string.ui_use_the_password_manager_configured_in_android_such_as_3d431)
                + getString(R.string.ui_veyra_does_not_store_passwords_or_passkeys_itself_37b82), 14, MUTED);
        info.setLineSpacing(dp(4), 1); list.addView(info);
        AlertDialog dialog = dialogBuilder().setTitle(getString(R.string.ui_passwords_and_passkeys_17e09)).setView(wrapScroll(list))
                .setNegativeButton(getString(R.string.ui_close_5a468), null).create();
        setting(list, getString(R.string.ui_fill_password_69897), getString(R.string.ui_select_a_login_field_on_the_website_first_64f80), v -> {
            dialog.dismiss(); Tab tab = current();
            if(tab.privateMode){toast(getString(R.string.private_autofill_disabled));return;}
            if (tab.home || tab.web == null || tab.error != null) { toast(getString(R.string.ui_open_a_login_page_and_select_a_login_field_first_5299e)); return; }
            AutofillManager manager = getSystemService(AutofillManager.class);
            if (manager == null || !manager.isEnabled()) {
                dialogBuilder().setTitle(getString(R.string.ui_choose_your_password_manager_9a528))
                        .setMessage(getString(R.string.ui_choose_your_password_manager_as_the_autofill_service_i_65497))
                        .setPositiveButton(getString(R.string.ui_autofill_settings_966d6), (d, w) -> openPasswordSettings()).setNegativeButton(getString(R.string.ui_close_5a468), null).show();
            } else { tab.web.requestFocus(); manager.requestAutofill(tab.web); }
        });
        setting(list, getString(R.string.ui_set_up_your_password_manager_e6a54), getString(R.string.ui_choose_your_autofill_service_4bc39), v -> { dialog.dismiss(); openPasswordSettings(); });
        setting(list, getString(R.string.ui_sign_in_with_an_external_passkey_f9cea), getString(R.string.ui_open_this_website_in_a_supported_browser_d5787), v -> { dialog.dismiss(); externalPasskeyLogin(); });
        TextView note = text(getString(R.string.ui_passkeys_for_arbitrary_websites_require_additional_bro_a88fe)
                + getString(R.string.ui_the_login_stays_in_that_browser_its_session_is_not_tra_de285), 12, MUTED);
        addSpaced(list, note, 18);
        dialog.show();
    }

    private void openPasswordSettings() {
        if (!AutofillSettings.open(this)) toast(getString(R.string.settings_passwords_unavailable));
    }

    private void externalPasskeyLogin() {
        Tab tab = current();
        if (tab.home || tab.error != null || !tab.url.startsWith("https://")) { toast(getString(R.string.ui_open_an_https_login_page_first_44f56)); return; }
        String url = UrlPolicy.normalizeWebUrl(tab.url);
        if (url == null) return;
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> browsers = new ArrayList<>();
        for (ResolveInfo match : getPackageManager().queryIntentActivities(probe, 0)) {
            if (match.activityInfo != null && match.activityInfo.exported
                    && !getPackageName().equals(match.activityInfo.packageName)) browsers.add(match);
        }
        if (browsers.isEmpty()) { toast(getString(R.string.ui_no_other_browser_found_install_a_browser_with_passkey_b5182)); return; }
        String[] labels = new String[browsers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = browsers.get(i).loadLabel(getPackageManager()).toString();
        dialogBuilder().setTitle(getString(R.string.ui_open_login_in_another_browser_01ef8)).setItems(labels, (dialog, which) -> {
            ResolveInfo browser = browsers.get(which);
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                    .setClassName(browser.activityInfo.packageName, browser.activityInfo.name);
            try { startActivity(open); } catch (ActivityNotFoundException | SecurityException unavailable) { toast(getString(R.string.ui_this_browser_is_not_available_a7408)); }
        }).setNegativeButton(getString(R.string.ui_cancel_c6a8d), null).show();
    }

    private void addBookmark() {
        Tab tab = current();
        if (tab.home || UrlPolicy.normalizeWebUrl(tab.url) == null) { toast(getString(R.string.ui_open_a_website_first_9bb0d)); return; }
        SavedSitesStore sites = new SavedSitesStore(preferences);
        if (sites.isBookmarked(tab.url)) { toast(getString(R.string.ui_this_bookmark_already_exists_ff1af)); return; }
        String title = tab.title == null || tab.title.trim().isEmpty() ? host(tab.url) : tab.title.trim();
        if (title.length() > 40) title = title.substring(0, Character.isHighSurrogate(title.charAt(39)) ? 39 : 40);
        try { sites.saveBookmark(-1, title, tab.url); toast(getString(R.string.ui_bookmark_saved_56b50)); home = null; }
        catch (IllegalArgumentException failure) { toast(getString(R.string.ui_could_not_save_36578)); }
    }

    private void showBookmarks() { showLibrary(false); }
    private void showLibrary(boolean startPage) {
        startActivityForResult(new Intent(this, LibraryActivity.class)
                .putExtra(LibraryActivity.EXTRA_START_PAGE, startPage).putExtra(LibraryActivity.EXTRA_RETURN_URL, true), LIBRARY_PAGE);
    }

    private void sharePage() {
        if (current().home) { toast(getString(R.string.ui_open_a_website_first_9bb0d)); return; }
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, current().url);
        startActivity(Intent.createChooser(share, getString(R.string.ui_share_page_9c8bc)));
    }

    private void findOnPage() {
        Tab tab = current();
        if (tab.home || tab.web == null) { toast(getString(R.string.ui_open_a_website_first_9bb0d)); return; }
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint(getString(R.string.ui_which_word_are_you_looking_for_5c1f2));
        dialogBuilder().setTitle(getString(R.string.ui_find_in_page_91025)).setView(query)
                .setPositiveButton(getString(R.string.ui_search_5d33a), (dialog, which) -> {
                    String term = query.getText().toString();
                    if (term.isEmpty() || tab.web == null) return;
                    tab.web.session.getFinder().find(term, GeckoSession.FINDER_FIND_FORWARD).accept(found -> {
                        if (destroyed || tab != current() || tab.web == null) return;
                        if (!found.found) { toast(getString(R.string.ui_no_results_addf4)); return; }
                        AlertDialog results = dialogBuilder().setTitle(found.total < 0 ? getString(R.string.ui_find_in_page_91025) : found.total + getString(R.string.ui_results_cc7a6))
                                .setPositiveButton("Volgende", null).setNeutralButton("Vorige", null)
                                .setNegativeButton(getString(R.string.ui_done_6fb77), null).create();
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
                    }, failure -> toast(getString(R.string.ui_search_failed_f82b1)));
                }).setNegativeButton(getString(R.string.ui_cancel_c6a8d), null).show();
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!"mailto".equalsIgnoreCase(scheme) && !"tel".equalsIgnoreCase(scheme) && !"geo".equalsIgnoreCase(scheme)) {
            toast(getString(R.string.ui_this_type_of_link_is_not_supported_yet_1ab45)); return;
        }
        dialogBuilder().setTitle(getString(R.string.ui_open_another_app_42d5c)).setMessage(uri.toString())
                .setPositiveButton(getString(R.string.ui_open_5b1e5), (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                    catch (ActivityNotFoundException unavailable) { toast(getString(R.string.ui_no_suitable_app_found_cc97e)); }
                }).setNegativeButton(getString(R.string.ui_cancel_c6a8d), null).show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("safe_mode",false)) recoverExtensions(); else openIntent(intent);
    }

    private boolean openIntent(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        String url = UrlPolicy.normalizeWebUrl(intent.getData().toString());
        if (url == null) return false;
        if(current().privateMode && tabs.size()>=MAX_TABS){toast(getString(R.string.ui_you_can_open_up_to_twelve_tabs_70529));return false;}
        if (!(tabs.size() == 1 && current().home && !current().privateMode) && tabs.size() < MAX_TABS) newTab();
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
        if (request == DOWNLOAD_DESTINATION) {
            org.mozilla.geckoview.WebResponse response = pendingDownload; pendingDownload = null;
            if (response != null) {
                if (result == RESULT_OK && data != null && data.getData() != null) {
                    Uri uri = data.getData();
                    try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
                    catch (SecurityException unavailable) { /* The selected URI can still have a temporary grant. */ }
                    if (DownloadService.enqueue(this,response,uri,pendingDownloadPrivate)) toast(getString(R.string.ui_download_started_see_menu_downloads_b2233));
                    else toast(getString(R.string.ui_the_download_could_not_start_check_the_destination_fil_00ed7));
                } else DownloadService.close(response.body);
            }
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
        if (fullscreen) { if(current().web != null)current().web.session.exitFullScreen();setFullscreen(false);return; }
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
        resumed=false;if(sitePermissions!=null)sitePermissions.suspend();
        super.onPause();
        if (!tabs.isEmpty() && current().web != null) current().web.onPause();
        persistTabs();
        if (engine != null) engine.clearPromptActivity(this);
        if (extensionUi != null) extensionUi.suspend();
    }

    @Override protected void onResume() {
        super.onResume();resumed=true;
        if (engine != null) {
            engine.setPromptActivity(this); extensionUi.attach();
            for (Tab tab : tabs) if (tab.web != null) attachExtensions(tab.web);
        }
        if (engine != null && consumeImportedTabs()) showSelected();
        if (!tabs.isEmpty() && current().web != null && !current().home) current().web.onResume();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            for (Tab tab : tabs) if (tab != current() && tab.web != null) dispose(tab, true);
            updateChrome();
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (sitePermissions != null) sitePermissions.cancel();
        if (pendingDownload != null) { DownloadService.close(pendingDownload.body); pendingDownload = null; }
        cancelFilePicker();
        if (engine != null) engine.removeBlockedListener(blockedListener);
        if (extensionUi != null) extensionUi.destroy();
        for (Tab tab : tabs) { dispose(tab, false);if(tab.privateMode)engine.privateTabClosed(); }
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
            int limit=Math.max(2,Math.min(4,preferences.getInt("resident_limit",MAX_RESIDENT)));
            if (resident <= limit || oldest == null) return;
            dispose(oldest, true);
        }
    }

    private void dispose(Tab tab, boolean saveState) {
        tab.recording=false;
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
        for (TabStateStore.Entry entry : TabStateStore.read(preferences.getString("tabs","[]"))) {
            Tab tab = new Tab(); tab.url = entry.url; tab.pageHost = host(entry.url); tab.home = entry.home; tab.title = entry.title; tabs.add(tab);
        }
        if (tabs.isEmpty()) tabs.add(new Tab());
        selected = Math.max(0,Math.min(preferences.getInt("selected",0),tabs.size()-1));
    }
    private boolean consumeImportedTabs() {
        if(!preferences.contains("tabs_import"))return false;
        List<TabStateStore.Entry> remaining=new ArrayList<>();boolean changed=false;
        for(TabStateStore.Entry entry:TabStateStore.read(preferences.getString("tabs_import","[]"))) {
            boolean exists=false;for(Tab tab:tabs)if(!tab.privateMode && tab.home==entry.home && (entry.home || tab.url.equals(entry.url)))exists=true;
            if(exists)continue;
            if(tabs.size()==1 && !entry.home && tabs.get(0).home && !tabs.get(0).privateMode && tabs.get(0).url.isEmpty() && tabs.get(0).web==null)tabs.clear();
            if(tabs.size()>=MAX_TABS){remaining.add(entry);continue;}
            Tab tab=new Tab();tab.url=entry.url;tab.pageHost=host(entry.url);tab.title=entry.title;tab.home=entry.home;tabs.add(tab);changed=true;
        }
        if(remaining.isEmpty())preferences.edit().remove("tabs_import").apply();
        else preferences.edit().putString("tabs_import",TabStateStore.encode(remaining)).apply();
        if(changed)persistTabs();return changed;
    }
    private void persistTabs() {
        if (destroyed) return;
        List<TabStateStore.Entry> entries = new ArrayList<>();
        for (Tab tab : tabs) entries.add(new TabStateStore.Entry(tab.url,tab.title,tab.home,tab.privateMode));
        preferences.edit().putString("tabs",TabStateStore.encode(entries)).putInt("selected",TabStateStore.selected(entries,selected)).apply();
    }
    private void updatePrivacyProtection() {
        boolean privateOpen = tabs.stream().anyMatch(tab -> tab.privateMode);
        if (privateOpen) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        connection.setContentDescription(current().privateMode ? getString(R.string.ui_private_tab_no_history_or_tab_restoration_2a1cb) : getString(R.string.ui_connection_information_d437e));
    }
    private void beginDownload(org.mozilla.geckoview.WebResponse response,boolean privateMode) {
        if (pendingDownload != null || response.body == null || response.statusCode >= 400) { DownloadService.close(response.body); toast(getString(R.string.ui_download_unavailable_or_a_file_picker_is_already_open_88af7)); return; }
        String source = response.uri.startsWith("blob:") ? response.uri.substring(5) : response.uri;
        if (!UrlPolicy.allowed(source,httpsOnly)) { DownloadService.close(response.body); toast(getString(R.string.ui_this_download_address_is_not_allowed_c75b9)); return; }
        Runnable choose = () -> {
            pendingDownload = response; pendingDownloadPrivate = privateMode;
            String type = response.headers.get("Content-Type"); if(type == null) type = "application/octet-stream"; type = type.split(";",2)[0];
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type)
                    .putExtra(Intent.EXTRA_TITLE,DownloadStore.filename(response.headers.get("Content-Disposition"),response.uri));
            try { startActivityForResult(save,DOWNLOAD_DESTINATION); }
            catch(ActivityNotFoundException unavailable) { pendingDownload = null; DownloadService.close(response.body); toast(getString(R.string.ui_no_file_picker_is_available_7a3e5)); }
        };
        if (privateMode) new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_save_a_private_file_85734))
                .setMessage(getString(R.string.ui_the_selected_file_will_remain_on_your_device_after_pri_1346d))
                .setPositiveButton(getString(R.string.ui_choose_file_235cc),(d,w)->choose.run()).setNegativeButton(getString(R.string.ui_cancel_c6a8d),(d,w)->DownloadService.close(response.body))
                .setOnCancelListener(d->DownloadService.close(response.body)).show();
        else choose.run();
    }
    private void showLinkMenu(String link,boolean privateMode) {
        String url = UrlPolicy.normalizeWebUrl(link); if(url == null)return;
        new MaterialAlertDialogBuilder(this).setTitle(SettingsViews.host(url)).setItems(new String[]{getString(R.string.ui_open_in_new_tab_02b11),getString(R.string.ui_open_in_private_tab_619b3),getString(R.string.ui_copy_link_97baa)},(d,which)->{
            if(which < 2) { if(tabs.size() >= MAX_TABS) { toast(getString(R.string.ui_you_can_open_up_to_twelve_tabs_70529)); return; } newTab(which == 1 || privateMode); navigate(url); }
            else { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Webadres",url)); toast(getString(R.string.ui_link_copied_9c8bb)); }
        }).show();
    }
    private void setFullscreen(boolean enabled) {
        fullscreen=enabled;if(browserControls!=null)browserControls.setVisibility(enabled?View.GONE:View.VISIBLE);
        progress.setVisibility(enabled?View.GONE:View.INVISIBLE);
        int flags=light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
        if(enabled)flags|=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void recoverExtensions() {
        for (Tab tab : tabs) dispose(tab,false);
        engine.disableUserExtensions().accept(done -> { toast(getString(R.string.ui_extra_extensions_disabled_built_in_protection_remains_58090)); showSelected(); },
                failure -> { toast(getString(R.string.ui_not_all_extensions_could_be_disabled_try_again_from_ex_5c159)); showSelected(); });
    }
    private void showDefaultBrowser() { DefaultBrowser.open(this); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);if(sitePermissions != null)sitePermissions.result(request,grants);
    }

    void closePagesForDataChange() {
        if (sitePermissions != null) sitePermissions.cancel();
        extensionUi.closePanels();
        for (Tab tab : tabs) { dispose(tab,false); if (!tab.home) tab.error = getString(R.string.ui_website_data_or_permissions_changed_open_the_page_agai_99d9d); }
        renderPage(); updateChrome();
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(event.isCtrlPressed() && !event.isAltPressed()) {
            if(key==KeyEvent.KEYCODE_L) { focusAddress(); return true; }
            if(key==KeyEvent.KEYCODE_T) { newTab(); return true; }
            if(key==KeyEvent.KEYCODE_P && event.isShiftPressed()) { newTab(true); return true; }
            if(key==KeyEvent.KEYCODE_W) { closeTab(selected); return true; }
            if(key==KeyEvent.KEYCODE_TAB) { selected=(selected+tabs.size()+(event.isShiftPressed()?-1:1))%tabs.size(); showSelected(); return true; }
        }
        return super.onKeyDown(key,event);
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
        button.setBackground(shape(ACCENT, 28, Color.TRANSPARENT)); button.setMinHeight(dp(48));button.setFocusable(true);SettingsViews.buttonRole(button);button.setOnClickListener(listener); return button;
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
