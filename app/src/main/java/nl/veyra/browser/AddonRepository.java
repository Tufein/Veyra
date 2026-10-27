package nl.veyra.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only Mozilla Add-ons catalog. Package verification remains GeckoView's responsibility. */
final class AddonRepository {
    static final int ENGINE_MAJOR = 158;
    static final class Addon {
        final String id, name, summary, version, downloadUrl;
        String permissions = "De engine toont de vereiste rechten vóór installatie.";
        Addon(String id, String name, String summary, String version, String url) {
            this.id = id; this.name = name; this.summary = summary; this.version = version; downloadUrl = url;
        }
    }
    List<Addon> search(String query) throws IOException, JSONException {
        String suffix = query.trim().isEmpty() ? "&sort=users" : "&q=" + URLEncoder.encode(query.trim(), "UTF-8");
        URL url = new URL("https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&page_size=25" + suffix);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Mozilla catalog HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("Catalog response too large");
                    bytes.write(buffer, 0, count);
                }
                return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
    static List<Addon> parse(String json) throws JSONException {
        List<Addon> results = new ArrayList<>();
        JSONArray addons = new JSONObject(json).getJSONArray("results");
        for (int i = 0; i < addons.length(); i++) {
            JSONObject addon = addons.getJSONObject(i), version = addon.optJSONObject("current_version");
            if (version == null || !"extension".equals(addon.optString("type"))) continue;
            JSONObject compatibility = version.optJSONObject("compatibility");
            JSONObject android = compatibility == null ? null : compatibility.optJSONObject("android");
            if (android == null || !compatible(android.optString("min", "0"), android.optString("max", "*"))) continue;
            JSONObject file = version.optJSONObject("file");
            if (file == null || !"public".equals(file.optString("status"))) continue;
            String download = file.optString("url");
            if (!isMozillaDownload(download)) continue;
            String id = addon.optString("guid");
            if (id.isEmpty()) continue;
            Addon result=new Addon(id, localized(addon.opt("name")), localized(addon.opt("summary")), version.optString("version"), download);
            JSONArray permissions=file.optJSONArray("permissions");
            if(permissions!=null && permissions.length()>0) {
                StringBuilder detail=new StringBuilder("Rechten volgens de catalogus:");
                for(int right=0;right<Math.min(permissions.length(),100);right++)detail.append("\n• ").append(permissions.optString(right));
                result.permissions=detail.toString();
            }
            results.add(result);
        }
        return results;
    }
    static boolean isMozillaDownload(String value) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && "addons.mozilla.org".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getRawUserInfo() == null
                    && uri.getPath().startsWith("/firefox/downloads/file/") && uri.getPath().endsWith(".xpi");
        } catch (Exception invalid) { return false; }
    }
    static boolean compatible(String min, String max) {
        try {
            int lower = Integer.parseInt(min.split("\\.")[0]);
            int upper = "*".equals(max) ? Integer.MAX_VALUE : Integer.parseInt(max.split("\\.")[0]);
            if (lower > ENGINE_MAJOR || upper < ENGINE_MAJOR) return false;
            if (lower == ENGINE_MAJOR) {
                String[] components = min.split("\\.");
                for (int i = 1; i < components.length; i++) {
                    String digits = components[i].replaceFirst("[^0-9].*$", "");
                    if (!digits.isEmpty() && Integer.parseInt(digits) > 0) return false;
                }
            }
            return true;
        } catch (NumberFormatException invalid) { return false; }
    }
    private static String localized(Object value) {
        if (value instanceof String) return (String) value;
        if (!(value instanceof JSONObject)) return "";
        JSONObject object = (JSONObject) value;
        String locale = java.util.Locale.getDefault().toLanguageTag();
        for (String key : new String[]{locale, "nl", "en-US", "en-GB"}) if (object.has(key)) return object.optString(key);
        java.util.Iterator<String> keys = object.keys();
        return keys.hasNext() ? object.optString(keys.next()) : "";
    }
}
