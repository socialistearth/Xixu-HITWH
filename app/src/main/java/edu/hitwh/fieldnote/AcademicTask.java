package edu.hitwh.fieldnote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Consumer;

/** One main-thread academic operation. The owner cancels it only when destroyed. */
final class AcademicTask {
 private static final String ENTRY="https://webvpn2.hitwh.edu.cn/";
 private static final long TIMEOUT_MS=15*60*1000L;
 private final Activity activity;
 private final FrameLayout parent;
 private final Vault vault;
 private final Consumer<JSONObject> done;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private JSONObject before;
 private SchoolLogin login;
 private WebView web;
 private Enhancements.Sync sync;
 private boolean started,ended,allowLogin,authenticated,needsVerification;
 private String stage="尚未开始同步";
 private int progress;

 AcademicTask(Activity a,FrameLayout holder,Vault store,Consumer<JSONObject> callback){activity=a;parent=holder;vault=store;done=callback;}

 void start(JSONObject state,boolean canLogin){
  if(started||ended)return;started=true;allowLogin=canLogin;
  handler.postDelayed(()->finish(result("timeout",needsVerification?"等待学校验证超时，已保留缓存；可稍后重新同步":"教务同步超时，已保留缓存；本次不重试")),TIMEOUT_MS);
  try{
   before=new JSONObject(state.toString());
   if(allowLogin&&Enhancements.Sync.requiresLogin(before))beginLogin();else beginSync();
  }catch(Exception e){finish(result("network","教务同步暂时无法启动，已保留缓存"));}
 }

 JSONObject snapshot(){
  JSONObject value=new JSONObject();try{value.put("busy",started&&!ended).put("needsVerification",!ended&&needsVerification).put("stage",stage).put("progress",progress);}catch(Exception ignored){}return value;
 }

 void verify(){
  if(ended||!started||login==null||!needsVerification||activity.isFinishing()||activity.isDestroyed())return;
  needsVerification=false;setStage("请在学校页面完成验证，完成后自动继续同步",-1);login.openVerification();
 }

 void cancel(){finish(result("cancelled","同步已停止，已保留本地缓存"));}

 private void setStage(String message,int percent){if(ended)return;stage=message;progress=Math.max(-1,Math.min(100,percent));}

 private void beginLogin()throws Exception{
  if(ended)return;
  authenticated=true;needsVerification=false;setStage("正在连接校园 VPN…",5);
  web=new WebView(activity);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setSafeBrowsingEnabled(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setLoadWithOverviewMode(true);settings.setUseWideViewPort(true);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  verificationSurface(false);parent.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){
    if(PortalActivity.allowed(request.getUrl().toString()))return false;
    finish(result("network","跳转超出学校 HTTPS 范围，已停止同步"));return true;
   }
   @Override public void onPageStarted(WebView w,String url,Bitmap icon){
    if(ended||login==null)return;
    if(!PortalActivity.allowed(url)){w.stopLoading();finish(result("network","跳转超出学校 HTTPS 范围，已停止同步"));return;}
    login.pageStarted();
   }
   @Override public void onPageFinished(WebView w,String url){
    if(ended||login==null)return;
    if(PortalActivity.allowed(url)){CookieManager.getInstance().flush();login.pageFinished();}
   }
   @Override public void onReceivedSslError(WebView w,SslErrorHandler response,SslError error){response.cancel();finish(result("network","学校证书验证失败，已保留缓存"));}
   @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())finish(result("network","学校网络不可用，已保留缓存；本次不重试"));}
   @Override public void onReceivedHttpError(WebView w,WebResourceRequest request,WebResourceResponse response){
    if(!request.isForMainFrame()||ended||login==null)return;
    int code=response.getStatusCode();
    if(code==401||code==403){login.pause("学校要求确认登录或访问权限，请点击手动验证继续。");return;}
    finish(result("network","学校登录页面返回 HTTP "+code+"，已保留缓存；本次不重试"));
   }
  });
  login=new SchoolLogin(activity,activity,web,vault,"academic",true,this::loginStatus,this::verificationSurface,this::blocked,this::loginReady);
  login.deferVerification();
  web.loadUrl(ENTRY);
 }

 private void loginStatus(String message){
  int value=progress<0?10:Math.min(40,progress);
  if(message.contains("个人课表"))value=38;
  else if(message.contains("新教务系统"))value=28;
  else if(message.contains("已保存资料"))value=18;
  else if(message.contains("统一身份认证"))value=12;
  setStage(message,value);
 }
 private void blocked(String message){if(ended)return;needsVerification=true;setStage(message,-1);}
 private void loginReady(){
  if(ended)return;
  needsVerification=false;CookieManager.getInstance().flush();closeLoginBrowser();
  try{beginSync();}catch(Exception e){finish(result("network","教务查询暂时无法启动，已保留缓存"));}
 }
 private void beginSync()throws Exception{
  if(ended)return;
  needsVerification=false;setStage("正在准备课表、考试与成绩查询…",45);
  sync=new Enhancements.Sync(activity,parent,result->{
   if(ended)return;sync=null;
   if("login".equals(result.optString("status"))&&allowLogin&&!authenticated&&before!=null&&!before.optString("studentId").isEmpty()&&!before.optString("password").isEmpty()){
    try{beginLogin();}catch(Exception e){finish(result("network","学校认证暂时无法启动，已保留缓存"));}return;
   }
   finish(result);
  },(message,percent)->setStage(message,percent<0?-1:45+percent/2));
  sync.start(before);
 }
 private void verificationSurface(boolean visible){
  WebView current=web;if(current==null)return;
  current.setAlpha(visible?1f:0f);current.setFocusable(visible);current.setFocusableInTouchMode(visible);
  current.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  if(!visible){current.clearFocus();InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(current.getWindowToken(),0);}
 }
 private void closeLoginBrowser(){
  SchoolLogin previous=login;login=null;
  if(previous!=null)try{previous.close();}catch(Exception ignored){}
  WebView previousWeb=web;web=null;
  if(previousWeb!=null){
   try{previousWeb.stopLoading();}catch(Exception ignored){}
   if(previousWeb.getParent() instanceof ViewGroup)((ViewGroup)previousWeb.getParent()).removeView(previousWeb);
   try{previousWeb.destroy();}catch(Exception ignored){}
  }
 }
 private void finish(JSONObject value){
  if(ended)return;ended=true;needsVerification=false;handler.removeCallbacksAndMessages(null);
  stage=value.optString("message","同步结束");if("read".equals(value.optString("status")))progress=100;
  Enhancements.Sync previous=sync;sync=null;if(previous!=null)previous.cancel();
  closeLoginBrowser();before=null;done.accept(value);
 }
 private static JSONObject result(String status,String message){JSONObject value=new JSONObject();try{value.put("status",status).put("message",message).put("results",new JSONArray());}catch(Exception ignored){}return value;}
}
