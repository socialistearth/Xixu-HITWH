package edu.hitwh.fieldnote;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class Enhancements {
 interface Callback {void done(JSONObject result);}
 static final class Session {
  final LaunchGate gate=new LaunchGate();boolean quoteStarted;JSONObject quote;
  final List<Callback> waiting=new ArrayList<>();
  void getQuote(Callback cb){
   if(quote!=null){cb.done(quote);return;}waiting.add(cb);if(quoteStarted)return;quoteStarted=true;
   new Thread(()->{JSONObject answer=new JSONObject();HttpURLConnection c=null;
    try{
     c=(HttpURLConnection)new URL("https://v1.hitokoto.cn/?c=a&c=c&c=d&c=h&c=i&c=k&encode=json&min_length=6&max_length=36").openConnection();
     c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Xixu-HITWH/0.4.1");
     if(c.getResponseCode()!=200)throw new IOException();
     JSONObject raw=new JSONObject(read(c.getInputStream(),16384));String text=raw.optString("hitokoto","").trim();
     if(text.isEmpty()||text.length()>90||text.contains("<")||text.contains(">"))throw new IOException();
     String from=raw.optString("from",""),author=raw.isNull("from_who")?"":raw.optString("from_who","");
     answer.put("text",text).put("from",from.substring(0,Math.min(120,from.length()))).put("author",author.substring(0,Math.min(60,author.length()))).put("provider","一言");
     String uuid=raw.optString("uuid","");if(uuid.matches("[0-9a-fA-F-]{36}"))answer.put("url","https://hitokoto.cn?uuid="+uuid);
    }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
    JSONObject value=answer;new Handler(Looper.getMainLooper()).post(()->{quote=value;for(Callback callback:waiting)callback.done(value);waiting.clear();});
   },"xixu-quote").start();
  }
 }
 static String read(InputStream stream,int max)throws IOException{
  try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Response too large");out.write(buf,0,n);}return out.toString("UTF-8");}
 }
 static boolean reusable(String url){
  if(!PortalActivity.allowed(url)||url.length()>1800)return false;
  String decoded=Uri.decode(url).toLowerCase(Locale.ROOT);
  if(decoded.matches(".*(?:ticket|token|password|samlresponse|jsessionid|sessionid|authorization|code)\\s*=.*"))return false;
  String p=Uri.parse(url).getPath();return p!=null&&!p.toLowerCase(Locale.ROOT).matches(".*(?:/login|/logout|/cas/|/oauth|/authserver/).*?");
 }
 static JSONObject candidate(JSONObject pending,String kind,String term)throws Exception{
  JSONObject t=new JSONObject().put("enabled",false);
  if(pending==null||!kind.equals(pending.optString("kind")))return t.put("reason","没有可复用的查询页");
  JSONObject s=pending.optJSONObject("snapshot");String url=pending.optString("sourceUrl");
  if(s==null||s.optBoolean("truncated")||s.optInt("blockedFrames")>0||s.optBoolean("possiblePagination"))return t.put("reason","页面存在分页、截断或不可读取框架，仍需手动同步");
  if(!reusable(url))return t.put("reason","此查询链接不能安全复用，仍需手动同步");
  String termToken=s.optString("termToken");if(termToken.isEmpty()||termToken.length()>500)return t.put("reason","无法识别页面的学期标识，仍需手动同步");
  byte[] hash=MessageDigest.getInstance("SHA-256").digest((kind+"|"+term+"|"+url).getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(int i=0;i<12;i++)key.append(String.format(Locale.ROOT,"%02x",hash[i]));
  return t.put("enabled",true).put("kind",kind).put("term",term).put("termToken",termToken).put("url",url).put("sourceKey",key.toString());
 }
 static final class Sync {
  interface Progress {void update(String stage,int percent);}
  private final Activity activity;private final FrameLayout parent;private final Callback callback;private final Progress progress;
  private final Handler handler=new Handler(Looper.getMainLooper());private final List<JSONObject> targets=new ArrayList<>();private final JSONArray results=new JSONArray();
  private WebView web;private String script,accessScript;private JSONObject target;private int index=-1,generation,redirects,httpStatus;private boolean ended,reading,prepared;
  static boolean requiresLogin(JSONObject state){return AcademicSyncAccess.requiresLogin(state);}
  Sync(Activity a,FrameLayout p,Callback cb){this(a,p,cb,(stage,percent)->{});}
  Sync(Activity a,FrameLayout p,Callback cb,Progress observer){activity=a;parent=p;callback=cb;progress=observer==null?(stage,percent)->{}:observer;}
  void start(JSONObject state)throws Exception{
   progress.update("正在准备教务查询…",0);
   if(state.has("pendingImport")){finish("pending","请先完成上次读取结果的核对");return;}
   JSONObject settings=state.optJSONObject("settings"),all=state.optJSONObject("syncTargets");String term=settings==null?"":settings.optString("termName");
   if(all!=null)for(String kind:new String[]{"courses","exams","grades"}){JSONObject t=all.optJSONObject(kind+"|"+term);if(t!=null&&t.optBoolean("enabled")&&reusable(t.optString("url")))targets.add(t);}
   if(targets.isEmpty()){finish("setup","请在用户页设置学期，例如 2026秋季，再点击同步教务");return;}
   script=read(activity.getAssets().open("scraper.js"),100000);accessScript=read(activity.getAssets().open("academic-access.js"),12000);
   web=new WebView(activity);web.setAlpha(0f);web.setFocusable(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);parent.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
   WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
   web.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){if(PortalActivity.allowed(r.getUrl().toString()))return false;finish("network","跳转超出学校 HTTPS 范围，已停止同步");return true;}
    @Override public void onPageStarted(WebView w,String url,Bitmap icon){if(ended||target==null)return;if(++redirects>6||!PortalActivity.allowed(url)){finish("network","页面跳转异常，已停止同步");return;}if(AcademicSyncAccess.loginPage(url))finish("login","学校登录已过期，正在准备统一认证");}
    @Override public void onPageFinished(WebView w,String url){if(ended||target==null||reading)return;reading=true;int g=generation;handler.postDelayed(()->capture(g,0),700);}
    @Override public void onReceivedSslError(WebView w,SslErrorHandler h,SslError e){h.cancel();finish("network","证书验证失败，保留已有数据");}
    @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())finish("network","网络不可用，保留缓存且不重试");}
    @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse response){
     if(!r.isForMainFrame()||ended||target==null)return;httpStatus=response.getStatusCode();
     if(AcademicSyncAccess.needsLogin(httpStatus,false)){finish("login","学校登录已过期或需要验证（HTTP "+httpStatus+"），正在准备统一认证");return;}
     // Inspect this response once. Never reload a failed query or treat every 5xx as expired login.
     reading=true;int g=generation,code=httpStatus;handler.postDelayed(()->captureHttpError(g,code),350);
    }
   });next();
  }
  private void next(){if(ended)return;handler.removeCallbacksAndMessages(null);if(++index>=targets.size()){finish("read","已读取本次保存的查询页");return;}target=targets.get(index);progress.update("正在读取"+AcademicSyncAccess.queryName(target.optString("kind"))+"…",index*100/targets.size());generation++;reading=false;prepared=false;redirects=0;httpStatus=0;int g=generation;handler.postDelayed(()->{if(g==generation&&!ended)finish("timeout","学校页面加载超时，本次不重试");},20000);web.loadUrl(target.optString("url"));}
  private void captureHttpError(int g,int code){if(ended||g!=generation||web==null||httpStatus!=code)return;
   web.evaluateJavascript(accessScript,raw->{if(ended||g!=generation||httpStatus!=code)return;
    if(AcademicSyncAccess.needsLogin(code,"true".equals(raw)))finish("login","学校页面要求重新登录，正在准备统一认证");
    else finish("network",AcademicSyncAccess.httpMessage(code,target==null?"":target.optString("kind")));
   });
  }
  private void capture(int g,int attempt){if(ended||g!=generation||web==null||httpStatus>0)return;
   if(target!=null&&target.optBoolean("verifiedSchool")&&!prepared){progress.update("正在选择"+AcademicSyncAccess.queryName(target.optString("kind"))+"查询学期…",(index*100+25)/targets.size());prepared=true;try{String prepare=read(activity.getAssets().open("prepare-query.js"),20000);web.evaluateJavascript(prepare+"("+JSONObject.quote(target.optString("kind"))+","+JSONObject.quote(target.optString("term"))+")",value->{if(ended||g!=generation||httpStatus>0)return;try{JSONObject answer=new JSONObject((String)new JSONTokener(value).nextValue());String state=answer.optString("state");if("ready".equals(state))capture(g,attempt);else if("submitted".equals(state)){reading=false;}else if("login".equals(state))finish("login","需要学校统一认证，完成后自动继续同步");else fail("未找到所选学期或查询控件，保留缓存");}catch(Exception e){fail("查询页面结构变化，保留缓存");}});}catch(Exception e){fail("查询准备失败");}return;
   }
progress.update("正在核对"+AcademicSyncAccess.queryName(target.optString("kind"))+"查询结果…",(index*100+65)/targets.size());
web.evaluateJavascript(script,raw->{if(ended||g!=generation||httpStatus>0)return;try{
   if(raw==null||raw.length()>2000000)throw new IOException();Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new IOException();JSONObject s=new JSONObject((String)decoded);
   if(s.optBoolean("loginRequired")){finish("login","学校登录已过期，正在准备统一认证");return;}
   if(s.optJSONArray("pages")==null||s.getJSONArray("pages").length()==0){if(attempt==0){handler.postDelayed(()->capture(g,1),1300);return;}fail("未识别到查询表格");return;}
   if(!target.optString("termToken").equals(s.optString("termToken"))){fail("查询页学期发生变化，未覆盖旧数据");return;}
   if(s.optBoolean("truncated")||s.optBoolean("possiblePagination")||s.optInt("blockedFrames")>0){fail("页面不完整，需要手动核对");return;}
   results.put(new JSONObject().put("kind",target.getString("kind")).put("term",target.getString("term")).put("sourceKey",target.getString("sourceKey")).put("snapshot",s).put("verifiedSchool",target.optBoolean("verifiedSchool")).put("host",Uri.parse(target.getString("url")).getHost()).put("capturedAt",System.currentTimeMillis()));queue();
  }catch(Exception e){fail("页面结构无法识别，保留缓存");}});}
  private void fail(String reason){if(ended||target==null)return;try{results.put(new JSONObject().put("kind",target.optString("kind")).put("error",reason));}catch(Exception ignored){}queue();}
  private void queue(){if(ended||target==null)return;progress.update(AcademicSyncAccess.queryName(target.optString("kind"))+"页面处理完成",(index+1)*100/targets.size());target=null;generation++;handler.removeCallbacksAndMessages(null);web.stopLoading();handler.postDelayed(this::next,1800);}
  void cancel(){finish("cancelled","启动同步已停止，保留已有数据");}
  private void finish(String status,String message){if(ended)return;ended=true;generation++;handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();parent.removeView(web);web.destroy();web=null;}try{callback.done(new JSONObject().put("status",status).put("message",message).put("results",results));}catch(Exception ignored){}}
 }
}
