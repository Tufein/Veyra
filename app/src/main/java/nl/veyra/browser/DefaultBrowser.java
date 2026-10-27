package nl.veyra.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

final class DefaultBrowser {
    private DefaultBrowser() { }
    static void open(Activity activity) {
        try {
            if(Build.VERSION.SDK_INT>=29){android.app.role.RoleManager roles=activity.getSystemService(android.app.role.RoleManager.class);
                if(roles!=null&&roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)&&!roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)){
                    activity.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),73);return;
                }}
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }catch(android.content.ActivityNotFoundException|SecurityException unavailable){android.widget.Toast.makeText(activity,activity.getString(R.string.default_browser_fallback),android.widget.Toast.LENGTH_LONG).show();}
    }
}
