package nl.veyra.browser;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import java.util.function.IntConsumer;

/** Material browser panels with bounded, scrollable content and explicit actions. */
final class BrowserSheets {
    static final class Sheet {
        final SettingsViews ui;
        final BottomSheetDialog dialog;
        final LinearLayout content, root;
        Sheet(Activity activity, String title, String summary) {
            ui = new SettingsViews(activity); dialog = new BottomSheetDialog(activity);
            root = ui.column(); android.graphics.drawable.GradientDrawable background = ui.shape(ui.surface, 28);
            float radius = ui.dp(28); background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
            root.setBackground(background);
            View handle = new View(activity); handle.setBackground(ui.shape(ui.outline, 3));
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL; handleParams.topMargin = ui.dp(12); handleParams.bottomMargin = ui.dp(12);
            root.addView(handle, handleParams);
            LinearLayout header = ui.row(); header.setPadding(ui.dp(20), 0, ui.dp(10), ui.dp(8));
            LinearLayout copy = ui.column(); TextView label = ui.text(title, 23, ui.ink); label.setAccessibilityHeading(true);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(label);
            if (summary != null && !summary.isEmpty()) {
                TextView detail = ui.text(summary, 12, ui.muted); detail.setMaxLines(2);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.END); ui.add(copy, detail, 4);
            }
            header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton close = icon(ui, R.drawable.ic_close, activity.getString(R.string.panel_close));
            close.setOnClickListener(v -> dialog.dismiss()); header.addView(close, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            root.addView(header);
            content = ui.column(); content.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(20));
            // Measure naturally for short menus; cap the scroll area so actions remain reachable.
            int maximum = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .72f);
            ScrollView bounded = new ScrollView(activity) {
                @Override protected void onMeasure(int width, int height) {
                    super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST));
                }
            };
            bounded.setClipToPadding(false); bounded.addView(content); root.addView(bounded);
            dialog.setContentView(root);
            if ((activity.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0) BrowserPrivacyUi.secure(dialog);
            dialog.setOnShowListener(d -> {
                FrameLayout panel = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (panel != null) {
                    panel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    panel.getLayoutParams().width = Math.min(activity.getResources().getDisplayMetrics().widthPixels, ui.dp(640));
                    panel.requestLayout();
                }
                dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
                dialog.getBehavior().setSkipCollapsed(true);
            });
        }
        void show() { dialog.show(); }
        void run(Runnable action) { dialog.dismiss(); action.run(); }
    }
    static void action(Sheet sheet, int icon, int label, boolean enabled, Runnable action) {
        SettingsViews ui = sheet.ui;
        LinearLayout row = ui.row(); row.setMinimumHeight(ui.dp(52)); row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((ui.primary & 0xFFFFFF) | 0x22000000), null, ui.shape(-1, 14)));
        row.addView(ui.image(icon, ui.primary), new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        TextView title = ui.text(label, 14, ui.ink); title.setPadding(ui.dp(14), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.setEnabled(enabled); row.setAlpha(enabled ? 1 : .38f); row.setFocusable(enabled);
        row.setContentDescription(ui.activity.getString(label)); row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        row.setOnClickListener(v -> sheet.run(action)); sheet.content.addView(row);
    }
    private static void divider(Sheet sheet) {
        View line = new View(sheet.ui.activity); line.setBackgroundColor(sheet.ui.outline);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, sheet.ui.dp(1));
        p.setMargins(sheet.ui.dp(12), sheet.ui.dp(10), sheet.ui.dp(12), sheet.ui.dp(8)); sheet.content.addView(line, p);
    }
    private static MaterialButton icon(SettingsViews ui, int drawable, String description) {
        MaterialButton button = new MaterialButton(ui.activity, null, com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(drawable); button.setIconTint(ColorStateList.valueOf(ui.muted));
        button.setContentDescription(description); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setInsetTop(0); button.setInsetBottom(0); button.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        return button;
    }
    static Sheet menu(Activity activity, boolean page, boolean bookmarked, String host, IntConsumer action) {
        return menu(activity, page, bookmarked, host, false, action);
    }
    static Sheet menu(Activity activity, boolean page, boolean bookmarked, String host, boolean privateMode, IntConsumer action) {
        Sheet sheet = new Sheet(activity, activity.getString(privateMode ? R.string.private_menu : R.string.panel_menu), page ? host : activity.getString(R.string.panel_menu_body));
        if (privateMode) BrowserPrivacyUi.secure(sheet.dialog);
        SettingsViews ui = sheet.ui;
        int[] labels = {privateMode ? R.string.normal_new : R.string.panel_new_tab, bookmarked ? R.string.panel_saved : R.string.panel_bookmark, R.string.library_title, R.string.settings_title};
        int[] icons = {R.drawable.ic_add, bookmarked ? R.drawable.ic_check : R.drawable.ic_bookmark, R.drawable.ic_library, R.drawable.ic_tune};
        int[] routes = {0, 1, 2, 7};
        int columns = activity.getResources().getConfiguration().fontScale >= 1.3f
                && activity.getResources().getDisplayMetrics().widthPixels < ui.dp(400) ? 1 : 2;
        for (int row = 0; row < 4 / columns; row++) {
            LinearLayout grid = ui.row();
            for (int col = 0; col < columns; col++) {
                final int index = row * columns + col;
                MaterialButton button = new MaterialButton(activity);
                button.setText(labels[index]); button.setIconResource(icons[index]); button.setTextSize(13);
                button.setBackgroundTintList(ColorStateList.valueOf(ui.high)); button.setTextColor(ui.ink); button.setIconTint(ColorStateList.valueOf(ui.primary));
                button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(ui.dp(64)); button.setSingleLine(false);
                button.setCornerRadius(ui.dp(18)); button.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
                button.setEnabled(index != 1 || (page && !bookmarked));
                if (!button.isEnabled()) button.setAlpha(.38f);
                button.setOnClickListener(v -> sheet.run(() -> action.accept(routes[index])));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(ui.dp(3), 0, ui.dp(3), 0); grid.addView(button, p);
            }
            ui.add(sheet.content, grid, 2);
        }
        divider(sheet);
        action(sheet, R.drawable.ic_share, R.string.panel_share, page, () -> action.accept(3));
        action(sheet, R.drawable.ic_search, R.string.panel_find, page, () -> action.accept(4));
        divider(sheet);
        action(sheet, R.drawable.ic_shield, R.string.panel_protection, true, () -> action.accept(5));
        action(sheet, R.drawable.ic_lock, R.string.panel_connection, true, () -> action.accept(6));
        action(sheet, R.drawable.ic_extension, R.string.settings_extensions, true, () -> action.accept(9));
        action(sheet, R.drawable.ic_key, R.string.settings_passwords, true, () -> action.accept(8));
        return sheet;
    }
    static final class TabItem {
        final String title, url;
        final boolean home, privateMode;
        TabItem(String title, String url, boolean home) { this(title,url,home,false); }
        TabItem(String title, String url, boolean home,boolean privateMode) { this.title = title; this.url = url; this.home = home; this.privateMode = privateMode; }
    }
    static Sheet tabs(Activity activity, List<TabItem> tabs, int selected, int limit, IntConsumer select, IntConsumer close, Runnable add) {
        return tabs(activity, tabs, selected, limit, select, close, add, null, null, null);
    }
    static Sheet tabs(Activity activity, List<TabItem> tabs, int selected, int limit, IntConsumer select, IntConsumer close,
                      Runnable add, Runnable addPrivate, Runnable closePrivate, Runnable reopen) {
        int privateCount = 0;
        for (TabItem tab : tabs) if (tab.privateMode) privateCount++;
        String summary = activity.getString(R.string.panel_tab_count, tabs.size(), limit);
        if (privateCount > 0) summary += " · " + activity.getString(R.string.tab_modes_count, tabs.size()-privateCount, privateCount);
        Sheet sheet = new Sheet(activity, activity.getString(R.string.panel_tabs), summary);
        if (privateCount > 0) BrowserPrivacyUi.secure(sheet.dialog);
        SettingsViews ui = sheet.ui;
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i; TabItem tab = tabs.get(i); boolean current = i == selected;
            MaterialCardView card = new MaterialCardView(activity); card.setRadius(ui.dp(18)); card.setCardElevation(0);
            int privateInk = BrowserPrivacyUi.ink(activity);
            card.setCardBackgroundColor(tab.privateMode ? BrowserPrivacyUi.surface(activity) : current ? ui.accentSurface : ui.low);
            card.setStrokeWidth(current ? ui.dp(2) : 0); card.setStrokeColor(tab.privateMode ? privateInk : ui.primary);
            LinearLayout row = ui.row(); row.setPadding(ui.dp(12), ui.dp(8), ui.dp(6), ui.dp(8));
            LinearLayout target = ui.row(); target.setMinimumHeight(ui.dp(60));
            View identity = tab.privateMode ? ui.image(R.drawable.ic_private, privateInk) : ui.monogram(tab.home ? "Veyra" : tab.title, tab.url);
            target.addView(identity, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
            LinearLayout copy = ui.column(); copy.setPadding(ui.dp(12), 0, ui.dp(4), 0);
            TextView title = ui.text(tab.home ? activity.getString(tab.privateMode ? R.string.private_home_title : R.string.panel_new_tab) : tab.title, 15, tab.privateMode ? privateInk : current ? ui.accentInk : ui.ink);
            title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(title);
            TextView subtitle = ui.text(tab.privateMode ? activity.getString(current ? R.string.private_selected : R.string.private_badge) : current ? activity.getString(R.string.panel_current_tab)
                    : tab.home ? "Veyra" : SettingsViews.host(tab.url), 12, tab.privateMode ? privateInk : current ? ui.accentInk : ui.muted);
            subtitle.setMaxLines(1); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END); ui.add(copy, subtitle, 4);
            target.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            target.setOnClickListener(v -> sheet.run(() -> select.accept(index))); target.setFocusable(true); target.setSelected(current); SettingsViews.buttonRole(target);
            target.setContentDescription(title.getText() + ", " + subtitle.getText());
            target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            row.addView(target, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton remove = icon(ui, R.drawable.ic_close, activity.getString(R.string.panel_close_tab, title.getText()));
            if (tab.privateMode) remove.setIconTint(ColorStateList.valueOf(privateInk));
            remove.setOnClickListener(v -> sheet.run(() -> close.accept(index))); row.addView(remove, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            card.addView(row); ui.add(sheet.content, card, 6);
        }
        MaterialButton newTab = ui.button(addPrivate == null ? R.string.panel_new_tab : R.string.normal_new, R.drawable.ic_add, v -> sheet.run(add));
        newTab.setEnabled(tabs.size() < limit); ui.add(sheet.content, newTab, 14);
        if (addPrivate != null) {
            MaterialButton privateTab = ui.button(R.string.private_new, R.drawable.ic_private, v -> sheet.run(addPrivate));
            privateTab.setBackgroundTintList(ColorStateList.valueOf(BrowserPrivacyUi.surface(activity)));
            privateTab.setTextColor(BrowserPrivacyUi.ink(activity)); privateTab.setIconTint(ColorStateList.valueOf(BrowserPrivacyUi.ink(activity)));
            privateTab.setEnabled(tabs.size() < limit); ui.add(sheet.content, privateTab, 6);
            action(sheet, R.drawable.ic_history, R.string.normal_reopen, reopen != null, () -> { if (reopen != null) reopen.run(); });
        }
        if (privateCount > 0 && closePrivate != null) action(sheet, R.drawable.ic_close, R.string.private_close_all, true, closePrivate);
        return sheet;
    }
}
