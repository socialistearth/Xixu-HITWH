package edu.hitwh.fieldnote;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String a=i.getAction();if(!Intent.ACTION_BOOT_COMPLETED.equals(a)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)&&!"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(a))return;NewsClient.schedule(c,false);try{AlarmScheduler.restore(c);}catch(Exception ignored){/* No network and no boot-time media playback. */}}
}
