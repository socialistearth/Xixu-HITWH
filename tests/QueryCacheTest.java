package edu.hitwh.fieldnote;
import org.json.*;

public class QueryCacheTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Cache check "+checks);}
    private static JSONObject record(String term,String name)throws Exception{return new JSONObject().put("term",term).put("name",name);}
    private static JSONObject sample()throws Exception{
        return new JSONObject().put("courses",new JSONArray().put(record("秋季","数学").put("manual",true)).put(record("春季","物理")))
            .put("exams",new JSONArray().put(record("秋季","考试"))).put("grades",new JSONArray().put(record("秋季","成绩")))
            .put("settings",new JSONObject().put("termName","秋季")).put("studentId","synthetic-account").put("password","synthetic-password")
            .put("memos",new JSONArray().put("保留备忘录")).put("alarms",new JSONArray().put(new JSONObject().put("id",1001)))
            .put("newsConfig",new JSONObject().put("baseUrl","https://news.test.invalid")).put("newsState",new JSONObject().put("cursor",5))
            .put("importMeta",new JSONObject().put("courses|秋季",1).put("courses|春季",2).put("exams|秋季",3).put("grades|秋季",4))
            .put("colors",new JSONObject().put("秋季|数学","green").put("春季|物理","blue"))
            .put("syncTargets",new JSONObject().put("courses|秋季",new JSONObject().put("enabled",true)))
            .put("syncPreferences",new JSONObject().put("enabled",true).put("intervalMinutes",60)).put("lastSyncAttempt",12345).put("syncRevision",8)
            .put("pendingImport",new JSONObject().put("kind","courses").put("capturedAt",1));
    }
    public static void main(String[] args)throws Exception{
        JSONObject state=sample(),before=new JSONObject(state.toString());
        check(QueryCache.clear(state,"courses","current","秋季",100)==1);
        check(state.getJSONArray("courses").length()==1);
        check(state.getJSONArray("courses").getJSONObject(0).getString("term").equals("春季"));
        check(!state.getJSONObject("importMeta").has("courses|秋季"));
        check(state.getJSONObject("importMeta").has("courses|春季"));
        check(!state.getJSONObject("colors").has("秋季|数学"));
        check(state.getJSONObject("colors").has("春季|物理"));
        check(!state.has("pendingImport"));check(state.getInt("syncRevision")==9);
        for(String key:new String[]{"exams","grades","settings","studentId","password","memos","alarms","newsConfig","newsState","syncTargets","syncPreferences","lastSyncAttempt"})check(state.get(key).toString().equals(before.get(key).toString()));
        check(QueryCache.clear(state,"courses","all","",101)==1);check(state.getJSONArray("courses").length()==0);
        check(!state.getJSONObject("importMeta").has("courses|春季"));check(state.getJSONObject("colors").length()==0);
        JSONObject exams=sample();String courses=exams.getJSONArray("courses").toString(),colors=exams.getJSONObject("colors").toString();
        check(QueryCache.clear(exams,"exams","all","",102)==1);
        check(exams.getJSONArray("exams").length()==0);check(exams.getJSONArray("courses").toString().equals(courses));check(exams.getJSONObject("colors").toString().equals(colors));check(exams.has("pendingImport"));
        check(exams.getJSONObject("importMeta").has("grades|秋季"));
        JSONObject invalid=sample();String saved=invalid.toString();boolean rejected=false;
        try{QueryCache.clear(invalid,"grades","all","",103);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected);check(invalid.toString().equals(saved));
        rejected=false;try{QueryCache.clear(invalid,"courses","other","秋季",103);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
        JSONObject other=sample();other.getJSONObject("pendingImport").put("term","春季");QueryCache.clear(other,"courses","current","秋季",104);check(other.has("pendingImport"));
        QueryCache.clear(other,"courses","all","",105);check(!other.has("pendingImport"));
        check(QueryCache.clear(other,"courses","all","",106)==0);
        for(String kind:new String[]{"courses","exams","grades"}){
            JSONObject one=sample();
            one.put(kind,new JSONArray().put(record("秋季","同名课程").put("id","chosen"))
                .put(record("秋季","同名课程").put("id","another-time"))
                .put(record("春季","同名课程").put("id","chosen")));
            one.getJSONObject("colors").put("秋季|同名课程","green");
            one.getJSONObject("importMeta").put(kind+"|秋季",new JSONObject().put("officialGpa","3.8").put("at",123));
            JSONObject original=new JSONObject(one.toString());
            QueryCache.remove(one,kind,"秋季","chosen",200);
            check(one.getJSONArray(kind).length()==2);
            check(one.getJSONArray(kind).getJSONObject(0).getString("id").equals("another-time"));
            check(one.getJSONArray(kind).getJSONObject(1).getString("term").equals("春季"));
            check(one.getJSONObject("colors").has("秋季|同名课程"));
            check(one.getJSONObject("importMeta").getJSONObject(kind+"|秋季").getBoolean("partial"));
            if(kind.equals("grades"))check(!one.getJSONObject("importMeta").getJSONObject(kind+"|秋季").has("officialGpa"));
            check(one.getInt("syncRevision")==9);
            for(String key:new String[]{"studentId","password","memos","alarms","newsConfig","newsState","syncTargets","settings","syncPreferences","lastSyncAttempt"})check(one.get(key).toString().equals(original.get(key).toString()));
            for(String key:new String[]{"courses","exams","grades"})if(!key.equals(kind))check(one.get(key).toString().equals(original.get(key).toString()));
            String unchanged=one.toString();rejected=false;
            try{QueryCache.remove(one,kind,"秋季","chosen",201);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected);check(one.toString().equals(unchanged));
            QueryCache.remove(one,kind,"秋季","another-time",202);
            if(kind.equals("courses"))check(!one.getJSONObject("colors").has("秋季|同名课程"));
        }
        JSONObject ambiguous=sample();ambiguous.put("courses",new JSONArray().put(record("秋季","A").put("id","duplicate")).put(record("秋季","B").put("id","duplicate")));
        String ambiguousBefore=ambiguous.toString();rejected=false;
        try{QueryCache.remove(ambiguous,"courses","秋季","duplicate",203);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected);check(ambiguous.toString().equals(ambiguousBefore));
        System.out.println("Query cache: "+checks+" checks passed");
    }
}
