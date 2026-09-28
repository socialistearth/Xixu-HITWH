package edu.hitwh.fieldnote;

import org.json.JSONArray;
import org.json.JSONObject;

/** Atomic local display-cache edits. Never clears school/model settings or dedup history. */
final class NewsCache {
    private static final long MAX_INTEGER = 9_007_199_254_740_991L;
    private NewsCache() {}

    static void delete(JSONObject data, JSONObject request, long now) throws Exception {
        if (data == null || request == null || now < 0 || now > MAX_INTEGER)
            throw new IllegalArgumentException("要删除的新闻缓存记录无效");
        Object rawScope = request.opt("scope");
        if (!(rawScope instanceof String)) throw new IllegalArgumentException("请选择要删除的新闻缓存范围");
        String scope = (String) rawScope;
        if (!"article".equals(scope) && !"digest".equals(scope) && !"all".equals(scope))
            throw new IllegalArgumentException("请选择要删除的新闻缓存范围");
        long requestedSeq = "all".equals(scope) ? 0 : integer(request, "seq", true, -1);
        String requestedId = "article".equals(scope) ? articleId(request.opt("articleId")) : "";

        JSONObject original = optionalObject(data, "newsState");
        JSONObject state = original == null ? new JSONObject() : new JSONObject(original.toString());
        JSONArray messages = optionalArray(state, "messages");
        if (messages == null) messages = new JSONArray();
        JSONArray pending = optionalArray(state, "pendingReady");
        long cursor = integer(state, "cursor", false, 0);
        int selectedIndex = -1, matchingMessages = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) throw invalidCache();
            long seq = integer(message, "seq", true, -1);
            cursor = Math.max(cursor, seq); // Preserve the high-water mark even when the newest item goes.
            if (seq == requestedSeq) { selectedIndex = i; matchingMessages++; }
        }
        if (!"all".equals(scope) && matchingMessages != 1)
            throw new IllegalArgumentException("这份精选已变化或不存在，请重新打开新闻列表");
        if ("all".equals(scope) && messages.length() == 0 && (pending == null || pending.length() == 0)
                && !state.has("failedReview")) return;

        long revision = integer(data, "newsRevision", false, 0);
        if (revision == MAX_INTEGER) throw invalidCache();
        JSONObject processed = optionalObject(state, "processed");
        if (processed == null) processed = new JSONObject();
        String stage;
        if ("article".equals(scope)) {
            JSONObject message = messages.getJSONObject(selectedIndex);
            JSONArray articles = optionalArray(message, "articles"), kept = new JSONArray();
            if (articles == null) articles = new JSONArray();
            JSONObject removed = null; int matches = 0;
            for (int i = 0; i < articles.length(); i++) {
                JSONObject article = articles.optJSONObject(i);
                if (article == null) throw invalidCache();
                if (requestedId.equals(articleId(article.opt("id")))) { removed = article; matches++; }
                else kept.put(article);
            }
            if (matches != 1) throw new IllegalArgumentException("这条新闻已变化或不存在，请重新打开详情");
            long deleted = integer(message, "deletedArticles", false, 0);
            if (deleted == MAX_INTEGER) throw invalidCache();
            remember(processed, removed, now);
            message.put("articles", kept).put("deletedArticles", deleted + 1);
            // Keep statistics, manual links, warnings and the digest itself after its final article is removed.
            stage = "已删除这条新闻缓存";
        } else if ("digest".equals(scope)) {
            rememberMessage(processed, messages.getJSONObject(selectedIndex), now);
            JSONArray kept = new JSONArray();
            for (int i = 0; i < messages.length(); i++) if (i != selectedIndex) kept.put(messages.getJSONObject(i));
            messages = kept;
            stage = "已删除这份精选缓存";
        } else {
            for (int i = 0; i < messages.length(); i++) rememberMessage(processed, messages.getJSONObject(i), now);
            messages = new JSONArray();
            state.remove("pendingReady"); state.remove("pendingDay"); state.remove("failedReview");
            state.put("lastSync", 0);
            // Unshown pending entries and unresolved links are deliberately not marked as processed.
            stage = "已清空新闻显示缓存，已处理标记保留";
        }
        state.put("messages", messages).put("processed", processed).put("cursor", cursor)
                .put("curation", new JSONObject().put("status", "idle")).put("stage", stage).put("lastError", "");
        state.remove("curationError");
        // Nothing above mutated data: malformed/ambiguous requests leave the entire vault unchanged.
        data.put("newsState", state).put("newsRevision", revision + 1);
    }

    private static void rememberMessage(JSONObject processed, JSONObject message, long now) throws Exception {
        JSONArray articles = optionalArray(message, "articles");
        if (articles == null) return;
        for (int i = 0; i < articles.length(); i++) {
            JSONObject article = articles.optJSONObject(i);
            if (article == null) throw invalidCache();
            remember(processed, article, now);
        }
    }

    private static void remember(JSONObject processed, JSONObject article, long now) throws Exception {
        String id = articleId(article.opt("id"));
        stamp(processed, id, now);
        if (id.matches("wh:[0-9]+")) stamp(processed, "hitwh-home:a" + id.substring(3), now);
    }

    private static void stamp(JSONObject processed, String id, long now) throws Exception {
        long previous = integer(processed, id, false, 0);
        processed.put(id, Math.max(previous, now));
    }

    private static String articleId(Object raw) {
        if (!(raw instanceof String)) throw new IllegalArgumentException("要删除的新闻编号无效");
        String id = (String) raw;
        if (id.isEmpty() || id.length() > 180 || !id.equals(id.trim()))
            throw new IllegalArgumentException("要删除的新闻编号无效");
        for (int i = 0; i < id.length(); i++) if (Character.isISOControl(id.charAt(i)))
            throw new IllegalArgumentException("要删除的新闻编号无效");
        return id;
    }

    private static JSONObject optionalObject(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return null;
        if (!(value instanceof JSONObject)) throw invalidCache();
        return (JSONObject) value;
    }

    private static JSONArray optionalArray(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return null;
        if (!(value instanceof JSONArray)) throw invalidCache();
        return (JSONArray) value;
    }

    private static long integer(JSONObject object, String key, boolean positive, long fallback) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            if (fallback >= 0) return fallback;
            throw new IllegalArgumentException("要删除的精选编号无效");
        }
        if (!(value instanceof Number)) throw invalidCache();
        Number number = (Number) value; long result = number.longValue();
        if (result < (positive ? 1 : 0) || result > MAX_INTEGER || number.doubleValue() != result) throw invalidCache();
        return result;
    }

    private static IllegalArgumentException invalidCache() {
        return new IllegalArgumentException("新闻缓存记录无效或已变化，请重新打开新闻列表");
    }
}
