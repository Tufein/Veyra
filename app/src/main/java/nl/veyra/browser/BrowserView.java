package nl.veyra.browser;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.EditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

/** A tab's view/session, with engine events separated from the browser chrome. */
// Programmatic tab views require a listener and are never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class BrowserView extends GeckoView {
    interface Listener {
        boolean allowNavigation(String uri, boolean gesture);
        void locationChanged(String uri);
        void progressChanged(int progress);
        void titleChanged(String title);
        void finished();
        void failed(String message);
        GeckoSession newSession(String uri);
        void chooseFile(String[] mimeTypes, boolean multiple, ValueCallback<Uri[]> callback);
    }
    final GeckoSession session = new GeckoSession();
    private boolean backwards, forwards, closed;
    private GeckoSession.SessionState savedState;
    private Listener listener;

    BrowserView(Activity activity, Listener events) { this(activity, events, false); }

    BrowserView(Activity activity, Listener events, boolean openedByEngine) {
        super(activity);
        listener = events;
        setAutofillEnabled(true);
        setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        session.getSettings().setSuspendMediaWhenInactive(true);
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
                return GeckoResult.fromValue(!closed && listener.allowNavigation(request.uri, request.hasUserGesture)
                        ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
            }
            @Override public void onLocationChange(GeckoSession s, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions, Boolean gesture) {
                if (!closed && url != null) listener.locationChanged(url);
            }
            @Override public void onCanGoBack(GeckoSession s, boolean can) { if (!closed) { backwards = can; listener.progressChanged(-1); } }
            @Override public void onCanGoForward(GeckoSession s, boolean can) { if (!closed) { forwards = can; listener.progressChanged(-1); } }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                return GeckoResult.fromValue(listener.newSession(uri));
            }
            @Override public GeckoResult<String> onLoadError(GeckoSession s, String uri, WebRequestError issue) {
                if (!closed) listener.failed("Controleer je verbinding of probeer het webadres opnieuw. Certificaatfouten worden niet omzeild.");
                return null;
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String url) { if (!closed) listener.progressChanged(0); }
            @Override public void onProgressChange(GeckoSession s, int value) { if (!closed) listener.progressChanged(value); }
            @Override public void onPageStop(GeckoSession s, boolean success) {
                if (!closed) { listener.progressChanged(100); listener.finished(); }
            }
            @Override public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState state) { savedState = state; }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession s, String title) { if (!closed && title != null) listener.titleChanged(title); }
            @Override public void onCrash(GeckoSession s) {
                if (!closed) { destroy(); listener.failed("De pagina is gesloten. Tik op opnieuw proberen."); }
            }
            @Override public void onExternalResponse(GeckoSession s, WebResponse response) {
                android.widget.Toast.makeText(activity, "Bestanden downloaden komt in een volgende versie.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public void onAndroidPermissionsRequest(GeckoSession s, String[] permissions, Callback callback) { callback.reject(); }
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission permission) {
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
            @Override public void onMediaPermissionRequest(GeckoSession s, String uri,
                    MediaSource[] video, MediaSource[] audio, MediaCallback callback) { callback.reject(); }
        });
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession s, FilePrompt prompt) {
                if (prompt.type == FilePrompt.Type.FOLDER) return GeckoResult.fromValue(prompt.dismiss());
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                listener.chooseFile(prompt.mimeTypes, prompt.type == FilePrompt.Type.MULTIPLE,
                        uris -> result.complete(uris == null ? prompt.dismiss() : prompt.confirm(activity, uris)));
                return result;
            }
            @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, AlertPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                new MaterialAlertDialogBuilder(activity).setTitle(prompt.title).setMessage(prompt.message)
                        .setPositiveButton("OK", null).setOnDismissListener(d -> result.complete(prompt.dismiss())).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, ButtonPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                new MaterialAlertDialogBuilder(activity).setTitle(prompt.title).setMessage(prompt.message)
                        .setPositiveButton("OK", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE)); })
                        .setNegativeButton("Annuleren", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE)); })
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.dismiss()); }).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, TextPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                EditText field = new EditText(activity); field.setText(prompt.defaultValue);
                new MaterialAlertDialogBuilder(activity).setTitle(prompt.title).setMessage(prompt.message).setView(field)
                        .setPositiveButton("OK", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(field.getText().toString())); })
                        .setNegativeButton("Annuleren", null)
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.dismiss()); }).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession s, BeforeUnloadPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                new MaterialAlertDialogBuilder(activity).setTitle("Pagina verlaten?")
                        .setMessage("Niet-opgeslagen wijzigingen kunnen verloren gaan.")
                        .setPositiveButton("Verlaten", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(AllowOrDeny.ALLOW)); })
                        .setNegativeButton("Blijven", null)
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.confirm(AllowOrDeny.DENY)); }).show();
                return result;
            }
        });
        VeyraRuntime engine = VeyraRuntime.get(activity);
        setSession(session);
        if (!openedByEngine) engine.ready.accept(unused -> {
            if (!closed) session.open(engine.runtime);
        }, error -> { if (!closed) listener.failed("De browserbescherming kon niet starten. Herstart Veyra."); });
    }

    boolean isClosed() { return closed; }
    boolean canGoBack() { return backwards; }
    boolean canGoForward() { return forwards; }
    void goBack() { session.goBack(); }
    void goForward() { session.goForward(); }
    void loadUrl(String url) { session.loadUri(url); }
    void reload() { session.reload(); }
    void stopLoading() { session.stop(); }
    void onPause() {
        session.setActive(false);

    }
    void onResume() {
        VeyraRuntime.get(getContext()).activeSession = session;
        session.setActive(true);
        VeyraRuntime.get(getContext()).runtime.getWebExtensionController().setTabActive(session, true);
    }
    void saveState(Bundle bundle) { if (savedState != null) bundle.putParcelable("gecko", savedState); }
    boolean restoreState(Bundle bundle) {
        GeckoSession.SessionState state = bundle.getParcelable("gecko");
        if (state == null) return false;
        session.restoreState(state); return true;
    }
    void destroy() {
        if (closed) return;
        closed = true; releaseSession();
        VeyraRuntime engine = VeyraRuntime.get(getContext());
        engine.sessionActions.remove(session);
        if (engine.activeSession == session) engine.activeSession = null;
        if (session.isOpen()) session.close();
    }
}
