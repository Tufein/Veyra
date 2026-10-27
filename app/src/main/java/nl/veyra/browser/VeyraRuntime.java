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
    final String privateContextId = "private-" + java.util.UUID.randomUUID();
    final GeckoRuntime runtime;
    final GeckoResult<Void> ready = new GeckoResult<>();
    private final SharedPreferences preferences;
    private final List<WebExtension.Port> ports = new ArrayList<>();
    private final java.util.Map<Activity, List<androidx.appcompat.app.AlertDialog>> prompts = new java.util.HashMap<>();
    private WeakReference<Activity> promptActivity = new WeakReference<>(null);
    private boolean protectionReady, profileReady;
    private boolean readinessFinished;
    private int blockedCount, privateTabs;
    private org.mozilla.geckoview.GeckoSession privateAnchor;
    private final java.util.Map<String,Boolean> privateExceptions = new java.util.HashMap<>();
    boolean exception(String host,boolean privateMode) {
        if(privateMode && privateExceptions.containsKey(host))return privateExceptions.get(host);
        return preferences.getStringSet("ad_exceptions",java.util.Collections.emptySet()).contains(host);
    }
    void togglePrivateException(String host) { privateExceptions.put(host,!exception(host,true));updatePreferences(); }
    void privateTabOpened() {
        if(privateTabs++ != 0)return;
        // Keep the private cookie jar alive even when inactive views are evicted under memory pressure.
        org.mozilla.geckoview.GeckoSession anchor = new org.mozilla.geckoview.GeckoSession(new org.mozilla.geckoview.GeckoSessionSettings.Builder()
                .usePrivateMode(true).contextId(privateContextId).build());privateAnchor=anchor;
        ready.accept(done->{if(privateTabs>0 && privateAnchor==anchor){anchor.open(runtime);anchor.setActive(false);}},error->{});
    }
    void privateTabClosed() {
        if(privateTabs<=0 || --privateTabs!=0)return;
        if(privateAnchor!=null && privateAnchor.isOpen())privateAnchor.close();privateAnchor=null;
        runtime.getStorageController().clearDataForSessionContext(privateContextId);privateExceptions.clear();updatePreferences();
    }
    private final List<WeakReference<BrowserActivity>> browsers = new ArrayList<>();
    void registerBrowser(BrowserActivity activity) { browsers.add(new WeakReference<>(activity)); }
    void closePagesForDataChange() {
        browsers.removeIf(reference -> reference.get() == null || reference.get().isDestroyed());
        for (WeakReference<BrowserActivity> reference : new ArrayList<>(browsers)) {
            BrowserActivity browser = reference.get(); if (browser != null) browser.closePagesForDataChange();
        }
    }
    private final java.util.Map<String,GeckoResult<java.util.Map<String,Integer>>> cookieRequests = new java.util.HashMap<>();
    GeckoResult<java.util.Map<String,Integer>> cookieCounts() {
        GeckoResult<java.util.Map<String,Integer>> result = new GeckoResult<>();String id=java.util.UUID.randomUUID().toString();cookieRequests.put(id,result);
        ready.accept(done->{
            if(!cookieRequests.containsKey(id))return;
            if(ports.isEmpty()){cookieRequests.remove(id);result.completeExceptionally(new IllegalStateException("Cookie-overzicht niet beschikbaar"));return;}
            try{ports.get(0).postMessage(new JSONObject().put("type","site-cookie-counts").put("requestId",id));}
            catch(JSONException impossible){cookieRequests.remove(id);result.completeExceptionally(impossible);}
        },error->{GeckoResult<java.util.Map<String,Integer>> pending=cookieRequests.remove(id);if(pending!=null)pending.completeExceptionally(error);});
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            GeckoResult<java.util.Map<String,Integer>> pending=cookieRequests.remove(id);if(pending!=null)pending.completeExceptionally(new IllegalStateException("Cookie-overzicht duurde te lang"));
        },10000);return result;
    }
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
        if(preferences.getInt("site_permission_schema",0)<1) {
            runtime.getStorageController().clearData(org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS).accept(done->{
                preferences.edit().putInt("site_permission_schema",1).apply();profileReady=true;completeReadiness();
            },this::startupFailed);
        } else profileReady=true;
        runtime.getWebExtensionController().setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    WebExtension extension, String[] permissions, String[] origins, String[] data) {
                return ask(context.getString(R.string.prompt_install), extension, permissions, origins, data)
                        .map(answer -> new WebExtension.PermissionPromptResponse(answer == AllowOrDeny.ALLOW, false, false));
            }
            @Override public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension extension,
                    String[] permissions, String[] origins, String[] data) {
                return ask(context.getString(R.string.prompt_update), extension, permissions, origins, data);
            }
            @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension extension,
                    String[] permissions, String[] origins, String[] data) {
                return ask(context.getString(R.string.prompt_optional), extension, permissions, origins, data);
            }
        });
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/veyra-protection/", PROTECTION_ID)
                .then(extension -> runtime.getWebExtensionController().setAllowedInPrivateBrowsing(extension,true))
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
                                    if ("site-cookie-counts".equals(type)) {
                                        JSONObject data=(JSONObject)message;GeckoResult<java.util.Map<String,Integer>> pending=cookieRequests.remove(data.optString("requestId"));
                                        if(pending!=null){
                                            JSONObject counts=data.optJSONObject("counts");
                                            if(data.optBoolean("error")||counts==null){pending.completeExceptionally(new IllegalStateException("Cookie-overzicht niet beschikbaar"));return;}
                                            java.util.Map<String,Integer> safe=new java.util.TreeMap<>();java.util.Iterator<String> keys=counts.keys();
                                            while(keys.hasNext()&&safe.size()<2000){String host=keys.next();int count=counts.optInt(host,-1);
                                                if(host.matches("(?i)^[a-z0-9.-]{1,253}$")&&count>=0&&count<=100000)safe.put(host,count);}
                                            pending.complete(safe);
                                        }
                                    } else if ("ready".equals(type)) sendPreferences(source);
                                    else if ("configured".equals(type) && !protectionReady) {
                                        protectionReady = true;
                                        completeReadiness();
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

    private void completeReadiness() {
        if(protectionReady && profileReady && !readinessFinished){readinessFinished=true;ready.complete(null);}
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
            java.util.Set<String> effective=new java.util.HashSet<>(preferences.getStringSet("ad_exceptions",java.util.Collections.emptySet()));
            config.put("exceptions",new JSONArray(effective));
            for(java.util.Map.Entry<String,Boolean> entry:privateExceptions.entrySet()) {
                if(entry.getValue())effective.add(entry.getKey());else effective.remove(entry.getKey());
            }
            config.put("privateExceptions",new JSONArray(effective));
            port.postMessage(config);
        } catch (JSONException invalid) { throw new IllegalStateException(invalid); }
    }
    private GeckoResult<AllowOrDeny> ask(String title, WebExtension extension,
            String[] permissions, String[] origins, String[] data) {
        Activity activity = promptActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            return GeckoResult.fromValue(AllowOrDeny.DENY);
        StringBuilder message = new StringBuilder(name(extension)).append(activity.getString(R.string.prompt_access));
        for (String permission : permissions) message.append("\n• ").append(permission);
        for (String origin : origins) message.append("\n• ").append(origin);
        if (data.length > 0) {
            message.append(activity.getString(R.string.prompt_collection));
            for (String item : data) message.append("\n• ").append(item);
        }
        if (permissions.length + origins.length + data.length == 0) message.append(activity.getString(R.string.prompt_none));
        message.append(activity.getString(R.string.prompt_trust));
        GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        boolean[] completed = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message)
                .setPositiveButton(activity.getString(R.string.prompt_allow), (d, w) -> { completed[0] = true; result.complete(AllowOrDeny.ALLOW); })
                .setNegativeButton(activity.getString(R.string.prompt_cancel), (d, w) -> { completed[0] = true; result.complete(AllowOrDeny.DENY); })
                .setOnDismissListener(d -> {
                    List<androidx.appcompat.app.AlertDialog> pending = prompts.get(activity);
                    if (pending != null) { pending.remove(d); if (pending.isEmpty()) prompts.remove(activity); }
                    if (!completed[0]) { completed[0] = true; result.complete(AllowOrDeny.DENY); }
                }).create();
        prompts.computeIfAbsent(activity, key -> new ArrayList<>()).add(dialog);
        dialog.show();
        return result;
    }
    GeckoResult<Void> disableUserExtensions() {
        return runtime.getWebExtensionController().list().then(list -> {
            List<GeckoResult<WebExtension>> pending = new ArrayList<>();
            for (WebExtension extension : list) if (!extension.isBuiltIn && extension.metaData != null && extension.metaData.enabled)
                pending.add(runtime.getWebExtensionController().disable(extension,WebExtensionController.EnableSource.USER));
            return GeckoResult.allOf(pending).map(disabled -> null);
        });
    }
    static String name(WebExtension extension) {
        return extension.metaData != null && extension.metaData.name != null ? extension.metaData.name : extension.id;
    }
}
