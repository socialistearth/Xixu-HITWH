package edu.hitwh.fieldnote;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Isolated school browser; never exposes a JavascriptInterface to school pages. */
public final class PortalActivity extends Activity {
    private WebView web; private TextView status; private Button capture; private String kind;
    private boolean guided=false;private SchoolLogin login;
    private TextView loginTitle;private ProgressBar progress;private Button manualVerification;
    private long lastCapture=0;private boolean loading=false;private Vault vault;
    private static final String ENTRY="https://webvpn2.hitwh.edu.cn/";
    static boolean allowed(String url){try{Uri u=Uri.parse(url);String h=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&h!=null&&(h.equals("hitwh.edu.cn")||h.endsWith(".hitwh.edu.cn")||h.equals("hit.edu.cn")||h.endsWith(".hit.edu.cn"));}catch(Exception e){return false;}}
    @Override public void onCreate(Bundle b){super.onCreate(b);NewsClient.academic(this,true);vault=new Vault(this);kind=getIntent().getStringExtra("kind");guided=getIntent().getBooleanExtra("guided",false);
        capture=new Button(this);capture.setText("读取此页");capture.setOnClickListener(v->capture());
        status=new TextView(this);status.setTextColor(0xff435b36);
        web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){if(allowed(r.getUrl().toString()))return false;stopLogin("该链接不属于学校范围，已停止跳转。");return true;}
            @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){loading=true;capture.setEnabled(false);status.setText(guided?"正在连接学校统一认证…":"正在打开学校页面…");if(!allowed(url)){w.stopLoading();if(login!=null)login.pause("跳转不属于学校范围，自动登录已停止。");}else if(login!=null)login.pageStarted();}
            @Override public void onPageFinished(WebView w,String url){loading=false;capture.setEnabled(allowed(url));status.setText(allowed(url)?(guided?"正在确认登录状态…":Uri.parse(url).getHost()+" · 请确认已打开查询结果"):"已停止不可信页面");CookieManager.getInstance().flush();if(login!=null&&allowed(url))login.pageFinished();}
            @Override public void onReceivedSslError(WebView w,SslErrorHandler h,SslError e){h.cancel();if(login!=null)login.pause("证书验证失败，自动登录已停止。");status.setText("证书验证失败，已停止访问。请检查手机时间或稍后重试。");}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){loading=false;capture.setEnabled(false);if(login!=null)login.pause("学校网络不可用，本次不重试。");status.setText("页面未能加载；请稍后手动重开，不会自动重试。");}}
        });
        if(guided)installLoginScreen();else installBrowser();
        if(guided)beginLogin();web.loadUrl(ENTRY);
    }
    private void beginLogin(){
        showProgress();if(login!=null){login.resume();return;}
        try{login=new SchoolLogin(this,web,vault,message->status.setText(message),this::verificationSurface,this::loginPaused,()->{
            CookieManager.getInstance().flush();
            if(guided){setResult(RESULT_OK);finish();}else status.setText("登录完成，可打开查询结果并读取此页。");
        });login.pageFinished();}catch(Exception e){stopLogin("自动登录暂时无法启动，请返回后重新同步。");}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
    private void installLoginScreen(){
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(0xfff1f3ec);
        // Keep the browser attached and laid out so the school DOM retains its normal viewport.
        // Alpha, focus and accessibility hide it; GONE would break form visibility detection.
        verificationSurface(false);stage.addView(web,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout cover=new LinearLayout(this);cover.setOrientation(LinearLayout.VERTICAL);cover.setGravity(Gravity.CENTER);cover.setPadding(dp(30),dp(24),dp(30),dp(24));cover.setBackgroundColor(0xfff1f3ec);cover.setClickable(true);cover.setFocusable(true);
        stage.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        TextView mark=label("XI XU / CAMPUS ACCESS",12,0xff657958);mark.setLetterSpacing(.13f);cover.addView(mark);
        progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xff435b36));LinearLayout.LayoutParams spin=new LinearLayout.LayoutParams(dp(44),dp(44));spin.gravity=Gravity.CENTER;spin.topMargin=dp(32);spin.bottomMargin=dp(24);cover.addView(progress,spin);
        loginTitle=label("正在连接教务",26,0xff213e34);cover.addView(loginTitle);
        TextView hint=label("验证完成后，自动同步课表、考试与成绩。",14,0xff627064);hint.setPadding(0,dp(12),0,dp(24));hint.setLineSpacing(dp(5),1);cover.addView(hint);
        status.setTextSize(14);status.setGravity(Gravity.CENTER);status.setLineSpacing(dp(5),1);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setText("正在准备统一认证…");cover.addView(status,new LinearLayout.LayoutParams(-1,-2));
        TextView note=label("需要验证码时，会弹窗请你完成。",12,0xff627064);note.setPadding(0,dp(14),0,dp(30));cover.addView(note);
        manualVerification=new Button(this);manualVerification.setText("手动完成验证");manualVerification.setVisibility(View.GONE);manualVerification.setOnClickListener(v->{if(login!=null){showProgress();login.openVerification();}});cover.addView(manualVerification,new LinearLayout.LayoutParams(-1,dp(50)));
        Button diagnostics=new Button(this);diagnostics.setText("复制登录诊断");diagnostics.setOnClickListener(v->{if(login!=null)login.copyDiagnostics();});cover.addView(diagnostics,new LinearLayout.LayoutParams(-1,dp(50)));
        Button cancel=new Button(this);cancel.setText("取消并返回");cancel.setOnClickListener(v->finish());cover.addView(cancel,new LinearLayout.LayoutParams(-1,dp(50)));
        UiInsets.install(this,stage);
    }
    private void installBrowser(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),0);root.setBackgroundColor(0xfff1f3ec);
        TextView help=label("打开查询结果，选择好学期后点击读取。",13,0xff213e34);help.setPadding(0,0,0,dp(8));root.addView(help);
        LinearLayout bar=new LinearLayout(this);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->{if(web.canGoBack())web.goBack();else finish();});bar.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        Button fill=new Button(this);fill.setText("统一认证");fill.setOnClickListener(v->beginLogin());bar.addView(fill,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(capture,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);
        status.setTextSize(11);status.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(status);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));UiInsets.install(this,root);
    }
    private void verificationSurface(boolean visible){
        boolean show=!guided||visible;web.setAlpha(show?1f:0f);web.setFocusable(show);web.setFocusableInTouchMode(show);web.setImportantForAccessibility(show?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(!show){web.clearFocus();android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(web.getWindowToken(),0);}
    }
    private void showProgress(){if(guided&&progress!=null){progress.setVisibility(View.VISIBLE);loginTitle.setText("正在连接教务");manualVerification.setVisibility(View.GONE);}}
    private void loginPaused(){if(guided&&progress!=null){progress.setVisibility(View.INVISIBLE);loginTitle.setText("登录暂未完成");manualVerification.setVisibility(View.VISIBLE);}}
    private void stopLogin(String message){if(login!=null)login.pause(message);else{status.setText(message);loginPaused();}}
    private void capture(){
        if(loading||!allowed(web.getUrl())){toast("请等待可信的查询结果页加载完成");return;}
        long now=android.os.SystemClock.elapsedRealtime();if(lastCapture>0&&now-lastCapture<2000)return;lastCapture=now;capture.setEnabled(false);
        try(InputStream in=getAssets().open("scraper.js")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);String js=bytes.toString("UTF-8");String sourceHost=Uri.parse(web.getUrl()).getHost();
            web.evaluateJavascript(js,result->{try{
                if(result==null||result.length()>2_000_000)throw new IllegalArgumentException();
                Object decoded=new JSONTokener(result).nextValue();if(!(decoded instanceof String))throw new IllegalArgumentException();
                JSONObject snapshot=new JSONObject((String)decoded);if(snapshot.optJSONArray("pages")==null||snapshot.optJSONArray("pages").length()==0){toast("未找到可读取表格。请进入实际查询结果；若在跨域框架中，请单独打开该页面。");capture.setEnabled(true);return;}
                JSONObject pending=new JSONObject().put("kind",kind).put("snapshot",snapshot).put("host",sourceHost).put("sourceUrl",web.getUrl()).put("capturedAt",System.currentTimeMillis());
                vault.update(o->o.put("pendingImport",pending));setResult(RESULT_OK);finish();
            }catch(Exception e){toast("页面读取未完成，未改动已有数据");capture.setEnabled(true);}});
        }catch(Exception e){toast("读取脚本未能载入");capture.setEnabled(true);}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(guided)finish();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){if(login!=null)login.active(false);web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(login!=null)login.active(true);}
    @Override protected void onDestroy(){if(login!=null)login.close();if(web!=null){web.stopLoading();web.destroy();}NewsClient.academic(this,false);super.onDestroy();}
}
