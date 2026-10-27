package nl.veyra.browser;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

/** Versioned authenticated encryption; the server never receives the recovery passphrase. */
final class SyncCrypto {
    static final int ITERATIONS=600000,MAX_ENVELOPE=4*1024*1024;
    private static final byte[] AAD="VeyraSync:1:PBKDF2-HMAC-SHA256:600000".getBytes(StandardCharsets.UTF_8);
    private SyncCrypto() { }
    static byte[] encrypt(byte[] plaintext,char[] passphrase) throws java.security.GeneralSecurityException {
        if(plaintext.length>BackupCodec.MAX_BYTES)throw new IllegalArgumentException("Te veel synchronisatiegegevens.");
        if(passphrase.length<12)throw new IllegalArgumentException("Gebruik een herstelzin van minstens twaalf tekens.");
        byte[] salt=new byte[16],nonce=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(nonce);
        byte[] key=derive(passphrase,salt);
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);
            JSONObject envelope=new JSONObject().put("format","veyra-sync").put("version",1).put("iterations",ITERATIONS)
                    .put("salt",encode(salt)).put("nonce",encode(nonce)).put("ciphertext",encode(cipher.doFinal(plaintext)));
            return envelope.toString().getBytes(StandardCharsets.UTF_8);
        }catch(org.json.JSONException impossible){throw new IllegalArgumentException("Synchronisatieformaat",impossible);}finally{Arrays.fill(key,(byte)0);}
    }
    static byte[] decrypt(byte[] bytes,char[] passphrase) throws java.security.GeneralSecurityException {
        if(bytes.length>MAX_ENVELOPE)throw new IllegalArgumentException("Synchronisatiebestand te groot.");
        try {
            JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"veyra-sync".equals(envelope.getString("format"))||envelope.getInt("version")!=1||envelope.getInt("iterations")!=ITERATIONS)
                throw new IllegalArgumentException("Niet-ondersteunde synchronisatieversie.");
            byte[] salt=decode(envelope.getString("salt")),nonce=decode(envelope.getString("nonce")),ciphertext=decode(envelope.getString("ciphertext"));
            if(salt.length!=16||nonce.length!=12||ciphertext.length<16||ciphertext.length>BackupCodec.MAX_BYTES+16)throw new IllegalArgumentException("Ongeldig synchronisatiebestand.");
            byte[] key=derive(passphrase,salt);
            try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);return cipher.doFinal(ciphertext);}
            finally{Arrays.fill(key,(byte)0);}
        }catch(org.json.JSONException invalid){throw new IllegalArgumentException("Ongeldig synchronisatiebestand.",invalid);}
    }
    private static byte[] derive(char[] password,byte[] salt)throws java.security.GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private static String encode(byte[] bytes){return java.util.Base64.getEncoder().encodeToString(bytes);}
    private static byte[] decode(String value){return java.util.Base64.getDecoder().decode(value);}
}
