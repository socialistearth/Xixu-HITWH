package edu.hitwh.fieldnote;

import android.app.*;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Bounded CAS flow. Only a human can request or submit a verification code. */
final class SchoolLogin {
    private final Activity activity;
    private final WebView web;
    private final Vault vault;
    private final Consumer<String> status;
    private final Runnable ready;
    private final Consumer<String> blocked;
    private final String target;
    private final boolean interactive;
    private final Consumer<Boolean> verificationSurface;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> actions=new HashSet<>();
    private final String script,probe;
    private final String appVersion,webViewProvider,webViewVersion;
    private final ArrayDeque<JSONObject> diagnosticHistory=new ArrayDeque<>();
    private String diagnosticClass="STARTING",diagnosticSignature="";
    private AlertDialog dialog;
    private boolean officialVisible;
    private boolean deferredVerification,verificationRequested,waitingForVerification,verificationContinues;
    private boolean ended,closed,loading,auto=true,active=true,checking,challenged,codeOnPage;
    private int navigations,menus,resources;
    private long deadline,unknownSince,otpSubmittedAt,lastCodeRequest;
    private String lastMethod="",resourceUrl="",readyUrl="";

    SchoolLogin(Activity a,WebView w,Vault v,Consumer<String> s,Consumer<Boolean> surface,Runnable onPause,Runnable done)throws Exception{
        this(a,a,w,v,"academic",true,s,surface,message->onPause.run(),done);
    }
    SchoolLogin(Context context,Activity a,WebView w,Vault v,String destination,boolean allowUi,Consumer<String> s,Consumer<Boolean> surface,Consumer<String> onBlocked,Runnable done)throws Exception{
        if(!"academic".equals(destination)&&!"news".equals(destination))throw new IllegalArgumentException("Unknown school destination");
        activity=a;web=w;vault=v;target=destination;interactive=allowUi&&a!=null;status=s;verificationSurface=surface;blocked=onBlocked;ready=done;
        script=Enhancements.read(context.getAssets().open("login-flow.js"),32000);
        probe=Enhancements.read(context.getAssets().open("login-diagnostics.js"),12000);
        String av="unknown",wp="unknown",wv="unknown";
        try{av=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
        try{android.content.pm.PackageInfo info=WebView.getCurrentWebViewPackage();if(info!=null){wp=info.packageName;wv=info.versionName;}}catch(Exception ignored){}
        appVersion=safeVersion(av);webViewProvider=safeVersion(wp);webViewVersion=safeVersion(wv);
        deadline=SystemClock.elapsedRealtime()+90000;
    }
    void pageStarted(){if(ended)return;loading=true;checking=false;if(officialVisible)verificationSurface.accept(false);handler.removeCallbacksAndMessages(null);if(++navigations>16){web.stopLoading();pause("学校页面跳转较多，自动登录已停止；可主动获取后手动完成登录。");}schedule(1000);}
    void pageFinished(){loading=false;schedule(700);}
    void active(boolean value){active=value;if(value)schedule(700);else handler.removeCallbacksAndMessages(null);}
    /** Main-screen tasks opt in: only an explicit openVerification may show school UI. */
    SchoolLogin deferVerification(){deferredVerification=true;return this;}
    void resume(){if(ended)return;auto=true;codeOnPage=false;unknownSince=0;challenged=false;resources=0;menus=0;deadline=SystemClock.elapsedRealtime()+90000;schedule(200);}
    void pause(String message){if(ended)return;auto=false;if(deferredVerification){waitingForVerification=true;verificationRequested=false;}if(!interactive){ended=true;web.stopLoading();handler.removeCallbacksAndMessages(null);}String visible=message+"（诊断："+diagnosticClass+"）";status.accept(visible);blocked.accept(visible);}
    void openVerification(){
        if(ended||dialog!=null||(deferredVerification&&verificationRequested))return;
        if(!interactive){pause("学校需要手动验证，请主动获取后继续。");return;}
        verificationRequested=true;waitingForVerification=false;resume();
        evaluate("inspect",null,null,r->{
            String state=r.optString("state");
            if(isContinuation(state)||"ready".equals(state)){verificationRequested=false;handle(r);return;}
            codeOnPage="otp".equals(state);
            if(deferredVerification&&("captcha".equals(state)||"credentials".equals(state))){
                evaluate("prefill",r,null,filled->{if(ended||!verificationRequested)return;if("filled".equals(filled.optString("state")))showOfficialVerification(r,true);else showOfficialVerification(r,false);});
            }else showOfficialVerification(r,false);
        });
    }
    void close(){closed=true;ended=true;handler.removeCallbacksAndMessages(null);if(dialog!=null){dialog.dismiss();dialog=null;}}
    /** Available only after the recognized target document has finished loading. */
    String readyUrl(){return readyUrl;}
    /** Bounded local diagnostics. Includes no credentials, form values, URLs or queries. */
    synchronized String diagnostics(){
        JSONObject out=new JSONObject();try{out.put("version",1).put("appVersion",appVersion).put("sdk",Build.VERSION.SDK_INT).put("webViewProvider",webViewProvider).put("webViewVersion",webViewVersion).put("target",target).put("classification",diagnosticClass).put("events",new JSONArray(diagnosticHistory));}catch(Exception ignored){}
        return out.toString();
    }
    void copyDiagnostics(){
        if(activity==null)return;
        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("汐序登录诊断",diagnostics()));Toast.makeText(activity,"登录诊断已复制",Toast.LENGTH_SHORT).show();}
    }
    private synchronized void recordDiagnostic(String outcome,String mode,JSONObject result,String error){
        try{
            String state=result==null?"":result.optString("state");
            if(!state.matches("[A-Za-z]{1,32}"))state="unknown";
            if("probe".equals(outcome)){}
            else if("handlerError".equals(outcome))diagnosticClass="NATIVE_HANDLER_ERROR";
            else if("nativeError".equals(outcome))diagnosticClass="NATIVE_EVALUATION_ERROR";
            else if("decodeError".equals(outcome))diagnosticClass="JS_RESULT_ERROR";
            else if("scriptError".equals(state))diagnosticClass="SCRIPT_ERROR";
            else if("unknown".equals(state))diagnosticClass="DOM_UNKNOWN";
            else if(!auto)diagnosticClass="NATIVE_PAUSED";
            else diagnosticClass=state.toUpperCase(Locale.ROOT);
            JSONObject item=new JSONObject().put("result",outcome).put("mode",mode).put("state",state).put("nativeRoute",route(web.getUrl())).put("auto",auto).put("active",active).put("loading",loading).put("dialog",officialVisible).put("deferredVerification",deferredVerification).put("waitingForVerification",waitingForVerification).put("navigations",navigations).put("resourceAttempts",resources);
            if(error!=null&&!error.isEmpty())item.put("error",error.replaceAll("[^A-Za-z]","").substring(0,Math.min(32,error.replaceAll("[^A-Za-z]","").length())));
            JSONObject dom=result==null?null:result.optJSONObject("diagnostic");
            if(dom!=null){
                JSONObject safe=new JSONObject();
                for(String key:new String[]{"route","path","ready","probeError"}){String value=dom.optString(key);if(value.matches("[A-Za-z/_.\\[\\]-]{1,40}"))safe.put(key,value);}
                for(String key:new String[]{"headings","targetHeadings","renderedTargets","exactCards","renderedExactCards","iframes","viewport"})safe.put(key,Math.max(0,Math.min(10000,dom.optInt(key))));
                safe.put("dashboard",dom.optBoolean("dashboard"));
                item.put("dom",safe);
            }
            if(result!=null&&!result.optString("page").isEmpty())item.put("pageMatches",samePage(web.getUrl(),result.optString("page")));
            String signature=item.toString();if(signature.equals(diagnosticSignature))return;diagnosticSignature=signature;
            diagnosticHistory.addLast(item);while(diagnosticHistory.size()>12)diagnosticHistory.removeFirst();
        }catch(Exception ignored){}
    }
    private static String safeVersion(String value){return value!=null&&value.matches("[A-Za-z0-9._+\\-]{1,80}")?value:"unknown";}
    private static boolean samePage(String left,String right){try{Uri a=Uri.parse(left),b=Uri.parse(right);return Objects.equals(a.getScheme(),b.getScheme())&&Objects.equals(a.getHost(),b.getHost())&&Objects.equals(a.getPath(),b.getPath());}catch(Exception e){return false;}}
    private void captureProbe(String mode){
        try{web.evaluateJavascript(probe+"("+new JSONObject().put("target",target)+")",raw->{
            if(ended)return;try{if(raw==null||raw.length()>8000)return;Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String))return;JSONObject value=new JSONObject().put("state","unknown").put("diagnostic",new JSONObject((String)decoded));recordDiagnostic("probe",mode,value,"");}catch(Exception ignored){}
        });}catch(Exception ignored){}
    }
    private static String route(String value){
        try{Uri u=Uri.parse(value);String h=u.getHost(),p=u.getPath();if(p==null)return "unknown";
            if("ids.hit.edu.cn".equals(h)&&p.startsWith("/authserver/"))return "identity";
            if(!"webvpn2.hitwh.edu.cn".equals(h))return "other";
            if("/".equals(p))return "vpnRoot";if("/login".equals(p))return "vpnLogin";
            if(p.startsWith("/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b/authserver/"))return "identity";
            if(p.startsWith(Uri.parse(SchoolPages.BASE).getPath()))return "academic";
            if(p.startsWith("/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b"))return "news";
            return "vpnOther";
        }catch(Exception e){return "unknown";}
    }
    private void schedule(long delay){if(ended||!active)return;handler.removeCallbacksAndMessages(null);handler.postDelayed(this::inspect,delay);}
    private void inspect(){
        if(ended||!active||(activity!=null&&activity.isFinishing()))return;
        if(deferredVerification&&verificationRequested&&!officialVisible&&dialog==null){schedule(1000);return;}
        if(auto&&dialog==null&&SystemClock.elapsedRealtime()>deadline){web.stopLoading();loading=false;pause("自动登录等待超时，未重复提交。可主动获取后手动完成学校登录。");if(ended)return;}
        if(loading||checking||(dialog!=null&&!officialVisible)){schedule(1000);return;}
        checking=true;
        int inspectedNavigation=navigations;
        evaluate("inspect",null,null,result->{checking=false;if(inspectedNavigation==navigations&&!loading&&!(deferredVerification&&verificationRequested&&!officialVisible&&dialog==null))handle(result);if(!ended)schedule(1000);});
    }
    private void handle(JSONObject result){
        String state=result.optString("state");
        if("ready".equals(state)){readyUrl=web.getUrl();ended=true;waitingForVerification=false;verificationRequested=false;handler.removeCallbacksAndMessages(null);if(dialog!=null){verificationSurface.accept(false);dialog.dismiss();}ready.run();return;}
        if(officialVisible){
            // Hide the school page as soon as verification reaches a recognized next step.
            if(isContinuation(state)||(!deferredVerification&&"otp".equals(state)&&!codeOnPage)){
                verificationContinues=true;verificationRequested=false;waitingForVerification=false;verificationSurface.accept(false);dialog.dismiss();auto=true;unknownSince=0;challenged=false;deadline=SystemClock.elapsedRealtime()+90000;
            }else{verificationSurface.accept(true);deadline=SystemClock.elapsedRealtime()+90000;return;}
        }
        // A verification request may finish navigating after the user has
        // closed its page. Recognized safe continuation can resume invisibly;
        // an unfinished challenge remains paused and never reopens itself.
        if(deferredVerification&&waitingForVerification&&isContinuation(state)){waitingForVerification=false;auto=true;unknownSince=0;challenged=false;deadline=SystemClock.elapsedRealtime()+90000;}
        if(!auto)return;
        long now=SystemClock.elapsedRealtime();
        if(!"unknown".equals(state))unknownSince=0;
        switch(state){
            case "resource":
                if(resources++>=2){pause("学校资源入口未完成跳转，请主动获取后手动打开。");return;}
                status.accept("news".equals(target)?"统一认证完成，正在打开校主页…":"统一认证完成，正在打开新教务系统…");
                resourceUrl=result.optString("resourceUrl");
                if(verifiedResource(result,resourceUrl)){loading=true;web.loadUrl(resourceUrl);return;}
                evaluate("resource",result,null,r->{if(!"acted".equals(r.optString("state")))pause("学校资源入口变化，请手动打开对应资源。");else if(!r.optString("resourceUrl").isEmpty())resourceUrl=r.optString("resourceUrl");});return;
            case "menu":
                if(menus++<2){status.accept("统一认证完成，正在打开个人课表…");web.loadUrl(SchoolPages.BASE+"/kbcx/queryGrkb");}
                else pause("教务入口尚未就绪，请在学校页面完成登录。");return;
            case "vpnMethod":case "vpnCas":case "academicCas":case "trustOnce":
                if(!actions.add(state))return;
                status.accept("trustOnce".equals(state)?"正在完成本次登录…":"正在进入学校统一身份认证…");
                evaluate(state,result,null,r->{if(!"acted".equals(r.optString("state")))pause("学校登录入口变化，请在此手动登录。");});return;
            case "credentials":
                if(actions.contains("credentials")){status.accept("登录已提交，正在等待学校验证；不会重复提交密码。");return;}
                submitCredentials(result,false);return;
            case "captcha":
                if(deferredVerification){pause("学校需要验证码，请点击“手动验证”后完成。");return;}
                if(!interactive){pause("学校需要验证码，请主动获取后完成验证。");return;}
                if(challenged)return;
                challenged=true;
                evaluate("prefill",result,null,r->{
                    if("filled".equals(r.optString("state")))showOfficialVerification(result,true);
                    else pause("请先在用户页保存统一认证学号和密码。");
                });return;
            case "otp":
                if(deferredVerification){pause("学校需要登录验证，请点击“手动验证”后完成。");return;}
                if(!interactive){pause("学校需要登录验证码，请主动获取后完成验证。");return;}
                if(codeOnPage){deadline=now+90000;return;}
                if(otpSubmittedAt>0&&now-otpSubmittedAt<4000)return;
                showCode(result,otpSubmittedAt>0);return;
            default:
                if(unknownSince==0)unknownSince=now;
                if(now-unknownSince>10000&&!challenged){challenged=true;pause("学校需要进一步确认，可点击“手动完成验证”继续。");}
        }
    }
    private static boolean isContinuation(String state){return Arrays.asList("menu","resource","vpnMethod","vpnCas","academicCas","trustOnce").contains(state);}
    /** A candidate still needs the exact resource-card DOM evidence from JS. */
    static boolean dashboardCandidate(String value){
        try{Uri u=Uri.parse(value);String path=u.getPath();
            return "https".equals(u.getScheme())&&"webvpn2.hitwh.edu.cn".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&path!=null&&!path.matches("(?i)^/(?:http|https)(?:/.*)?$")&&!java.util.regex.Pattern.compile("(?i)(?:^|/)(?:authserver|auth|login|logout|cas|sso|oauth|saml|mfa)(?:/|$)").matcher(path).find();
        }catch(Exception e){return false;}
    }
    /** Direct same-window navigation only from the real portal's verified card. */
    private boolean verifiedResource(JSONObject page,String value){
        try{
            Uri destination=Uri.parse(value);
            String sourcePage=page.optString("page"),expectedName="news".equals(target)?"校主页":"新教务系统",expectedHost="news".equals(target)?"www.hitwh.edu.cn":"jwts.hitwh.edu.cn";
            if(!dashboardCandidate(web.getUrl())||!dashboardCandidate(sourcePage)||!samePage(web.getUrl(),sourcePage)||!page.optBoolean("dashboard")||!expectedName.equals(page.optString("resourceName"))||!expectedHost.equals(page.optString("resourceHost")))return false;
            String expected="news".equals(target)?"/http/77726476706e69737468656265737421e7e056d22f397c4776468ca88d1b203b":Uri.parse(SchoolPages.BASE).getPath();
            return "https".equals(destination.getScheme())&&"webvpn2.hitwh.edu.cn".equals(destination.getHost())&&destination.getUserInfo()==null&&(destination.getPort()==-1||destination.getPort()==443)&&(expected.equals(destination.getPath())||(expected+"/").equals(destination.getPath()));
        }catch(Exception e){return false;}
    }
    private void submitCredentials(JSONObject page,boolean verified){
        if(!verified&&!actions.add("credentials"))return;
        actions.add("credentials");
        status.accept("正在使用已保存资料进行统一认证…");
        evaluate(verified?"verifiedCredentials":"credentials",page,null,r->{
            String state=r.optString("state");
            if("missing".equals(state)){pause("请返回用户页保存学号和统一认证密码后重试。");}
            else if("captcha".equals(state)){actions.remove("credentials");if(deferredVerification)pause("学校需要验证码，请点击“手动验证”后完成。");else showOfficialVerification(page,true);}
            else if(!"acted".equals(state))pause("学校登录表单变化，请在此手动完成登录。");
        });
    }
    /** Credentials are never returned to the app UI or accepted on other school hosts. */
    private boolean identityPage(){
        try{Uri u=Uri.parse(web.getUrl());String path=u.getPath();
            return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&path!=null&&
                (("ids.hit.edu.cn".equals(u.getHost())&&path.startsWith("/authserver/"))||
                ("webvpn2.hitwh.edu.cn".equals(u.getHost())&&path.startsWith("/https/77726476706e69737468656265737421f9f352d22f397c1e7b0c9ce29b5b/authserver/")));
        }catch(Exception e){return false;}
    }
    private void evaluate(String mode,JSONObject page,JSONObject extra,Consumer<JSONObject> callback){
        if(ended)return;
        try{
            JSONObject arg=extra==null?new JSONObject():extra;arg.put("mode",mode).put("target",target).put("interactive",interactive).put("resourceUrl",resourceUrl);
            if(!interactive&&Arrays.asList("chooseMethod","sendCode","submitCode","verifiedCredentials","prefill").contains(mode)){
                callback.accept(new JSONObject().put("state","blocked"));return;
            }
            if(page!=null)arg.put("expected",page.optString("page"));
            if(Arrays.asList("prefill","credentials","verifiedCredentials","submitCode").contains(mode)){
                if(!identityPage()){callback.accept(new JSONObject().put("state","changed"));return;}
                if(!"submitCode".equals(mode)){JSONObject data=vault.load();arg.put("account",data.optString("studentId")).put("password",data.optString("password"));}
            }
            String probeArg=new JSONObject().put("target",target).toString();
            String call="(function(){var value;try{value="+script+"("+arg+");}catch(error){value=JSON.stringify({state:'scriptError',error:String(error&&error.name||'Error').replace(/[^A-Za-z]/g,'').slice(0,32)});}try{var data=JSON.parse(value);data.diagnostic=JSON.parse("+probe+"("+probeArg+"));return JSON.stringify(data);}catch(error){return JSON.stringify({state:'scriptError',error:'DiagnosticError'});}})()";
            web.evaluateJavascript(call,raw->{
                if(ended)return;
                JSONObject decoded;boolean decodeOk=true;
                try{if(raw==null||raw.length()>32000)throw new IllegalArgumentException();Object value=new JSONTokener(raw).nextValue();if(!(value instanceof String))throw new IllegalArgumentException();decoded=new JSONObject((String)value);}
                catch(Exception e){decodeOk=false;decoded=object("state","scriptError");recordDiagnostic("decodeError",mode,decoded,e.getClass().getSimpleName());captureProbe(mode);}
                if(decodeOk)recordDiagnostic("evaluated",mode,decoded,decoded.optString("error"));
                try{callback.accept(decoded);}catch(RuntimeException e){recordDiagnostic("handlerError",mode,decoded,e.getClass().getSimpleName());pause("登录页面处理出现异常，请复制诊断后反馈。");}
            });
        }catch(Exception e){JSONObject failed=object("state","scriptError");recordDiagnostic("nativeError",mode,failed,e.getClass().getSimpleName());callback.accept(failed);}
    }
    private void showCode(JSONObject page,boolean retry){
        if(deferredVerification){pause("学校需要登录验证，请点击“手动验证”后完成。");return;}
        if(!interactive){pause("学校需要登录验证码，请主动获取后完成验证。");return;}
        if(dialog!=null||ended)return;
        status.accept("学校要求验证码，完成验证后自动继续同步。");
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*activity.getResources().getDisplayMetrics().density);box.setPadding(pad,8,pad,8);
        TextView hint=new TextView(activity);hint.setText(retry?"学校尚未通过验证，请核对验证码后再次提交。":"选择学校提供的验证方式，再获取并输入验证码。");box.addView(hint);
        RadioGroup methods=new RadioGroup(activity);JSONArray options=page.optJSONArray("methods");
        if(options!=null)for(int i=0;i<options.length();i++){String name=options.optString(i);RadioButton b=new RadioButton(activity);b.setId(View.generateViewId());b.setText(name);b.setTag(name);methods.addView(b);}
        box.addView(methods);
        Button send=new Button(activity);send.setText("获取验证码");box.addView(send);
        EditText code=new EditText(activity);code.setSingleLine(true);code.setHint("输入验证码");code.setSaveEnabled(false);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});box.addView(code);
        methods.setOnCheckedChangeListener((group,id)->{
            RadioButton b=group.findViewById(id);if(b==null)return;
            String method=String.valueOf(b.getTag());if(method.equals(lastMethod))return;lastMethod=method;
            send.setEnabled(false);evaluate("chooseMethod",page,object("method",method),r->{send.setEnabled(true);hint.setText("点击获取验证码；如已收到，可直接填写。");});
        });
        if(methods.getChildCount()>0){RadioButton selected=(RadioButton)methods.getChildAt(0);for(int i=0;i<methods.getChildCount();i++){RadioButton b=(RadioButton)methods.getChildAt(i);if(lastMethod.equals(b.getTag()))selected=b;}selected.setChecked(true);}
        send.setOnClickListener(v->{
            long now=SystemClock.elapsedRealtime();if(lastCodeRequest>0&&now-lastCodeRequest<60000){hint.setText("刚刚请求过验证码，请稍等一分钟后再获取。");return;}
            send.setEnabled(false);evaluate("sendCode",page,null,r->{if("acted".equals(r.optString("state"))){lastCodeRequest=SystemClock.elapsedRealtime();hint.setText("已请求验证码，请查看哈工大 APP 或短信。");}else hint.setText("学校暂未允许再次获取；如已收到可直接输入。");send.setEnabled(true);});
        });
        final boolean[] openPage={false};
        dialog=new AlertDialog.Builder(activity).setTitle("学校登录验证").setView(box)
            .setPositiveButton("验证并继续",null).setNegativeButton("取消登录",(d,w)->activity.finish())
            .setNeutralButton("在学校页面验证",(d,w)->{codeOnPage=true;openPage[0]=true;})
            .create();
        dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->activity.finish());
        dialog.setOnDismissListener(d->{code.setText("");dialog=null;deadline=SystemClock.elapsedRealtime()+90000;schedule(1000);if(openPage[0]&&!ended&&!activity.isFinishing())handler.post(()->showOfficialVerification(page,false));});
        dialog.show();AlertDialog current=dialog;
        current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=code.getText().toString().trim();if(value.isEmpty()){code.setError("请填写验证码");return;}
            current.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            evaluate("submitCode",page,object("code",value),r->{
                if("acted".equals(r.optString("state"))){otpSubmittedAt=SystemClock.elapsedRealtime();current.dismiss();}
                else{hint.setText("验证页面已变化，请关闭弹窗后在学校页面完成验证。");current.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}
            });
        });
    }
    /** Reuse the real page for image/slider challenges, without copying images or tokens. */
    private void showOfficialVerification(JSONObject page,boolean credentials){
        if(deferredVerification&&!verificationRequested){pause("学校需要验证，请点击“手动验证”后打开网页。");return;}
        if(!interactive){pause("学校需要手动验证，请主动获取后继续。");return;}
        if(dialog!=null||ended||!(web.getParent() instanceof ViewGroup))return;
        officialVisible=true;
        ViewGroup parent=(ViewGroup)web.getParent();int index=parent.indexOfChild(web);ViewGroup.LayoutParams params=web.getLayoutParams();parent.removeView(web);
        FrameLayout holder=new FrameLayout(activity);holder.addView(web,new FrameLayout.LayoutParams(-1,-1));verificationSurface.accept(true);
        int height=(int)(activity.getResources().getDisplayMetrics().heightPixels*.62);
        holder.setMinimumHeight(height);
        dialog=new AlertDialog.Builder(activity).setTitle(credentials?"请完成学校验证码":"请完成学校页面验证")
            .setView(holder).setPositiveButton("完成验证，继续",deferredVerification?null:(d,w)->{if(credentials)submitCredentials(page,true);else resume();})
            .setNegativeButton(deferredVerification?"关闭网页":"取消登录",(d,w)->{if(!deferredVerification)activity.finish();}).setNeutralButton("复制诊断",null).create();
        dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{if(!deferredVerification)activity.finish();});
        dialog.setOnDismissListener(d->{
            boolean continues=verificationContinues||ended;verificationContinues=false;verificationRequested=false;
            holder.removeView(web);officialVisible=false;dialog=null;
            if(closed||activity.isFinishing()||activity.isDestroyed())return;
            verificationSurface.accept(false);if(web.getParent()==null)parent.addView(web,Math.min(index,parent.getChildCount()),params);deadline=SystemClock.elapsedRealtime()+90000;
            if(deferredVerification&&!continues){codeOnPage=false;pause("验证网页已关闭，任务仍在等待；可再次点击“手动验证”。");}
            schedule(500);
        });
        dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->copyDiagnostics());
        if(deferredVerification)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(credentials){v.setEnabled(false);submitCredentials(page,true);return;}
            evaluate("inspect",null,null,r->{String state=r.optString("state");if("ready".equals(state)||isContinuation(state))handle(r);else status.accept("学校尚未确认验证完成，请在网页内继续登录；关闭网页后任务会等待。");});
        });
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,height+160);
    }
    private static JSONObject object(String key,String value){JSONObject o=new JSONObject();try{o.put(key,value);}catch(Exception ignored){}return o;}
}
