package edu.hitwh.fieldnote;
import org.json.*;
import java.util.regex.*;
/** Read-only routes observed through the authenticated school menu on 2026-09-17. */
final class SchoolPages {
 static final String BASE="https://webvpn2.hitwh.edu.cn/http/77726476706e69737468656265737421fae0558f693861446900c7a99c406d3667";
 static String path(String kind){return "courses".equals(kind)?"/kbcx/queryGrkb":"exams".equals(kind)?"/kscx/queryKcForXs":"grades".equals(kind)?"/cjcx/queryQmcj":"";}
 static String token(String term){Matcher m=Pattern.compile("(20\\d{2})(?:\\s*[-–—/]\\s*(20\\d{2}))?.*?([春夏秋冬])").matcher(term);return m.find()?(m.group(2)!=null&&!"秋".equals(m.group(3))?m.group(2):m.group(1))+m.group(3)+"季":"";}
 static JSONObject target(String kind,String term)throws Exception{return new JSONObject().put("enabled",true).put("verifiedSchool",true).put("kind",kind).put("term",term).put("termToken",token(term)).put("url",BASE+path(kind)).put("sourceKey","hitwh-v1-"+kind);}
 static void configure(Vault vault)throws Exception{vault.update(o->{JSONObject settings=o.optJSONObject("settings");if(settings==null||token(settings.optString("termName")).isEmpty())return;String term=settings.getString("termName");JSONObject all=o.optJSONObject("syncTargets");if(all==null)all=new JSONObject();for(String k:new String[]{"courses","exams","grades"})all.put(k+"|"+term,target(k,term));o.put("syncTargets",all);});}
}
