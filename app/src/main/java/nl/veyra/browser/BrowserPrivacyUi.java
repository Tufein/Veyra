package nl.veyra.browser;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Visible private-mode chrome; this view never reads URLs, history, or personal shortcuts. */
final class BrowserPrivacyUi {
    private BrowserPrivacyUi() { }

    static int surface(Activity activity) { return ThemeSupport.color(activity, com.google.android.material.R.attr.colorTertiaryContainer); }
    static int ink(Activity activity) { return ThemeSupport.color(activity, com.google.android.material.R.attr.colorOnTertiaryContainer); }

    static MaterialAlertDialogBuilder dialog(Activity activity) {
        return new MaterialAlertDialogBuilder(activity) {
            @Override public AlertDialog create() {
                AlertDialog result = super.create();
                if ((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0)
                    secure(result);
                return result;
            }
        };
    }

    static void secure(android.app.Dialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    static final class Banner {
        final LinearLayout view;
        private final Activity activity;
        private final LinearLayout information;
        private final TextView count;
        Banner(Activity activity, Runnable explain, Runnable close) {
            this.activity = activity; SettingsViews ui = new SettingsViews(activity);
            view = ui.row(); view.setId(R.id.private_banner);
            view.setPadding(ui.dp(16), ui.dp(4), ui.dp(8), ui.dp(4));
            view.setBackgroundColor(surface(activity));
            information = ui.row(); information.setMinimumHeight(ui.dp(48));
            information.addView(ui.image(R.drawable.ic_private, ink(activity)), new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            LinearLayout copy = ui.column(); copy.setPaddingRelative(ui.dp(12), 0, ui.dp(6), 0);
            TextView title = ui.text(R.string.private_active, 14, ink(activity)); title.setTypeface(null, Typeface.BOLD);
            copy.addView(title); count = ui.text("", 12, ink(activity)); copy.addView(count);
            copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            information.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            SettingsViews.buttonRole(information); information.setFocusable(true); information.setOnClickListener(v -> explain.run());
            view.addView(information, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton exit = new MaterialButton(activity, null, com.google.android.material.R.attr.materialIconButtonStyle);
            exit.setId(R.id.private_close_all); exit.setIconResource(R.drawable.ic_close);
            exit.setIconTint(ColorStateList.valueOf(ink(activity))); exit.setContentDescription(activity.getString(R.string.private_close_all));
            exit.setMinWidth(0); exit.setMinimumWidth(0); exit.setInsetTop(0); exit.setInsetBottom(0);
            exit.setOnClickListener(v -> close.run()); view.addView(exit, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            bind(false, 0);
        }
        void bind(boolean active, int privateCount) {
            view.setVisibility(active ? View.VISIBLE : View.GONE);
            String value = activity.getResources().getQuantityString(R.plurals.private_tab_count, privateCount, privateCount);
            count.setText(value);
            information.setContentDescription(activity.getString(R.string.private_active) + ", " + value);
        }
    }

    static View home(Activity activity, Runnable search, Runnable normal, Runnable close) {
        SettingsViews ui = new SettingsViews(activity);
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        FrameLayout center = new FrameLayout(activity); LinearLayout content = ui.column();
        content.setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(24));
        center.addView(content, new FrameLayout.LayoutParams(Math.min(activity.getResources().getDisplayMetrics().widthPixels, ui.dp(720)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        TextView brand = ui.text("veyra", 29, ui.ink); brand.setTypeface(null, Typeface.BOLD); content.addView(brand);
        LinearLayout hero = ui.card(content, false);
        ((MaterialCardView) hero.getParent()).setCardBackgroundColor(surface(activity));
        hero.addView(ui.image(R.drawable.ic_private, ink(activity)), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView title = ui.text(R.string.private_home_title, 28, ink(activity)); title.setAccessibilityHeading(true);
        ui.add(hero, title, 16); ui.add(hero, ui.text(R.string.private_home_body, 15, ink(activity)), 12);
        ui.add(content, ui.button(R.string.private_search, R.drawable.ic_search, v -> search.run()), 20);
        ui.add(content, ui.text(R.string.private_saved_actions, 14, ui.muted), 18);
        ui.add(content, ui.button(R.string.normal_new, R.drawable.ic_add, v -> normal.run()), 16);
        ui.add(content, ui.button(R.string.private_close_all, R.drawable.ic_close, v -> close.run()), 8);
        scroll.addView(center); return scroll;
    }
}
