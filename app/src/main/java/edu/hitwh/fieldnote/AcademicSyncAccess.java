package edu.hitwh.fieldnote;

import java.net.URI;
import java.util.Locale;
import org.json.JSONObject;

/** Decisions only; performs no requests and never returns credentials or page contents. */
final class AcademicSyncAccess {
 static boolean requiresLogin(JSONObject state){
  if(state==null||state.has("pendingImport")||state.optString("studentId").trim().isEmpty()||state.optString("password").isEmpty())return false;
  JSONObject settings=state.optJSONObject("settings"),targets=state.optJSONObject("syncTargets");
  if(settings==null||targets==null)return false;
  String term=settings.optString("termName");if(SchoolPages.token(term).isEmpty())return false;
  for(String kind:new String[]{"courses","exams","grades"}){
   JSONObject target=targets.optJSONObject(kind+"|"+term);
   if(target!=null&&target.optBoolean("enabled")&&target.optBoolean("verifiedSchool")&&kind.equals(target.optString("kind"))&&term.equals(target.optString("term"))&&(SchoolPages.BASE+SchoolPages.path(kind)).equals(target.optString("url")))return true;
  }
  return false;
 }
 static boolean loginPage(String url){
  try{
   URI u=new URI(url);String host=u.getHost(),path=u.getPath();
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||host==null||path==null)return false;
   host=host.toLowerCase(Locale.ROOT);if(!(host.equals("hitwh.edu.cn")||host.endsWith(".hitwh.edu.cn")||host.equals("hit.edu.cn")||host.endsWith(".hit.edu.cn")))return false;
   path=path.toLowerCase(Locale.ROOT);
   return path.matches(".*/(?:login|cas/login)/?$")||path.contains("/authserver/");
  }catch(Exception ignored){return false;}
 }
 static boolean needsLogin(int httpStatus,boolean loginDocument){return httpStatus==401||httpStatus==403||loginDocument;}
 static String queryName(String kind){return "courses".equals(kind)?"课表":"exams".equals(kind)?"考试":"grades".equals(kind)?"成绩":"教务";}
 static String httpMessage(int httpStatus,String kind){return queryName(kind)+"查询页面返回 HTTP "+httpStatus+"，已保留缓存；本次不重试";}
}
