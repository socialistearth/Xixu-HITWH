package edu.hitwh.fieldnote;
import org.json.JSONObject;
public final class AcademicSyncAccessTest {
 private static int count;
 private static void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
 private static JSONObject state()throws Exception{
  String term="2026秋季";
  return new JSONObject().put("studentId","synthetic-student").put("password","synthetic-only")
   .put("settings",new JSONObject().put("termName",term))
   .put("syncTargets",new JSONObject().put("courses|"+term,SchoolPages.target("courses",term)));
 }
 public static void main(String[] args)throws Exception{
  check(AcademicSyncAccess.requiresLogin(state()),"configured saved account authenticates before a deep query");
  check(!AcademicSyncAccess.requiresLogin(null),"absent state");
  for(String missing:new String[]{"studentId","password","settings","syncTargets"}){JSONObject s=state();s.remove(missing);check(!AcademicSyncAccess.requiresLogin(s),"missing "+missing);}
  JSONObject pending=state().put("pendingImport",new JSONObject());check(!AcademicSyncAccess.requiresLogin(pending),"pending import stays untouched");
  for(String flag:new String[]{"enabled","verifiedSchool"}){JSONObject s=state();s.getJSONObject("syncTargets").getJSONObject("courses|2026秋季").put(flag,false);check(!AcademicSyncAccess.requiresLogin(s),"disabled "+flag);}
  for(String url:new String[]{SchoolPages.BASE+"/kbcx/queryGrkb?ticket=synthetic",SchoolPages.BASE+"/different", "https://example.invalid/kbcx/queryGrkb"}){JSONObject s=state();s.getJSONObject("syncTargets").getJSONObject("courses|2026秋季").put("url",url);check(!AcademicSyncAccess.requiresLogin(s),"noncanonical target rejected");}
  JSONObject mismatch=state();mismatch.getJSONObject("settings").put("termName","2027春季");check(!AcademicSyncAccess.requiresLogin(mismatch),"other semester needs its own configured target");
  for(String url:new String[]{"https://webvpn2.hitwh.edu.cn/login","https://webvpn2.hitwh.edu.cn/login/?cas_login=true","https://webvpn2.hitwh.edu.cn/http/abc/cas/login","https://webvpn2.hitwh.edu.cn/https/abc/authserver/login?service=synthetic","https://ids.hit.edu.cn/authserver/login"})check(AcademicSyncAccess.loginPage(url),"recognize auth route "+url);
  for(String url:new String[]{SchoolPages.BASE+"/kbcx/queryGrkb","https://webvpn2.hitwh.edu.cn/","https://webvpn2.hitwh.edu.cn/login-history","https://webvpn2.hitwh.edu.cn.attacker.invalid/login","https://attacker.invalid/authserver/login","http://webvpn2.hitwh.edu.cn/login","https://synthetic@webvpn2.hitwh.edu.cn/login","https://webvpn2.hitwh.edu.cn:444/login"})check(!AcademicSyncAccess.loginPage(url),"reject nonlogin or unsafe URL");
  for(int code:new int[]{401,403})check(AcademicSyncAccess.needsLogin(code,false),"HTTP "+code+" enters one bounded authentication handoff");
  for(int code:new int[]{400,404,408,429,500,502,503})check(!AcademicSyncAccess.needsLogin(code,false),"HTTP "+code+" is not blindly retried or mislabeled login");
  check(AcademicSyncAccess.needsLogin(500,true),"explicit expired login page can hand off authentication");
  check(AcademicSyncAccess.needsLogin(200,true),"login document may use HTTP 200");
  check(AcademicSyncAccess.httpMessage(503,"courses").contains("课表查询页面返回 HTTP 503"),"safe diagnosable query and status");
  check(!AcademicSyncAccess.httpMessage(503,"grades").contains("http://"),"no response URL or credentials in message");
  System.out.println("Academic access: "+count+" offline checks passed");
 }
}
