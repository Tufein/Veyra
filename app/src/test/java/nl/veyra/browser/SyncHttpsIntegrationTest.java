package nl.veyra.browser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.*;
import static org.junit.Assert.*;

/** Real local TLS/HTTP traffic between independent clients; no Android-device claim. */
public final class SyncHttpsIntegrationTest {
    private static Path directory;
    private static SSLContext tls;
    private javax.net.ssl.SSLServerSocket server;
    private ExecutorService executor;
    private String endpoint;
    private byte[] stored;
    private int revision, writes, redirected;
    private boolean redirect, rejectingCertificate;private volatile Throwable serverFailure;
    private static final char[] PHRASE = "Temporary integration-test recovery phrase".toCharArray();

    @BeforeClass public static void certificate() throws Exception {
        directory = Files.createTempDirectory("veyra-sync-tls-");
        String password = java.util.UUID.randomUUID().toString();
        Path file = directory.resolve("tls.p12");
        ProcessBuilder keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keystore", file.toString(), "-storetype", "PKCS12",
                "-storepass:env", "VEYRA_TEST_TLS_PASSWORD", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-noprompt");
        keytool.environment().put("VEYRA_TEST_TLS_PASSWORD", password);
        keytool.redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile());
        assertEquals("Generate an ephemeral test certificate", 0, keytool.start().waitFor());
        KeyStore keys = KeyStore.getInstance("PKCS12", "SUN");
        try (java.io.InputStream input = Files.newInputStream(file)) { keys.load(input, password.toCharArray()); }
        KeyManagerFactory km = KeyManagerFactory.getInstance("SunX509", "SunJSSE"); km.init(keys, password.toCharArray());
        TrustManagerFactory tm = TrustManagerFactory.getInstance("PKIX", "SunJSSE"); tm.init(keys);
        tls = SSLContext.getInstance("TLS", "SunJSSE"); tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
    }

    @AfterClass public static void removeCertificate() throws Exception {
        if (directory != null) try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Before public void startServer() throws Exception {
        server = (javax.net.ssl.SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
        executor = Executors.newCachedThreadPool();
        endpoint = "https://localhost:" + server.getLocalPort() + "/sync";
        executor.submit(() -> {
            while (!server.isClosed()) try {
                java.net.Socket socket = server.accept();
                executor.submit(() -> respond(socket));
            } catch (IOException closed) { if (!server.isClosed()) throw new java.io.UncheckedIOException(closed); }
        });
    }

    private void respond(java.net.Socket socket) {
        try (java.net.Socket connection = socket) {
            connection.setSoTimeout(5000);
            java.io.InputStream input = new java.io.BufferedInputStream(connection.getInputStream());
            String request = line(input); if (request == null) return;
            java.util.Map<String,String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (int i=0;i<64;i++) { String header=line(input);if(header==null||header.isEmpty())break;
                int colon=header.indexOf(':');if(colon>0)headers.put(header.substring(0,colon),header.substring(colon+1).trim()); }
            int length=Integer.parseInt(headers.getOrDefault("Content-Length","0"));if(length<0||length>4*1024*1024)throw new IOException("Invalid fixture request");
            byte[] incoming=input.readNBytes(length);int status;byte[] body=new byte[0];String extra="";
            synchronized(this) {
                if(request.startsWith("GET /sink ")){redirected++;status=200;}
                else if(!authorization().equals(headers.get("Authorization")))status=401;
                else if(redirect){status=302;extra="Location: "+endpoint.replace("/sync","/sink")+"\r\n";}
                else if(request.startsWith("GET ")){status=stored==null?404:200;if(stored!=null){body=stored;extra="ETag: "+etag()+"\r\n";}}
                else {
                    boolean matches=stored==null?"*".equals(headers.get("If-None-Match")):etag().equals(headers.get("If-Match"));
                    if(!matches)status=412;else{stored=incoming;revision++;writes++;status=201;extra="ETag: "+etag()+"\r\n";}
                }
            }
            java.io.OutputStream output=connection.getOutputStream();
            output.write(("HTTP/1.1 "+status+" Fixture\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n"+extra+"\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write(body);output.flush();
        } catch (Throwable failure) { if(!rejectingCertificate)serverFailure=failure; }
    }
    private static String line(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream value=new java.io.ByteArrayOutputStream();int next;
        while((next=input.read())!=-1&&next!='\n'){if(value.size()>8192)throw new IOException("Oversized fixture header");if(next!='\r')value.write(next);}
        return next==-1&&value.size()==0?null:value.toString("US-ASCII");
    }
    @After public void stopServer() throws Exception { if (server != null) server.close(); if (executor != null) executor.shutdownNow();if(serverFailure!=null)throw new AssertionError("HTTPS fixture failed",serverFailure); }
    private String etag() { return "\"revision-" + revision + "\""; }
    private static String authorization() { return "Basic " + Base64.getEncoder().encodeToString("client:test-password".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private SyncClient client() {
        return new SyncClient(url -> {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            // Trust only this fixture's certificate; keep the standard hostname verifier.
            connection.setSSLSocketFactory(tls.getSocketFactory()); return connection;
        });
    }

    @Test public void twoEncryptedClientsRejectAStaleConditionalWrite() throws Exception {
        SyncClient first = client(), second = client();
        assertNull(first.fetch(endpoint, "client", "test-password").data);
        first.upload(endpoint, "client", "test-password", SyncCrypto.encrypt(new byte[]{1}, PHRASE), null);
        SyncClient.Remote old = first.fetch(endpoint, "client", "test-password");
        SyncClient.Remote latest = second.fetch(endpoint, "client", "test-password");
        assertArrayEquals(new byte[]{1}, SyncCrypto.decrypt(latest.data, PHRASE));
        second.upload(endpoint, "client", "test-password", SyncCrypto.encrypt(new byte[]{2}, PHRASE), latest.etag);
        try { first.upload(endpoint, "client", "test-password", SyncCrypto.encrypt(new byte[]{3}, PHRASE), old.etag); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Conflict")); }
        assertArrayEquals(new byte[]{2}, SyncCrypto.decrypt(first.fetch(endpoint, "client", "test-password").data, PHRASE));
        assertEquals(2, writes);
    }

    @Test public void simultaneousCreationCannotReplaceAnExistingServerFile() throws Exception {
        SyncClient first = client(), second = client();
        assertNull(first.fetch(endpoint, "client", "test-password").data);
        assertNull(second.fetch(endpoint, "client", "test-password").data);
        first.upload(endpoint, "client", "test-password", new byte[]{7}, null);
        try { second.upload(endpoint, "client", "test-password", new byte[]{8}, null); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Conflict")); }
        assertArrayEquals(new byte[]{7}, first.fetch(endpoint, "client", "test-password").data); assertEquals(1, writes);
    }

    @Test public void redirectsDoNotForwardCredentialsToAnotherPath() throws Exception {
        redirect = true;
        try { client().fetch(endpoint, "client", "test-password"); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage(),expected.getMessage().contains("De server verwijst"));assertEquals(0, redirected); assertEquals(0, writes); }
    }

    @Test public void trustedCertificateStillRequiresTheCorrectHostname() throws Exception {
        rejectingCertificate=true;
        try { client().fetch(endpoint.replace("localhost","127.0.0.1"), "client", "test-password");fail(); }
        catch (SSLException expected) { assertEquals(0,writes); }
    }

    @Test public void normalClientRejectsTheUntrustedFixtureCertificate() throws Exception {
        rejectingCertificate=true;
        try { new SyncClient().fetch(endpoint, "client", "test-password"); fail(); }
        catch (SSLException expected) { assertEquals(0, writes); }
    }
}
