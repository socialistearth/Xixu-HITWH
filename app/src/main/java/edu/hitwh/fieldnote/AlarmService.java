package edu.hitwh.fieldnote;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;

public final class AlarmService extends Service {
    static final String CHANNEL="xixu-alarm-v1";static final int NOTICE=81;
    private MediaPlayer player;private final Handler handler=new Handler(Looper.getMainLooper());private int ringingId=-1;
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"CANCEL".equals(intent.getAction())){if(ringingId==intent.getIntExtra("id",-1))finishAlarm();else if(ringingId<0)stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&"STOP".equals(intent.getAction())){finishAlarm();return START_NOT_STICKY;}
        int id=intent==null?-1:intent.getIntExtra("id",-1);NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"课程与考试闹钟",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("仅在你设置的具体日期和时间响铃");ch.setSound(null,null);nm.createNotificationChannel(ch);
        JSONObject a=null;try{JSONArray all=AlarmScheduler.list(this);for(int x=0;x<all.length();x++)if(all.getJSONObject(x).getInt("id")==id)a=all.getJSONObject(x);}catch(Exception ignored){}
        String title=a==null?"学习提醒":a.optString("name"),detail=a==null?"":a.optString("date")+" "+a.optString("start")+" · "+a.optString("room");
        Intent open=new Intent(this,AlarmActivity.class).putExtra("title",title).putExtra("detail",detail).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent screen=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,AlarmService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n=new Notification.Builder(this,CHANNEL).setSmallIcon(edu.hitwh.fieldnote.R.drawable.ic_launcher).setContentTitle(title).setContentText(detail).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(screen).addAction(new Notification.Action.Builder(null,"关闭闹钟",stop).build());
        if(Build.VERSION.SDK_INT<34||nm.canUseFullScreenIntent())n.setFullScreenIntent(screen,true);
        startForeground(NOTICE,n.build());
        if(a==null){stopSelf();return START_NOT_STICKY;}
        if(ringingId>=0&&ringingId!=id)try{AlarmScheduler.cancel(this,ringingId);}catch(Exception ignored){}
        ringingId=id;releasePlayer();
        try{Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());player.setDataSource(this,sound);player.setLooping(true);player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);player.prepare();player.start();}catch(Exception ignored){/* Notification remains visible if a ringtone is unavailable. */}
        handler.removeCallbacksAndMessages(null);handler.postDelayed(this::finishAlarm,10*60_000L);return START_NOT_STICKY;
    }
    private void finishAlarm(){if(ringingId>=0)try{AlarmScheduler.cancel(this,ringingId);}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private void releasePlayer(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);releasePlayer();super.onDestroy();}
}
