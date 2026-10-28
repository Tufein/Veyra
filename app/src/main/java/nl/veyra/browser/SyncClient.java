package nl.veyra.browser;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS/WebDAV file transport with bounded reads, no redirects, and conditional writes. */
final class SyncClient {
    static final class Remote {final byte[] data;final String etag;Remote(byte[] data,String etag){this.data=data;this.etag=etag;}}
    interface Connections {HttpURLConnection open(URL url)throws java.io.IOException;}
    private final Connections connections;
    SyncClient(){this(url->(HttpURLConnection)url.openConnection());}
    SyncClient(Connections connections){this.connections=connections;}
    static String endpoint(String raw){String value=UrlPolicy.normalizeWebUrl(raw.trim());
        if(value==null||!value.startsWith("https://")||java.net.URI.create(value).getRawFragment()!=null||value.length()>2048)throw new IllegalArgumentException("Gebruik een volledig HTTPS-adres naar een synchronisatiebestand.");return value;}
    private HttpURLConnection connection(String endpoint,String user,String password,String method)throws java.io.IOException {
        if(user.contains(":")||user.contains("\n")||password.contains("\n"))throw new IllegalArgumentException("Ongeldige servergegevens.");
        HttpURLConnection connection=connections.open(new URL(endpoint(endpoint)));connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestMethod(method);connection.setUseCaches(false);
        connection.setRequestProperty("Accept","application/json");
        if(!user.isEmpty()||!password.isEmpty())connection.setRequestProperty("Authorization","Basic "+java.util.Base64.getEncoder().encodeToString((user+":"+password).getBytes(StandardCharsets.UTF_8)));
        return connection;
    }
    Remote fetch(String endpoint,String user,String password)throws java.io.IOException {
        HttpURLConnection connection=connection(endpoint,user,password,"GET");
        try{
            int code=connection.getResponseCode();if(code==404)return new Remote(null,null);check(code);
            String etag=connection.getHeaderField("ETag");if(!strongEtag(etag))throw new java.io.IOException("De server biedt geen bruikbare versiecontrole (ETag). Er is niets overschreven.");
            try(InputStream input=connection.getInputStream()){
                ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>SyncCrypto.MAX_ENVELOPE)throw new java.io.IOException("Synchronisatiebestand te groot.");output.write(buffer,0,count);}
                return new Remote(output.toByteArray(),etag);
            }
        }finally{connection.disconnect();}
    }
    void upload(String endpoint,String user,String password,byte[] bytes,String etag)throws java.io.IOException {
        if(etag!=null&&!strongEtag(etag))throw new java.io.IOException("De server biedt geen bruikbare versiecontrole (ETag). Er is niets overschreven.");
        if(bytes.length>SyncCrypto.MAX_ENVELOPE)throw new java.io.IOException("Synchronisatiebestand te groot.");
        HttpURLConnection connection=connection(endpoint,user,password,"PUT");
        try{
            connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty(etag==null?"If-None-Match":"If-Match",etag==null?"*":etag);
            connection.setFixedLengthStreamingMode(bytes.length);try(java.io.OutputStream output=connection.getOutputStream()){output.write(bytes);}
            int code=connection.getResponseCode();check(code);
            if(code!=200&&code!=201&&code!=204)throw new java.io.IOException("De server heeft opslaan nog niet bevestigd. Synchroniseer opnieuw.");
        }finally{connection.disconnect();}
    }
    private static boolean strongEtag(String value) {
        if(value==null||value.length()<2||value.length()>4096||value.charAt(0)!='"'||value.charAt(value.length()-1)!='"')return false;
        for(int i=1;i<value.length()-1;i++){char c=value.charAt(i);if(c!='!'&&(c<'#'||c>'~')&&(c<128||c>255))return false;}
        return true;
    }
    private static void check(int code)throws java.io.IOException {
        if(code>=200&&code<300)return;
        if(code==412||code==409)throw new java.io.IOException("Conflict: het serverbestand is ondertussen gewijzigd. Synchroniseer opnieuw; er is niets overschreven.");
        if(code==401||code==403)throw new java.io.IOException("De server heeft de toegang geweigerd. Controleer je inloggegevens.");
        if(code>=300&&code<400)throw new java.io.IOException("De server verwijst door. Gebruik het definitieve HTTPS-bestandsadres.");
        throw new java.io.IOException("De synchronisatieserver antwoordde met HTTP "+code+".");
    }
}
