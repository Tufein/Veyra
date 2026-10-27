package nl.veyra.browser;

import android.content.SharedPreferences;
import java.net.URI;

/** Custom templates are HTTPS URLs containing exactly one query placeholder. */
final class SearchProvider {
    private SearchProvider() { }
    static boolean valid(String template) {
        if(template==null || template.length()>2048 || template.indexOf("%s")<0
                || template.indexOf("%s")!=template.lastIndexOf("%s"))return false;
        String expanded=template.replace("%s","veyra");
        String normalized=UrlPolicy.normalizeWebUrl(expanded);
        if(normalized==null || !normalized.startsWith("https://"))return false;
        int query=template.indexOf('?'),fragment=template.indexOf('#'),placeholder=template.indexOf("%s");
        if(query<0 || placeholder<query || (fragment>=0 && placeholder>fragment))return false;
        URI uri=URI.create(normalized);
        return uri.getRawQuery()!=null && uri.getRawQuery().contains("veyra")
                && template.substring(0,template.indexOf('?')).indexOf("%s")<0;
    }
    static String template(SharedPreferences preferences) {
        switch(preferences.getString("search","duckduckgo")) {
            case "google":return "https://www.google.com/search?q=%s";
            case "brave":return "https://search.brave.com/search?q=%s";
            case "ecosia":return "https://www.ecosia.org/search?q=%s";
            case "custom":
                String custom=preferences.getString("custom_search",""); if(valid(custom))return custom;
            default:return "https://duckduckgo.com/?q=%s";
        }
    }
    static String resolve(String input,SharedPreferences preferences) {
        String resolved=UrlPolicy.resolve(input,"veyra-search:");
        if(resolved==null || !resolved.startsWith("veyra-search:"))return resolved;
        return template(preferences).replace("%s",resolved.substring("veyra-search:".length()));
    }
}
