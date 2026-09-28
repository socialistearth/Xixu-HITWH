package edu.hitwh.fieldnote;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONTokener;

/** Original school articles share the app's WebVPN session; there is no native JS bridge. */
public final class NewsArticleActivity extends Activity {
    private static final String VPN="webvpn2.hitwh.edu.cn";
    private static final String IDENTITY="/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b/authserver/";
    private static final String ARTICLE_CHECK="(function(){var title=document.title||'';if(/统一身份认证|用户登录|访问验证|安全验证/.test(title))return false;var body=document.querySelector('.wp_articlecontent,.field--name-body,.article-content,.article_content,.arti_content');return !!body&&((body.innerText||body.textContent||'').trim().length>=25||!!body.querySelector('img,a[href]'));})()";
    private WebView web;
    private TextView status;
    private Button verification;
    private SchoolLogin login;
    private Vault vault;
    private String requestedUrl;
    private int pageGeneration;
    private boolean destroying,attemptedLogin;

    /** Only the gateway's actual proxy routes can be passed to this article reader. */
    static boolean articleUrl(String value){
        try{Uri u=Uri.parse(value);return secure(u)&&VPN.equals(u.getHost())&&proxyPath(u.getPath());}catch(Exception e){return false;}
    }
    private static boolean secure(Uri u){return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}
    private static boolean proxyPath(String path){return path!=null&&path.matches("/(?i:http|https)/[a-fA-F0-9]{32,}(?:/.*)?");}
    private static boolean identityUrl(String value){
        try{Uri u=Uri.parse(value);String path=u.getPath();return secure(u)&&path!=null&&(("ids.hit.edu.cn".equals(u.getHost())&&path.startsWith("/authserver/"))||(VPN.equals(u.getHost())&&path.startsWith(IDENTITY)));}catch(Exception e){return false;}
    }
    private static boolean loginUrl(String value){
        if(identityUrl(value))return true;
        try{Uri u=Uri.parse(value);return secure(u)&&VPN.equals(u.getHost())&&"/login".equals(u.getPath());}catch(Exception e){return false;}
    }
    private static boolean dashboardUrl(String value){
        return SchoolLogin.dashboardCandidate(value);
    }
    static boolean allowed(String value){
        if(articleUrl(value)||identityUrl(value))return true;
        if(dashboardUrl(value))return true;
        try{Uri u=Uri.parse(value);return secure(u)&&VPN.equals(u.getHost())&&"/login".equals(u.getPath());}catch(Exception e){return false;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestedUrl=getIntent().getStringExtra("url");vault=new Vault(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff1f3ec);root.setPadding(dp(12),dp(8),dp(12),0);
        LinearLayout bar=new LinearLayout(this);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->goBack());bar.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=new Button(this);close.setText("关闭原文");close.setOnClickListener(v->finish());bar.addView(close,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);
        status=new TextView(this);status.setTextColor(0xff435b36);status.setTextSize(12);status.setPadding(dp(8),dp(8),dp(8),dp(8));status.setText("正在通过校园 VPN 打开学校原文…");root.addView(status);
        verification=new Button(this);verification.setText("手动完成验证");verification.setVisibility(View.GONE);verification.setOnClickListener(v->{if(login!=null)login.openVerification();});root.addView(verification,new LinearLayout.LayoutParams(-1,dp(48)));
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setLoadWithOverviewMode(true);settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                if(allowed(request.getUrl().toString()))return false;
                status.setText("该链接超出校园 VPN 原文范围，已停止跳转。");return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
                pageGeneration++;
                if(!allowed(url)){view.stopLoading();stopLogin("跳转超出校园 VPN 范围，已停止访问。");return;}
                status.setText(loginUrl(url)?"学校登录已过期，正在完成统一认证…":"正在加载学校原文…");
                if(login!=null)login.pageStarted();
            }
            @Override public void onPageFinished(WebView view,String url){
                if(destroying||!allowed(url))return;
                CookieManager.getInstance().flush();
                if(loginUrl(url)||dashboardUrl(url)){if(login==null&&!attemptedLogin)beginLogin();if(login!=null)login.pageFinished();return;}
                if(login==null){status.setText("学校原文 · 校园 VPN");return;}
                // A restored CAS session may return directly to this article.
                // Read its existing document only; never fetch the homepage in
                // place of an already-readable article.
                int generation=pageGeneration;
                view.evaluateJavascript(ARTICLE_CHECK,raw->{
                    if(destroying||generation!=pageGeneration||login==null)return;
                    boolean readable=false;try{readable=Boolean.TRUE.equals(new JSONTokener(raw).nextValue());}catch(Exception ignored){}
                    if(readable&&articleUrl(web.getUrl())){endLogin();status.setText("学校原文 · 校园 VPN");}
                    else if(login!=null)login.pageFinished();
                });
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();stopLogin("学校页面证书验证失败，已停止访问。");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())stopLogin("学校原文暂时无法加载，请稍后重新打开。");}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));UiInsets.install(this,root);
        if(articleUrl(requestedUrl))web.loadUrl(requestedUrl);else status.setText("这条新闻没有可用的校园 VPN 原文地址。");
    }
    private void beginLogin(){
        attemptedLogin=true;
        try{
            login=new SchoolLogin(this,this,web,vault,"news",true,status::setText,visible->{},message->{status.setText(message);verification.setVisibility(View.VISIBLE);},()->{
                CookieManager.getInstance().flush();endLogin();
                if(!destroying&&!isFinishing()&&articleUrl(requestedUrl)){status.setText("登录完成，正在打开学校原文…");web.loadUrl(requestedUrl);}
            });
            login.pageFinished();
        }catch(Exception e){status.setText("自动登录暂时不可用，可在当前学校页面手动登录。");}
    }
    private void stopLogin(String message){if(login!=null)login.pause(message);status.setText(message);}
    private void endLogin(){SchoolLogin current=login;login=null;if(current!=null)current.close();verification.setVisibility(View.GONE);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void goBack(){if(web!=null&&web.canGoBack())web.goBack();else finish();}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(login!=null)login.active(true);}
    @Override protected void onPause(){if(login!=null)login.active(false);if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){destroying=true;endLogin();if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
