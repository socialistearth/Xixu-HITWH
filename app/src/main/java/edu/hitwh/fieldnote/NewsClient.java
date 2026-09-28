package edu.hitwh.fieldnote;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.FrameLayout;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Phone-owned news collection. School cookies and API keys never enter the UI bridge. */
final class NewsClient {
    private static final int JOB=8401, NOTIFICATION=8402;
    private static final String CHANNEL="campus_news", ENGINE="phone-v1";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Run current;
    private static long nextRunId;
    private static volatile boolean academicBusy;
    private static List<String> keys(JSONObject o){List<String> out=new ArrayList<>();java.util.Iterator<String> it=o.keys();while(it.hasNext())out.add(it.next());return out;}
    private static JSONObject object(JSONObject o,String key){JSONObject v=o.optJSONObject(key);return v==null?new JSONObject():v;}
    private static void migrate(Vault vault)throws Exception {
        if(ENGINE.equals(object(vault.load(),"newsConfig").optString("engine")))return;
        vault.update(data->{JSONObject old=object(data,"newsConfig");if(ENGINE.equals(old.optString("engine")))return;
            data.put("newsConfig",new JSONObject().put("engine",ENGINE).put("baseUrl","").put("model","").put("apiKey","").put("profile","").put("autoEnabled",true).put("pushTime","19:00").put("notifyEnabled",old.optBoolean("notifications",true)));
            JSONObject state=object(data,"newsState");state.remove("curation");state.remove("curationError");state.put("lastError","").put("stage","").put("hasMore",false);data.put("newsState",state);data.put("newsRevision",data.optLong("newsRevision")+1);
        });
    }
    static JSONObject snapshot(Vault vault)throws Exception {
        migrate(vault);JSONObject data=vault.load(),config=object(data,"newsConfig"),state=object(data,"newsState");
        boolean key=!config.optString("apiKey").isEmpty();config.remove("apiKey");config.remove("token");config.put("hasApiKey",key);
        Run run=current;boolean busy=run!=null&&run.valid();
        JSONObject job=object(state,"curation");if(!busy&&"running".equals(job.optString("status")))job.put("status","failed").put("error","上次获取被系统中断，请手动重试");
        NewsCrawler crawler=busy?run.crawler:null;
        boolean needsVerification=busy&&run.manual&&crawler!=null&&crawler.needsVerification();
        int progress=busy?(crawler==null?-1:crawler.progress()):("completed".equals(job.optString("status"))?100:-1);
        return new JSONObject().put("config",config).put("configured",key&&!config.optString("baseUrl").isEmpty()&&!config.optString("model").isEmpty())
            .put("messages",state.optJSONArray("messages")==null?new JSONArray():state.getJSONArray("messages")).put("unread",NewsData.unread(state)).put("failedReview",object(state,"failedReview"))
            .put("lastSync",state.optLong("lastSync")).put("lastAttempt",state.optLong("lastAttempt")).put("lastError",state.optString("lastError"))
            .put("busy",busy).put("needsVerification",needsVerification).put("progress",progress)
            .put("stage",busy?run.stage:state.optString("stage")).put("curation",job).put("newsCursor",state.optLong("cursor")).put("hasMore",false);
    }
    static void configure(Context context,Vault vault,JSONObject input)throws Exception {
        migrate(vault);JSONObject old=object(vault.load(),"newsConfig"),next=new JSONObject(input.toString());
        String base=next.optString("baseUrl").trim().replaceAll("/+$",""),supplied=next.optString("apiKey").trim();
        if(supplied.isEmpty()&&base.equals(old.optString("baseUrl")))supplied=old.optString("apiKey");
        next.put("baseUrl",base).put("apiKey",supplied);LocalNewsPolicy.validateConfig(next);
        String clock=next.optString("pushTime","19:00");LocalNewsPolicy.nextDaily(System.currentTimeMillis(),clock);
        JSONObject clean=new JSONObject().put("engine",ENGINE).put("baseUrl",base).put("model",next.optString("model").trim()).put("apiKey",supplied)
            .put("profile",next.optString("profile").trim()).put("autoEnabled",next.optBoolean("autoEnabled",true)).put("pushTime",clock).put("notifyEnabled",next.optBoolean("notifyEnabled",true));
        cancelActive("新闻设置已修改，本次获取已停止");vault.update(data->{data.put("newsConfig",clean);data.put("newsRevision",data.optLong("newsRevision")+1);});schedule(context,true);
    }
    static void disconnect(Context context,Vault vault)throws Exception {
        cancel(context);vault.update(data->{JSONObject c=object(data,"newsConfig");c.put("apiKey","").put("autoEnabled",false);data.put("newsConfig",c);data.put("newsRevision",data.optLong("newsRevision")+1);});
    }
    static void cancel(Context context){cancelActive("本次获取已取消");context.getSystemService(JobScheduler.class).cancel(JOB);context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);}
    static void cancelActive(String message){Run r=current;if(r!=null)r.fail(message);}
    static void cancelAutomatic(long runId){Run r=current;if(r!=null&&!r.manual&&r.id==runId)r.fail("系统暂停了后台获取，将等待下次定时任务");}
    static boolean busy(){return current!=null;}
    static void academic(Context context,boolean busy){academicBusy=busy;}
    static void schedule(Context context,boolean changed){
        try{Vault vault=new Vault(context);migrate(vault);JSONObject c=object(vault.load(),"newsConfig");JobScheduler scheduler=context.getSystemService(JobScheduler.class);
            if(!c.optBoolean("autoEnabled")||c.optString("apiKey").isEmpty()){scheduler.cancel(JOB);return;}
            JobInfo old=scheduler.getPendingJob(JOB);if(!changed&&old!=null&&!old.isPeriodic())return;
            long now=System.currentTimeMillis(),target=LocalNewsPolicy.nextDaily(now,c.optString("pushTime","19:00"));
            PersistableBundle extras=new PersistableBundle();extras.putLong("targetAt",target);
            scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,NewsSyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(1000,target-now)).setPersisted(true).setExtras(extras).build());
        }catch(Exception ignored){}
    }
    static JSONObject sync(Context context,boolean ignored)throws Exception{return snapshot(new Vault(context));}
    static JSONObject startManual(Activity activity)throws Exception {
        android.view.View content=activity.findViewById(android.R.id.content);
        if(!(content instanceof FrameLayout))throw new IllegalArgumentException("新闻浏览器容器未就绪，请重新打开应用");
        return startManual(activity,(FrameLayout)content);
    }
    static JSONObject startManual(Activity activity,FrameLayout holder)throws Exception {
        if(activity==null||holder==null)throw new IllegalArgumentException("新闻浏览器容器未就绪，请重新打开应用");
        if(start(activity,true,null)){
            Run run=current;
            try{if(run!=null&&run.valid())run.attach(activity,holder);}
            catch(Exception e){if(run!=null)run.fail("学校浏览器暂时无法启动，请稍后重试");}
        }
        return snapshot(new Vault(activity));
    }
    static long startAutomatic(Context context,Runnable completed){
        try{if(!start(context,false,completed))return 0;Run r=current;MAIN.post(()->{try{if(r.valid())r.attach(null,null);}catch(Exception e){r.fail("后台获取未完成");}});return r.id;}
        catch(Exception e){return 0;}
    }
    private static boolean start(Context context,boolean manual,Runnable completed)throws Exception {
        if(current!=null)return false;Vault vault=new Vault(context);migrate(vault);JSONObject data=vault.load(),config=object(data,"newsConfig");
        String error="";try{LocalNewsPolicy.validateConfig(config);}catch(Exception e){error="请先在用户页配置新闻模型接口、模型名和 API 密钥";}
        if(data.optString("studentId").isEmpty()||data.optString("password").isEmpty())error="请先在用户页保存学校统一认证学号和密码";
        if(academicBusy)error="教务同步正在进行，请完成后再获取新闻";
        if(!manual&&!config.optBoolean("autoEnabled"))return false;
        String today=day(System.currentTimeMillis());JSONObject state=object(data,"newsState");if(!manual&&today.equals(state.optString("lastAutomaticDay")))return false;
        if(!error.isEmpty()){if(manual){String message=error;vault.update(o->{JSONObject s=object(o,"newsState");s.put("lastError",message).put("stage",message).put("curation",new JSONObject().put("status","failed").put("error",message).put("manual",true));o.put("newsState",s);});}return false;}
        Run run=new Run(context.getApplicationContext(),vault,data,config,manual,completed);current=run;
        try{vault.update(o->{JSONObject s=object(o,"newsState");s.remove("failedReview");s.put("lastAttempt",System.currentTimeMillis()).put("lastError","").put("stage",run.stage).put("curation",new JSONObject().put("status","running").put("manual",manual));if(!manual)s.put("lastAutomaticDay",today);o.put("newsState",s);});}
        catch(Exception e){current=null;throw e;}
        MAIN.postDelayed(run.timeout,manual?15*60_000L:8*60_000L);return true;
    }
    static boolean attach(Activity activity,FrameLayout holder){Run r=current;if(r==null||!r.manual)return false;try{r.attach(activity,holder);return true;}catch(Exception e){r.fail("学校浏览器暂时无法启动");return false;}}
    static void active(Activity activity,boolean value){Run r=current;if(r!=null&&r.activity==activity&&r.crawler!=null)r.crawler.active(value);}
    static void verify(Activity activity){Run r=current;if(r!=null&&r.activity==activity&&r.crawler!=null)r.crawler.openVerification();}
    static void cancelFor(Activity activity){Run r=current;if(r!=null&&r.activity==activity)r.fail("本次获取已取消");}
    static void deleteCache(Context context,Vault vault,JSONObject request)throws Exception{
        // Validate against a disposable copy before cancelling an active task.
        long now=System.currentTimeMillis();NewsCache.delete(vault.load(),request,now);
        cancelActive("新闻缓存已清理，本次获取已停止");
        vault.update(data->NewsCache.delete(data,request,now));
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);
    }
    static String loginDiagnostics(Activity activity){Run r=current;return r!=null&&r.activity==activity&&r.crawler!=null?r.crawler.loginDiagnostics():"当前无进行中的学校登录";}
    private static String day(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();}
    private static Set<String> known(JSONObject state){Set<String> known=new HashSet<>();JSONObject processed=object(state,"processed");long cutoff=System.currentTimeMillis()-90L*86400000;for(String id:keys(processed))if(processed.optLong(id)>=cutoff)known.add(id);
        JSONArray messages=state.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONArray articles=messages.optJSONObject(i).optJSONArray("articles");if(articles!=null)for(int j=0;j<articles.length();j++){String id=articles.optJSONObject(j).optString("id");known.add(id);if(id.matches("wh:[0-9]+"))known.add("hitwh-home:a"+id.substring(3));}}
        if(day(System.currentTimeMillis()).equals(state.optString("pendingDay"))){JSONArray p=state.optJSONArray("pendingReady");if(p!=null)for(int i=0;i<p.length();i++)known.add(p.optJSONObject(i).optString("id"));}
        return known;}
    private static final class Run {
        final long id=++nextRunId;final Context context;final Vault vault;final JSONObject config;final long revision;final String account;final boolean manual;final Runnable completion;final Set<String> known;
        JSONObject sourceReview=new JSONObject();
        volatile String stage="正在连接校园 VPN…";Activity activity;volatile NewsCrawler crawler;Thread worker;volatile boolean ended;
        final Runnable timeout=()->fail("本次获取等待超时，已停止；可稍后手动重试");
        Run(Context c,Vault v,JSONObject data,JSONObject cfg,boolean m,Runnable done){context=c;vault=v;config=cfg;revision=data.optLong("newsRevision");account=data.optString("studentId");manual=m;completion=done;known=known(object(data,"newsState"));}
        boolean valid(){return current==this&&!ended;}
        void attach(Activity a,FrameLayout holder)throws Exception {if(!valid()||crawler!=null||worker!=null)return;activity=a;crawler=new NewsCrawler(context,a,holder,known,this::stage,this::collected,this::fail);crawler.start();}
        void stage(String text){if(valid())stage=text;}
        void collected(JSONObject source){if(!valid())return;sourceReview=source;if(source.optInt("visited")>0&&source.optInt("skipped")==source.optInt("visited")){fail("本次文章正文均未能读取，请检查学校登录状态后重试");return;}stage="正在根据当前年级筛选新闻…";if(crawler!=null){crawler.cancel();crawler=null;}
            worker=new Thread(()->{try{JSONArray articles=source.optJSONArray("articles");if(articles==null)articles=new JSONArray();JSONObject result=LocalNews.select(config,articles,System.currentTimeMillis());JSONArray warnings=result.optJSONArray("warnings");if(warnings==null)warnings=new JSONArray();JSONArray sourceWarnings=source.optJSONArray("warnings");if(sourceWarnings!=null)for(int i=0;i<sourceWarnings.length()&&warnings.length()<8;i++)warnings.put(sourceWarnings.get(i));result.put("warnings",warnings);
                // No model batch completed: preserve every article for a future attempt.
                JSONArray processed=result.optJSONArray("processedIds");if(articles.length()>0&&(processed==null||processed.length()==0)&&warnings.length()>0)throw new IllegalArgumentException(warnings.optString(0));
                if(!valid()||Thread.currentThread().isInterrupted())return;
                JSONObject stats=source.optJSONObject("stats");stats=stats==null?new JSONObject():new JSONObject(stats.toString());
                stats.put("read",articles.length()).put("failed",source.optInt("skipped")).put("modelProcessed",processed==null?0:processed.length());
                mergePending(result);JSONArray selected=result.getJSONArray("articles");Set<String> thisBatch=new HashSet<>();if(processed!=null)for(int i=0;i<processed.length();i++)thisBatch.add(processed.getString(i));
                int carried=0;for(int i=0;i<selected.length();i++)if(!thisBatch.contains(selected.getJSONObject(i).optString("id")))carried++;
                stats.put("selected",selected.length()).put("carried",carried);result.put("stats",stats).put("emptyReason",source.optString("emptyReason")).put("unresolvedLinks",NewsData.cleanUnresolved(source.optJSONArray("unresolvedLinks")));
                publish(result,source.optString("homeUrl"));MAIN.post(()->finish(true,""));
            }catch(Exception e){String message=e instanceof LocalNews.Failure||e instanceof IllegalArgumentException?e.getMessage():"新闻精选未完成，已保留旧记录；请检查网络或模型配置";MAIN.post(()->fail(message));}},"xixu-local-news");worker.start();
        }
        void mergePending(JSONObject result)throws Exception {
            JSONObject state=object(vault.load(),"newsState");List<JSONObject> all=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(String key:new String[]{"articles","deferredArticles"}){JSONArray a=result.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(seen.add(item.optString("id")))all.add(item);}}
            if(day(System.currentTimeMillis()).equals(state.optString("pendingDay"))){JSONArray a=state.optJSONArray("pendingReady");if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(seen.add(item.optString("id")))all.add(item);}}
            all.sort(Comparator.<JSONObject>comparingInt(o->o.optInt("score")).reversed().thenComparing(o->o.optString("published"),Comparator.reverseOrder()));
            JSONArray shown=new JSONArray(),pending=new JSONArray(),overflow=new JSONArray();for(JSONObject item:all){if(shown.length()<10)shown.put(item);else if(pending.length()<40)pending.put(item);else overflow.put(item.getString("id"));}result.put("articles",shown).put("deferredArticles",pending).put("overflowIds",overflow);
        }
        void publish(JSONObject result,String home)throws Exception {
            if(!valid())return;long now=System.currentTimeMillis();final int[] added={0};vault.update(data->{if(!valid()||data.optLong("newsRevision")!=revision||!account.equals(data.optString("studentId")))throw new IllegalArgumentException("账号或配置已变化，本次结果未写入");
                JSONObject s=object(data,"newsState");JSONArray old=s.optJSONArray("messages");if(old==null)old=new JSONArray();long seq=s.optLong("cursor");for(int i=0;i<old.length();i++)seq=Math.max(seq,old.optJSONObject(i).optLong("seq"));seq++;
                JSONArray recommended=result.optJSONArray("articles");if(recommended==null)recommended=new JSONArray();added[0]=recommended.length();JSONObject message=new JSONObject().put("seq",seq).put("id",day(now)).put("day",day(now)).put("createdAt",now).put("manual",manual).put("read",false).put("title","校园消息 "+day(now)+"｜"+recommended.length()+"条推荐").put("context",result.getJSONObject("context")).put("articles",recommended).put("warnings",result.getJSONArray("warnings"));
                message.put("stats",result.getJSONObject("stats")).put("emptyReason",result.optString("emptyReason")).put("unresolvedLinks",result.optJSONArray("unresolvedLinks")==null?new JSONArray():result.getJSONArray("unresolvedLinks"));s.remove("failedReview");
                JSONArray all=new JSONArray().put(message);int bytes=message.toString().getBytes(StandardCharsets.UTF_8).length;for(int i=0;i<old.length()&&all.length()<90;i++){JSONObject m=old.getJSONObject(i);int size=m.toString().getBytes(StandardCharsets.UTF_8).length;if(bytes+size>1_100_000)break;all.put(m);bytes+=size;}
                JSONArray deferred=result.optJSONArray("deferredArticles");if(deferred==null)deferred=new JSONArray();Set<String> deferredIds=new HashSet<>();for(int i=0;i<deferred.length();i++)deferredIds.add(deferred.getJSONObject(i).getString("id"));JSONArray overflow=result.optJSONArray("overflowIds");if(overflow!=null)for(int i=0;i<overflow.length();i++)deferredIds.add(overflow.getString(i));s.put("pendingReady",deferred).put("pendingDay",day(now));
                JSONObject processed=object(s,"processed");long cutoff=now-90L*86400000;for(String id:new ArrayList<>(keys(processed)))if(processed.optLong(id)<cutoff)processed.remove(id);JSONArray ids=result.optJSONArray("processedIds");if(ids!=null)for(int i=0;i<ids.length();i++)if(!deferredIds.contains(ids.getString(i)))processed.put(ids.getString(i),now);for(int i=0;i<recommended.length();i++)processed.put(recommended.getJSONObject(i).getString("id"),now);
                if(processed.length()>2000){List<String> keys=new ArrayList<>(keys(processed));keys.sort(Comparator.comparingLong(processed::optLong));for(int i=0;i<keys.size()-2000;i++)processed.remove(keys.get(i));}
                s.put("messages",all).put("processed",processed).put("cursor",seq).put("lastSync",now).put("lastError","").put("homeUrl",home).put("stage","本次获取已完成").put("curation",new JSONObject().put("status","completed").put("newsSeq",seq).put("manual",manual));data.put("newsState",s);
            });if(!manual&&added[0]>0&&config.optBoolean("notifyEnabled")&&valid())notifyNew(context,added[0]);
        }
        void fail(String message){
            if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->fail(message));return;}if(!valid())return;ended=true;
            try{
                JSONObject review=crawler==null?sourceReview:crawler.diagnostics();
                JSONArray links=NewsData.cleanUnresolved(review.optJSONArray("unresolvedLinks"));
                JSONObject savedReview=new JSONObject().put("unresolvedLinks",links).put("stats",object(review,"stats"));
                vault.update(o->{if(o.optLong("newsRevision")!=revision)return;JSONObject s=object(o,"newsState");
                    if(links.length()>0)s.put("failedReview",savedReview);else s.remove("failedReview");
                    s.put("lastError",message==null?"新闻获取未完成":message).put("stage",message==null?"新闻获取未完成":message).put("curation",new JSONObject().put("status","failed").put("error",message==null?"新闻获取未完成":message).put("manual",manual));o.put("newsState",s);
                });
            }catch(Exception ignored){}cleanup(false,message);
        }
        void finish(boolean success,String message){if(!valid())return;ended=true;cleanup(success,message);}
        void cleanup(boolean success,String message){MAIN.removeCallbacks(timeout);if(crawler!=null){crawler.cancel();crawler=null;}if(worker!=null&&!success)worker.interrupt();if(current==this)current=null;stage=success?"本次获取已完成":message;if(completion!=null)completion.run();if(!manual)schedule(context,true);}

    }
    static void markRead(Context context,long seq)throws Exception {new Vault(context).update(o->{JSONObject s=object(o,"newsState");NewsData.markRead(s,seq);o.put("newsState",s);});if(seq==0)context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);}
    static boolean notificationAllowed(Context context){return (Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&context.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    private static void notifyNew(Context context,int count){if(!notificationAllowed(context))return;NotificationManager manager=context.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHANNEL,"校园新闻",NotificationManager.IMPORTANCE_DEFAULT));Intent intent=new Intent(context,MainActivity.class).setAction("edu.hitwh.fieldnote.NEWS").putExtra("openNews",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent pending=PendingIntent.getActivity(context,8403,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.notify(NOTIFICATION,new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("汐序 · 校园消息已更新").setContentText("精选了 "+count+" 条消息，点击查看").setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());}
}
