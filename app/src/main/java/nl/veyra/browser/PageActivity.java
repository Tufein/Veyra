package nl.veyra.browser;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Consistent, inset-aware Material pages without browser controls. */
abstract class PageActivity extends Activity {
    SettingsViews ui; LinearLayout root,content; MaterialToolbar toolbar; ScrollView pageScroll;
    @Override public void onCreate(Bundle state) {
        ThemeSupport.apply(this,getSharedPreferences("veyra",MODE_PRIVATE));super.onCreate(state);ui=new SettingsViews(this);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::handleBack);
    }
    void page(String title) {
        root=ui.column();root.setBackgroundColor(ui.surface);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        toolbar=new MaterialToolbar(this);toolbar.setTitle(title);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(getString(R.string.ui_back_ebf6c));toolbar.setNavigationOnClickListener(v->handleBack());root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);pageScroll=scroll;scroll.setFillViewport(true);FrameLayout frame=new FrameLayout(this);
        content=ui.column();content.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(32));
        frame.addView(content,new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,ui.dp(840)),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        scroll.addView(frame);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        getWindow().setStatusBarColor(ui.surface);getWindow().setNavigationBarColor(ui.surface);
        getWindow().getDecorView().setSystemUiVisibility(ThemeSupport.isLight(this,getSharedPreferences("veyra",MODE_PRIVATE))
                ?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
    void handleBack(){finish();}
    @Override public void onBackPressed(){handleBack();}
    void body(String value) { ui.add(content,ui.text(value,14,ui.muted),12); }
    MaterialButton button(String value,int icon,View.OnClickListener listener) {
        MaterialButton button=new MaterialButton(this);button.setText(value);button.setAllCaps(false);button.setMinHeight(ui.dp(52));
        button.setSingleLine(false);if(icon!=0)button.setIconResource(icon);button.setOnClickListener(listener);ui.add(content,button,12);return button;
    }
    TextInputEditText field(String hint,String value) {
        TextInputLayout box=new TextInputLayout(this);box.setHint(hint);box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxCornerRadii(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));TextInputEditText input=new TextInputEditText(box.getContext());
        input.setSingleLine(true);input.setText(value);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(input);ui.add(content,box,16);return input;
    }
    void message(String text) { android.widget.Toast.makeText(this,ErrorText.display(this,text),android.widget.Toast.LENGTH_LONG).show(); }
}
