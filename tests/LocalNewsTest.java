package edu.hitwh.fieldnote;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** No school/API traffic: exercises the production policy and selector with synthetic model replies. */
public final class LocalNewsTest {
    private static int checks;
    private static final long NOW = Instant.parse("2026-09-21T12:40:00Z").toEpochMilli();
    interface Action { void run() throws Exception; }
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
    static void rejected(Action action) throws Exception {
        try { action.run(); } catch (LocalNews.Failure | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    static JSONObject config() throws Exception {
        return new JSONObject().put("baseUrl", "https://model.example/v1/").put("model", "example-model")
                .put("apiKey", "test-key-only").put("profile", "学生，偏好科创和讲座");
    }
    static JSONArray articles(int count) throws Exception {
        JSONArray articles = new JSONArray();
        for (int i = 0; i < count; i++) articles.put(new JSONObject().put("id", "wh:" + i).put("title", "校园通知" + i)
                .put("url", "https://webvpn2.hitwh.edu.cn/https/school/2026/0921/c1a" + i + "/page.htm")
                .put("source", "校主页").put("published", "2026-09-21").put("body", "面向本科生开展技术交流活动，请留意通知中明确的办理方式。")
                .put("note", "含附件，未读取附件内容"));
        return articles;
    }
    static JSONArray requestArticles(JSONObject payload) throws Exception {
        return new JSONObject(payload.getJSONArray("messages").getJSONObject(1).getString("content")).getJSONArray("articles");
    }
    static JSONObject rows(JSONArray batch) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; i < batch.length(); i++) result.put(new JSONObject().put("id", batch.getJSONObject(i).getString("id"))
                .put("score", 80 + i).put("summary", "本科生可以了解本次技术交流活动，资格和具体办理要求请核对学校原文。")
                .put("campus", "威海"));
        return new JSONObject().put("items", result);
    }
    static String response(JSONObject decision) throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", "stop")
                .put("message", new JSONObject().put("content", decision.toString())))).toString();
    }
    static String valid(JSONObject payload) throws LocalNews.Failure {
        try { return response(rows(requestArticles(payload))); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    static void newsHostsRemainDistinct() throws Exception {
        // The homepage links to both the Weihai news site and the university news site.
        // Keep the discovered origin locally; neither URL is evidence of an article's audience.
        JSONArray input = articles(2);
        input.getJSONObject(0).put("id", "hitwh-home:a220695").put("source", "校区新闻")
                .put("url", "https://news.hitwh.edu.cn/2026/0922/c1040a220695/page.htm");
        input.getJSONObject(1).put("id", "hit-news:a243604").put("source", "工大要闻")
                .put("url", "https://news.hit.edu.cn/2026/0927/c1510a243604/page.htm");
        String before = input.toString();
        JSONObject selected = LocalNews.select(config(), input, NOW, (endpoint, key, payload) -> {
            try {
                JSONArray sent = requestArticles(payload);
                check(sent.length() == 2);
                check(!payload.toString().contains("news.hitwh.edu.cn"));
                check(!payload.toString().contains("news.hit.edu.cn"));
                check(sent.getJSONObject(0).getString("source").equals("校区新闻"));
                check(sent.getJSONObject(1).getString("source").equals("工大要闻"));
                JSONObject scores = rows(sent);
                // Applicability comes from article content/model assessment, not its hosting domain.
                scores.getJSONArray("items").getJSONObject(0).put("campus", "全校");
                scores.getJSONArray("items").getJSONObject(1).put("campus", "威海");
                return response(scores);
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        check(input.toString().equals(before));
        check(selected.getJSONArray("processedIds").length() == 2);
        check(selected.getJSONArray("warnings").length() == 0);
        JSONArray shown = selected.getJSONArray("articles");
        check(shown.length() == 2);
        for (int i = 0; i < shown.length(); i++) {
            JSONObject article = shown.getJSONObject(i);
            int originalIndex = article.getString("id").equals("hitwh-home:a220695") ? 0 : 1;
            check(article.getString("url").equals(input.getJSONObject(originalIndex).getString("url")));
            check(article.getString("source").equals(input.getJSONObject(originalIndex).getString("source")));
            check(article.getString("campus").equals(originalIndex == 0 ? "全校" : "威海"));
        }
    }
    public static void main(String[] args) throws Exception {
        newsHostsRemainDistinct();
        JSONObject first = LocalNewsPolicy.studentContext(NOW);
        check(first.getString("label").equals("2026级本科生 · 大一 · 秋季学期"));
        check(first.getInt("expected_graduation_year") == 2030);
        check(LocalNewsPolicy.studentContext(Instant.parse("2027-08-31T15:59:59Z").toEpochMilli()).getInt("grade") == 1);
        check(LocalNewsPolicy.studentContext(Instant.parse("2027-08-31T16:00:00Z").toEpochMilli()).getInt("grade") == 2);
        check(LocalNewsPolicy.studentContext(Instant.parse("2026-08-31T15:59:59Z").toEpochMilli()).getString("grade_label").equals("准大一"));
        check(LocalNewsPolicy.studentContext(Instant.parse("2030-09-01T00:00:00Z").toEpochMilli()).getString("grade_label").contains("学籍待确认"));
        check(LocalNewsPolicy.studentContext(Instant.parse("2027-03-01T00:00:00Z").toEpochMilli()).getString("term").equals("春季学期"));
        check(LocalNewsPolicy.nextDaily(Instant.parse("2026-09-21T10:59:59Z").toEpochMilli(), "19:00") == Instant.parse("2026-09-21T11:00:00Z").toEpochMilli());
        check(LocalNewsPolicy.nextDaily(Instant.parse("2026-09-21T11:00:00Z").toEpochMilli(), "19:00") == Instant.parse("2026-09-22T11:00:00Z").toEpochMilli());
        check(LocalNewsPolicy.nextDaily(Instant.parse("2026-09-21T15:59:59Z").toEpochMilli(), "00:00") == Instant.parse("2026-09-21T16:00:00Z").toEpochMilli());
        for (String time : new String[]{"24:00", "7:00", "19:60", "abc"}) rejected(() -> LocalNewsPolicy.nextDaily(NOW, time));
        check(LocalNewsPolicy.endpoint("https://model.example/v1/").toString().equals("https://model.example/v1/chat/completions"));
        check(LocalNewsPolicy.endpoint("https://model.example/v1/chat/completions").toString().equals("https://model.example/v1/chat/completions"));
        for (String bad : new String[]{"http://model.example/v1", "https://key@model.example/v1", "https://model.example/v1?token=secret",
                "https://model.example/v1#token", "https://model.example/../v1", "https://model.example/%2e%2e/v1", "https://model.example:0"})
            rejected(() -> LocalNewsPolicy.endpoint(bad));
        rejected(() -> LocalNewsPolicy.validateConfig(config().put("apiKey", "bad\r\nheader")));
        rejected(() -> LocalNewsPolicy.validateConfig(config().put("model", "")));

        AtomicInteger calls = new AtomicInteger();
        LocalNews.Transport good = (endpoint, key, payload) -> {
            calls.incrementAndGet();
            check(endpoint.toString().equals("https://model.example/v1/chat/completions"));
            check(key.equals("test-key-only")); check(!payload.toString().contains(key));
            check(!payload.toString().contains("webvpn2.hitwh.edu.cn"));
            try {
                JSONArray modelArticles = requestArticles(payload);
                check(modelArticles.length() <= 5);
                for (int i = 0; i < modelArticles.length(); i++) check(!modelArticles.getJSONObject(i).has("url"));
            } catch (Exception e) { throw new AssertionError(e); }
            return valid(payload);
        };
        JSONArray input = articles(13); String original = input.toString();
        JSONObject result = LocalNews.select(config(), input, NOW, good);
        check(calls.get() == 3); check(result.getJSONArray("processedIds").length() == 13);
        check(result.getJSONArray("articles").length() == 10); check(result.getJSONArray("warnings").length() == 0);
        check(result.getJSONArray("deferredArticles").length() == 3);
        java.util.Set<String> surfaced = new java.util.HashSet<>();
        for (String field : new String[]{"articles", "deferredArticles"}) {
            JSONArray entries = result.getJSONArray(field);
            for (int i = 0; i < entries.length(); i++) {
                check(surfaced.add(entries.getJSONObject(i).getString("id")));
                check(!entries.getJSONObject(i).has("body"));
            }
        }
        check(surfaced.size() == result.getJSONArray("processedIds").length());
        check(input.toString().equals(original));
        JSONObject entry = result.getJSONArray("articles").getJSONObject(0);
        check(entry.getInt("score") == 84); check(!entry.has("body"));
        check(entry.getString("url").contains("webvpn2.hitwh.edu.cn")); check(entry.getString("note").contains("附件"));
        LocalNews.select(config(), new JSONArray(), NOW, good); check(calls.get() == 3);
        rejected(() -> LocalNews.select(config(), articles(26), NOW, good)); check(calls.get() == 3);
        JSONArray duplicates = articles(2); duplicates.getJSONObject(1).put("id", "wh:0");
        rejected(() -> LocalNews.select(config(), duplicates, NOW, good)); check(calls.get() == 3);
        AtomicInteger partialCalls = new AtomicInteger();
        JSONObject partial = LocalNews.select(config(), articles(13), NOW, (endpoint, key, payload) -> {
            if (partialCalls.incrementAndGet() == 2) throw new LocalNews.Failure("模型接口连接失败或超时");
            return valid(payload);
        });
        check(partialCalls.get() == 2); check(partial.getJSONArray("processedIds").length() == 5);
        check(partial.getJSONArray("warnings").getString(0).contains("8 篇未完成"));
        JSONObject malformed = LocalNews.select(config(), articles(2), NOW, (endpoint, key, payload) -> "not JSON: secret-body");
        check(malformed.getJSONArray("processedIds").length() == 0);
        check(!malformed.getJSONArray("warnings").toString().contains("secret-body"));

        JSONArray batch = articles(2);
        JSONObject missing = rows(batch); missing.getJSONArray("items").remove(1);
        rejected(() -> LocalNews.parseResponse(response(missing), batch));
        JSONObject duplicate = rows(batch); duplicate.getJSONArray("items").getJSONObject(1).put("id", "wh:0");
        rejected(() -> LocalNews.parseResponse(response(duplicate), batch));
        for (Object score : new Object[]{101, -1, "80", 80.5, true}) {
            JSONObject bad = rows(batch); bad.getJSONArray("items").getJSONObject(0).put("score", score);
            rejected(() -> LocalNews.parseResponse(response(bad), batch));
        }
        for (String content : new String[]{"https://malicious.example", "<script>secret</script>", "```json", " "}) {
            JSONObject bad = rows(batch); bad.getJSONArray("items").getJSONObject(0).put("summary", content);
            rejected(() -> LocalNews.parseResponse(response(bad), batch));
        }
        JSONObject unknown = rows(batch); unknown.getJSONArray("items").getJSONObject(0).put("campus", "其他校区");
        rejected(() -> LocalNews.parseResponse(response(unknown), batch));
        JSONObject length = new JSONObject(response(rows(batch))); length.getJSONArray("choices").getJSONObject(0).put("finish_reason", "length");
        rejected(() -> LocalNews.parseResponse(length.toString(), batch));
        JSONObject low = LocalNews.select(config(), articles(2), NOW, (endpoint, key, payload) -> {
            try {
                JSONObject scored = rows(requestArticles(payload));
                scored.getJSONArray("items").getJSONObject(0).put("score", 59);
                scored.getJSONArray("items").getJSONObject(1).put("score", 60);
                return response(scored);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        check(low.getJSONArray("processedIds").length() == 2); check(low.getJSONArray("articles").length() == 1);
        check(low.getJSONArray("articles").getJSONObject(0).getInt("score") == 60);
        JSONArray longBody = articles(1); longBody.getJSONObject(0).put("body", "文".repeat(5499) + "😀" + "文".repeat(500));
        JSONObject limited = LocalNews.select(config(), longBody, NOW, (endpoint, key, payload) -> {
            try {
                JSONObject item = requestArticles(payload).getJSONObject(0);
                check(item.getString("body").length() == 5499);
                check(item.getString("note").contains("5500"));
            } catch (Exception e) { throw new AssertionError(e); }
            return valid(payload);
        });
        check(limited.getJSONArray("articles").length() == 1);
        Thread.currentThread().interrupt();
        try { rejected(() -> LocalNews.select(config(), articles(1), NOW, good)); }
        finally { Thread.interrupted(); }
        System.out.println("LocalNews: " + checks + " offline checks passed");
    }
}
