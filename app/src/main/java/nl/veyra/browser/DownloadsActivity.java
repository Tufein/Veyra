package nl.veyra.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

public final class DownloadsActivity extends PageActivity {
    private DownloadStore store;private LinearLayout list;
    private String structure="";
    private final java.util.Map<String,android.widget.TextView> progressLabels=new java.util.HashMap<>();private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,750);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new DownloadStore(getSharedPreferences("veyra_downloads",0));store.recover(DownloadService.active());
        page(getString(R.string.ui_downloads_d5fdc));body(getString(R.string.ui_files_are_saved_where_you_choose_using_the_signed_in_w_6996d));
        list=ui.column();ui.add(content,list,12);render();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void render(){if(list==null)return;java.util.List<DownloadStore.Entry> entries=store.read();
        StringBuilder next=new StringBuilder();for(DownloadStore.Entry entry:entries)next.append(entry.id).append(entry.status).append(entry.error);
        if(next.toString().equals(structure)&&!progressLabels.isEmpty()){
            for(DownloadStore.Entry entry:entries){android.widget.TextView label=progressLabels.get(entry.id);if(label!=null)label.setText(progress(entry));}return;
        }
        structure=next.toString();int position=pageScroll.getScrollY();list.removeAllViews();progressLabels.clear();
        if(entries.isEmpty())ui.add(list,ui.text(getString(R.string.ui_no_downloads_yet_8ab73),18,ui.muted),20);
        for(DownloadStore.Entry entry:entries){LinearLayout card=ui.card(list,false);card.addView(ui.text(entry.name,17,ui.ink));
            android.widget.TextView label=ui.text(progress(entry),13,ui.muted);progressLabels.put(entry.id,label);ui.add(card,label,8);
            if(!entry.error.isEmpty())ui.add(card,ui.text(ErrorText.display(this,entry.error),13,ui.muted),8);
            if(DownloadStore.RUNNING.equals(entry.status))ui.add(card,ui.button(R.string.download_cancel,R.drawable.ic_close,v->DownloadService.cancel(entry.id)),8);
            else{
                if(DownloadStore.COMPLETE.equals(entry.status))ui.add(card,ui.button(R.string.download_open,R.drawable.ic_link,v->{
                    try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(entry.uri),entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
                    catch(android.content.ActivityNotFoundException|SecurityException error){message(getString(R.string.ui_no_app_or_permission_is_available_to_open_this_file_b55c9));}
                }),8);
                else if(UrlPolicy.normalizeWebUrl(entry.url)!=null)ui.add(card,ui.button(R.string.download_retry,R.drawable.ic_link,v->{
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(getString(R.string.ui_request_again_1b4ff))
                            .setMessage(getString(R.string.ui_the_website_will_reopen_with_your_current_session_conf_ae2a3))
                            .setNegativeButton(getString(R.string.ui_cancel_c6a8d),null).setPositiveButton(getString(R.string.ui_open_website_fb953),(d,w)->{
                                startActivity(new Intent(this,BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(entry.url)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}).show();
                }),8);
                ui.add(card,ui.button(R.string.download_forget,R.drawable.ic_delete,v->{store.remove(entry.id);render();}),4);
            }
        }
        pageScroll.post(()->pageScroll.scrollTo(0,position));
    }
    private String progress(DownloadStore.Entry entry) {
        String state=DownloadStore.RUNNING.equals(entry.status)?getString(R.string.ui_in_progress_18d64):DownloadStore.COMPLETE.equals(entry.status)?getString(R.string.ui_complete_ca532):DownloadStore.CANCELLED.equals(entry.status)?getString(R.string.ui_cancelled_92c56):getString(R.string.ui_interrupted_be65c);
        return state+" · "+android.text.format.Formatter.formatFileSize(this,entry.bytes)+(entry.total>0?" / "+android.text.format.Formatter.formatFileSize(this,entry.total):"");
    }
}
