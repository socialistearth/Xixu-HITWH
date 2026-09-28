package edu.hitwh.fieldnote;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises local cache deletion with synthetic records only; never performs any I/O or network call. */
public final class NewsCacheTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("NewsCache check " + checks); }
    private static JSONObject article(String id) throws Exception {
        return new JSONObject().put("id", id).put("title", "同名新闻").put("summary", "仅用于离线测试")
                .put("url", "https://webvpn2.hitwh.edu.cn/http/observed/2026/0921/c1a1/page.htm");
    }
    private static JSONObject digest(long seq, String... ids) throws Exception {
        JSONArray articles = new JSONArray(); for (String id : ids) articles.put(article(id));
        return new JSONObject().put("seq", seq).put("day", "2026-09-21").put("title", "校园消息")
                .put("articles", articles).put("read", false).put("createdAt", 10)
                .put("stats", new JSONObject().put("selected", ids.length).put("read", 5).put("unresolvedRecent", 1))
                .put("warnings", new JSONArray().put("一条链接尚未识别"))
                .put("unresolvedLinks", new JSONArray().put(new JSONObject().put("id", "unresolved:1").put("title", "待人工读取")));
    }
    private static JSONObject sample() throws Exception {
        return new JSONObject().put("studentId", "synthetic-account").put("password", "synthetic-password")
                .put("courses", new JSONArray().put(new JSONObject().put("id", "course")))
                .put("exams", new JSONArray().put("exam")).put("grades", new JSONArray().put("grade"))
                .put("memos", new JSONArray().put("备忘录")).put("alarms", new JSONArray().put(9))
                .put("settings", new JSONObject().put("termName", "秋季"))
                .put("syncRevision", 42).put("newsRevision", 7)
                .put("newsConfig", new JSONObject().put("model", "test-model").put("apiKey", "synthetic-key").put("autoEnabled", true))
                .put("newsState", new JSONObject().put("cursor", 12).put("lastSync", 123).put("lastAttempt", 120)
                        .put("lastAutomaticDay", "2026-09-21").put("homeUrl", "https://webvpn2.hitwh.edu.cn/")
                        .put("messages", new JSONArray().put(digest(12, "hitwh-home:a1", "wh:2")).put(digest(10, "hitwh-home:a1", "wh:3")))
                        .put("processed", new JSONObject().put("pre-existing", 55))
                        .put("pendingReady", new JSONArray().put(article("pending:9"))).put("pendingDay", "2026-09-21")
                        .put("failedReview", new JSONObject().put("unresolvedLinks", new JSONArray().put(article("unresolved:99"))))
                        .put("curation", new JSONObject().put("status", "completed").put("newsSeq", 12))
                        .put("lastError", "历史错误").put("curationError", "旧版错误"));
    }
    private static JSONObject request(String scope, long seq, String id) throws Exception {
        JSONObject request = new JSONObject().put("scope", scope);
        if (seq != 0) request.put("seq", seq); if (id != null) request.put("articleId", id); return request;
    }
    private static void otherDataPreserved(JSONObject after, JSONObject before) throws Exception {
        Iterator<String> keys = before.keys();
        while (keys.hasNext()) { String key = keys.next(); if (!key.equals("newsState") && !key.equals("newsRevision")) check(after.get(key).toString().equals(before.get(key).toString())); }
    }
    private static void rejectedUnchanged(JSONObject data, JSONObject request) throws Exception {
        String before = data.toString(); boolean rejected = false;
        try { NewsCache.delete(data, request, 200); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected); check(data.toString().equals(before));
    }
    public static void main(String[] args) throws Exception {
        JSONObject one = sample(), original = new JSONObject(one.toString());
        NewsCache.delete(one, request("article", 12, "hitwh-home:a1"), 200);
        JSONObject state = one.getJSONObject("newsState"), message = state.getJSONArray("messages").getJSONObject(0);
        check(message.getJSONArray("articles").length() == 1); check(message.getJSONArray("articles").getJSONObject(0).getString("id").equals("wh:2"));
        check(message.getInt("deletedArticles") == 1); check(message.getJSONObject("stats").getInt("selected") == 2);
        check(message.getJSONArray("warnings").length() == 1); check(message.getJSONArray("unresolvedLinks").length() == 1);
        check(state.getJSONArray("messages").getJSONObject(1).toString().equals(original.getJSONObject("newsState").getJSONArray("messages").getJSONObject(1).toString()));
        check(state.getJSONObject("processed").getLong("hitwh-home:a1") == 200);
        check(state.getJSONObject("processed").getLong("pre-existing") == 55); check(!state.getJSONObject("processed").has("unresolved:1"));
        check(state.getLong("cursor") == 12); check(one.getInt("newsRevision") == 8);
        check(state.getJSONObject("curation").getString("status").equals("idle")); check(!state.getJSONObject("curation").has("newsSeq"));
        check(state.getString("lastError").isEmpty()); check(!state.has("curationError")); check(state.getLong("lastSync") == 123);
        check(state.getJSONArray("pendingReady").length() == 1); check(state.has("failedReview")); otherDataPreserved(one, original);
        NewsCache.delete(one, request("article", 12, "wh:2"), 201);
        state = one.getJSONObject("newsState"); message = state.getJSONArray("messages").getJSONObject(0);
        check(state.getJSONArray("messages").length() == 2); check(message.getJSONArray("articles").length() == 0);
        check(message.getInt("deletedArticles") == 2); check(message.getJSONObject("stats").getInt("selected") == 2);
        check(message.getJSONArray("warnings").length() == 1); check(message.getJSONArray("unresolvedLinks").length() == 1);
        check(state.getJSONObject("processed").getLong("wh:2") == 201); check(state.getJSONObject("processed").getLong("hitwh-home:a2") == 201);
        rejectedUnchanged(one, request("article", 12, "wh:2"));

        JSONObject whole = sample(), wholeBefore = new JSONObject(whole.toString());
        NewsCache.delete(whole, request("digest", 12, null), 300);
        state = whole.getJSONObject("newsState"); check(state.getJSONArray("messages").length() == 1);
        check(state.getJSONArray("messages").getJSONObject(0).getLong("seq") == 10); check(state.getLong("cursor") == 12);
        for (String id : new String[]{"hitwh-home:a1", "wh:2", "hitwh-home:a2"}) check(state.getJSONObject("processed").getLong(id) == 300);
        check(!state.getJSONObject("processed").has("wh:3")); check(state.has("failedReview")); check(state.getJSONArray("pendingReady").length() == 1);
        otherDataPreserved(whole, wholeBefore);
        NewsCache.delete(one, request("digest", 12, null), 301); check(one.getJSONObject("newsState").getJSONArray("messages").length() == 1);

        JSONObject all = sample(), allBefore = new JSONObject(all.toString());
        NewsCache.delete(all, request("all", 0, null), 400);
        state = all.getJSONObject("newsState"); check(state.getJSONArray("messages").length() == 0);
        check(!state.has("pendingReady")); check(!state.has("pendingDay")); check(!state.has("failedReview"));
        check(state.getLong("cursor") == 12); check(state.getLong("lastSync") == 0); check(all.getInt("newsRevision") == 8);
        check(state.getLong("lastAttempt") == 120); check(state.getString("lastAutomaticDay").equals("2026-09-21"));
        for (String id : new String[]{"hitwh-home:a1", "wh:2", "hitwh-home:a2", "wh:3", "hitwh-home:a3"}) check(state.getJSONObject("processed").getLong(id) == 400);
        for (String id : new String[]{"pending:9", "unresolved:1", "unresolved:99"}) check(!state.getJSONObject("processed").has(id));
        check(state.getJSONObject("processed").getLong("pre-existing") == 55); otherDataPreserved(all, allBefore);
        String emptyBefore = all.toString(); NewsCache.delete(all, request("all", 0, null), 401); check(all.toString().equals(emptyBefore));

        JSONObject onlyPending = new JSONObject().put("newsState", new JSONObject().put("cursor", 9).put("pendingReady", new JSONArray().put(article("pending-only"))).put("pendingDay", "2026-09-21"));
        NewsCache.delete(onlyPending, request("all", 0, null), 402); check(onlyPending.getInt("newsRevision") == 1);
        check(onlyPending.getJSONObject("newsState").getJSONObject("processed").length() == 0); check(onlyPending.getJSONObject("newsState").getLong("cursor") == 9);
        JSONObject onlyDiagnostic = new JSONObject().put("newsState", new JSONObject().put("failedReview", new JSONObject().put("unresolvedLinks", new JSONArray().put(article("not-read")))));
        NewsCache.delete(onlyDiagnostic, request("all", 0, null), 403); check(onlyDiagnostic.getInt("newsRevision") == 1);
        check(!onlyDiagnostic.getJSONObject("newsState").has("failedReview")); check(onlyDiagnostic.getJSONObject("newsState").getJSONObject("processed").length() == 0);
        JSONObject fresh = new JSONObject().put("studentId", "keep"); String freshBefore = fresh.toString();
        NewsCache.delete(fresh, request("all", 0, null), 404); check(fresh.toString().equals(freshBefore));

        JSONObject old = sample(); old.getJSONObject("newsState").remove("cursor"); old.getJSONObject("newsState").remove("processed");
        old.getJSONObject("newsState").getJSONArray("messages").getJSONObject(0).remove("stats");
        NewsCache.delete(old, request("digest", 12, null), 500); check(old.getJSONObject("newsState").getLong("cursor") == 12);
        check(old.getJSONObject("newsState").getJSONObject("processed").has("hitwh-home:a2"));
        JSONObject future = sample(); future.getJSONObject("newsState").getJSONObject("processed").put("wh:2", 999);
        NewsCache.delete(future, request("article", 12, "wh:2"), 501); check(future.getJSONObject("newsState").getJSONObject("processed").getLong("wh:2") == 999);

        for (JSONObject invalid : new JSONObject[]{new JSONObject(), new JSONObject().put("scope", true), request("everything", 0, null), request("", 0, null),
                request("article", 0, "wh:2"), request("article", 12, null), request("article", 12, ""), request("article", 12, "wh:missing"), request("digest", 99, null)})
            rejectedUnchanged(sample(), invalid);
        for (Object bad : new Object[]{"12", 0, -1, 1.5, true, JSONObject.NULL, 9_007_199_254_740_992L})
            rejectedUnchanged(sample(), new JSONObject().put("scope", "digest").put("seq", bad));
        for (Object bad : new Object[]{12, true, " bad ", "a\nb", "a".repeat(181)})
            rejectedUnchanged(sample(), new JSONObject().put("scope", "article").put("seq", 12).put("articleId", bad));
        JSONObject duplicateSeq = sample(); duplicateSeq.getJSONObject("newsState").getJSONArray("messages").put(digest(12, "another"));
        rejectedUnchanged(duplicateSeq, request("digest", 12, null));
        JSONObject duplicateId = sample(); duplicateId.getJSONObject("newsState").getJSONArray("messages").getJSONObject(0).getJSONArray("articles").put(article("wh:2"));
        rejectedUnchanged(duplicateId, request("article", 12, "wh:2"));
        JSONObject corrupt = sample(); corrupt.getJSONObject("newsState").getJSONArray("messages").getJSONObject(1).getJSONArray("articles").put(new JSONObject().put("id", 123));
        rejectedUnchanged(corrupt, request("all", 0, null));
        JSONObject overflow = sample().put("newsRevision", 9_007_199_254_740_991L);
        rejectedUnchanged(overflow, request("all", 0, null));
        JSONObject distinctSites = sample();
        distinctSites.getJSONObject("newsState").getJSONArray("messages").getJSONObject(0).getJSONArray("articles").getJSONObject(0)
            .put("id", "hit-news:a1").put("url", "https://news.hit.edu.cn/2026/0921/c1a1/page.htm");
        NewsCache.delete(distinctSites, request("article", 12, "hit-news:a1"), 600);
        JSONObject distinctProcessed = distinctSites.getJSONObject("newsState").getJSONObject("processed");
        check(distinctProcessed.has("hit-news:a1")); check(!distinctProcessed.has("hitwh-home:a1"));
        check(distinctSites.getJSONObject("newsState").getJSONArray("messages").getJSONObject(1).getJSONArray("articles").getJSONObject(0).getString("id").equals("hitwh-home:a1"));
        System.out.println("News cache: " + checks + " offline checks passed");
    }
}
