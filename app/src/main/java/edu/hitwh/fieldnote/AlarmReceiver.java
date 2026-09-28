package edu.hitwh.fieldnote;
import android.content.*;
public final class AlarmReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){c.startForegroundService(new Intent(c,AlarmService.class).putExtra("id",i.getIntExtra("id",-1)));}
}
