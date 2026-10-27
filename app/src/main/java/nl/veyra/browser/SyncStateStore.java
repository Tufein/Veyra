package nl.veyra.browser;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;

/** Baseline contains only already-saved bookmarks/shortcuts; never connection secrets or history. */
final class SyncStateStore {
    private SyncStateStore() { }
    static String identity(String endpoint,String user){
        try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((endpoint+"\n"+user).getBytes(StandardCharsets.UTF_8));
            StringBuilder id=new StringBuilder();for(byte b:bytes)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return id.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static byte[] read(Context context,String identity){SharedPreferences store=context.getSharedPreferences("veyra_sync_state",0);
        if(!identity.equals(store.getString("identity","")))return null;String raw=store.getString("baseline",null);if(raw==null)return null;
        try{byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);BackupCodec.parse(bytes);return bytes;}catch(IllegalArgumentException invalid){return null;}
    }
    static void save(Context context,String identity,byte[] bytes){BackupCodec.parse(bytes);context.getSharedPreferences("veyra_sync_state",0).edit()
            .putString("identity",identity).putString("baseline",new String(bytes,StandardCharsets.UTF_8)).putLong("last_sync",System.currentTimeMillis()).apply();}
    static void clear(Context context){context.getSharedPreferences("veyra_sync_state",0).edit().clear().apply();}
}
