package edu.hitwh.fieldnote;

import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Shared JSON contract. No Android APIs, cookies, school account or model key. */
final class NewsData {
    static String baseUrl(String value){
        try{URI uri=new URI(value.trim());String path=uri.getPath();
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()==0||uri.getPort()>65535||value.length()>500||path.contains("..")||!path.matches("[A-Za-z0-9/_-]*"))throw new Exception();
            return uri.toASCIIString().replaceAll("/+$","");
        }catch(Exception e){throw new IllegalArgumentException("请填写 HTTPS 新闻服务地址，例如 https://news.example.com");}
    }
    static boolean articleUrl(String value){
        try{URI u=new URI(value);
            boolean legacy=Arrays.asList("today.hitwh.edu.cn","www.hitwh.edu.cn").contains(u.getHost());
            boolean homepageNews=Arrays.asList("news.hit.edu.cn","news.hitwh.edu.cn").contains(u.getHost())
                &&"https".equals(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)&&u.getQuery()==null&&u.getFragment()==null
                &&u.getRawPath()!=null&&u.getRawPath().matches("/(?:[A-Za-z0-9_-]+/)*20[0-9]{2}/[0-9]{4}/c[0-9]+a[0-9]+/page\\.(?:htm|psp)");
            return value.length()<=1600&&("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==80||u.getPort()==443)&&(legacy||homepageNews);}
        catch(Exception e){return false;}
    }
    static boolean vpnArticleUrl(String value){
        try{URI u=new URI(value);return value.length()<=1600&&"https".equals(u.getScheme())&&"webvpn2.hitwh.edu.cn".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getQuery()==null&&u.getFragment()==null&&u.getPath().matches("/(?:http|https)(?:-[0-9]+)?/[0-9a-fA-F]{20,}/20[0-9]{2}/[0-9]{4}/c[0-9]+a[0-9]+/page\\.htm");}
        catch(Exception e){return false;}
    }
    /** Saved manual-review links only: official school hosts, no login/session data. */
    static boolean unresolvedArticleUrl(String value){
        try{URI u=new URI(value);String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT),scheme=u.getScheme();
            boolean http="http".equals(scheme),https="https".equals(scheme);
            return value.length()<=2048&&(http||https)&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPort()==-1||http&&u.getPort()==80||https&&u.getPort()==443)
                &&(host.equals("hit.edu.cn")||host.endsWith(".hit.edu.cn")||host.equals("hitwh.edu.cn")||host.endsWith(".hitwh.edu.cn"))
                &&u.getRawPath()!=null&&u.getRawPath().matches("(?:/[A-Za-z0-9_-]+)*/20[0-9]{2}/[0-9]{4}/c[0-9]+a[0-9]+/page\\.(?:htm|psp)");
        }catch(Exception e){return false;}
    }
    static JSONArray cleanUnresolved(JSONArray source)throws Exception{
        JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();if(source==null)return out;
        for(int i=0;i<source.length()&&out.length()<40;i++){
            JSONObject row=source.optJSONObject(i);if(row==null)continue;String url=row.optString("url"),id=row.optString("id");
            if(!unresolvedArticleUrl(url)||id.isEmpty()||id.length()>100||!seen.add(id))continue;
            JSONObject clean=new JSONObject().put("id",id).put("url",url);
            for(String key:new String[]{"title","published","reason"}){String v=row.optString(key);int limit="title".equals(key)?180:"published".equals(key)?10:200;clean.put(key,v.substring(0,Math.min(v.length(),limit)));}
            out.put(clean);
        }
        return out;
    }
    static String unresolvedLink(JSONObject state,JSONObject request)throws Exception{
        long seq=integer(request,"seq"),index=integer(request,"index");if(state==null||index>=40)throw new IllegalArgumentException("这条链接已不存在，请更新列表");
        JSONObject owner=seq==0?state.optJSONObject("failedReview"):null;
        if(seq>0){JSONArray messages=state.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optLong("seq")==seq){owner=m;break;}}}
        JSONArray links=owner==null?null:owner.optJSONArray("unresolvedLinks");JSONObject link=links==null?null:links.optJSONObject((int)index);
        String url=link==null?"":link.optString("url");if(link==null||request.optString("linkId").isEmpty()||!request.optString("linkId").equals(link.optString("id"))||!unresolvedArticleUrl(url))throw new IllegalArgumentException("这条链接已不存在或不可打开，请更新列表");return url;
    }
    /** Resolve a displayed article against the current cache, never a URL supplied by the WebView. */
    static String savedArticleLink(JSONObject state,JSONObject request)throws Exception{
        long seq=integer(request,"seq");String articleId=text(request,"articleId",180);
        if(state==null||seq==0||articleId.isEmpty()||!articleId.equals(articleId.trim())||articleId.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("这条新闻已不存在，请更新列表");
        JSONObject owner=null;JSONArray messages=state.optJSONArray("messages");
        if(messages!=null)for(int i=0;i<messages.length();i++){
            JSONObject message=messages.optJSONObject(i);
            if(message!=null&&message.optLong("seq")==seq){
                if(owner!=null)throw new IllegalArgumentException("新闻缓存记录不明确，请更新列表");owner=message;
            }
        }
        JSONObject article=null;JSONArray articles=owner==null?null:owner.optJSONArray("articles");
        if(articles!=null)for(int i=0;i<articles.length();i++){
            JSONObject candidate=articles.optJSONObject(i);
            if(candidate!=null&&articleId.equals(candidate.opt("id"))){
                if(article!=null)throw new IllegalArgumentException("新闻缓存记录不明确，请更新列表");article=candidate;
            }
        }
        String url=article==null?"":article.optString("url");
        if(!articleUrl(url)&&!vpnArticleUrl(url))throw new IllegalArgumentException("这条新闻已不存在或原文链接无效，请更新列表");
        URI parsed=new URI(url);
        if(parsed.getQuery()!=null||parsed.getFragment()!=null)throw new IllegalArgumentException("原文链接包含临时参数，暂不复制，请重新获取新闻");
        return url;
    }
    private static String text(JSONObject o,String key,int max)throws Exception{Object v=o.opt(key);if(!(v instanceof String)||((String)v).length()>max)throw new Exception("Invalid news field");return (String)v;}
    private static long integer(JSONObject o,String key)throws Exception{Object n=o.get(key);if(!(n instanceof Number))throw new Exception();long value=((Number)n).longValue();if(value<0||value>9_007_199_254_740_991L||((Number)n).doubleValue()!=value)throw new Exception();return value;}
    private static String day(JSONObject o,String key)throws Exception{String value=text(o,key,10);if(!LocalDate.parse(value).toString().equals(value))throw new Exception();return value;}
    static JSONObject curation(JSONObject raw)throws Exception{
        String id=text(raw,"id",32),status=text(raw,"status",12),error=text(raw,"error",40);
        if(!id.matches("[a-f0-9]{32}")||!Arrays.asList("queued","running","succeeded","failed").contains(status)||!Arrays.asList("","interrupted","curation_failed").contains(error))throw new Exception("Invalid curation status");
        JSONObject clean=new JSONObject().put("id",id).put("status",status).put("error",error);
        for(String key:new String[]{"createdAt","startedAt","finishedAt","newsSeq"})clean.put(key,integer(raw,key));
        if("succeeded".equals(status)&&clean.getLong("newsSeq")==0)throw new Exception("Missing curation publication");
        return clean;
    }
    private static JSONObject message(JSONObject raw)throws Exception{
        long seq=integer(raw,"seq");if(seq==0)throw new Exception();String date=day(raw,"day");
        if(!date.equals(text(raw,"id",10)))throw new Exception();
        JSONObject m=new JSONObject().put("seq",seq).put("id",date).put("day",date).put("title",text(raw,"title",300)).put("createdAt",integer(raw,"createdAt")).put("manual",raw.optBoolean("manual"));
        JSONObject context=raw.optJSONObject("context");if(context==null)context=new JSONObject();
        m.put("context",new JSONObject().put("label",context.has("label")?text(context,"label",200):"").put("phase",context.has("phase")?text(context,"phase",400):""));
        JSONArray articles=raw.getJSONArray("articles");if(articles.length()>15)throw new Exception();JSONArray clean=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<articles.length();i++){JSONObject a=articles.getJSONObject(i);String id=text(a,"id",100),url=text(a,"url",1600);if(id.isEmpty()||!ids.add(id)||!articleUrl(url))throw new Exception();
            clean.put(new JSONObject().put("id",id).put("url",url).put("title",text(a,"title",500)).put("published",day(a,"published")).put("source",text(a,"source",80)).put("summary",text(a,"summary",1200)).put("campus",text(a,"campus",40)).put("note",text(a,"note",600)));
        }
        JSONArray warnings=raw.getJSONArray("warnings"),safeWarnings=new JSONArray();if(warnings.length()>8)throw new Exception();for(int i=0;i<warnings.length();i++){Object value=warnings.get(i);if(!(value instanceof String)||((String)value).length()>600)throw new Exception();safeWarnings.put(value);}
        return m.put("articles",clean).put("warnings",safeWarnings).put("read",false);
    }
    static JSONObject merge(JSONObject previous,JSONObject response)throws Exception{
        if(response.getInt("version")!=1)throw new Exception("News API version");String feed=text(response,"feedId",32);if(!feed.matches("[a-f0-9]{32}"))throw new Exception();
        boolean reset=response.optBoolean("reset")||!feed.equals(previous.optString("feedId"));
        JSONObject next=new JSONObject(previous.toString());long cursor=reset?0:previous.optLong("cursor");
        Map<Long,JSONObject> all=new TreeMap<>(Comparator.reverseOrder());
        if(!reset){JSONArray cached=previous.optJSONArray("messages");if(cached!=null)for(int i=0;i<cached.length();i++){JSONObject old=cached.getJSONObject(i);all.put(old.getLong("seq"),old);}}
        JSONArray items=response.getJSONArray("items");if(items.length()>30)throw new Exception();long last=cursor;
        for(int i=0;i<items.length();i++){JSONObject clean=message(items.getJSONObject(i));long seq=clean.getLong("seq");if(seq<=last)throw new Exception("Non-monotonic news cursor");last=seq;all.put(seq,clean);}
        long received=integer(response,"nextCursor");boolean more=response.getBoolean("hasMore");
        if(received!=last||(more&&items.length()==0))throw new Exception("News cursor mismatch");
        JSONArray messages=new JSONArray();int bytes=0;
        for(JSONObject m:all.values()){int size=m.toString().getBytes(StandardCharsets.UTF_8).length;if(messages.length()>=90||bytes+size>1_100_000)break;messages.put(m);bytes+=size;}
        next.put("feedId",feed).put("cursor",received).put("messages",messages).put("hasMore",more);
        return next;
    }
    static int unread(JSONObject state){JSONArray messages=state.optJSONArray("messages");int n=0;if(messages!=null)for(int i=0;i<messages.length();i++)if(!messages.optJSONObject(i).optBoolean("read"))n++;return n;}
    static void markRead(JSONObject state,long seq)throws Exception{JSONArray messages=state.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);if(seq==0||m.optLong("seq")==seq)m.put("read",true);}}
}
