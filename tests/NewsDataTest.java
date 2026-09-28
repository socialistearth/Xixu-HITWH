package edu.hitwh.fieldnote;
import org.json.*;
import java.nio.file.*;

/** Uses a response captured from the real local Python HTTP server. */
public class NewsDataTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("News check " + checks);}
    interface Action{void run()throws Exception;}
    static void rejected(Action action)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed);}
    public static void main(String[] args)throws Exception{
        JSONObject feed=new JSONObject(Files.readString(Path.of(args[0])));
        JSONObject state=NewsData.merge(new JSONObject(),feed);
        check(state.getJSONArray("messages").length()==1);
        check(state.getJSONArray("messages").getJSONObject(0).getJSONArray("articles").getJSONObject(0).getString("summary").contains("合成新闻"));
        check(NewsData.unread(state)==1);
        NewsData.markRead(state,state.getLong("cursor"));check(NewsData.unread(state)==0);
        final JSONObject baseline=state;
        JSONObject empty=new JSONObject(feed.toString()).put("items",new JSONArray());
        JSONObject stable=NewsData.merge(state,empty);check(NewsData.unread(stable)==0);
        check(stable.getLong("cursor")==state.getLong("cursor"));
        rejected(()->NewsData.merge(baseline,feed));
        JSONObject mismatch=new JSONObject(feed.toString()).put("nextCursor",99);
        rejected(()->NewsData.merge(new JSONObject(),mismatch));
        JSONObject evil=new JSONObject(feed.toString());
        evil.getJSONArray("items").getJSONObject(0).getJSONArray("articles").getJSONObject(0).put("url","https://today.hitwh.edu.cn.evil.example/article");
        rejected(()->NewsData.merge(new JSONObject(),evil));
        JSONObject changed=new JSONObject(feed.toString()).put("feedId","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        check(NewsData.unread(NewsData.merge(state,changed))==1);
        JSONObject reset=new JSONObject(feed.toString()).put("reset",true);
        check(NewsData.unread(NewsData.merge(state,reset))==1);
        check(NewsData.baseUrl(" https://news.example.com/xixu/ ").equals("https://news.example.com/xixu"));
        for(String invalid:new String[]{"http://news.example.com","https://key@news.example.com","https://news.example.com?token=x","https://news.example.com/../secret","https://news.example.com/#x"})rejected(()->NewsData.baseUrl(invalid));
        String vpn="https://webvpn2.hitwh.edu.cn/http/77726476706e69737468656265737421abcdefabcdefabcdefabcdef/2026/0921/c1a123/page.htm";
        check(NewsData.vpnArticleUrl(vpn));
        for(String invalid:new String[]{vpn.replace("https://","http://"),vpn.replace("webvpn2.hitwh.edu.cn","webvpn2.hitwh.edu.cn.evil.example"),vpn+"?ticket=private",vpn+"#fragment",vpn.replace("/2026/0921/c1a123/page.htm","/authserver/login"),"javascript:alert(1)"})check(!NewsData.vpnArticleUrl(invalid));
        check(!NewsData.articleUrl("javascript:alert(1)"));check(!NewsData.articleUrl("https://today.hitwh.edu.cn@evil.example/"));
        String manualUrl="https://news.hit.edu.cn/2026/0921/c1a456/page.htm";
        check(NewsData.unresolvedArticleUrl(manualUrl));
        check(NewsData.unresolvedArticleUrl(manualUrl.replace("https:","http:")));
        for(String invalid:new String[]{manualUrl+"?ticket=private",manualUrl+"#fragment",manualUrl.replace("news.hit.edu.cn","news.hit.edu.cn.evil.example"),manualUrl.replace("news.hit.edu.cn","user:pass@news.hit.edu.cn"),manualUrl.replace("news.hit.edu.cn","news.hit.edu.cn:8443"),"javascript:alert(1)","https://news.hit.edu.cn/authserver/login"})check(!NewsData.unresolvedArticleUrl(invalid));
        JSONObject manualLink=new JSONObject().put("id","hitwh-home:a456").put("url",manualUrl).put("title","待人工查看").put("published","2026-09-21");
        JSONArray reviewLinks=NewsData.cleanUnresolved(new JSONArray().put(manualLink).put(manualLink).put(new JSONObject(manualLink.toString()).put("id","bad").put("url",manualUrl+"?ticket=private")));
        check(reviewLinks.length()==1);
        JSONObject linkState=new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("seq",9).put("unresolvedLinks",reviewLinks))).put("failedReview",new JSONObject().put("unresolvedLinks",reviewLinks));
        JSONObject linkRequest=new JSONObject().put("seq",9).put("index",0).put("linkId","hitwh-home:a456");
        check(NewsData.unresolvedLink(linkState,linkRequest).equals(manualUrl));
        check(NewsData.unresolvedLink(linkState,new JSONObject(linkRequest.toString()).put("seq",0)).equals(manualUrl));
        rejected(()->NewsData.unresolvedLink(linkState,new JSONObject(linkRequest.toString()).put("seq",8)));
        rejected(()->NewsData.unresolvedLink(linkState,new JSONObject(linkRequest.toString()).put("index",1)));
        rejected(()->NewsData.unresolvedLink(linkState,new JSONObject(linkRequest.toString()).put("linkId","changed")));
        rejected(()->NewsData.unresolvedLink(linkState,new JSONObject(linkRequest.toString()).put("index",-1)));
        String publicArticle="https://news.hit.edu.cn/2026/0927/c1510a243604/page.htm";
        check(NewsData.articleUrl(publicArticle));check(NewsData.articleUrl(publicArticle.replace("news.hit.edu.cn","news.hitwh.edu.cn")));
        check(NewsData.articleUrl(publicArticle.replace("page.htm","page.psp")));
        for(String invalid:new String[]{publicArticle+"?ticket=private",publicArticle+"#fragment",publicArticle.replace("https:","http:"),publicArticle.replace("news.hit.edu.cn","news.hit.edu.cn.evil.example"),publicArticle.replace("news.hit.edu.cn","news.hit.edu.cn:8443"),publicArticle.replace("/2026/0927/c1510a243604/page.htm","/authserver/login"),publicArticle.replace("news.hit.edu.cn","x.news.hit.edu.cn")})check(!NewsData.articleUrl(invalid));
        JSONObject articleRequest=new JSONObject().put("seq",15).put("articleId","copy-me");
        JSONObject selectedState=new JSONObject().put("messages",new JSONArray()
            .put(new JSONObject().put("seq",15).put("articles",new JSONArray().put(new JSONObject().put("id","copy-me").put("url",vpn)).put(new JSONObject().put("id","other").put("url",publicArticle))))
            .put(new JSONObject().put("seq",14).put("articles",new JSONArray().put(new JSONObject().put("id","copy-me").put("url",publicArticle)))));
        String untouched=selectedState.toString();
        check(NewsData.savedArticleLink(selectedState,articleRequest).equals(vpn));
        check(NewsData.savedArticleLink(selectedState,new JSONObject(articleRequest.toString()).put("seq",14)).equals(publicArticle));
        check(selectedState.toString().equals(untouched));
        check(NewsData.savedArticleLink(selectedState,new JSONObject(articleRequest.toString()).put("url","https://evil.example/stolen")).equals(vpn));
        for(Object bad:new Object[]{0,-1,1.5,"15",true,JSONObject.NULL})rejected(()->NewsData.savedArticleLink(selectedState,new JSONObject(articleRequest.toString()).put("seq",bad)));
        for(Object bad:new Object[]{"",123,"other-missing","a\nb\nc",JSONObject.NULL})rejected(()->NewsData.savedArticleLink(selectedState,new JSONObject(articleRequest.toString()).put("articleId",bad)));
        rejected(()->NewsData.savedArticleLink(null,articleRequest));
        JSONObject removed=new JSONObject(untouched);removed.getJSONArray("messages").getJSONObject(0).getJSONArray("articles").remove(0);
        rejected(()->NewsData.savedArticleLink(removed,articleRequest));
        JSONObject duplicate=new JSONObject(untouched);duplicate.getJSONArray("messages").getJSONObject(0).getJSONArray("articles").put(new JSONObject().put("id","copy-me").put("url",vpn));
        rejected(()->NewsData.savedArticleLink(duplicate,articleRequest));
        JSONObject duplicateDigest=new JSONObject(untouched);duplicateDigest.getJSONArray("messages").put(duplicateDigest.getJSONArray("messages").getJSONObject(0));
        rejected(()->NewsData.savedArticleLink(duplicateDigest,articleRequest));
        for(String invalid:new String[]{"javascript:alert(1)",vpn+"?ticket=private",publicArticle.replace("news.hit.edu.cn","evil.example"),"https://www.hitwh.edu.cn/2026/0927/c1a2/page.htm?ticket=private"}){
            JSONObject invalidState=new JSONObject(untouched);invalidState.getJSONArray("messages").getJSONObject(0).getJSONArray("articles").getJSONObject(0).put("url",invalid);
            rejected(()->NewsData.savedArticleLink(invalidState,articleRequest));
        }
        JSONArray legacyAndNew=NewsData.cleanUnresolved(new JSONArray().put(manualLink).put(new JSONObject(manualLink.toString()).put("id","hit-news:a456")));
        check(legacyAndNew.length()==2);
        JSONObject migratedReview=new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("seq",16).put("unresolvedLinks",legacyAndNew)));
        check(NewsData.unresolvedLink(migratedReview,new JSONObject().put("seq",16).put("index",0).put("linkId","hitwh-home:a456")).equals(manualUrl));
        check(NewsData.unresolvedLink(migratedReview,new JSONObject().put("seq",16).put("index",1).put("linkId","hit-news:a456")).equals(manualUrl));
        JSONObject distinctSites=new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("seq",17).put("articles",new JSONArray()
            .put(new JSONObject().put("id","hit-news:a123").put("url",publicArticle))
            .put(new JSONObject().put("id","hitwh-home:a123").put("url",vpn)))));
        check(NewsData.savedArticleLink(distinctSites,new JSONObject().put("seq",17).put("articleId","hit-news:a123")).equals(publicArticle));
        check(NewsData.savedArticleLink(distinctSites,new JSONObject().put("seq",17).put("articleId","hitwh-home:a123")).equals(vpn));
        JSONObject badDate=new JSONObject(feed.toString());badDate.getJSONArray("items").getJSONObject(0).put("day","2026-02-30");
        rejected(()->NewsData.merge(new JSONObject(),badDate));
        JSONObject malformed=new JSONObject(empty.toString()).put("hasMore",true);
        rejected(()->NewsData.merge(baseline,malformed));
        for(int offset=1;offset<=100;offset++){
            JSONObject page=new JSONObject(feed.toString());JSONObject item=page.getJSONArray("items").getJSONObject(0);
            item.put("seq",offset+1);page.put("nextCursor",offset+1);
            state=NewsData.merge(state,page);
        }
        check(state.getJSONArray("messages").length()==90);check(state.getLong("cursor")==101);
        NewsData.markRead(state,0);check(NewsData.unread(state)==0);
        check(NewsData.baseUrl("https://203.0.113.10").equals("https://203.0.113.10"));
        check(NewsData.baseUrl("https://[2001:db8::1]").equals("https://[2001:db8::1]"));
        if(args.length>1){
            JSONObject manual=new JSONObject(Files.readString(Path.of(args[1]))),manualFeed=manual.getJSONObject("feed");
            JSONObject first=NewsData.merge(new JSONObject(),manualFeed),job=NewsData.curation(manual.getJSONObject("job"));
            check(first.getJSONArray("messages").getJSONObject(0).getBoolean("manual"));
            check(job.getString("status").equals("succeeded"));check(job.getLong("newsSeq")==first.getLong("cursor"));
            JSONObject second=new JSONObject(manualFeed.toString());long newer=first.getLong("cursor")+1;
            second.getJSONArray("items").getJSONObject(0).put("seq",newer);second.put("nextCursor",newer);
            JSONObject combined=NewsData.merge(first,second);
            check(combined.getJSONArray("messages").length()==2);
            check(combined.getJSONArray("messages").getJSONObject(0).getString("day").equals(combined.getJSONArray("messages").getJSONObject(1).getString("day")));
            rejected(()->NewsData.curation(new JSONObject(job.toString()).put("newsSeq",0)));
            rejected(()->NewsData.curation(new JSONObject(job.toString()).put("status","<script>")));
            rejected(()->NewsData.curation(new JSONObject(job.toString()).put("createdAt",1.5)));
        }
        System.out.println("NewsData HTTP contract: " + checks + " checks passed");
    }
}
