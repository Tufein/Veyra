package nl.veyra.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.geckoview.WebResponse;

/** Copy the already authenticated Gecko response; never repeat a POST or export cookies. */
public final class DownloadService extends Service {
    private static final String CHANNEL="veyra_downloads";
    private static final int NOTICE=84;
    private static final Map<String,Transfer> transfers=new ConcurrentHashMap<>();
    private static final class Transfer {
        final InputStream input;final DownloadStore.Entry entry;final AtomicBoolean cancelled=new AtomicBoolean(),started=new AtomicBoolean();
        Transfer(InputStream input,DownloadStore.Entry entry){this.input=input;this.entry=entry;}
    }
    private final java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
    private DownloadStore store;
    static boolean enqueue(android.app.Activity activity,WebResponse response,Uri destination,boolean privateMode){
        if(response.body==null)return false;
        if(transfers.size()>=3){close(response.body);return false;}
        String id=UUID.randomUUID().toString(),type=response.headers.get("Content-Type");
        if(type==null||!type.matches("[\\w.+-]+/[\\w.+-]+(?:;.*)?"))type="application/octet-stream";type=type.split(";",2)[0];
        long total=-1;try{total=Long.parseLong(response.headers.get("Content-Length"));}catch(Exception ignored){/* Unknown size. */}
        if(response.headers.get("Content-Encoding")!=null&&!"identity".equalsIgnoreCase(response.headers.get("Content-Encoding")))total=-1;
        DownloadStore.Entry entry=new DownloadStore.Entry(id,DownloadStore.filename(response.headers.get("Content-Disposition"),response.uri),
                privateMode?"":response.uri,destination.toString(),type,DownloadStore.RUNNING,"",0,total,System.currentTimeMillis());
        transfers.put(id,new Transfer(response.body,entry));new DownloadStore(activity.getSharedPreferences("veyra_downloads",0)).save(entry);
        try{activity.startForegroundService(new Intent(activity,DownloadService.class).putExtra("id",id));return true;}
        catch(RuntimeException unavailable){transfers.remove(id);close(response.body);new DownloadStore(activity.getSharedPreferences("veyra_downloads",0))
                .save(entry.progress(DownloadStore.FAILED,"Download kon niet starten. Controleer het lege doelbestand.",0));return false;}
    }
    static java.util.Set<String> active(){return new java.util.HashSet<>(transfers.keySet());}
    static void cancel(String id){Transfer transfer=transfers.get(id);if(transfer!=null){transfer.cancelled.set(true);close(transfer.input);}}
    @Override public void onCreate(){super.onCreate();store=new DownloadStore(getSharedPreferences("veyra_downloads",0));
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Downloads",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        try{
            Notification notification=notice();
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTICE,notification);
        }catch(RuntimeException denied){for(String id:active()){Transfer failed=transfers.remove(id);if(failed!=null){close(failed.input);store.save(failed.entry.progress(DownloadStore.FAILED,"Android kon de download niet op de voorgrond starten. Controleer het doelbestand.",0));}}stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("id");if(id==null){if(transfers.isEmpty())stopSelf();return START_NOT_STICKY;}Transfer transfer=transfers.get(id);
        if(transfer!=null&&transfer.started.compareAndSet(false,true))workers.submit(()->copy(id,transfer));else if(transfers.isEmpty())stopSelf();
        return START_NOT_STICKY;
    }
    private Notification notice(){
        Intent show=new Intent(this,DownloadsActivity.class);PendingIntent open=PendingIntent.getActivity(this,0,show,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle(getString(R.string.download_notification_title))
                .setContentText(getString(R.string.download_notification_body)).setContentIntent(open).setOngoing(true).build();
    }
    private void copy(String id,Transfer transfer){long[] measured={0,0};String status=DownloadStore.COMPLETE,error="";
        try(InputStream input=transfer.input;OutputStream output=getContentResolver().openOutputStream(Uri.parse(transfer.entry.uri),"wt")){
            if(output==null)throw new java.io.IOException("Doelbestand niet beschikbaar");
            DownloadCopy.copy(input,output,transfer.entry.total,transfer.cancelled::get,count->{
                measured[0]=count;long now=System.currentTimeMillis();
                if(now-measured[1]>=500){store.save(transfer.entry.progress(DownloadStore.RUNNING,"",count));measured[1]=now;}
            });
        }catch(Exception failure){status=transfer.cancelled.get()?DownloadStore.CANCELLED:DownloadStore.FAILED;
            error=transfer.cancelled.get()?"Geannuleerd":"Download onderbroken. Vraag het bestand opnieuw aan via de website.";
            try{if(!android.provider.DocumentsContract.deleteDocument(getContentResolver(),Uri.parse(transfer.entry.uri)))error+=" Controleer het gedeeltelijke doelbestand.";}
            catch(Exception unavailable){error+=" Controleer het gedeeltelijke doelbestand.";}
        }
        store.save(transfer.entry.progress(status,error,measured[0]));transfers.remove(id);
        new android.os.Handler(getMainLooper()).post(()->{if(transfers.isEmpty()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});
    }
    static void close(InputStream stream){if(stream!=null)try{stream.close();}catch(java.io.IOException ignored){}}
    @Override public void onDestroy(){for(String id:active())cancel(id);workers.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int type){for(String id:active())cancel(id);stopSelf();}
    @Override public IBinder onBind(Intent intent){return null;}
}
