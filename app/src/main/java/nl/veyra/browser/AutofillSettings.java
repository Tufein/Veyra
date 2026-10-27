package nl.veyra.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Open Android's public Autofill provider picker, with an OEM-compatible fallback. */
final class AutofillSettings {
    private AutofillSettings() { }
    static boolean open(Activity activity) {
        Intent picker = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                Uri.parse("package:" + activity.getPackageName()));
        for (Intent intent : new Intent[]{picker, new Intent(Settings.ACTION_SETTINGS)}) {
            try { activity.startActivity(intent); return true; }
            catch (ActivityNotFoundException | SecurityException unavailable) { /* Try the next supported route. */ }
        }
        return false;
    }
}
