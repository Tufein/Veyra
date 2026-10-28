package nl.veyra.browser;

import android.os.Bundle;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.mozilla.geckoview.StorageController;

public final class SiteSettingsActivity extends PageActivity {
    private String url,lastRenderedUrl;private int visibleLimit=100;private SitePermissions permissions;
    private java.util.Map<String,Integer> cookies=new java.util.TreeMap<>();private String cookieState="";
    @Override public void onCreate(Bundle state){super.onCreate(state);url=state==null?getIntent().getStringExtra("url"):state.getString("url");if(state!=null)visibleLimit=state.getInt("visibleLimit",100);permissions=new SitePermissions(getSharedPreferences("veyra_sites",0));render();
        VeyraRuntime.get(this).cookieCounts().accept(counts->{if(isDestroyed())return;cookies=counts;cookieState=getString(R.string.site_cookies_explained);render();},
                error->{if(!isDestroyed()){cookieState=getString(R.string.site_cookies_unavailable);render();}});
    }
    private void render(){
        int scroll=java.util.Objects.equals(lastRenderedUrl,url)&&pageScroll!=null?pageScroll.getScrollY():0;lastRenderedUrl=url;
        page(getString(R.string.site_settings));pageScroll.post(()->pageScroll.scrollTo(0,scroll));String origin=SitePermissions.origin(url);
        if(origin==null){body(getString(R.string.ui_choose_a_known_https_website_this_overview_shows_permi_fb2b3));
            if(!cookieState.isEmpty())body(cookieState);
            java.util.Set<String> origins=new java.util.TreeSet<>(permissions.origins());
            for(String host:cookies.keySet())origins.add("https://"+host);
            for(HistoryStore.Visit visit:new HistoryStore(getSharedPreferences("veyra_history",0)).read("")){String value=SitePermissions.origin(visit.url);if(value!=null)origins.add(value);}
            int shown=0;for(String value:origins){if(shown++>=visibleLimit)break;ui.site(content,SettingsViews.host(value),value,cookies.containsKey(java.net.URI.create(value).getHost())?getString(R.string.site_cookie_count,cookies.get(java.net.URI.create(value).getHost())):null,v->{url=value;render();});}
            if(origins.size()>visibleLimit)button(getString(R.string.show_more),R.drawable.ic_add,v->{visibleLimit+=100;render();});
            if(origins.isEmpty())body(getString(R.string.ui_no_known_websites_yet_7cc63));return;
        }
        body(origin);if(!cookieState.isEmpty())body(cookieState);
        if(cookies.containsKey(java.net.URI.create(origin).getHost()))body(getString(R.string.site_cookie_count,cookies.get(java.net.URI.create(origin).getHost())));
        body(getString(R.string.ui_these_choices_apply_to_this_https_origin_android_also_c400b));
        button(getString(R.string.site_android_permissions),R.drawable.ic_tune,v->{
            try{startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
            catch(android.content.ActivityNotFoundException|SecurityException unavailable){message(getString(R.string.site_android_permissions_fallback));}
        });
        for(String type:new String[]{SitePermissions.CAMERA,SitePermissions.MICROPHONE,SitePermissions.LOCATION}){
            int value=permissions.get(origin,type,false);String[] labels={getString(R.string.ui_always_ask_2070c),getString(R.string.ui_allow_aefe3),getString(R.string.ui_block_76d58)};
            LinearLayout card=ui.card(content,false);ui.navigation(card,R.string.site_permission_choice,SitePermissions.label(this,type)+" · "+labels[value],v->
                    new MaterialAlertDialogBuilder(this).setTitle(SitePermissions.label(this,type)).setSingleChoiceItems(labels,value,(d,which)->{
                        permissions.set(origin,type,which,false);d.dismiss();
                        // Close pages before revoking, so active camera/location use cannot linger.
                        VeyraRuntime engine=VeyraRuntime.get(this);engine.closePagesForDataChange();
                        engine.runtime.getStorageController().getPermissions(origin).accept(list->{
                            for(org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission permission:list)
                                engine.runtime.getStorageController().setPermission(permission,org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT);
                        },error->message(getString(R.string.ui_engine_permissions_could_not_be_refreshed_restart_veyr_fdaa7)));render();
                    }).setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).show());
        }
        button(getString(R.string.ui_ask_for_permissions_again_b3c09),R.drawable.ic_tune,v->{
            permissions.reset(origin);VeyraRuntime engine=VeyraRuntime.get(this);engine.closePagesForDataChange();
            engine.runtime.getStorageController().getPermissions(origin).accept(list->{for(org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission p:list)
                engine.runtime.getStorageController().setPermission(p,org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT);message(getString(R.string.ui_permissions_reset_9fbaa));render();},error->message(getString(R.string.ui_reset_not_completed_try_again_0f4b6)));
        });
        button(getString(R.string.ui_clear_website_data_e4ca6),R.drawable.ic_delete,v->new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_clear_website_data_27434))
                .setMessage(getString(R.string.ui_cookies_storage_cache_and_permissions_for_01444)+SettingsViews.host(origin)+getString(R.string.ui_will_be_cleared_you_may_be_signed_out_open_pages_close_fce56))
                .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_clear_54e3b),(d,w)->{
                    VeyraRuntime engine=VeyraRuntime.get(this);engine.closePagesForDataChange();
                    engine.runtime.getStorageController().clearDataFromHost(java.net.URI.create(origin).getHost(),StorageController.ClearFlags.SITE_DATA|StorageController.ClearFlags.AUTH_SESSIONS)
                            .accept(done->{permissions.resetHost(java.net.URI.create(origin).getHost());cookies.remove(java.net.URI.create(origin).getHost());message(getString(R.string.ui_website_data_cleared_2f7fd));render();},error->message(getString(R.string.ui_clearing_not_completed_try_again_de1a3)));
                }).show());
    }
    @Override public void onSaveInstanceState(Bundle out){out.putString("url",url);out.putInt("visibleLimit",visibleLimit);super.onSaveInstanceState(out);}
    @Override void handleBack(){if(url!=null && getIntent().getStringExtra("url")==null){url=null;render();}else finish();}

}
