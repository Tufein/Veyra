package nl.veyra.browser;

import java.nio.charset.StandardCharsets;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SyncSecurityTest {
    private static final char[] PHRASE="A long test recovery phrase".toCharArray();
    @Test public void encryptedRoundTripUsesFreshSaltAndNonceAndHidesContents()throws Exception{
        byte[] plain="{\"bookmarks\":\"private contents\"}".getBytes(StandardCharsets.UTF_8);
        byte[] first=SyncCrypto.encrypt(plain,PHRASE),second=SyncCrypto.encrypt(plain,PHRASE);
        assertFalse(Arrays.equals(first,second));assertFalse(new String(first,StandardCharsets.UTF_8).contains("private contents"));
        assertArrayEquals(plain,SyncCrypto.decrypt(first,PHRASE));
    }
    @Test public void wrongPhraseAndTamperingAreRejectedBeforeImport()throws Exception{
        byte[] bytes=SyncCrypto.encrypt("data".getBytes(StandardCharsets.UTF_8),PHRASE);
        try{SyncCrypto.decrypt(bytes,"A different recovery phrase".toCharArray());fail();}catch(javax.crypto.AEADBadTagException expected){}
        JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));byte[] encrypted=Base64.getDecoder().decode(envelope.getString("ciphertext"));encrypted[0]^=1;
        envelope.put("ciphertext",Base64.getEncoder().encodeToString(encrypted));try{SyncCrypto.decrypt(envelope.toString().getBytes(StandardCharsets.UTF_8),PHRASE);fail();}catch(javax.crypto.AEADBadTagException expected){}
    }
    @Test public void unsupportedOrExpensiveKdfMetadataIsRejected()throws Exception{
        JSONObject envelope=new JSONObject(new String(SyncCrypto.encrypt(new byte[]{1},PHRASE),StandardCharsets.UTF_8));envelope.put("iterations",Integer.MAX_VALUE);
        try{SyncCrypto.decrypt(envelope.toString().getBytes(StandardCharsets.UTF_8),PHRASE);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void transportRejectsHttpCredentialsAndFragments(){
        for(String address:new String[]{"http://example.org/file","https://user:pass@example.org/file","https://example.org/file#fragment"})
            try{SyncClient.endpoint(address);fail(address);}catch(IllegalArgumentException expected){}
    }
    @Test public void existingFilesRequireStrongEtagAndRedirectsDoNotFollow()throws Exception{
        Fake missing=new Fake(200,"data",null);try{new SyncClient(url->missing).fetch("https://example.org/sync","user","password");fail();}catch(IOException expected){}
        Fake redirect=new Fake(302,"","\"one\"");try{new SyncClient(url->redirect).fetch("https://example.org/sync","user","password");fail();}catch(IOException expected){}
        assertFalse(redirect.getInstanceFollowRedirects());assertTrue(redirect.disconnected);
    }
    @Test public void uploadsUseConditionalHeadersAndConflictsFail()throws Exception{
        Fake update=new Fake(204,"",null);new SyncClient(url->update).upload("https://example.org/sync","user","password",new byte[]{1},"\"one\"");
        assertEquals("\"one\"",update.getRequestProperty("If-Match"));assertNull(update.getRequestProperty("If-None-Match"));
        Fake create=new Fake(201,"",null);new SyncClient(url->create).upload("https://example.org/sync","","",new byte[]{1},null);assertEquals("*",create.getRequestProperty("If-None-Match"));
        Fake conflict=new Fake(412,"",null);try{new SyncClient(url->conflict).upload("https://example.org/sync","","",new byte[]{1},"\"old\"");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Conflict"));}
    }
    @Test public void remoteReadIsBoundedAnd404MeansCreate()throws Exception{
        Fake absent=new Fake(404,"",null);assertNull(new SyncClient(url->absent).fetch("https://example.org/sync","","").data);
        Fake oversized=new Fake(200,new byte[SyncCrypto.MAX_ENVELOPE+1],"\"one\"");try{new SyncClient(url->oversized).fetch("https://example.org/sync","","");fail();}catch(IOException expected){}
    }
    private static final class Fake extends HttpURLConnection{
        final int code;final byte[] body;final String etag;boolean disconnected;
        Fake(int code,String body,String etag)throws Exception{this(code,body.getBytes(StandardCharsets.UTF_8),etag);}
        Fake(int code,byte[] body,String etag)throws Exception{super(new URL("https://example.org/sync"));this.code=code;this.body=body;this.etag=etag;}
        public int getResponseCode(){return code;}public String getHeaderField(String name){return name.equals("ETag")?etag:null;}
        public InputStream getInputStream(){return new ByteArrayInputStream(body);}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
    }
}
