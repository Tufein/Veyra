package nl.veyra.browser;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Shared Material typography, spacing, tonal surfaces, and accessible settings rows. */
final class SettingsViews {
    final Activity activity;
    final int primary, ink, muted, surface, low, high, outline, accentSurface, accentInk;
    SettingsViews(Activity activity) {
        this.activity = activity;
        primary = color(com.google.android.material.R.attr.colorPrimary);
        ink = color(com.google.android.material.R.attr.colorOnSurface);
        muted = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
        surface = color(com.google.android.material.R.attr.colorSurface);
        low = color(com.google.android.material.R.attr.colorSurfaceContainerLow);
        high = color(com.google.android.material.R.attr.colorSurfaceContainerHigh);
        outline = color(com.google.android.material.R.attr.colorOutlineVariant);
        accentSurface = color(com.google.android.material.R.attr.colorPrimaryContainer);
        accentInk = color(com.google.android.material.R.attr.colorOnPrimaryContainer);
    }
    int color(int attribute) { return ThemeSupport.color(activity, attribute); }
    int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    LinearLayout column() {
        LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    LinearLayout row() {
        LinearLayout v = new LinearLayout(activity); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    TextView text(int resource, int size, int color) { return text(activity.getString(resource), size, color); }
    TextView text(String value, int size, int color) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setLineSpacing(dp(3), 1); return v;
    }
    void heading(LinearLayout parent, int title, int summary) {
        TextView description = text(summary, 15, muted);
        add(parent, description, 8);
    }
    void section(LinearLayout parent, int resource) {
        TextView label = text(resource, 12, muted); label.setLetterSpacing(.09f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setAccessibilityHeading(true); add(parent, label, 26);
    }
    void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(child, p);
    }
    LinearLayout card(LinearLayout parent, boolean prominent) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(dp(22)); card.setStrokeWidth(0); card.setCardElevation(0);
        card.setCardBackgroundColor(prominent ? accentSurface : low);
        LinearLayout inside = column(); inside.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.addView(inside); add(parent, card, 12); return inside;
    }
    void category(LinearLayout parent, int icon, String title, String summary, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(activity); card.setRadius(dp(18));
        card.setStrokeWidth(0); card.setCardElevation(0); card.setCardBackgroundColor(low);
        LinearLayout row = row(); row.setPadding(dp(14), dp(12), dp(12), dp(12)); row.setMinimumHeight(dp(76));
        ImageView badge = image(icon, primary); badge.setPadding(dp(10), dp(10), dp(10), dp(10));
        badge.setBackground(shape(high, 14));
        row.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = column(); copy.setPadding(dp(12), 0, dp(8), 0);
        TextView label = text(title, 16, ink); label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(label); add(copy, text(summary, 13, muted), 4);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(image(R.drawable.ic_chevron_right, muted), new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        card.addView(row); card.setContentDescription(title + ", " + summary);
        buttonRole(card); card.setOnClickListener(listener); card.setFocusable(true); add(parent, card, 4);
    }
    View site(LinearLayout parent, String title, String url, String detail, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setRadius(dp(18)); card.setStrokeWidth(0); card.setCardElevation(0); card.setCardBackgroundColor(low);
        LinearLayout row = row(); row.setMinimumHeight(dp(78)); row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.addView(monogram(title, url), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = column(); copy.setPadding(dp(12), 0, dp(8), 0);
        TextView label = text(title, 15, ink); label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(label);
        TextView address = text(host(url), 12, muted); address.setMaxLines(1); address.setEllipsize(android.text.TextUtils.TruncateAt.END);
        add(copy, address, 3);
        if (detail != null && !detail.isEmpty()) add(copy, text(detail, 11, primary), 4);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(image(R.drawable.ic_chevron_right, muted), new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        card.addView(row); card.setContentDescription(title + ", " + url + (detail == null ? "" : ", " + detail));
        card.setOnClickListener(listener); card.setFocusable(true); buttonRole(card); add(parent, card, 5); return card;
    }
    TextView monogram(String title, String url) {
        String value = title.trim().isEmpty() ? host(url) : title.trim();
        String letter = value.isEmpty() ? "V" : value.substring(0, value.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        TextView badge = text(letter, 18, accentInk); badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); badge.setBackground(shape(accentSurface, 14));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return badge;
    }
    static String host(String url) {
        try { String host = java.net.URI.create(url).getHost(); return host == null ? url : host.replaceFirst("^www\\.", ""); }
        catch (IllegalArgumentException invalid) { return url; }
    }
    void navigation(LinearLayout parent, int title, String summary, View.OnClickListener listener) {
        LinearLayout row = row(); row.setMinimumHeight(dp(76)); row.setPadding(0, dp(14), 0, dp(14));
        LinearLayout copy = column(); copy.addView(text(title, 16, ink)); add(copy, text(summary, 13, muted), 5);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(image(R.drawable.ic_chevron_right, muted), new LinearLayout.LayoutParams(dp(32), dp(32)));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(primary & 0x00FFFFFF | 0x22000000), null, shape(-1, 16)));
        row.setOnClickListener(listener); row.setFocusable(true); buttonRole(row); parent.addView(row);
    }
    MaterialSwitch toggle(LinearLayout parent, int title, int summary, boolean checked,
                          android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = row(); row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout copy = column(); copy.addView(text(title, 16, ink)); add(copy, text(summary, 13, muted), 5);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch control = new MaterialSwitch(activity);
        control.setContentDescription(activity.getString(title) + ". " + activity.getString(summary));
        control.setMinHeight(dp(48)); control.setMinWidth(dp(48)); control.setChecked(checked);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginStart(dp(16));
        row.addView(control, params); control.setOnCheckedChangeListener(listener);
        row.setOnClickListener(v -> { if (control.isEnabled()) control.toggle(); }); parent.addView(row); return control;
    }
    MaterialButton button(int label, int icon, View.OnClickListener listener) {
        MaterialButton v = new MaterialButton(activity); v.setText(label); v.setAllCaps(false);
        v.setTextSize(14); v.setMinHeight(dp(56)); v.setCornerRadius(dp(28)); v.setSingleLine(false);
        if (icon != 0) v.setIconResource(icon);
        v.setOnClickListener(listener); return v;
    }
    ImageView image(int drawable, int tint) {
        ImageView v = new ImageView(activity); v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(tint)); v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v;
    }
    GradientDrawable shape(int fill, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
    }
    private static void buttonRole(View v) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
    }
    static final class PaletteIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int light, dark;
        PaletteIcon(int light, int dark) { this.light = light; this.dark = dark; }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            paint.setColor(light); canvas.drawArc(b.left, b.top, b.right, b.bottom, 90, 180, true, paint);
            paint.setColor(dark); canvas.drawArc(b.left, b.top, b.right, b.bottom, -90, 180, true, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 40; }
        @Override public int getIntrinsicHeight() { return 40; }
    }
}
