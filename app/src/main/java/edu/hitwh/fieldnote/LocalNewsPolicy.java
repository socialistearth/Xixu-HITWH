package edu.hitwh.fieldnote;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.json.JSONException;
import org.json.JSONObject;

/** Offline, deterministic policy shared by scheduled and foreground news collection. */
public final class LocalNewsPolicy {
    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private LocalNewsPolicy() {}

    /** First daily target strictly after now, including the Beijing date boundary. */
    public static long nextDaily(long now, String pushTime) {
        if (pushTime == null || !pushTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))
            throw new IllegalArgumentException("每日获取时间应为 HH:mm，例如 19:00");
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(BEIJING);
        ZonedDateTime next = current.toLocalDate().atTime(LocalTime.parse(pushTime)).atZone(BEIJING);
        if (!next.toInstant().isAfter(current.toInstant())) next = next.plusDays(1);
        return next.toInstant().toEpochMilli();
    }

    public static JSONObject studentContext(long now) {
        LocalDate day = Instant.ofEpochMilli(now).atZone(BEIJING).toLocalDate();
        int month = day.getMonthValue(), schoolYear = day.getYear() - (month < 9 ? 1 : 0);
        int grade = schoolYear - 2026 + 1;
        String term = month >= 9 || month <= 2 ? "秋季学期" : month <= 6 ? "春季学期" : "夏季学期/暑期";
        String[] phases = {"", "秋季期末及寒假衔接阶段", "寒假及春季开学准备阶段", "春季开学阶段",
                "春季学习阶段", "春季学习及暑期准备阶段", "春季期末及夏季学期准备阶段",
                "夏季学期及暑期实践阶段", "暑期及下学年准备阶段", "秋季开学阶段", "秋季学习阶段",
                "秋季学习阶段", "秋季期末准备阶段"};
        String[] seasons = {"", "考试、成绩复核、离返校手续及寒假校园服务", "返校、补缓考、选课和春季学期准备",
                "注册、课程调整、补缓考及本年级项目报名", "竞赛、创新项目、讲座和本科生交流机会",
                "学习支持、竞赛和暑期项目的提前报名", "考试、评教、夏季选课、实践和交流项目报名",
                "夏季课程、社会实践、暑期校园服务和符合年级的实习", "实践材料、返校、新学年选课和提前开放的项目",
                "注册、缴费、选课、课程调整及本年级开学事务", "学习支持、竞赛、讲座、创新项目和学生组织活动",
                "学习支持、竞赛、讲座、创新项目和学生组织活动", "考试安排、课程评教、学分核对和假期安排"};
        String[] priorities = {"", "新生手续、英语分级考试、军训安排、学业适应、基础课程、助学申请、社团招新、入门竞赛和科创启蒙；春夏季关注跨专业/辅修的介绍及符合条件的招生",
                "专业学习、跨专业/辅修、学科竞赛、科创立项、本科科研、交流交换、奖助学金和早期实践",
                "本科科研、实习、竞赛成果、交流项目、升学及就业准备、夏令营/推免的提前准备；仍需核对届别和申请条件",
                "本届推免/考研、秋招春招、毕业设计、毕业资格、学分核对、学位申请、就业与离校手续"};
        String phase = phases[month], seasonal = seasons[month], gradeLabel, focus;
        if (day.isBefore(LocalDate.of(2026, 9, 1))) {
            grade = 0; schoolYear = 2026; term = "入学前"; gradeLabel = "准大一";
            phase = "新生入学准备阶段"; seasonal = "报到材料、缴费资助、住宿、入学安排和新生准备"; focus = seasonal;
        } else if (grade > 4) {
            gradeLabel = "标准学制期满（学籍待确认）";
            focus = "毕业后手续或明确适用的机会；不假定继续在校或已成为研究生";
        } else {
            gradeLabel = new String[]{"", "大一", "大二", "大三", "大四"}[grade]; focus = priorities[grade];
        }
        try {
            return new JSONObject().put("campus", "威海").put("degree", "本科").put("enrollment_date", "2026-09-01")
                    .put("admission_year", 2026).put("cohort", "2026级").put("assumed_study_years", 4)
                    .put("expected_graduation_year", 2030).put("graduation_note", "毕业年份按标准学制推算，不代表已确认实际毕业或当前学籍")
                    .put("academic_year", schoolYear + "-" + (schoolYear + 1)).put("grade", grade).put("grade_label", gradeLabel)
                    .put("term", term).put("phase", phase).put("label", "2026级本科生 · " + gradeLabel + " · " + term)
                    .put("grade_priorities", focus).put("seasonal_priorities", seasonal)
                    .put("calendar_basis", "按北京时间月份估算：9月至次年2月秋季（含寒假），3—6月春季，7—8月夏季/暑期；实际校历及个人教学安排以通知为准");
        } catch (JSONException impossible) { throw new IllegalStateException("无法生成学生画像"); }
    }

    public static void validateConfig(JSONObject config) {
        if (config == null) throw new IllegalArgumentException("请先配置新闻精选模型");
        endpoint(config.optString("baseUrl", ""));
        String model = config.optString("model", "").trim(), key = config.optString("apiKey", "").trim();
        if (model.isEmpty() || model.length() > 200 || hasControls(model))
            throw new IllegalArgumentException("请填写有效的模型名称");
        if (key.isEmpty() || key.length() > 4096 || hasControls(key))
            throw new IllegalArgumentException("请填写有效的模型 API Key");
        if (config.optString("profile", "").length() > 2000)
            throw new IllegalArgumentException("补充偏好不能超过 2000 字");
    }

    static URI endpoint(String baseUrl) {
        try {
            String base = baseUrl.trim();
            if (base.isEmpty() || base.length() > 2048 || hasControls(base)) throw new IllegalArgumentException();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            URI uri = new URI(base);
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                    || path == null || path.contains("\\") || path.matches(".*(?:^|/)\\.\\.?(?:/|$).*")
                    || !uri.normalize().equals(uri)) throw new IllegalArgumentException();
            return new URI(base.endsWith("/chat/completions") ? base : base + "/chat/completions");
        } catch (Exception invalid) {
            throw new IllegalArgumentException("模型地址须为有效的 HTTPS 接口地址，不包含账号、查询参数或片段");
        }
    }

    private static boolean hasControls(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }
}
