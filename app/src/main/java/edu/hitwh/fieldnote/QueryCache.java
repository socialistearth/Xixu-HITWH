package edu.hitwh.fieldnote;

import org.json.*;
import java.util.*;

/** A local, atomic edit; never deletes login details or registered reminders. */
final class QueryCache {
    static void remove(JSONObject data,String kind,String term,String id,long now)throws Exception{
        if(!Arrays.asList("courses","exams","grades").contains(kind)||term==null||term.isEmpty()||term.length()>60||id==null||id.isEmpty()||id.length()>150)throw new IllegalArgumentException("要删除的缓存记录无效");
        JSONArray before=data.optJSONArray(kind),kept=new JSONArray();JSONObject removed=null;int count=0;
        if(before!=null)for(int i=0;i<before.length();i++){
            JSONObject item=before.getJSONObject(i);
            if(term.equals(item.optString("term"))&&id.equals(item.optString("id"))){removed=item;count++;}else kept.put(item);
        }
        if(count!=1)throw new IllegalArgumentException("这条记录已变化或不存在，请重新打开详情");
        data.put(kind,kept);
        JSONObject meta=data.optJSONObject("importMeta");
        JSONObject info=meta==null?null:meta.optJSONObject(kind+"|"+term);
        if(info!=null){info.put("partial",true);if("grades".equals(kind))info.remove("officialGpa");}
        if("courses".equals(kind)){
            boolean sameName=false;String name=removed.optString("name");
            for(int i=0;i<kept.length();i++){JSONObject item=kept.getJSONObject(i);if(term.equals(item.optString("term"))&&name.equals(item.optString("name")))sameName=true;}
            JSONObject colors=data.optJSONObject("colors");if(!sameName&&colors!=null)colors.remove(term+"|"+name);
        }
        JSONObject pending=data.optJSONObject("pendingImport"),settings=data.optJSONObject("settings");
        String pendingTerm=pending==null?"":pending.optString("term",settings==null?"":settings.optString("termName"));
        if(pending!=null&&kind.equals(pending.optString("kind"))&&term.equals(pendingTerm))data.remove("pendingImport");
        data.put("syncRevision",data.optInt("syncRevision")+1);
        data.put("syncReport",new JSONObject().put("at",now).put("message","已删除「"+removed.optString("name")+"」这一条缓存；后续同步可重新获取"));
    }

    static int clear(JSONObject data,String kind,String scope,String term,long now)throws Exception{
        if(!Arrays.asList("courses","exams").contains(kind)||!Arrays.asList("current","all").contains(scope))throw new IllegalArgumentException("请选择课表或考试，以及要删除的学期范围");
        boolean all="all".equals(scope);
        if(!all&&(term==null||term.trim().isEmpty()||term.length()>60))throw new IllegalArgumentException("当前学期无效，请先设置学期");
        JSONArray before=data.optJSONArray(kind),kept=new JSONArray();int count=0;
        if(before!=null)for(int i=0;i<before.length();i++){
            JSONObject item=before.getJSONObject(i);
            if(all||term.equals(item.optString("term")))count++;else kept.put(item);
        }
        data.put(kind,kept);
        JSONObject meta=data.optJSONObject("importMeta");
        if(meta!=null){List<String> remove=new ArrayList<>();Iterator<String> keys=meta.keys();while(keys.hasNext()){String key=keys.next();if(all?key.startsWith(kind+"|"):key.equals(kind+"|"+term))remove.add(key);}for(String key:remove)meta.remove(key);}
        if("courses".equals(kind)){
            JSONObject colors=data.optJSONObject("colors");
            if(colors!=null){List<String> remove=new ArrayList<>();Iterator<String> keys=colors.keys();while(keys.hasNext()){String key=keys.next();if(all||key.startsWith(term+"|"))remove.add(key);}for(String key:remove)colors.remove(key);}
        }
        JSONObject pending=data.optJSONObject("pendingImport"),settings=data.optJSONObject("settings");
        String pendingTerm=pending==null?"":pending.optString("term",settings==null?"":settings.optString("termName"));
        if(pending!=null&&kind.equals(pending.optString("kind"))&&(all||term.equals(pendingTerm)))data.remove("pendingImport");
        data.put("syncRevision",data.optInt("syncRevision")+1);
        String label="courses".equals(kind)?"课表":"考试";
        data.put("syncReport",new JSONObject().put("at",now).put("message","已清除"+(all?"全部学期":term)+"的"+label+"缓存（"+count+" 条）；下次同步可重新获取"));
        return count;
    }
}
