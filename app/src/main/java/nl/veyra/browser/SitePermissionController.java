package nl.veyra.browser;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Explicit website consent plus Android's runtime permission check. */
final class SitePermissionController {
    static final int REQUEST=71;
    private final Activity activity;private final SitePermissions store;
    private GeckoSession.PermissionDelegate.Callback pending;
    private BooleanSupplier validPending;
    private String[] pendingPermissions;
    private final List<androidx.appcompat.app.AlertDialog> dialogs=new ArrayList<>();
    SitePermissionController(Activity activity){this.activity=activity;store=new SitePermissions(activity.getSharedPreferences("veyra_sites",0));}
    void androidPermissions(String[] requested,GeckoSession.PermissionDelegate.Callback callback,BooleanSupplier valid) {
        if(!valid.getAsBoolean()||requested==null||pending!=null){callback.reject();return;}
        java.util.Set<String> allowed=new java.util.HashSet<>(java.util.Arrays.asList(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION));
        for(String permission:requested)if(!allowed.contains(permission)){callback.reject();return;}
        List<String> missing=new ArrayList<>();for(String permission:requested)if(!granted(permission))missing.add(permission);
        if(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION) && !missing.contains(Manifest.permission.ACCESS_COARSE_LOCATION))missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(missing.isEmpty()){callback.grant();return;}
        pending=callback;validPending=valid;pendingPermissions=requested.clone();
        try{activity.requestPermissions(missing.toArray(new String[0]),REQUEST);}catch(RuntimeException error){pending=null;validPending=null;pendingPermissions=null;callback.reject();}
    }
    boolean result(int request,int[] grants) {
        if(request!=REQUEST)return false;
        GeckoSession.PermissionDelegate.Callback callback=pending;BooleanSupplier valid=validPending;String[] requested=pendingPermissions;pending=null;validPending=null;pendingPermissions=null;
        if(callback!=null){boolean allow=valid!=null&&valid.getAsBoolean()&&requested!=null&&requested.length>0;
            if(requested!=null)for(String permission:requested)allow&=granted(permission);
            if(allow)callback.grant();else callback.reject();}return true;
    }
    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
                || (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission) && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED);
    }
    GeckoResult<Integer> content(GeckoSession.PermissionDelegate.ContentPermission permission,boolean privateMode,BooleanSupplier valid) {
        if(permission.permission!=GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION)
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        GeckoResult<Integer> result=new GeckoResult<>();
        ask(permission.uri,new String[]{SitePermissions.LOCATION},privateMode,valid,allow->result.complete(allow
                ?GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW:GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY));return result;
    }
    void media(String uri,GeckoSession.PermissionDelegate.MediaSource[] video,GeckoSession.PermissionDelegate.MediaSource[] audio,
               GeckoSession.PermissionDelegate.MediaCallback callback,boolean privateMode,BooleanSupplier valid) {
        GeckoSession.PermissionDelegate.MediaSource camera=first(video,GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA);
        GeckoSession.PermissionDelegate.MediaSource microphone=first(audio,GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE);
        if((video!=null&&video.length>0&&camera==null)||(audio!=null&&audio.length>0&&microphone==null)){callback.reject();return;}
        List<String> types=new ArrayList<>();if(camera!=null)types.add(SitePermissions.CAMERA);if(microphone!=null)types.add(SitePermissions.MICROPHONE);
        if(types.isEmpty()){callback.reject();return;}
        ask(uri,types.toArray(new String[0]),privateMode,valid,allow->{if(allow)callback.grant(camera,microphone);else callback.reject();});
    }
    private interface Answer{void accept(boolean value);}
    private void ask(String uri,String[] types,boolean privateMode,BooleanSupplier valid,Answer answer) {
        String origin=SitePermissions.origin(uri);
        if(origin==null||!valid.getAsBoolean()||activity.isFinishing()||activity.isDestroyed()){answer.accept(false);return;}
        boolean allowed=true;for(String type:types){int state=store.get(uri,type,privateMode);if(state==SitePermissions.BLOCK){answer.accept(false);return;}allowed&=state==SitePermissions.ALLOW;}
        if(allowed){answer.accept(true);return;}
        MaterialCheckBox remember=new MaterialCheckBox(activity);remember.setText(activity.getString(R.string.ui_remember_for_this_website_826e0));remember.setMinHeight(Math.round(48*activity.getResources().getDisplayMetrics().density));remember.setEnabled(!privateMode);
        // Gecko stores geolocation decisions. Make that persistence explicit rather than offering a false one-time grant.
        if(!privateMode && java.util.Arrays.asList(types).contains(SitePermissions.LOCATION)){remember.setChecked(true);remember.setEnabled(false);}
        StringBuilder body=new StringBuilder(origin).append(activity.getString(R.string.ui_requests_access_to_d7e16));for(String type:types)body.append("\n• ").append(SitePermissions.label(activity,type));
        boolean[] completed={false};
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.ui_website_permission_e9fd6))
                .setMessage(body.toString()).setView(remember).setPositiveButton(activity.getString(R.string.ui_allow_aefe3),(d,w)->{
                    completed[0]=true;boolean allow=valid.getAsBoolean();if(allow&&remember.isChecked())for(String type:types)store.set(uri,type,SitePermissions.ALLOW,privateMode);answer.accept(allow);
                }).setNegativeButton(activity.getString(R.string.ui_deny_cd769),(d,w)->{
                    completed[0]=true;if(remember.isChecked())for(String type:types)store.set(uri,type,SitePermissions.BLOCK,privateMode);answer.accept(false);
                }).setOnDismissListener(d->{dialogs.remove(d);if(!completed[0]){if(remember.isChecked())for(String type:types)store.set(uri,type,SitePermissions.BLOCK,privateMode);answer.accept(false);}}).create();dialogs.add(dialog);dialog.show();
    }
    private static GeckoSession.PermissionDelegate.MediaSource first(GeckoSession.PermissionDelegate.MediaSource[] sources,int type) {
        if(sources!=null)for(GeckoSession.PermissionDelegate.MediaSource source:sources)if(source.source==type)return source;return null;
    }
    void suspend(){for(androidx.appcompat.app.AlertDialog dialog:new ArrayList<>(dialogs))dialog.dismiss();}
    void cancel(){suspend();if(pending!=null){pending.reject();pending=null;validPending=null;pendingPermissions=null;}}
}
