package nl.veyra.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.InputStream;
import java.io.OutputStream;

public final class BackupActivity extends PageActivity {
    private static final int EXPORT=11,IMPORT=12;
    private boolean includeSettings, includeTabs;
    private final java.util.concurrent.ExecutorService files=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);includeSettings=state!=null&&state.getBoolean("settings");includeTabs=state!=null&&state.getBoolean("tabs");page(getString(R.string.ui_import_and_export_2ae54));
        body(getString(R.string.ui_take_your_bookmarks_and_start_page_with_you_in_a_local_aca50));
        MaterialSwitch settings=new MaterialSwitch(this);settings.setText(getString(R.string.ui_include_personal_preferences_4b857));settings.setMinHeight(ui.dp(56));
        settings.setChecked(includeSettings);settings.setOnCheckedChangeListener((v,on)->includeSettings=on);ui.add(content,settings,16);
        MaterialSwitch tabs=new MaterialSwitch(this);tabs.setText(R.string.backup_include_tabs);tabs.setMinHeight(ui.dp(56));tabs.setChecked(includeTabs);
        tabs.setOnCheckedChangeListener((v,on)->includeTabs=on);ui.add(content,tabs,8);
        button(getString(R.string.ui_export_backup_2bb69),R.drawable.ic_share,v->launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Veyra-backup.json"),EXPORT));
        button(getString(R.string.ui_import_backup_6cf71),R.drawable.ic_library,v->launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT));
        body(getString(R.string.ui_import_merges_websites_and_preserves_existing_entries_33727));
    }
    private void launch(Intent intent,int request) {try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException unavailable){message(getString(R.string.ui_no_file_picker_is_available_7a3e5));}}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();boolean settings=includeSettings,restoreTabs=includeTabs;SharedPreferences preferences=getSharedPreferences("veyra",MODE_PRIVATE);
        final byte[] backup;
        try{backup=request==EXPORT?BackupCodec.export(preferences,settings,includeTabs):null;}
        catch(IllegalArgumentException tooLarge){message(tooLarge.getMessage());return;}
        files.submit(()->{
            try {
                if(request==EXPORT) {
                    try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new java.io.IOException();output.write(backup);}
                    runOnUiThread(()->{if(!isDestroyed())message(getString(R.string.ui_backup_saved_5f54c));});
                } else if(request==IMPORT) {
                    BackupCodec.Preview preview;
                    try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new java.io.IOException();preview=BackupCodec.parse(BackupCodec.read(input));}
                    runOnUiThread(()->{if(isDestroyed()||isFinishing())return;
                        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_merge_backup_29005))
                                .setMessage((!restoreTabs||preview.tabs.isEmpty()?"":getString(R.string.backup_import_tabs,preview.tabs.size()))+preview.bookmarks.size()+getString(R.string.ui_bookmarks_and_54838)+preview.shortcuts.size()+getString(R.string.ui_shortcuts_existing_websites_are_retained_1077f)
                                        +(settings?getString(R.string.ui_supported_preferences_will_be_applied_25c77):getString(R.string.ui_preferences_will_not_change_45a4e)))
                                .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_import_1b9b2),(d,w)->{
                                    try{BackupCodec.merge(preferences,preview,settings,restoreTabs);message(getString(R.string.ui_backup_imported_fc396));setResult(RESULT_OK);finish();}
                                    catch(IllegalArgumentException invalid){message(invalid.getMessage());}
                                }).show();
                    });
                }
            }catch(Exception error){runOnUiThread(()->{if(!isDestroyed())message(getString(R.string.ui_not_completed_9db37)+(error.getMessage()==null?getString(R.string.ui_file_unavailable_a3a8d):error.getMessage()));});}
        });
    }
    @Override public void onSaveInstanceState(Bundle out){out.putBoolean("settings",includeSettings);out.putBoolean("tabs",includeTabs);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){files.shutdownNow();super.onDestroy();}
}
