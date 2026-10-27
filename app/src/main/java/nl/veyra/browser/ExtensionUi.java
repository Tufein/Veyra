package nl.veyra.browser;

import android.app.Activity;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

/** Hosts extension popups/options and wires browser-action and tabs.create delegates. */
final class ExtensionUi {
    interface TabFactory {
        GeckoSession create(String uri);
        default GeckoSession create(WebExtension.CreateTabDetails details) { return create(details.url); }
    }
    private final Activity activity;
    private final VeyraRuntime engine;
    private final TabFactory tabs;
    private final java.util.Map<String, WebExtension> bound = new java.util.HashMap<>();
    private boolean active;
    private final List<androidx.appcompat.app.AlertDialog> dialogs = new ArrayList<>();
    private final List<BrowserView> panels = new ArrayList<>();
    final WebExtension.ActionDelegate actions = new WebExtension.ActionDelegate() {
        @Override public void onBrowserAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            if (session == null) engine.actions.put(extension.id, action);
            else engine.sessionActions.computeIfAbsent(session, key -> new java.util.HashMap<>()).put(extension.id, action);
        }
        @Override public void onPageAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
            onBrowserAction(extension, session, action);
        }
        @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension extension, WebExtension.Action action) {
            return GeckoResult.fromValue(active && !activity.isFinishing() && !activity.isDestroyed()
                    ? panel(VeyraRuntime.name(extension), false).session : null);
        }
        @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension extension, WebExtension.Action action) {
            return onTogglePopup(extension, action);
        }
    };
    ExtensionUi(Activity activity, TabFactory tabs) {
        this.activity = activity; this.tabs = tabs; engine = VeyraRuntime.get(activity);
    }
    void bind(WebExtension extension) {
        if (!active || activity.isFinishing() || activity.isDestroyed() || extension.isBuiltIn) return;
        bound.put(extension.id, extension);
        engine.extensionOwners.put(extension.id, this);
        extension.setActionDelegate(actions);
        extension.setTabDelegate(new WebExtension.TabDelegate() {
            @Override public GeckoResult<GeckoSession> onNewTab(WebExtension source, WebExtension.CreateTabDetails details) {
                if (!active || activity.isFinishing() || activity.isDestroyed()) return null;
                return GeckoResult.fromValue(tabs == null ? panel(VeyraRuntime.name(source), true).session : tabs.create(details));
            }
            @Override public void onOpenOptionsPage(WebExtension source) { options(source); }
        });
    }
    void suspend() { active = false; }
    void attach() {
        active = true;
        engine.runtime.getWebExtensionController().list().accept(list -> {
            if (!active || activity.isFinishing() || activity.isDestroyed()) return;
            for (WebExtension extension : list) bind(extension);
        }, failure -> { });
    }
    void open(WebExtension extension) {
        WebExtension.Action action = engine.actions.get(extension.id);
        if (engine.activeSession != null) {
            java.util.Map<String, WebExtension.Action> overrides = engine.sessionActions.get(engine.activeSession);
            WebExtension.Action override = overrides == null ? null : overrides.get(extension.id);
            if (override != null) action = action == null ? override : override.withDefault(action);
        }
        if (action != null && !Boolean.FALSE.equals(action.enabled)) action.click();
        else if (extension.metaData != null && extension.metaData.optionsPageUrl != null) options(extension);
        else Toast.makeText(activity, activity.getString(R.string.ui_this_extension_works_in_the_background_and_has_no_cont_67f1b), Toast.LENGTH_LONG).show();
    }
    void options(WebExtension extension) {
        if (!active || activity.isFinishing() || activity.isDestroyed()) return;
        if (extension.metaData == null || extension.metaData.optionsPageUrl == null) return;
        panel(VeyraRuntime.name(extension), false).loadUrl(extension.metaData.optionsPageUrl);
    }
    private BrowserView panel(String title, boolean engineOpens) {
        BrowserView view = new BrowserView(activity, new BrowserView.Listener() {
            @Override public boolean allowNavigation(String uri, boolean gesture) {
                return uri.startsWith("moz-extension://") || "about:blank".equals(uri)
                        || UrlPolicy.allowed(uri, engine.runtime.getSettings().getAllowInsecureConnections()
                        == org.mozilla.geckoview.GeckoRuntimeSettings.HTTPS_ONLY);
            }
            @Override public void locationChanged(String uri) { }
            @Override public void progressChanged(int value) { }
            @Override public void titleChanged(String title) { }
            @Override public void finished() { }
            @Override public void failed(String error) { Toast.makeText(activity, error, Toast.LENGTH_LONG).show(); }
            @Override public GeckoSession newSession(String uri) {
                return tabs == null ? null : tabs.create(uri);
            }
            @Override public void chooseFile(String[] mimeTypes, boolean multiple, ValueCallback<Uri[]> callback) {
                callback.onReceiveValue(null);
            }
        }, engineOpens);
        panels.add(view);
        int height = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .68f);
        view.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, height));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setView(view).setNegativeButton(activity.getString(R.string.ui_close_5a468), null)
                .setOnDismissListener(d -> { view.destroy(); panels.remove(view); dialogs.remove(d); }).create();
        dialogs.add(dialog); dialog.show();
        return view;
    }
    void closePanels() {
        for (androidx.appcompat.app.AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        for (BrowserView panel : new ArrayList<>(panels)) panel.destroy(); panels.clear(); dialogs.clear();
    }
    void destroy() {
        active = false;
        for (androidx.appcompat.app.AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
        for (BrowserView view : new ArrayList<>(panels)) view.destroy(); panels.clear();
        for (WebExtension extension : bound.values()) if (engine.extensionOwners.get(extension.id) == this) {
            extension.setActionDelegate(null); extension.setTabDelegate(null); engine.extensionOwners.remove(extension.id);
        }
        bound.clear();
    }
}
