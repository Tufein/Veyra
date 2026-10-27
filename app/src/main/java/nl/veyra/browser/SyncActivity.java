package nl.veyra.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;
import org.json.JSONObject;

/** End-to-end encrypted sync, explicit conflict choices, and conditional server writes. */
public final class SyncActivity extends PageActivity {
    private TextInputEditText server,user,password,phrase;
    private TextView status;
    private MaterialButton sync,forget;
    private CheckBox remember;
    private boolean busy;
    private volatile boolean closed;
    private char[] activeRecovery;
    private final java.util.concurrent.ExecutorService network=java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);page(getString(R.string.ui_encrypted_sync_158d1));
        body(getString(R.string.ui_sync_bookmarks_and_the_start_page_through_your_own_htt_6b549));
        server=field(getString(R.string.ui_https_address_of_the_sync_file_286d4),"");
        user=field(getString(R.string.ui_server_username_a3be5),"");password=field(getString(R.string.ui_server_password_97db4),"");
        phrase=field(getString(R.string.ui_recovery_phrase_at_least_12_characters_90487),"");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        phrase.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for(TextInputEditText input:new TextInputEditText[]{server,user,password,phrase})input.setSaveEnabled(false);
        remember=new CheckBox(this);remember.setText(R.string.ui_store_connection_details_encrypted_on_this_device_14832);remember.setMinHeight(ui.dp(56));ui.add(content,remember,14);
        try {
            String saved=LocalSecrets.read(this);
            if(saved!=null){JSONObject values=new JSONObject(saved);server.setText(values.optString("server"));user.setText(values.optString("user"));password.setText(values.optString("password"));phrase.setText(values.optString("phrase"));remember.setChecked(true);}
        } catch(Exception unavailable){body(getString(R.string.ui_stored_connection_details_could_not_be_decrypted_enter_987c4));}
        status=ui.text(R.string.ui_not_synced_yet_db346,14,ui.muted);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);ui.add(content,status,16);
        sync=button(getString(R.string.ui_merge_and_sync_b2e10),R.drawable.ic_sync,v->begin());
        forget=button(getString(R.string.ui_forget_saved_connection_b7e28),R.drawable.ic_delete,v->{
            LocalSecrets.forget(this);SyncStateStore.clear(this);password.setText("");phrase.setText("");remember.setChecked(false);message(getString(R.string.ui_local_connection_forgotten_the_server_file_was_not_del_59363));
        });
        body(getString(R.string.sync_protocol_help));
    }
    private SharedPreferences preferences(){return getSharedPreferences("veyra",0);}
    private void begin() {
        if(busy)return;
        final String endpoint;
        try{endpoint=SyncClient.endpoint(server.getText().toString());if(phrase.length()<12)throw new IllegalArgumentException(getString(R.string.ui_use_a_recovery_phrase_of_at_least_twelve_characters_ca958));}
        catch(IllegalArgumentException invalid){message(invalid.getMessage());return;}
        String username=user.getText().toString(),secret=password.getText().toString();char[] recovery=phrase.getText().toString().toCharArray();boolean keep=remember.isChecked();
        activeRecovery=recovery;String identity=SyncStateStore.identity(endpoint,username);
        setBusy(true);status.setText(R.string.ui_fetching_encrypted_server_file_2619f);
        network.submit(()->{
            try {
                if(keep)try{LocalSecrets.save(this,new JSONObject().put("server",endpoint).put("user",username).put("password",secret).put("phrase",new String(recovery)).toString());}
                    catch(Exception unavailable){runOnUiThread(()->{if(!closed)message(getString(R.string.ui_secure_storage_failed_the_connection_will_only_be_used_47707));});}
                else LocalSecrets.forget(this);
                SyncClient client=new SyncClient();SyncClient.Remote remote=client.fetch(endpoint,username,secret);
                byte[] remoteBytes=remote.data==null?null:SyncCrypto.decrypt(remote.data,recovery);
                if(remoteBytes!=null)BackupCodec.parse(remoteBytes);
                byte[] baseline=remoteBytes==null?null:SyncStateStore.read(this,identity);
                runOnUiThread(()->{
                    if(closed){Arrays.fill(recovery,'\0');return;}
                    try {
                        byte[] local=BackupCodec.export(preferences(),false);
                        boolean starter=baseline==null && remoteBytes!=null && !preferences().contains("bookmarks") && !preferences().contains("shortcuts");
                        SyncMerger.Result planned=starter?new SyncMerger.Result(SyncMerger.sitesOnly(remoteBytes),0,0):SyncMerger.merge(local,remoteBytes,baseline,true);
                        confirm(endpoint,username,secret,recovery,identity,client,remote,remoteBytes,baseline,local,starter,planned);
                    }catch(Exception error){Arrays.fill(recovery,'\0');fail(error);}
                });
            }catch(Exception error){Arrays.fill(recovery,'\0');fail(error);}
        });
    }
    private void confirm(String endpoint,String username,String secret,char[] recovery,String identity,SyncClient client,
                         SyncClient.Remote remote,byte[] remoteBytes,byte[] baseline,byte[] local,boolean starter,SyncMerger.Result plan) {
        MaterialAlertDialogBuilder dialog=new MaterialAlertDialogBuilder(this)
                .setTitle(remoteBytes==null?R.string.ui_create_a_new_encrypted_file_868cb:starter?R.string.sync_use_server:R.string.ui_merge_server_data_d6504)
                .setMessage(starter?getString(R.string.sync_starter_help):remoteBytes==null?getString(R.string.ui_your_bookmarks_and_start_page_will_be_encrypted_on_the_a4219):
                        plan.conflicts>0?getString(R.string.sync_conflict_preview,plan.conflicts):getString(R.string.sync_preview,plan.conflicts,plan.removedLocal))
                .setNeutralButton(R.string.prompt_cancel,(d,w)->{Arrays.fill(recovery,'\0');finishBusy(getString(R.string.sync_cancelled));})
                .setOnCancelListener(d->{Arrays.fill(recovery,'\0');finishBusy(getString(R.string.sync_cancelled));})
                .setPositiveButton(plan.conflicts>0?R.string.sync_keep_local:R.string.ui_sync_9210d,(d,w)->
                        upload(endpoint,username,secret,recovery,identity,client,remote,remoteBytes,baseline,local,starter,true));
        if(plan.conflicts>0)dialog.setNegativeButton(R.string.sync_keep_remote,(d,w)->
                upload(endpoint,username,secret,recovery,identity,client,remote,remoteBytes,baseline,local,false,false));
        dialog.show();
    }
    private void upload(String endpoint,String username,String secret,char[] recovery,String identity,SyncClient client,
                        SyncClient.Remote remote,byte[] remoteBytes,byte[] baseline,byte[] local,boolean starter,boolean preferLocal) {
        if(!unchanged(local)){Arrays.fill(recovery,'\0');finishBusy(getString(R.string.sync_local_changed));return;}
        final byte[] merged;final SyncMerger.Result result;
        try{result=starter?new SyncMerger.Result(SyncMerger.sitesOnly(remoteBytes),0,0):SyncMerger.merge(local,remoteBytes,baseline,preferLocal);merged=result.bytes;BackupCodec.parse(merged);}
        catch(Exception invalid){Arrays.fill(recovery,'\0');fail(invalid);return;}
        if(result.conflicts>0) {
            BackupCodec.Preview selected=BackupCodec.parse(merged);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.sync_confirm_changes)
                    .setMessage(getString(R.string.sync_final_count,result.removedLocal,selected.bookmarks.size(),selected.shortcuts.size()))
                    .setNegativeButton(R.string.prompt_cancel,(d,w)->finishBusy(getString(R.string.sync_cancelled)))
                    .setOnCancelListener(d->finishBusy(getString(R.string.sync_cancelled)))
                    .setPositiveButton(R.string.ui_sync_9210d,(d,w)->performUpload(endpoint,username,secret,recovery,identity,client,remote,local,merged)).show();
        } else performUpload(endpoint,username,secret,recovery,identity,client,remote,local,merged);
    }
    private void performUpload(String endpoint,String username,String secret,char[] recovery,String identity,SyncClient client,SyncClient.Remote remote,byte[] local,byte[] merged) {
        if(!unchanged(local)){finishBusy(getString(R.string.sync_local_changed));return;}
        status.setText(R.string.ui_encrypting_and_saving_dbb19);
        network.submit(()->{
            try {
                byte[] encrypted=SyncCrypto.encrypt(merged,recovery);
                if(closed||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                client.upload(endpoint,username,secret,encrypted,remote.etag);
                SyncStateStore.save(getApplicationContext(),identity,merged);
                runOnUiThread(()->{
                    if(closed)return;
                    if(!unchanged(local)){finishBusy(getString(R.string.ui_server_saved_local_data_changed_in_the_meantime_sync_a_cbba5));return;}
                    BackupCodec.replaceSites(preferences(),BackupCodec.parse(merged));finishBusy(getString(R.string.ui_synced_your_data_is_stored_encrypted_on_the_server_67e4c));
                });
            }catch(Exception error){fail(error);}finally{Arrays.fill(recovery,'\0');}
        });
    }
    private boolean unchanged(byte[] snapshot){try{return Arrays.equals(snapshot,BackupCodec.export(preferences(),false));}catch(IllegalArgumentException oversized){return false;}}
    private void fail(Exception error) {
        runOnUiThread(()->{if(closed)return;
            String message;
            if(error instanceof javax.crypto.AEADBadTagException)message=getString(R.string.ui_incorrect_recovery_phrase_or_damaged_file_nothing_chan_daffe);
            else if(error instanceof javax.net.ssl.SSLException)message=getString(R.string.sync_tls_error);
            else if(error instanceof java.net.SocketTimeoutException)message=getString(R.string.sync_timeout);
            else message=getString(R.string.ui_not_synced_efac6)+(error.getMessage()==null?getString(R.string.ui_check_your_connection_and_recovery_phrase_cced8):error.getMessage());
            finishBusy(message);
        });
    }
    private void setBusy(boolean value) {
        busy=value;sync.setEnabled(!value);if(forget!=null)forget.setEnabled(!value);remember.setEnabled(!value);
        for(TextInputEditText input:new TextInputEditText[]{server,user,password,phrase})input.setEnabled(!value);
    }
    private void finishBusy(String message){if(activeRecovery!=null){Arrays.fill(activeRecovery,'\0');activeRecovery=null;}setBusy(false);status.setText(ErrorText.display(this,message));}
    @Override protected void onDestroy(){closed=true;if(activeRecovery!=null)Arrays.fill(activeRecovery,'\0');network.shutdownNow();super.onDestroy();}
}
