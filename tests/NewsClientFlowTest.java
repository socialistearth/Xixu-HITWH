package edu.hitwh.fieldnote;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production client/crawler call and state tests. Android/login/model boundaries are fake. */
public final class NewsClientFlowTest {
    private static int checks;
    private static final String HOME="https://webvpn2.hitwh.edu.cn/https/0123456789abcdef0123456789abcdef/";
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static JSONObject snapshot(Context c)throws Exception{return NewsClient.snapshot(new Vault(c));}
    private static void reset()throws Exception {
        NewsClient.cancelActive("test reset");Handler.drain();NewsClient.academic(new Context(),false);
        LocalNews.entered=null;LocalNews.release=null;
        Vault.data=new JSONObject().put("studentId","test-student").put("password","not-a-real-password")
            .put("newsConfig",new JSONObject().put("engine","phone-v1").put("baseUrl","https://model.invalid/v1")
                .put("model","offline").put("apiKey","fake-key").put("autoEnabled",true).put("pushTime","19:00"))
            .put("newsState",new JSONObject());
    }
    private static FrameLayout holder(Activity a){FrameLayout h=new FrameLayout(a);h.addView(new View(),0,new FrameLayout.LayoutParams(-1,-1));a.content=h;return h;}
    private static void home(){WebView.last.url=HOME;SchoolLogin.last.ready.run();}
    private static JSONObject emptyHome(){return new JSONObject().put("state","home").put("stats",new JSONObject().put("new",0)).put("articles",new JSONArray()).put("emptyReason","allProcessed");}
    private static void awaitComplete(Context context)throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(NewsClient.busy()&&System.nanoTime()<limit){Handler.drain();Thread.sleep(5);}
        Handler.drain();check(!NewsClient.busy(),"worker completed within local timeout");
        JSONObject s=snapshot(context);check("completed".equals(s.getJSONObject("curation").optString("status")),"completed state persisted");
        check(s.getInt("progress")==100,"completed snapshot progress 100");
    }
    public static void main(String[] args)throws Exception {
        // Any accidental java.net URL access fails locally instead of reaching the school/model.
        URL.setURLStreamHandlerFactory(protocol->new URLStreamHandler(){protected URLConnection openConnection(URL u){throw new AssertionError("External network forbidden: "+u.getProtocol());}});
        reset();Activity owner=new Activity();FrameLayout h=holder(owner);View main=h.children.get(0);
        int created=WebView.created;JSONObject started=NewsClient.startManual(owner,h);WebView web=WebView.last;SchoolLogin login=SchoolLogin.last;
        check(owner.starts==0,"manual start does not launch Activity");check(WebView.created==created+1,"one hidden crawler created");
        check(h.children.size()==2&&h.children.get(0)==web&&h.children.get(1)==main,"hidden webview inserted beneath main content at index 0");
        check(web.alpha==0&&!web.enabled&&!web.clickable&&!web.focusable&&!web.focusableTouch,"hidden browser does not accept focus or touches");
        check(web.accessibility==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,"hidden school page excluded from accessibility");
        check(web.settings.blockImages&&!web.settings.loadImages,"hidden challenge images blocked");
        check(login.interactive&&login.deferred,"manual login opts into deferred verification");
        check(started.getBoolean("busy")&&!started.getBoolean("needsVerification")&&started.getInt("progress")==-1,"login starts busy with unknown progress");
        NewsClient.startManual(owner,h);check(WebView.created==created+1&&SchoolLogin.last==login,"duplicate start leaves original crawler intact");
        login.blocked.accept("需要验证码");JSONObject blocked=snapshot(owner);
        check(blocked.getBoolean("busy")&&blocked.getBoolean("needsVerification")&&blocked.getInt("progress")==-1,"blocked manual run waits with unknown progress");
        check(login.opened==0&&web.alpha==0,"blocked callback does not open verification UI");
        Activity stranger=new Activity();NewsClient.verify(stranger);check(login.opened==0,"nonowner verify ignored");
        NewsClient.verify(owner);check(login.opened==1&&!snapshot(owner).getBoolean("needsVerification"),"owner verify clears waiting state and calls login");
        check(web.alpha==1&&web.enabled&&web.clickable&&web.settings.loadImages,"explicit verification enables challenge surface");
        login.blocked.accept("验证码仍需处理");check(snapshot(owner).getBoolean("needsVerification"),"blocked again restores verification state");
        home();check(!snapshot(owner).getBoolean("needsVerification"),"homepage-ready clears verification state");
        check(web.alpha==0&&!web.enabled&&web.callback!=null,"homepage-ready hides surface and starts scraper");
        check(snapshot(owner).getInt("progress")==-1,"homepage discovery has unknown progress");
        NewsClient.academic(owner,true);check(NewsClient.busy()&&!web.destroyed,"academic busy change does not cancel active news");
        NewsClient.cancelFor(stranger);check(NewsClient.busy()&&!web.destroyed,"nonowner cancellation ignored");
        NewsClient.cancelFor(owner);check(!NewsClient.busy()&&web.destroyed&&h.children.size()==1,"owner cancellation disposes only news browser");
        check(snapshot(owner).getInt("progress")==-1,"cancelled snapshot does not claim completion");
        created=WebView.created;NewsClient.startManual(owner,h);
        check(!NewsClient.busy()&&WebView.created==created,"academic busy rejects a new news crawl");
        check(snapshot(owner).optString("lastError").contains("教务同步"),"manual academic contention is explained");

        reset();owner=new Activity();h=holder(owner);NewsClient.startManual(owner,h);home();
        LocalNews.entered=new CountDownLatch(1);LocalNews.release=new CountDownLatch(1);web=WebView.last;web.respond(emptyHome());
        check(LocalNews.entered.await(2,TimeUnit.SECONDS),"local model boundary reached");
        check(web.destroyed&&snapshot(owner).getInt("progress")==-1,"model stage disposes browser and retains unknown progress");
        LocalNews.release.countDown();awaitComplete(owner);
        check(owner.starts==0,"completed manual run never launched Activity");

        reset();Context background=new Context();int[] finished={0};long run=NewsClient.startAutomatic(background,()->finished[0]++);Handler.drain();
        check(run>0&&NewsClient.busy(),"automatic job starts");login=SchoolLogin.last;web=WebView.last;
        check(!login.interactive&&!login.deferred&&web.parent==null,"automatic login is noninteractive and browser detached");
        login.blocked.accept("学校需要人工验证");check(!NewsClient.busy()&&finished[0]==1,"automatic blocked fails and completes callback once");
        check(web.destroyed&&login.closed&&login.opened==0,"automatic blocked closes browser without opening UI");
        check(background.starts==0&&background.notifications.shown==0,"automatic failure shows no Activity or notification");
        check(!snapshot(background).getBoolean("needsVerification")&&snapshot(background).getInt("progress")==-1,"automatic failure not presented as actionable running verification");
        NewsClient.cancelAutomatic(run);check(finished[0]==1,"stale automatic stop cannot repeat completion");

        reset();background=new Context();finished[0]=0;run=NewsClient.startAutomatic(background,()->finished[0]++);Handler.drain();home();WebView.last.respond(emptyHome());awaitComplete(background);
        check(finished[0]==1&&background.starts==0&&background.notifications.shown==0,"empty automatic completion stays silent");
        check(!snapshot(background).getJSONObject("curation").getBoolean("manual"),"automatic completion records automatic origin");
        check(NewsClient.startAutomatic(background,()->{})==0,"automatic same-day duplicate skipped");
        System.out.println("NewsClientFlowTest: "+checks+" offline state/call checks passed (Android boundaries mocked; not device tests)");
    }
}
