package nl.veyra.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

/** Process-wide engine/profile: installed add-ons survive activity and app restarts. */
final class VeyraRuntime {
    static final String PROTECTION_ID = "protection@veyra.nl";
    private static VeyraRuntime instance;
    final java.util.Map<String, ExtensionUi> extensionOwners = new java.util.HashMap<>();
    final java.util.Map<String, WebExtension.Action> actions = new java.util.HashMap<>();
    final java.util.Map<org.mozilla.geckoview.GeckoSession, java.util.Map<String, WebExtension.Action>> sessionActions = new java.util.HashMap<>();
    org.mozilla.geckoview.GeckoSession activeSession;
    final GeckoRuntime runtime;
    final GeckoResult<Void> ready = new GeckoResult<>();
    private final SharedPreferences preferences;
    private final List<WebExtension.Port> ports = new ArrayList<>();
    private final java.util.Map<Activity, List<androidx.appcompat.app.AlertDialog>> prompts = new java.util.HashMap<>();
    private WeakReference<Activity> promptActivity = new WeakReference<>(null);
    private boolean protectionReady;
    private boolean readinessFinished;
    private int blockedCount;
    private final List<Runnable> blockedListeners = new ArrayList<>();

    static synchronized VeyraRuntime get(Context context) {
        if (instance == null) instance = new VeyraRuntime(context.getApplicationContext());
        return instance;
    }

    private VeyraRuntime(Context context) {
        preferences = context.getSharedPreferences("veyra", Context.MODE_PRIVATE);
        ContentBlocking.Settings protection = new ContentBlocking.Settings.Builder()
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
                .build();
        runtime = GeckoRuntime.create(context, new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true).remoteDebuggingEnabled(false)
                .automaticFontSizeAdjustment(false).contentBlocking(protection).build());
        updatePreferences();
        runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    WebExtension extension, String[] permissions, String[] origins, String[] data) {
                return ask("Extensie toevoegen?", extension, permissions, origins, data)
                        .map(answer -> new WebExtension.PermissionPromptResponse(answer == AllowOrDeny.ALLOW, false, false));
            }
            @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension extension,
                    String[] permissions, String[] origins, String[] data) {
                return ask("Nieuwe rechten voor update", extension, permissions, origins, data);
            }
            @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension extension,
                    String[] permissions, String[] origins, String[] data) {
                return ask("Extra rechten toestaan?", extension, permissions, origins, data);
            }
        });
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/veyra-protection/", PROTECTION_ID)
                .accept(extension -> {
                    extension.setMessageDelegate(new WebExtension.MessageDelegate() {
                        @Override public void onConnect(WebExtension.Port port) {
                            if (!PROTECTION_ID.equals(port.sender.webExtension.id)
                                    || port.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION
                                    || port.sender.session != null) { port.disconnect(); return; }
                            ports.add(port);
                            port.setDelegate(new WebExtension.PortDelegate() {
                                @Override public void onPortMessage(Object message, WebExtension.Port source) {
                                    if (!(message instanceof JSONObject)) return;
                                    String type = ((JSONObject) message).optString("type");
                                    if ("ready".equals(type)) sendPreferences(source);
                                    else if ("configured".equals(type) && !protectionReady) {
                                        protectionReady = true;
                                        if (!readinessFinished) { readinessFinished = true; ready.complete(null); }
                                    } else if ("blocked".equals(type)) {
                                        blockedCount++;
                                        for (Runnable listener : new ArrayList<>(blockedListeners)) listener.run();
                                    }
                                }
                                @Override public void onDisconnect(WebExtension.Port source) { ports.remove(source); }
                            });
                        }
                    }, "veyraProtection");
                }, this::startupFailed);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!readinessFinished) startupFailed(new IllegalStateException("Protection startup timed out"));
        }, 60000);
    }

    private void startupFailed(Throwable error) {
        if (!readinessFinished) { readinessFinished = true; ready.completeExceptionally(error); }
    }
    void setPromptActivity(Activity activity) { promptActivity = new WeakReference<>(activity); }
    void clearPromptActivity(Activity activity) {
        if (promptActivity.get() == activity) promptActivity.clear();
        List<androidx.appcompat.app.AlertDialog> pending = prompts.get(activity);
        if (pending != null) for (androidx.appcompat.app.AlertDialog dialog : new ArrayList<>(pending)) dialog.dismiss();
    }
    int blockedCount() { return blockedCount; }
    void addBlockedListener(Runnable listener) { blockedListeners.add(listener); }
    void removeBlockedListener(Runnable listener) { blockedListeners.remove(listener); }

    void updatePreferences() {
        runtime.getSettings().setFontSizeFactor(preferences.getInt("text_zoom", 100) / 100f);
        runtime.getSettings().setAllowInsecureConnections(preferences.getBoolean("https_only", false)
                ? GeckoRuntimeSettings.HTTPS_ONLY : GeckoRuntimeSettings.ALLOW_ALL);
        for (WebExtension.Port port : new ArrayList<>(ports)) sendPreferences(port);
    }
    private void sendPreferences(WebExtension.Port port) {
        try {
            JSONObject config = new JSONObject(); config.put("type", "config");
            config.put("blocking", preferences.getBoolean("blocking", true));
            config.put("httpsOnly", preferences.getBoolean("https_only", false));
            config.put("exceptions", new JSONArray(preferences.getStringSet("ad_exceptions", java.util.Collections.emptySet())));
            port.postMessage(config);
        } catch (JSONException invalid) { throw new IllegalStateException(invalid); }
    }
    private GeckoResult<AllowOrDeny> ask(String title, WebExtension extension,
            String[] permissions, String[] origins, String[] data) {
        Activity activity = promptActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            return GeckoResult.fromValue(AllowOrDeny.DENY);
        StringBuilder message = new StringBuilder(name(extension)).append("\n\nDeze extensie vraagt toegang tot:");
        for (String permission : permissions) message.append("\n• ").append(permission);
        for (String origin : origins) message.append("\n• ").append(origin);
        if (data.length > 0) {
            message.append("\n\nGegevensverzameling:");
            for (String item : data) message.append("\n• ").append(item);
        }
        if (permissions.length + origins.length + data.length == 0) message.append("\nGeen extra rechten.");
        message.append("\n\nVoeg alleen extensies toe die je vertrouwt.");
        GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        boolean[] completed = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message)
                .setPositiveButton("Toestaan", (d, w) -> { completed[0] = true; result.complete(AllowOrDeny.ALLOW); })
                .setNegativeButton("Annuleren", (d, w) -> { completed[0] = true; result.complete(AllowOrDeny.DENY); })
                .setOnDismissListener(d -> {
                    List<androidx.appcompat.app.AlertDialog> pending = prompts.get(activity);
                    if (pending != null) { pending.remove(d); if (pending.isEmpty()) prompts.remove(activity); }
                    if (!completed[0]) { completed[0] = true; result.complete(AllowOrDeny.DENY); }
                }).create();
        prompts.computeIfAbsent(activity, key -> new ArrayList<>()).add(dialog);
        dialog.show();
        return result;
    }
    static String name(WebExtension extension) {
        return extension.metaData != null && extension.metaData.name != null ? extension.metaData.name : extension.id;
    }
}
