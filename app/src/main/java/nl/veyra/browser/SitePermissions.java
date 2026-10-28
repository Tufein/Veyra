package nl.veyra.browser;

import android.content.SharedPreferences;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Permission decisions are scoped to exact HTTPS origins, not parent domains. */
final class SitePermissions {
    static final int ASK=0,ALLOW=1,BLOCK=2;
    static final String CAMERA="camera",MICROPHONE="microphone",LOCATION="location";
    private final SharedPreferences preferences;
    SitePermissions(SharedPreferences preferences){this.preferences=preferences;}
    static String origin(String url) {
        String normalized=UrlPolicy.normalizeWebUrl(url);if(normalized==null||!normalized.startsWith("https://"))return null;
        URI uri=URI.create(normalized);int port=uri.getPort();return "https://"+uri.getHost()+(port<0||port==443?"":":"+port);
    }
    int get(String url,String type,boolean privateMode) {
        if(privateMode)return ASK;String origin=origin(url);if(origin==null)return BLOCK;
        int value=preferences.getInt(origin+"|"+type,ASK);return value>=ASK&&value<=BLOCK?value:ASK;
    }
    void set(String url,String type,int value,boolean privateMode) {
        String origin=origin(url);if(origin==null||privateMode)return;
        if(!java.util.Arrays.asList(CAMERA,MICROPHONE,LOCATION).contains(type)||value<ASK||value>BLOCK)throw new IllegalArgumentException("permission");
        SharedPreferences.Editor edit=preferences.edit();if(value==ASK)edit.remove(origin+"|"+type);else edit.putInt(origin+"|"+type,value);edit.apply();
    }
    List<String> origins() {
        java.util.Set<String> origins=new java.util.TreeSet<>();
        for(String key:preferences.getAll().keySet()) {int divider=key.indexOf('|');if(divider>0)origins.add(key.substring(0,divider));}
        return new ArrayList<>(origins);
    }
    void reset(String url) {
        String origin=origin(url);if(origin==null)return;SharedPreferences.Editor editor=preferences.edit();
        for(String type:new String[]{CAMERA,MICROPHONE,LOCATION})editor.remove(origin+"|"+type);editor.apply();
    }
    void resetHost(String host) {
        if(host==null)return;SharedPreferences.Editor editor=preferences.edit();
        for(String value:origins())if(host.equalsIgnoreCase(URI.create(value).getHost()))
            for(String type:new String[]{CAMERA,MICROPHONE,LOCATION})editor.remove(value+"|"+type);
        editor.apply();
    }
    void clear(){preferences.edit().clear().apply();}
    static String label(android.content.Context context,String type) {switch(type){case CAMERA:return context.getString(R.string.site_camera);case MICROPHONE:return context.getString(R.string.site_microphone);default:return context.getString(R.string.site_location);}}
}
