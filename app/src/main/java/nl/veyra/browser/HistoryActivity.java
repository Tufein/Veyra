package nl.veyra.browser;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class HistoryActivity extends PageActivity {
    private HistoryStore store;private LinearLayout list;private String query="";private int visibleLimit=100;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new HistoryStore(getSharedPreferences("veyra_history",MODE_PRIVATE));
        if(state!=null){query=state.getString("query","");visibleLimit=Math.max(100,state.getInt("visibleLimit",100));}
        page(getString(R.string.ui_history_749cb));body(getString(R.string.ui_recent_websites_stay_on_this_device_private_tabs_are_n_ffa72));
        TextInputEditText search=field(getString(R.string.ui_search_history_010f1),query);
        list=ui.column();ui.add(content,list,12);render();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c) { }
            public void onTextChanged(CharSequence s,int a,int b,int c) {query=s.toString();visibleLimit=100;render();}
            public void afterTextChanged(Editable s) { }
        });
        button(getString(R.string.ui_clear_history_07b6a),R.drawable.ic_delete,v->new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_clear_all_history_61c73))
                .setMessage(getString(R.string.ui_this_clears_local_history_not_bookmarks_or_website_dat_af770))
                .setPositiveButton(getString(R.string.ui_clear_54e3b),(d,w)->{store.clear();render();}).setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).show());
    }
    private void render() {
        list.removeAllViews();java.util.List<HistoryStore.Visit> entries=store.read(query);
        if(entries.isEmpty())ui.add(list,ui.text(getString(R.string.ui_no_visited_websites_found_53ebd),16,ui.muted),20);
        for(HistoryStore.Visit visit:entries.subList(0,Math.min(visibleLimit,entries.size())))ui.site(list,visit.title,visit.url,
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(new java.util.Date(visit.time)),v->{
                    BrowserSheets.Sheet sheet=new BrowserSheets.Sheet(this,visit.title,SettingsViews.host(visit.url));
                    BrowserSheets.action(sheet,R.drawable.ic_link,R.string.library_open,true,()->{
                        startActivity(new Intent(this,BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(visit.url))
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
                    });
                    BrowserSheets.action(sheet,R.drawable.ic_delete,R.string.history_remove,true,()->{store.remove(visit.url);render();});sheet.show();
                });
        if(entries.size()>visibleLimit)ui.add(list,ui.button(R.string.show_more,R.drawable.ic_add,v->{visibleLimit+=100;render();}),12);
    }
    @Override public void onSaveInstanceState(Bundle out) {out.putString("query",query);out.putInt("visibleLimit",visibleLimit);super.onSaveInstanceState(out);}
}
