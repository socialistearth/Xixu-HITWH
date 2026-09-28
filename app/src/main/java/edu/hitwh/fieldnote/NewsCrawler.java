package edu.hitwh.fieldnote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One bounded, serial visit to the authenticated 校主页; no JavaScript bridge on school pages. */
final class NewsCrawler {
    private static final String ENTRY="https://webvpn2.hitwh.edu.cn/";
    private static final Pattern PROXY_ROOT=Pattern.compile("^/(?:http|https)/[A-Za-z0-9_-]+(?=/|$)");
    private static final Pattern ARTICLE_PATH=Pattern.compile("^/(?:http|https)/[A-Za-z0-9_-]+/20\\d{2}/\\d{4}/c\\d+a\\d+/page\\.(?:htm|psp)$");
    private static final Pattern PUBLIC_PATH=Pattern.compile("^/20\\d{2}/\\d{4}/c\\d+a\\d+/page\\.(?:htm|psp)$");
    private static final Pattern REVIEW_PATH=Pattern.compile("/(20\\d{2})/(\\d{2})(\\d{2})/c\\d+a(\\d+)/page\\.(?:htm|psp)$",Pattern.CASE_INSENSITIVE);
    private static final long PACE_MS=900;
    private final Context context;
    private final Activity activity;
    private final FrameLayout holder;
    private final Set<String> knownIds;
    private final Consumer<String> status;
    private final Consumer<JSONObject> success;
    private final Consumer<String> failure;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String script;
    private final JSONArray articles=new JSONArray();
    private final ArrayDeque<JSONObject> pending=new ArrayDeque<>();
    private final Set<String> warningReasons=new LinkedHashSet<>();
    private final Set<String> discoveredUrls=new HashSet<>();
    private final Set<String> publicFallbacks=new HashSet<>();
    private WebView web;
    private SchoolLogin login;
    private String homeUrl="";
    private String pendingAlternative="";
    private JSONObject current;
    private JSONObject stats=new JSONObject();
    private JSONArray unresolvedLinks=new JSONArray();
    private String emptyReason="";
    private boolean ended,started,loggedIn,active=true,loaded,reading,waitingNext;
    private volatile boolean waitingVerification;
    private volatile int readProgress=-1;
    private int skipped,visited,navigationVersion,homeChecks;
    
