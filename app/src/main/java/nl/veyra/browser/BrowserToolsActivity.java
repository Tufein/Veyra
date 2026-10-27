package nl.veyra.browser;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class BrowserToolsActivity extends PageActivity {
    @Override public void onCreate(Bundle state){super.onCreate(state);page(getString(R.string.ui_browser_and_data_2f71d));
        button(getString(R.string.ui_set_veyra_as_the_default_browser_bac8b),R.drawable.ic_home,v->DefaultBrowser.open(this));
        button(getString(R.string.ui_history_749cb),R.drawable.ic_history,v->startActivity(new Intent(this,HistoryActivity.class)));
        button(getString(R.string.ui_downloads_d5fdc),R.drawable.ic_download,v->startActivity(new Intent(this,DownloadsActivity.class)));
        button(getString(R.string.ui_import_and_export_2ae54),R.drawable.ic_library,v->startActivity(new Intent(this,BackupActivity.class)));
        button("Websitevoorkeuren",R.drawable.ic_tune,v->startActivity(new Intent(this,SiteSettingsActivity.class)));
        button(getString(R.string.ui_privacy_and_clear_data_30b22),R.drawable.ic_shield,v->startActivity(new Intent(this,DataSettingsActivity.class)));
        button(getString(R.string.ui_encrypted_sync_158d1),R.drawable.ic_sync,v->startActivity(new Intent(this,SyncActivity.class)));
        android.content.SharedPreferences preferences=getSharedPreferences("veyra",0);
        body(getString(R.string.ui_custom_search_engine_use_an_https_template_with_s_in_p_b9363));
        TextInputEditText template=field(getString(R.string.ui_search_url_with_s_14e39),preferences.getString("custom_search",""));template.setId(R.id.browser_custom_template);template.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        button(getString(R.string.ui_use_custom_search_engine_cd3a4),R.drawable.ic_search,v->{String value=template.getText().toString().trim();
            if(!SearchProvider.valid(value)){((com.google.android.material.textfield.TextInputLayout)template.getParent().getParent()).setError(getString(R.string.ui_use_a_valid_https_address_with_exactly_one_s_in_the_qu_d527f));return;}
            preferences.edit().putString("custom_search",value).putString("search","custom").apply();message(getString(R.string.ui_custom_search_engine_selected_ebd1e));setResult(RESULT_OK);
        });
        body(getString(R.string.ui_tab_restoration_only_saves_normal_tab_addresses_and_ti_b907a));
        button(getString(R.string.ui_choose_maximum_active_pages_5413b),R.drawable.ic_tune,v->{String[] labels={getString(R.string.ui_2_active_pages_55bb6),getString(R.string.ui_3_active_pages_d6367),getString(R.string.ui_4_active_pages_265c7)};int current=Math.max(0,Math.min(2,preferences.getInt("resident_limit",3)-2));
            new MaterialAlertDialogBuilder(this).setTitle("Tabgeheugen").setSingleChoiceItems(labels,current,(d,which)->{preferences.edit().putInt("resident_limit",which+2).apply();d.dismiss();message(getString(R.string.ui_applies_on_the_next_tab_switch_a3ec9));}).setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).show();
        });
        button(getString(R.string.ui_recover_browser_without_extra_extensions_06233),R.drawable.ic_shield,v->new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_open_recovery_mode_da3ef))
                .setMessage(getString(R.string.ui_extra_extensions_will_be_disabled_website_pages_will_r_91089))
                .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_recover_6f145),(d,w)->{
                    startActivity(new Intent(this,BrowserActivity.class).putExtra("safe_mode",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}).show());
        body(getString(R.string.ui_keyboard_ctrl_l_address_bar_ctrl_t_new_tab_ctrl_shift_0ca33));
    }
}
