package nl.veyra.browser;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Optional device-local storage for sync credentials, encrypted with Android Keystore. */
final class LocalSecrets {
    private static final String ALIAS="veyra.sync.credentials.v1";
    private LocalSecrets() { }
    private static SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    static void save(Context context,String value)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences("veyra_sync_secret",0).edit().putString("nonce",java.util.Base64.getEncoder().encodeToString(cipher.getIV()))
                .putString("data",java.util.Base64.getEncoder().encodeToString(encrypted)).apply();
    }
    static String read(Context context)throws Exception {
        android.content.SharedPreferences preferences=context.getSharedPreferences("veyra_sync_secret",0);String value=preferences.getString("data",null);if(value==null)return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Base64.getDecoder().decode(preferences.getString("nonce",""))));
        return new String(cipher.doFinal(java.util.Base64.getDecoder().decode(value)),StandardCharsets.UTF_8);
    }
    static void forget(Context context){context.getSharedPreferences("veyra_sync_secret",0).edit().clear().apply();}
}
