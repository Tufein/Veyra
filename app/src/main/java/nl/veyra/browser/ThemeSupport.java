package nl.veyra.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.DynamicColorsOptions;

/** One Material 3 palette for browser chrome, settings, and dialogs. */
final class ThemeSupport {
    private ThemeSupport() { }

    static boolean isLight(Activity activity, SharedPreferences preferences) {
        String theme = preferences.getString("theme", "dark");
        return "light".equals(theme) || ("system".equals(theme)
                && (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES);
    }

    static void apply(Activity activity, SharedPreferences preferences) {
        boolean light = isLight(activity, preferences);
        int base = light ? R.style.Theme_Veyra_Light : R.style.Theme_Veyra;
        activity.setTheme(base);
        activity.getTheme().applyStyle(base, true);
        switch (preferences.getString("accent", "mint")) {
            case "violet": activity.getTheme().applyStyle(light
                    ? R.style.ThemeOverlay_Veyra_Violet_Light : R.style.ThemeOverlay_Veyra_Violet, true); break;
            case "blue": activity.getTheme().applyStyle(light
                    ? R.style.ThemeOverlay_Veyra_Blue_Light : R.style.ThemeOverlay_Veyra_Blue, true); break;
            case "peach": activity.getTheme().applyStyle(light
                    ? R.style.ThemeOverlay_Veyra_Peach_Light : R.style.ThemeOverlay_Veyra_Peach, true); break;
            default: break;
        }
        if (preferences.getBoolean("dynamic_colors", true)) {
            // Explicit light/dark overlays also respect Veyra's manual theme selection.
            DynamicColors.applyToActivityIfAvailable(activity,
                    new DynamicColorsOptions.Builder().setThemeOverlay(
                            light ? com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
                                    : com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark).build());
        }
    }

    static int color(Activity activity, int attribute) {
        return MaterialColors.getColor(activity, attribute, "Veyra");
    }
}
