package edu.hitwh.fieldnote;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView ui;
    private Vault vault;
    private Enhancements.Session session;
    private AcademicTask startupSync;
    private final Handler main=new Handler(Looper.getMainLooper());
    private String permissionId; private JSONObject pendingAlarm;
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private boolean ready=false,resumeSync=false,portalGuided=false,openNews=false;
    @Override public void onCreate(Bundle b){super.onCreate(b);vault=new Vault(this);Object retained=getLastNonConfigurationInstance();session=retained instanceof Enhancements.Session?(Enhancements.Session)retained:new Enhancements.Session();
        if(b!=null){resumeSync=b.getBoolean("resumeSync");portalGuided=b.getBoolean("portalGuided");}
        openNews=getIntent().getBooleanExtra("openNews",false);NewsClient.schedule(this,false);
        getWindow().setStatusBarColor(Color.rgb(241,243,236));
        ui=new WebView(this);UiInsets.install(this,ui);ui.setBackgroundColor(Color.rgb(241,243,236));
        WebSettings ws=ui.getSettings();ws.setJavaScriptEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setDomStorageEnabled(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ui.addJavascriptInterface(new Bridge(),"Native");
        ui.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest req){
                Uri u=req.getUrl();String path=u.getPath();
                if(ORIGIN.equals(u.getScheme()+"://"+u.getHost())&&path!=null&&path.matches("/app/[a-zA-Z0-9_.-]+")){
                    try{String name=path.substring(5);String mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":"application/javascript";
                        return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));}catch(IOException ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView w,String url){ready=true;}
        });
        ui.loadUrl(ORIGIN+"/app/index.html");
    }
    @Override protected void onResume(){super.onResume();if(ready&&ui!=null)ui.evaluateJavascript("window.refreshNative&&window.refreshNative(false)",null);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openNews=intent.getBooleanExtra("openNews",false);if(ready&&ui!=null&&openNews){openNews=false;ui.evaluateJavascript("window.openNewsTab&&window.openNewsTab()",null);}}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("resumeSync",resumeSync);b.putBoolean("portalGuided",portalGuided);super.onSaveInstanceState(b);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==72){resumeSync=portalGuided&&result==RESULT_OK;portalGuided=false;}}
    @Override public Object onRetainNonConfigurationInstance(){return session;}
    private void cancelSync(){if(startupSync!=null){AcademicTask old=startupSync;startupSync=null;old.cancel();NewsClient.academic(this,false);}}
    private JSONObject syncSnapshot()throws JSONException{return startupSync!=null?startupSync.snapshot():new JSONObject().put("busy",false).put("needsVerification",false).put("stage","").put("progress",0);}
    @Override protected void onDestroy(){cancelSync();NewsClient.cancelFor(this);if(ui!=null){ui.removeJavascriptInterface("Native");ui.destroy();ui=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(ui!=null)ui.evaluateJavascript("window.appBack&&window.appBack()",handled->{if(!"true".equals(handled)){if(startupSync!=null||NewsClient.busy())moveTaskToBack(true);else finish();}});else super.onBackPressed();}
    private void reply(String id,Object value,String error){if(ui==null||isFinishing()||isDestroyed())return;String v=value==null?"null":value.toString();ui.evaluateJavascript("window.NativeDone("+JSONObject.quote(id)+","+v+","+(error==null?"null":JSONObject.quote(error))+")",null);}
    private JSONObject bootstrap() throws Exception {
        JSONObject obj=vault.load();boolean hasPassword=!obj.optString("password").isEmpty();obj.remove("password");obj.remove("syncTargets");obj.remove("newsConfig");obj.remove("newsState");obj.remove("newsRevision");obj.put("openNews",openNews);openNews=false;obj.put("hasPassword",hasPassword);JSONObject pending=obj.optJSONObject("pendingImport");if(pending!=null)pending.remove("sourceUrl");
        obj.put("native",true);obj.put("resumeSync",resumeSync);obj.put("syncTask",syncSnapshot());resumeSync=false;obj.put("alarmPermission",Build.VERSION.SDK_INT<31||getSystemService(AlarmManager.class).canScheduleExactAlarms());
        return obj;
    }
    private final class Bridge {
        @JavascriptInterface public void request(String id,String method,String payload){
            if(id==null||id.length()>100||payload==null||payload.length()>2_000_000)return;
            main.post(()->{try{handle(id,method,new JSONObject(payload));}catch(Exception e){reply(id,null,e instanceof IllegalArgumentException?e.getMessage():"操作未完成，请检查本地数据或系统权限后重试");}});
        }
    }
    private void copyNewsLink(String url){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null)throw new IllegalArgumentException("系统剪贴板暂不可用，请稍后重试");
        clipboard.setPrimaryClip(ClipData.newPlainText("学校新闻链接",url));
    }
    private void handle(String id,String method,JSONObject arg)throws Exception {
        switch(method){
            case "bootstrap": reply(id,bootstrap(),null);return;
            case "newsState": reply(id,NewsClient.snapshot(vault).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsSync": {
                boolean manual=arg.optBoolean("manual");Context context=getApplicationContext();
                new Thread(()->{try{JSONObject result=NewsClient.sync(context,manual).put("notificationAllowed",NewsClient.notificationAllowed(context));main.post(()->reply(id,result,null));}catch(Exception e){main.post(()->reply(id,null,"新闻本地资料暂时无法读取"));}},"xixu-news-sync").start();return;
            }
            case "newsCurate": reply(id,NewsClient.startManual(this,(FrameLayout)ui.getParent()).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsVerify": NewsClient.verify(this);reply(id,NewsClient.snapshot(vault).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsCurationStatus": reply(id,NewsClient.snapshot(vault).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsSaveConfig": NewsClient.configure(this,vault,arg);reply(id,NewsClient.snapshot(vault).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsDisconnect": NewsClient.disconnect(this,vault);reply(id,NewsClient.snapshot(vault),null);return;
            case "newsRead": NewsClient.markRead(this,arg.optLong("seq"));reply(id,NewsClient.snapshot(vault),null);return;
            case "newsDelete": NewsClient.deleteCache(this,vault,arg);reply(id,NewsClient.snapshot(vault).put("notificationAllowed",NewsClient.notificationAllowed(this)),null);return;
            case "newsArticleCopy": {
                String url=NewsData.savedArticleLink(vault.load().optJSONObject("newsState"),arg);
                copyNewsLink(url);reply(id,new JSONObject().put("ok",true),null);return;
            }
            case "newsUnresolvedOpen":case "newsUnresolvedCopy": {
                String url=NewsData.unresolvedLink(vault.load().optJSONObject("newsState"),arg);
                if("newsUnresolvedCopy".equals(method)){
                    copyNewsLink(url);
                }else if(NewsData.vpnArticleUrl(url))startActivity(new Intent(this,NewsArticleActivity.class).putExtra("url",url));
                else try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){throw new IllegalArgumentException("未找到浏览器，可使用复制链接");}
                reply(id,new JSONObject().put("ok",true),null);return;
            }
            case "newsPermission":
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},73);
                else if(!getSystemService(NotificationManager.class).areNotificationsEnabled())startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
                reply(id,new JSONObject().put("ok",true),null);return;
            case "newsOriginal": {
                String url=arg.optString("url");
                if(NewsData.vpnArticleUrl(url)){startActivity(new Intent(this,NewsArticleActivity.class).putExtra("url",url));reply(id,new JSONObject(),null);return;}
                if(!NewsData.articleUrl(url))throw new IllegalArgumentException("原文链接不属于已保存的学校新闻范围");
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){throw new IllegalArgumentException("未找到浏览器");}reply(id,new JSONObject(),null);return;
            }
            case "startupQuote": session.getQuote(q->reply(id,q,null));return;
            case "quoteSource": {
                String url=arg.optString("url");if(!url.matches("https://hitokoto\\.cn\\?uuid=[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("来源链接无效");
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){throw new IllegalArgumentException("未找到浏览器");}reply(id,new JSONObject(),null);return;
            }
            case "syncStatus": reply(id,syncSnapshot(),null);return;
            case "syncVerify": if(startupSync!=null)startupSync.verify();reply(id,syncSnapshot(),null);return;
            case "syncNow":
            case "startupSync": {
                if(startupSync!=null){reply(id,new JSONObject().put("status","already").put("message","同步正在进行").put("results",new JSONArray()),null);return;}
                if(NewsClient.busy()){reply(id,new JSONObject().put("status","already").put("message","新闻获取正在进行，请完成后再同步教务").put("results",new JSONArray()),null);return;}
                if("startupSync".equals(method)&&!session.gate.take()){reply(id,new JSONObject().put("status","already").put("results",new JSONArray()),null);return;}
                JSONObject initial=vault.load();long now=System.currentTimeMillis();
                if("startupSync".equals(method)){
                    JSONObject prefs=initial.optJSONObject("syncPreferences");
                    if(prefs!=null&&!prefs.optBoolean("enabled",true)){reply(id,new JSONObject().put("status","disabled").put("message","启动自动同步已关闭，可点击同步教务手动更新"),null);return;}
                    int interval=AutoSyncPolicy.minutes(prefs==null?60:prefs.optInt("intervalMinutes",60));
                    long last=initial.optLong("lastSyncAttempt");
                    if(last>now){vault.update(o->o.put("lastSyncAttempt",now));last=now;}
                    long wait=AutoSyncPolicy.remaining(last,now,interval);
                    if(wait>0){reply(id,new JSONObject().put("status","cooldown").put("message","距上次同步尝试不足 "+interval+" 分钟，约 "+((wait+59999)/60000)+" 分钟后可自动同步").put("remainingMs",wait),null);return;}
                }
                if(!vault.load().has("settings")&&arg.optJSONObject("settings")!=null)vault.update(o->o.put("settings",arg.getJSONObject("settings")));SchoolPages.configure(vault);
                JSONObject before=vault.load();int revision=before.optInt("syncRevision");
                if(!before.has("pendingImport")&&before.optJSONObject("syncTargets")!=null&&before.getJSONObject("syncTargets").length()>0)vault.update(o->o.put("lastSyncAttempt",now));
                NewsClient.academic(this,true);
                try{
                    startupSync=new AcademicTask(this,(FrameLayout)ui.getParent(),vault,r->{startupSync=null;NewsClient.academic(this,false);try{r.put("revision",revision);}catch(Exception ignored){}reply(id,r,null);});
                    startupSync.start(before,arg.optBoolean("allowLogin",true));
                }catch(Exception e){cancelSync();NewsClient.academic(this,false);throw e;}return;
            }
            case "inspectSyncTarget": {
                JSONObject target=Enhancements.candidate(vault.load().optJSONObject("pendingImport"),arg.getString("kind"),arg.getString("term"));target.remove("url");target.remove("termToken");reply(id,target,null);return;
            }
            case "commitImport": {
                String kind=arg.getString("kind"),term=arg.getString("term").trim();if(!java.util.Arrays.asList("courses","exams","grades").contains(kind)||term.isEmpty()||term.length()>60)throw new IllegalArgumentException("导入类型或学期无效");
                JSONArray data=arg.getJSONArray("data");if(data.length()>2500)throw new IllegalArgumentException("导入条目过多");
                JSONObject target=Enhancements.candidate(vault.load().optJSONObject("pendingImport"),kind,term);
                vault.update(o->{JSONObject p=o.optJSONObject("pendingImport");if(p==null||p.optLong("capturedAt")!=arg.optLong("capturedAt"))throw new IllegalArgumentException("读取结果已变化，请重新核对");
                    if(!o.has("settings"))o.put("settings",arg.getJSONObject("settings"));o.put(kind,data);JSONObject meta=o.optJSONObject("importMeta");if(meta==null)meta=new JSONObject();meta.put(kind+"|"+term,arg.getJSONObject("meta"));o.put("importMeta",meta);
                    JSONObject targets=o.optJSONObject("syncTargets");if(targets==null)targets=new JSONObject();if(target.optBoolean("enabled"))targets.put(kind+"|"+term,target);else targets.remove(kind+"|"+term);o.put("syncTargets",targets);o.put("syncRevision",o.optInt("syncRevision")+1);o.remove("pendingImport");});
                reply(id,bootstrap(),null);return;
            }
            case "applyStartupSync": {
                JSONArray updates=arg.getJSONArray("updates");if(updates.length()>3)throw new IllegalArgumentException("同步结果过多");
                vault.update(o->{if(o.optInt("syncRevision")!=arg.optInt("revision"))throw new IllegalArgumentException("同步期间数据已手动修改，本次没有覆盖");
                    JSONObject targets=o.optJSONObject("syncTargets"),meta=o.optJSONObject("importMeta");if(meta==null)meta=new JSONObject();
                    for(int i=0;i<updates.length();i++){JSONObject update=updates.getJSONObject(i);String kind=update.getString("kind"),term=update.getString("term");JSONObject t=targets==null?null:targets.optJSONObject(kind+"|"+term);
                        if(!java.util.Arrays.asList("courses","exams","grades").contains(kind)||t==null||!t.optString("sourceKey").equals(update.getString("sourceKey")))throw new IllegalArgumentException("同步目标已变化");
                        JSONArray data=update.getJSONArray("data");if(data.length()>2500)throw new IllegalArgumentException("同步条目过多");o.put(kind,data);meta.put(kind+"|"+term,update.getJSONObject("meta"));}
                    o.put("importMeta",meta);o.put("syncReport",arg.getJSONObject("report"));if(updates.length()>0)o.put("syncRevision",o.optInt("syncRevision")+1);});reply(id,bootstrap(),null);return;
            }
            case "clearQueryCache": {
                String kind=arg.getString("kind"),scope=arg.getString("scope"),term=arg.optString("term");
                cancelSync();resumeSync=false;
                vault.update(o->{if("current".equals(scope)){JSONObject settings=o.optJSONObject("settings");if(settings==null||!term.equals(settings.optString("termName")))throw new IllegalArgumentException("学期已变化，请重新选择要清除的缓存");}QueryCache.clear(o,kind,scope,term,System.currentTimeMillis());});
                reply(id,bootstrap(),null);return;
            }
            case "deleteQueryRecord": {
                String kind=arg.getString("kind"),term=arg.getString("term"),recordId=arg.getString("id");int expected=arg.getInt("revision");
                cancelSync();resumeSync=false;
                vault.update(o->{if(o.optInt("syncRevision")!=expected)throw new IllegalArgumentException("缓存已更新，请重新打开详情再删除");QueryCache.remove(o,kind,term,recordId,System.currentTimeMillis());});
                reply(id,bootstrap(),null);return;
            }
            case "save": {
                String key=arg.getString("key");if(!java.util.Arrays.asList("settings","courses","exams","grades","memos","colors","importMeta").contains(key))throw new IllegalArgumentException("不支持的数据类型");
                Object data=arg.get("value");vault.update(o->{o.put(key,data);if(java.util.Arrays.asList("settings","courses","exams","grades").contains(key))o.put("syncRevision",o.optInt("syncRevision")+1);});reply(id,new JSONObject().put("ok",true),null);return;
            }
            case "saveSyncPreferences": {
                int interval=arg.getInt("intervalMinutes");if(interval<1||interval>AutoSyncPolicy.MAX_MINUTES)throw new IllegalArgumentException("同步间隔应为 1–10080 分钟");
                JSONObject prefs=new JSONObject().put("enabled",arg.getBoolean("enabled")).put("intervalMinutes",interval);
                vault.update(o->o.put("syncPreferences",prefs));reply(id,prefs,null);return;
            }
            case "saveAccount": {
                cancelSync();
                NewsClient.cancelActive("账号资料已修改，本次新闻获取停止");
                String account=arg.optString("studentId").trim(),pass=arg.optString("password");if(!account.matches("[A-Za-z0-9_-]{3,32}"))throw new IllegalArgumentException("请填写有效学号");
                boolean changed=!vault.load().optString("studentId").equals(account);
                if(changed){AlarmScheduler.cancelAll(this);clearSession();}
                vault.update(o->{if(changed)for(String key:new String[]{"courses","exams","grades","memos","importMeta","pendingImport","password","syncTargets","syncReport","lastSyncAttempt"})o.remove(key);o.put("studentId",account);o.put("syncRevision",o.optInt("syncRevision")+1);if(!pass.isEmpty())o.put("password",pass);});
                reply(id,bootstrap(),null);return;
            }
            case "forgetPassword": cancelSync();NewsClient.cancelActive("密码已清除，本次新闻获取停止");vault.update(o->o.remove("password"));reply(id,bootstrap(),null);return;
            case "portal": {
                if(NewsClient.busy())throw new IllegalArgumentException("新闻获取正在进行，请完成后再手动导入");
                cancelSync();
                String kind=arg.optString("kind","courses");if(!java.util.Arrays.asList("courses","exams","grades").contains(kind))throw new IllegalArgumentException("查询类型无效");
                portalGuided=arg.optBoolean("autoResume");resumeSync=false;startActivityForResult(new Intent(this,PortalActivity.class).putExtra("kind",kind).putExtra("guided",portalGuided),72);reply(id,new JSONObject().put("ok",true),null);return;
            }
            case "consumeImport": vault.update(o->o.remove("pendingImport"));reply(id,new JSONObject(),null);return;
            case "logout": cancelSync();NewsClient.cancelActive("学校会话已退出，本次新闻获取停止");clearSession();reply(id,new JSONObject().put("ok",true),null);return;
            case "clear": cancelSync();NewsClient.cancel(this);AlarmScheduler.cancelAll(this);stopService(new Intent(this,AlarmService.class));clearSession();vault.clear();reply(id,bootstrap(),null);return;
            case "alarm": scheduleWithPermissions(id,arg);return;
            case "alarms": reply(id,AlarmScheduler.list(this),null);return;
            case "cancelAlarm": AlarmScheduler.cancel(this,arg.getInt("id"));startService(new Intent(this,AlarmService.class).setAction("CANCEL").putExtra("id",arg.getInt("id")));reply(id,new JSONObject(),null);return;
            case "calendar": {
                validateEvent(arg);long start=ScheduleMath.millis(arg.getString("date"),arg.getString("start")),end=ScheduleMath.millis(arg.getString("date"),arg.getString("end"));
                Intent i=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE,arg.getString("name"))
                    .putExtra(CalendarContract.Events.EVENT_LOCATION,arg.optString("room"))
                    .putExtra(CalendarContract.Events.DESCRIPTION,"汐序 · 哈工大（威海）\n时间以教务系统最新通知为准。")
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE,"Asia/Shanghai");
                try{startActivity(i);}catch(ActivityNotFoundException e){throw new IllegalArgumentException("手机没有可接收日程的日历应用");}
                reply(id,new JSONObject().put("ok",true),null);return;
            }
            default:throw new IllegalArgumentException("未知操作");
        }
    }
    private void clearSession(){CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();}
    static void validateEvent(JSONObject x)throws Exception{
        if(x.optString("name").isEmpty()||x.optString("name").length()>200)throw new IllegalArgumentException("课程名称无效");
        long a=ScheduleMath.millis(x.getString("date"),x.getString("start")),b=ScheduleMath.millis(x.getString("date"),x.getString("end"));
        if(b<=a)throw new IllegalArgumentException("结束时间必须晚于开始时间");
    }
    private void scheduleWithPermissions(String id,JSONObject arg)throws Exception{
        validateEvent(arg);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            if(permissionId!=null)throw new IllegalArgumentException("请先完成上一项权限请求");
            permissionId=id;pendingAlarm=arg;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);return;
        }
        if(Build.VERSION.SDK_INT>=31&&!getSystemService(AlarmManager.class).canScheduleExactAlarms()){
            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException ignored){}
            reply(id,null,"请开启“闹钟和提醒”权限，返回后再次点击设置闹钟");return;
        }
        if(!getSystemService(NotificationManager.class).areNotificationsEnabled())throw new IllegalArgumentException("请在系统设置中允许汐序通知，确保闹钟可见");
        JSONObject alarm=AlarmScheduler.add(this,arg);reply(id,alarm,null);
    }
    @Override public void onRequestPermissionsResult(int req,String[] names,int[] values){super.onRequestPermissionsResult(req,names,values);if(req==71){String id=permissionId;JSONObject arg=pendingAlarm;permissionId=null;pendingAlarm=null;if(id==null)return;try{if(values.length>0&&values[0]==PackageManager.PERMISSION_GRANTED)scheduleWithPermissions(id,arg);else reply(id,null,"通知权限未开启，闹钟尚未创建");}catch(Exception e){reply(id,null,"闹钟未创建，请检查日期和权限");}}}
}
