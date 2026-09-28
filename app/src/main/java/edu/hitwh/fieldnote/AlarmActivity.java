package edu.hitwh.fieldnote;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
public final class AlarmActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);if(android.os.Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else{getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER);l.setPadding(32,48,32,48);l.setBackgroundColor(0xff213e34);TextView t=new TextView(this);t.setText(getIntent().getStringExtra("title"));t.setTextSize(32);t.setTextColor(0xffd0ec83);l.addView(t);TextView d=new TextView(this);d.setText(getIntent().getStringExtra("detail"));d.setTextSize(18);d.setTextColor(0xfff1f3ec);d.setPadding(0,24,0,48);l.addView(d);Button stop=new Button(this);stop.setText("关闭闹钟");stop.setOnClickListener(v->{startService(new Intent(this,AlarmService.class).setAction("STOP"));finish();});l.addView(stop);UiInsets.install(this,l);}
}
