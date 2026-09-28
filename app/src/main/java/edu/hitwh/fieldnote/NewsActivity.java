package edu.hitwh.fieldnote;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Foreground-only progress surface. School pages appear only for user verification. */
public final class NewsActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,title,hint;private ProgressBar spinner;private Button verify,cancel;private boolean attached;
    private final Runnable poll=new Runnable(){public void run(){try{JSONObject s=NewsClient.snapshot(new Vault(NewsActivity.this));status.setText(s.optString("stage","正在获取校园消息…"));boolean busy=s.optBoolean("busy");spinner.setVisibility(busy?View.VISIBLE:View.INVISIBLE);verify.setVisibility(busy?View.VISIBLE:View.GONE);JSONObject curation=s.optJSONObject("curation");if(!busy&&curation!=null&&"completed".equals(curation.optString("status"))){finish();return;}title.setText(busy?"正在获取校园消息":"校园消息获取未完成");hint.setText(busy?"需要验证码时会弹窗。完成后自动返回新闻列表。":"本次获取已停止，已有新闻保持不变。");cancel.setText(busy?"取消并返回":"返回新闻列表");}catch(Exception ignored){}handler.postDelayed(this,700);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xfff1f3ec);FrameLayout browserHolder=new FrameLayout(this);root.addView(browserHolder,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout cover=new LinearLayout(this);cover.setOrientation(LinearLayout.VERTICAL);cover.setGravity(Gravity.CENTER);int pad=Math.round(28*getResources().getDisplayMetrics().density);cover.setPadding(pad,pad,pad,pad);cover.setBackgroundColor(0xfff1f3ec);cover.setClickable(true);root.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        title=new TextView(this);title.setText("正在获取校园消息");title.setTextSize(26);title.setTextColor(0xff213e34);title.setGravity(Gravity.CENTER);cover.addView(title);
        spinner=new ProgressBar(this);LinearLayout.LayoutParams spin=new LinearLayout.LayoutParams(pad*2,pad*2);spin.gravity=Gravity.CENTER;spin.topMargin=pad;spin.bottomMargin=pad;cover.addView(spinner,spin);
        status=new TextView(this);status.setTextSize(15);status.setTextColor(0xff435b36);status.setGravity(Gravity.CENTER);status.setPadding(0,0,0,pad);cover.addView(status);
        hint=new TextView(this);hint.setText("需要验证码时会弹窗。完成后自动返回新闻列表。");hint.setGravity(Gravity.CENTER);hint.setTextColor(0xff627064);cover.addView(hint);
        verify=new Button(this);verify.setText("手动完成验证");verify.setOnClickListener(v->NewsClient.verify(this));cover.addView(verify,new LinearLayout.LayoutParams(-1,-2));
        Button diagnostics=new Button(this);diagnostics.setText("复制登录诊断");diagnostics.setOnClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("汐序登录诊断",NewsClient.loginDiagnostics(this)));Toast.makeText(this,"已复制；不含账号、密码或验证码",Toast.LENGTH_SHORT).show();});cover.addView(diagnostics,new LinearLayout.LayoutParams(-1,-2));
        cancel=new Button(this);cancel.setText("取消并返回");cancel.setOnClickListener(v->{NewsClient.cancelFor(this);finish();});cover.addView(cancel,new LinearLayout.LayoutParams(-1,-2));UiInsets.install(this,root);
        attached=NewsClient.attach(this,browserHolder);if(!attached)finish();
    }
    @Override protected void onResume(){super.onResume();NewsClient.active(this,true);handler.post(poll);}
    @Override protected void onPause(){handler.removeCallbacks(poll);NewsClient.active(this,false);super.onPause();}
    @Override public void onBackPressed(){NewsClient.cancelFor(this);super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(attached)NewsClient.cancelFor(this);super.onDestroy();}
}
