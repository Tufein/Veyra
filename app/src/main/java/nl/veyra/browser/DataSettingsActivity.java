package nl.veyra.browser;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.mozilla.geckoview.StorageController;

public final class DataSettingsActivity extends PageActivity {
    private boolean busy;
    @Override public void onCreate(Bundle state){super.onCreate(state);page(getString(R.string.ui_privacy_and_data_5621b));
        body(getString(R.string.ui_private_tabs_use_a_separate_gecko_private_session_thei_55769));
        button(getString(R.string.ui_website_data_and_permissions_70a57),R.drawable.ic_tune,v->startActivity(new Intent(this,SiteSettingsActivity.class)));
        button(getString(R.string.ui_import_and_export_2ae54),R.drawable.ic_library,v->startActivity(new Intent(this,BackupActivity.class)));
        button(getString(R.string.ui_encrypted_sync_158d1),R.drawable.ic_sync,v->startActivity(new Intent(this,SyncActivity.class)));
        MaterialCheckBox history=choice(getString(R.string.ui_clear_browsing_history_fc740)),cookies=choice(getString(R.string.ui_clear_cookies_and_website_storage_may_sign_you_out_a4e9c)),
                cache=choice(getString(R.string.ui_clear_image_and_network_caches_9edde)),permissions=choice(getString(R.string.ui_ask_for_site_permissions_again_cf4ba)),settings=choice(getString(R.string.ui_reset_personal_preferences_1014b));
        button(getString(R.string.ui_clear_selected_data_ce79a),R.drawable.ic_delete,v->{if(busy)return;
            final boolean clearHistory=history.isChecked(),clearCookies=cookies.isChecked(),clearCache=cache.isChecked(),clearPermissions=permissions.isChecked(),clearSettings=settings.isChecked();
            if(!clearHistory&&!clearCookies&&!clearCache&&!clearPermissions&&!clearSettings){message(getString(R.string.ui_choose_what_to_clear_first_7b441));return;}
            StringBuilder selected=new StringBuilder();for(MaterialCheckBox option:new MaterialCheckBox[]{history,cookies,cache,permissions,settings})if(option.isChecked())selected.append("\n• ").append(option.getText());
            new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_clear_selected_data_32834)).setMessage(selected+getString(R.string.ui_bookmarks_and_saved_files_are_retained_open_pages_clos_c0a09))
                    .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_clear_54e3b),(d,w)->{
                        busy=true;long flags=0;if(clearCookies)flags|=StorageController.ClearFlags.COOKIES|StorageController.ClearFlags.DOM_STORAGES|StorageController.ClearFlags.AUTH_SESSIONS;
                        if(clearCache)flags|=StorageController.ClearFlags.ALL_CACHES;if(clearPermissions)flags|=StorageController.ClearFlags.PERMISSIONS;
                        Runnable local=()->{
                            if(clearHistory)new HistoryStore(getSharedPreferences("veyra_history",0)).clear();
                            if(clearPermissions)new SitePermissions(getSharedPreferences("veyra_sites",0)).clear();
                            if(clearSettings){
                                android.content.SharedPreferences.Editor edit=getSharedPreferences("veyra",0).edit();
                                for(String key:new String[]{"theme","accent","dynamic_colors","nickname","compact_home","text_zoom","search","custom_search","blocking","https_only","ad_exceptions","resident_limit"})edit.remove(key);edit.apply();VeyraRuntime.get(this).updatePreferences();
                            }
                            busy=false;message(getString(R.string.ui_selected_data_cleared_2b733));setResult(RESULT_OK);finish();
                        };
                        if(flags==0){if(clearHistory)VeyraRuntime.get(this).closePagesForDataChange();local.run();}else{
                            VeyraRuntime engine=VeyraRuntime.get(this);engine.closePagesForDataChange();
                            engine.runtime.getStorageController().clearData(flags).accept(done->local.run(),error->{busy=false;message(getString(R.string.ui_the_engine_could_not_confirm_clearing_try_again_local_9d985));});
                        }
                    }).show();
        });
    }
    private MaterialCheckBox choice(String title){MaterialCheckBox box=new MaterialCheckBox(this);box.setText(title);box.setMinHeight(ui.dp(56));ui.add(content,box,8);return box;}
}
