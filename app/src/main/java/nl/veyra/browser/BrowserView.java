package nl.veyra.browser;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.EditText;
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
        default void download(WebResponse response) { DownloadService.close(response.body); }
        default void androidPermissions(String[] permissions, GeckoSession.PermissionDelegate.Callback callback) { callback.reject(); }
        default GeckoResult<Integer> contentPermission(GeckoSession.PermissionDelegate.ContentPermission permission) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        }
        default void mediaPermission(String uri, GeckoSession.PermissionDelegate.MediaSource[] video,
                GeckoSession.PermissionDelegate.MediaSource[] audio, GeckoSession.PermissionDelegate.MediaCallback callback) { callback.reject(); }
        default void contextMenu(String link) { }
        default void fullscreen(boolean enabled) { }
        default void recording(boolean camera,boolean microphone) { }

    }
    final GeckoSession session;
    private boolean backwards, forwards, closed;
    private GeckoSession.SessionState savedState;
    private Listener listener;

    BrowserView(Activity activity, Listener events) { this(activity, events, false); }

    BrowserView(Activity activity, Listener events, boolean openedByEngine) { this(activity, events, openedByEngine, false); }
    BrowserView(Activity activity, Listener events, boolean openedByEngine, boolean privateMode) {
        super(activity);
        session = new GeckoSession(new org.mozilla.geckoview.GeckoSessionSettings.Builder().usePrivateMode(privateMode)
                .contextId(privateMode ? VeyraRuntime.get(activity).privateContextId : null).build());
        listener = events;
        setAutofillEnabled(!privateMode);
        setImportantForAutofill(privateMode ? View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS : View.IMPORTANT_FOR_AUTOFILL_YES);
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
                if (!closed) listener.failed(activity.getString(R.string.ui_check_your_connection_or_try_the_address_again_certifi_dce35));
                return null;
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String url) { if (!closed) listener.progressChanged(0); }
            @Override public void onProgressChange(GeckoSession s, int value) { if (!closed) listener.progressChanged(value); }
            @Override public void onPageStop(GeckoSession s, boolean success) {
                if (!closed) { listener.progressChanged(100); if(success) listener.finished(); }
            }
            @Override public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState state) { savedState = state; }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession s, String title) { if (!closed && title != null) listener.titleChanged(title); }
            @Override public void onFullScreen(GeckoSession s,boolean enabled) { if(!closed)listener.fullscreen(enabled); }
            @Override public void onContextMenu(GeckoSession s,int x,int y,ContextElement element) {
                if (!closed && element.linkUri != null) listener.contextMenu(element.linkUri);
            }
            @Override public void onKill(GeckoSession s) {
                if (!closed) { destroy(); listener.failed(activity.getString(R.string.ui_this_page_was_removed_from_memory_tap_try_again_f7257)); }
            }
            @Override public void onCrash(GeckoSession s) {
                if (!closed) { destroy(); listener.failed(activity.getString(R.string.ui_the_page_has_closed_tap_try_again_73e47)); }
            }
            @Override public void onExternalResponse(GeckoSession s, WebResponse response) {
                if (!closed) listener.download(response); else DownloadService.close(response.body);
            }
        });
        session.setMediaDelegate(new GeckoSession.MediaDelegate() {
            @Override public void onRecordingStatusChanged(GeckoSession s,RecordingDevice[] devices) {
                if(closed)return;boolean camera=false,microphone=false;
                for(RecordingDevice device:devices)if(device.status==RecordingDevice.Status.RECORDING) {
                    camera|=device.type==RecordingDevice.Type.CAMERA;microphone|=device.type==RecordingDevice.Type.MICROPHONE;
                }
                listener.recording(camera,microphone);
            }
        });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public void onAndroidPermissionsRequest(GeckoSession s, String[] permissions, Callback callback) { listener.androidPermissions(permissions,callback); }
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission permission) {
                return listener.contentPermission(permission);
            }
            @Override public void onMediaPermissionRequest(GeckoSession s, String uri,
                    MediaSource[] video, MediaSource[] audio, MediaCallback callback) { listener.mediaPermission(uri,video,audio,callback); }
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
                BrowserPrivacyUi.dialog(activity).setTitle(prompt.title).setMessage(prompt.message)
                        .setPositiveButton("OK", null).setOnDismissListener(d -> result.complete(prompt.dismiss())).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, ButtonPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                BrowserPrivacyUi.dialog(activity).setTitle(prompt.title).setMessage(prompt.message)
                        .setPositiveButton("OK", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE)); })
                        .setNegativeButton(activity.getString(R.string.ui_cancel_c6a8d), (d, w) -> { answered[0] = true; result.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE)); })
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.dismiss()); }).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, TextPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                EditText field = new EditText(activity); field.setText(prompt.defaultValue);
                BrowserPrivacyUi.dialog(activity).setTitle(prompt.title).setMessage(prompt.message).setView(field)
                        .setPositiveButton("OK", (d, w) -> { answered[0] = true; result.complete(prompt.confirm(field.getText().toString())); })
                        .setNegativeButton(activity.getString(R.string.ui_cancel_c6a8d), null)
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.dismiss()); }).show();
                return result;
            }
            @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession s, BeforeUnloadPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>(); boolean[] answered = {false};
                BrowserPrivacyUi.dialog(activity).setTitle(activity.getString(R.string.ui_leave_this_page_83769))
                        .setMessage(activity.getString(R.string.ui_unsaved_changes_may_be_lost_08c51))
                        .setPositiveButton(activity.getString(R.string.ui_leave_65275), (d, w) -> { answered[0] = true; result.complete(prompt.confirm(AllowOrDeny.ALLOW)); })
                        .setNegativeButton("Blijven", null)
                        .setOnDismissListener(d -> { if (!answered[0]) result.complete(prompt.confirm(AllowOrDeny.DENY)); }).show();
                return result;
            }
        });
        VeyraRuntime engine = VeyraRuntime.get(activity);
        setSession(session);
        if (!openedByEngine) engine.ready.accept(unused -> {
            if (!closed) session.open(engine.runtime);
        }, error -> { if (!closed) listener.failed(activity.getString(R.string.ui_browser_protection_could_not_start_restart_veyra_b843a)); });
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