    NewsCrawler(Context context,Activity activity,FrameLayout holder,Set<String> knownIds,
            Consumer<String> status,Consumer<JSONObject> success,Consumer<String> failure)throws Exception{
        this.context=context.getApplicationContext();this.activity=activity;this.holder=holder;
        this.knownIds=new HashSet<>(knownIds);this.status=status;this.success=success;this.failure=failure;
        script=Enhancements.read(context.getAssets().open("news-scraper.js"),32000);
    }
    void start(){onMain(()->{if(started||ended)return;started=true;try{createBrowser();status.accept("正在通过校园 VPN 打开校主页…");web.loadUrl(ENTRY);}catch(Exception e){fail("新闻浏览器启动失败，请稍后重试。");}});}
    void cancel(){onMain(()->{if(ended)return;ended=true;dispose();});}
    void active(boolean value){onMain(()->{if(ended)return;active=value;if(web!=null){if(value)web.onResume();else web.onPause();}if(login!=null)login.active(value);if(value&&loggedIn){if(!pendingAlternative.isEmpty())loadAlternative();else if(waitingNext)next();else if(loaded&&!reading)readPage();}});}
    boolean needsVerification(){return waitingVerification;}
    int progress(){return waitingVerification?-1:readProgress;}
    void openVerification(){onMain(()->{if(!ended&&login!=null&&!loggedIn){waitingVerification=false;readProgress=-1;login.openVerification();}});}
    String loginDiagnostics(){return login==null?"当前无进行中的学校登录":login.diagnostics();}
    /** Retained after dispose, so a failed run can offer its safe unresolved links for manual review. */
    JSONObject diagnostics(){
        try{
            JSONObject counters=new JSONObject(stats.toString()).put("read",articles.length()).put("failed",skipped);
            return new JSONObject().put("homeUrl",reviewUrl(homeUrl,false)).put("stats",counters).put("unresolvedLinks",new JSONArray(unresolvedLinks.toString()));
        }catch(Exception e){return new JSONObject();}
    }
    private String reviewUrl(String value,boolean article){
        try{
            if(value==null||value.length()>2048||value.indexOf('\\')>=0||value.matches("(?s).*[\\x00-\\x20\\x7f].*"))return "";
            Uri u=Uri.parse(value);String scheme=u.getScheme(),host=u.getHost(),path=u.getEncodedPath();int port=u.getPort();
            if(!("http".equals(scheme)||"https".equals(scheme))||u.getUserInfo()!=null||host==null||!(host.equals("hit.edu.cn")||host.endsWith(".hit.edu.cn")||host.equals("hitwh.edu.cn")||host.endsWith(".hitwh.edu.cn")))return "";
            if(port!=-1&&!("http".equals(scheme)&&port==80)&&!("https".equals(scheme)&&port==443))return "";
            if(article&&(path==null||!REVIEW_PATH.matcher(path).find()))return "";
            return u.buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return "";}
    }
    private void captureUnresolved(JSONObject result){
        JSONArray input=result.optJSONArray("unresolvedLinks"),safe=new JSONArray();Set<String> seen=new HashSet<>();
        if(input!=null)for(int i=0;i<input.length()&&safe.length()<40;i++){
            try{
                JSONObject row=input.optJSONObject(i);if(row==null)continue;String url=reviewUrl(row.optString("url"),true);if(url.isEmpty())continue;
                Matcher identity=REVIEW_PATH.matcher(Uri.parse(url).getEncodedPath());if(!identity.find())continue;
                String id=("news.hit.edu.cn".equals(Uri.parse(url).getHost())?"hit-news:a":"hitwh-home:a")+identity.group(4),published=identity.group(1)+"-"+identity.group(2)+"-"+identity.group(3);
                if(!id.equals(row.optString("id"))||!published.equals(row.optString("published"))||knownIds.contains(id)||!seen.add(id))continue;
                String title=row.optString("title","待手动查看的文章").trim(),reason=row.optString("reason","未能转换为可自动读取的校园 VPN 链接").trim();
                safe.put(new JSONObject().put("id",id).put("title",title.substring(0,Math.min(180,title.length()))).put("url",url).put("published",published).put("reason",reason.substring(0,Math.min(200,reason.length()))));
            }catch(Exception ignored){}
        }
        unresolvedLinks=safe;
    }
    private void onMain(Runnable action){if(Looper.myLooper()==Looper.getMainLooper())action.run();else handler.post(action);}
    private void createBrowser()throws Exception{
        web=new WebView(activity==null?context:activity);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);settings.setUseWideViewPort(true);
        settings.setLoadsImagesAutomatically(false);settings.setBlockNetworkImage(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient());
        surface(false);
        // Alpha zero still receives touches: keep this hidden browser beneath the main interface.
        if(holder!=null)holder.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
        else{
            // A detached background WebView still needs a real viewport for the school's form DOM.
            int width=(int)(context.getResources().getDisplayMetrics().density*390);
            int height=(int)(context.getResources().getDisplayMetrics().density*760);
            web.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            web.layout(0,0,width,height);
        }
        login=new SchoolLogin(context,activity,web,new Vault(context),"news",activity!=null&&holder!=null,status,this::surface,
            message->{if(activity==null||holder==null)fail(message);else{waitingVerification=true;readProgress=-1;status.accept(message);}},this::homepageReady);
        if(activity!=null&&holder!=null)login.deferVerification();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();
                if(!request.isForMainFrame())return !PortalActivity.allowed(url);
                if(!"GET".equalsIgnoreCase(request.getMethod())&&loggedIn){pageFailed("文章要求提交表单，已跳过。",false);return true;}
                if(allowedNavigation(url))return false;
                if(loggedIn)pageFailed("文章跳转超出本篇允许的地址范围");else fail("学校登录跳转到不支持的页面，已停止本次获取。");return true;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                if(ended||waitingNext)return;navigationVersion++;loaded=false;reading=false;
                if(!allowedNavigation(url)){view.stopLoading();if(loggedIn)pageFailed("文章跳转超出本篇允许的地址范围");else fail("学校页面跳转超出本次访问范围，已停止获取。");return;}
                if(!loggedIn&&login!=null)login.pageStarted();
                if(loggedIn&&current!=null){int version=navigationVersion;handler.postDelayed(()->{if(!ended&&!waitingNext&&version==navigationVersion&&!loaded)pageFailed("文章连接等待超时");},25000);}
            }
            @Override public void onPageFinished(WebView view,String url){
                if(ended||!allowedNavigation(url)||!url.equals(view.getUrl()))return;loaded=true;
                if(!loggedIn){if(login!=null)login.pageFinished();return;}
                int version=navigationVersion;handler.postDelayed(()->{if(!ended&&active&&version==navigationVersion)readPage();},500);
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();if(errorForCurrent(error.getUrl()))pageFailed("网页证书验证失败");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){if(request.isForMainFrame()&&errorForCurrent(request.getUrl().toString()))pageFailed("连接失败或超时");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&errorForCurrent(request.getUrl().toString()))pageFailed("HTTP "+response.getStatusCode());}
        });
    }
    private void surface(boolean visible){
        if(web==null)return;web.setAlpha(visible?1f:0f);web.setEnabled(visible);web.setClickable(visible);web.setFocusable(visible);web.setFocusableInTouchMode(visible);
        web.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Verification images are enabled only when a human is viewing the school challenge.
        web.getSettings().setBlockNetworkImage(!visible);web.getSettings().setLoadsImagesAutomatically(visible);
        if(!visible)web.clearFocus();
    }
    private void homepageReady(){
        if(ended)return;
        try{
            homeUrl=web.getUrl();Uri uri=Uri.parse(homeUrl);Matcher match=PROXY_ROOT.matcher(uri.getPath()==null?"":uri.getPath());
            if(!"https".equals(uri.getScheme())||!"webvpn2.hitwh.edu.cn".equals(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!match.find()){
                fail("校主页没有通过校园 VPN 打开，请主动获取后选择“校主页”。");return;
            }
            waitingVerification=false;readProgress=-1;loggedIn=true;loaded=true;surface(false);
            status.accept("已登录校主页，正在读取最近七天的新闻链接…");readPage();
        }catch(Exception e){fail("校主页地址无法识别，请主动获取后重新登录。");}
    }
    private String articleUrl(String url){
        try{
            Uri uri=Uri.parse(url);String path=uri.getEncodedPath();
            if(url.length()>2048||url.indexOf('\\')>=0||url.matches("(?s).*[\\x00-\\x20\\x7f].*")||uri.getUserInfo()!=null||path==null)return "";
            String host=uri.getHost(),scheme=uri.getScheme();int port=uri.getPort();
            boolean secure="https".equals(scheme)&&(port==-1||port==443);
            boolean publicNews="news.hit.edu.cn".equals(host)||"news.hitwh.edu.cn".equals(host);
            boolean publicScheme=secure||"http".equals(scheme)&&(port==-1||port==80);
            if(!((secure&&"webvpn2.hitwh.edu.cn".equals(host)&&ARTICLE_PATH.matcher(path).matches())||(publicNews&&publicScheme&&PUBLIC_PATH.matcher(path).matches())))return "";
            return "https://"+host+path.replaceFirst("/page\\.psp$","/page.htm");
        }catch(Exception e){return "";}
    }
    /** Ignore a late error from the VPN request after its public alternative or next article began. */
    private boolean errorForCurrent(String url){
        if(ended||waitingNext||web==null)return false;
        if(!loggedIn||current==null)return url!=null&&url.equals(web.getUrl());
        String canonical=articleUrl(url);return !canonical.isEmpty()&&canonical.equals(current.optString("url"));
    }
    private boolean allowedNavigation(String url){
        if(!loggedIn)return PortalActivity.allowed(url);
        if(current==null)return homeUrl.equals(url);
        // A raw homepage HTTP link is upgraded before queueing; an actual network
        // redirect must remain HTTPS, never silently downgrade the reader.
        if(!"https".equals(Uri.parse(url).getScheme()))return false;
        String canonical=articleUrl(url);
        return !canonical.isEmpty()&&discoveredUrls.contains(canonical)&&canonical.equals(current.optString("url"));
    }
    private void readPage(){
        if(ended||!active||!loggedIn||!loaded||reading||waitingNext)return;
        if(!allowedNavigation(web.getUrl())){fail("学校登录状态已变化，请主动获取后重新验证。");return;}
        reading=true;int version=navigationVersion;
        try{
            JSONObject arg=new JSONObject().put("homeUrl",homeUrl).put("nativeUrl",web.getUrl()).put("expected",web.getUrl()).put("nowMs",System.currentTimeMillis());
            if(current==null){arg.put("mode","home");JSONArray known=new JSONArray();for(String id:knownIds){if(known.length()>=20000)break;known.put(id);}arg.put("knownIds",known);}
            else arg.put("mode","article").put("articleUrl",current.optString("url")).put("publicUrl",current.optString("publicUrl")).put("id",current.optString("id")).put("title",current.optString("title"));
            web.evaluateJavascript(script+"("+arg+")",raw->{
                if(ended||version!=navigationVersion)return;
                try{
                    if(raw==null||raw.length()>400000)throw new IllegalArgumentException();
                    Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new IllegalArgumentException();
                    JSONObject result=new JSONObject((String)decoded);String state=result.optString("state");
                    if("login".equals(state)){if(current==null)fail(result.optString("message","学校需要重新登录，请主动获取后完成验证。"));else pageFailed(result.optString("message","文章需要进一步访问验证，已跳过。"),false);return;}
                    if(current==null&&"homePending".equals(state)){
                        captureUnresolved(result);
                        JSONObject reported=result.optJSONObject("stats");if(reported!=null)stats=reported;
                        if(++homeChecks<3){
                            reading=false;status.accept("正在等待校主页新闻链接加载完成…");
                            handler.postDelayed(()->{if(!ended&&active&&version==navigationVersion)readPage();},1200);
                        }else fail("已进入校主页，但新闻链接未能完整识别（页面链接 "+stats.optInt("anchors")+" 条，近期链接未转换 "+stats.optInt("unresolvedRecent")+" 篇，缺少标题 "+stats.optInt("missingTitle")+" 篇）；本次未调用精选模型。");
                    }else if(current==null&&"home".equals(state)){
                        captureUnresolved(result);
                        JSONObject reported=result.optJSONObject("stats");if(reported==null)throw new IllegalArgumentException();stats=reported;emptyReason=result.optString("emptyReason");
                        JSONArray links=result.optJSONArray("articles");
                        if(links==null)throw new IllegalArgumentException();
                        for(int i=0;i<links.length()&&pending.size()<20;i++){
                            JSONObject item=links.optJSONObject(i);if(item==null)continue;
                            String linked=articleUrl(item.optString("url"));
                            if(!linked.isEmpty()&&!knownIds.contains(item.optString("id"))&&discoveredUrls.add(linked)){
                                item.put("url",linked);pending.add(item);
                            }
                        }
                        stats.put("queued",pending.size());
                        if(stats.optInt("new")>0&&pending.isEmpty()){fail("校主页新闻链接校验未通过，本次未调用精选模型。");return;}
                        if(stats.optInt("unresolvedRecent")>0)warningReasons.add(stats.optInt("unresolvedRecent")+"篇近期文章的链接未能识别为可访问的校园 VPN 链接，尚未读取");
                        if(stats.optInt("missingTitle")>0)warningReasons.add(stats.optInt("missingTitle")+"篇近期文章尚未读取到标题，后续获取可再次尝试");
                        if(result.optBoolean("limited"))warningReasons.add("为减少学校访问，本次最多读取20篇新文章");
                        next();
                    }else if(current!=null&&"article".equals(state)){
                        JSONObject item=result.optJSONObject("article");
                        if(item==null||!current.optString("id").equals(item.optString("id"))||!allowedNavigation(item.optString("url")))throw new IllegalArgumentException();
                        articles.put(item);next();
                    }else if(current!=null&&"failed".equals(state))pageFailed(result.optString("message","正文未能读取"));
                    else if(current!=null)pageFailed("文章页面已变化，未能确认本篇正文");
                    else fail("学校页面已变化，未改动已有新闻，请主动获取后重试。");
                }catch(Exception e){pageFailed("新闻页面读取未完成");}
            });
        }catch(Exception e){pageFailed("新闻读取脚本未能运行");}
    }
    private void next(){
        if(ended)return;waitingNext=true;loaded=false;reading=false;navigationVersion++;
        int total=visited+pending.size();readProgress=total>0?Math.min(100,(articles.length()+skipped)*100/total):100;
        if(!active)return;
        if(pending.isEmpty()){complete();return;}
        long delay=PACE_MS;
        int version=navigationVersion;
        handler.postDelayed(()->{
            if(ended||!active||version!=navigationVersion)return;
            current=pending.removeFirst();visited++;waitingNext=false;
            status.accept("正在读取校主页新闻 "+visited+" / "+(visited+pending.size())+"…");web.loadUrl(current.optString("url"));
        },delay);
    }
    private void pageFailed(String message){pageFailed(message,true);}
    private void pageFailed(String message,boolean allowPublic){
        if(ended||waitingNext)return;
        if(!loggedIn||current==null){fail("校主页访问失败："+message);return;}
        if(allowPublic&&publicFallback())return;
        skipped++;if(warningReasons.size()<6)warningReasons.add(message);if(web!=null)web.stopLoading();next();
    }
    /** One same-article HTTPS alternative, supplied by the homepage; never retry or guess a VPN route. */
    private boolean publicFallback(){
        String candidate=articleUrl(current.optString("publicUrl")),original=articleUrl(current.optString("url"));
        try{
            Uri alternative=Uri.parse(candidate),prior=Uri.parse(original);
            String host=alternative.getHost(),path=alternative.getEncodedPath(),previousPath=prior.getEncodedPath();
            if(candidate.isEmpty()||!("news.hit.edu.cn".equals(host)||"news.hitwh.edu.cn".equals(host))||!"webvpn2.hitwh.edu.cn".equals(prior.getHost())||path==null||previousPath==null||!previousPath.endsWith(path)||!publicFallbacks.add(current.optString("id")))return false;
            current.put("url",candidate);discoveredUrls.add(candidate);pendingAlternative=candidate;waitingNext=true;loaded=false;reading=false;navigationVersion++;
            web.stopLoading();status.accept("校园 VPN 未能读取本篇，正在读取同篇学校新闻公开正文…");
            loadAlternative();
            return true;
        }catch(Exception e){return false;}
    }
    private void loadAlternative(){
        if(ended||!active||pendingAlternative.isEmpty())return;
        String candidate=pendingAlternative;int version=navigationVersion;
        handler.postDelayed(()->{if(ended||!active||version!=navigationVersion||!candidate.equals(pendingAlternative))return;pendingAlternative="";waitingNext=false;web.loadUrl(candidate);},PACE_MS);
    }
    private void complete(){
        if(ended)return;
        try{
            JSONArray warnings=new JSONArray();if(skipped>0)warnings.put(skipped+"篇校主页文章未能读取，后续获取可再次尝试");for(String reason:warningReasons)warnings.put(reason);
            stats.put("read",articles.length()).put("failed",skipped);
            JSONObject result=new JSONObject().put("unresolvedLinks",unresolvedLinks).put("stats",stats).put("emptyReason",emptyReason).put("articles",articles).put("warnings",warnings).put("homeUrl",reviewUrl(homeUrl,false)).put("visited",visited).put("skipped",skipped);
            ended=true;dispose();success.accept(result);
        }catch(Exception e){fail("新闻结果整理失败，请稍后重试。");}
    }
    private void fail(String message){if(ended)return;ended=true;dispose();failure.accept(message);}
    private void dispose(){
        waitingVerification=false;readProgress=-1;
        handler.removeCallbacksAndMessages(null);if(login!=null){login.close();login=null;}
        if(web!=null){web.stopLoading();web.setWebChromeClient(null);web.setWebViewClient(new WebViewClient());if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}
    }
}
