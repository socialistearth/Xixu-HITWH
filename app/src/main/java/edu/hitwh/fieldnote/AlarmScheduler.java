package edu.hitwh.fieldnote;
import android.app.*;
import android.content.*;
import org.json.*;

final class AlarmScheduler {
    static JSONArray list(Context c)throws Exception{return new Vault(c).load().optJSONArray("alarms")==null?new JSONArray():new Vault(c).load().getJSONArray("alarms");}
    static PendingIntent pending(Context c,int id){return PendingIntent.getBroadcast(c,id,new Intent(c,AlarmReceiver.class).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void arm(Context c,JSONObject alarm)throws Exception{
        if(android.os.Build.VERSION.SDK_INT>=31&&!c.getSystemService(AlarmManager.class).canScheduleExactAlarms())throw new IllegalArgumentException("请开启闹钟和提醒权限");
        int id=alarm.getInt("id");PendingIntent show=PendingIntent.getActivity(c,id,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(alarm.getLong("at"),show),pending(c,id));
    }
    static synchronized JSONObject add(Context c,JSONObject input)throws Exception{
        long at=ScheduleMath.alarmTime(input.getString("date"),input.getString("start"),input.optInt("advance",10),System.currentTimeMillis());
        Vault v=new Vault(c);JSONObject state=v.load();JSONArray old=state.optJSONArray("alarms");if(old==null)old=new JSONArray();
        String key=input.getString("name")+"|"+input.getString("date")+"|"+input.getString("start")+"|"+input.optInt("advance",10);
        for(int i=0;i<old.length();i++){JSONObject a=old.getJSONObject(i);if(key.equals(a.optString("key"))){arm(c,a);return a;}}
        int id=state.optInt("nextAlarmId",1000)+1;JSONObject a=new JSONObject(input.toString()).put("id",id).put("at",at).put("key",key);
        JSONArray next=new JSONArray();for(int i=0;i<old.length();i++)if(old.getJSONObject(i).optLong("at")>System.currentTimeMillis())next.put(old.getJSONObject(i));
        if(next.length()>=100)throw new IllegalArgumentException("已有 100 项闹钟，请先清理");next.put(a);
        v.update(o->{o.put("alarms",next);o.put("nextAlarmId",id);});
        try{arm(c,a);}catch(Exception e){cancel(c,id);throw e;}return a;
    }
    static synchronized void cancel(Context c,int id)throws Exception{
        c.getSystemService(AlarmManager.class).cancel(pending(c,id));new Vault(c).update(o->{JSONArray before=o.optJSONArray("alarms"),after=new JSONArray();if(before!=null)for(int i=0;i<before.length();i++)if(before.getJSONObject(i).getInt("id")!=id)after.put(before.get(i));o.put("alarms",after);});
    }
    static void cancelAll(Context c)throws Exception{JSONArray a=list(c);for(int i=0;i<a.length();i++)c.getSystemService(AlarmManager.class).cancel(pending(c,a.getJSONObject(i).getInt("id")));new Vault(c).update(o->o.put("alarms",new JSONArray()));}
    static void restore(Context c)throws Exception{if(android.os.Build.VERSION.SDK_INT>=31&&!c.getSystemService(AlarmManager.class).canScheduleExactAlarms())return;JSONArray a=list(c);for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);if(x.getLong("at")>System.currentTimeMillis())arm(c,x);}}
}
