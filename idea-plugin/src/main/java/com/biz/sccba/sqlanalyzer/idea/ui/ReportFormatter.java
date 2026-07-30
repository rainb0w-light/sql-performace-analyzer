package com.biz.sccba.sqlanalyzer.idea.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Readable projection of the standard report for the Tool Window's combined report tab. */
public final class ReportFormatter {
    private ReportFormatter() {}

    public static String format(String reportJson) {
        try {
            JsonObject root = JsonParser.parseString(reportJson).getAsJsonObject();
            StringBuilder out = new StringBuilder();
            JsonObject subject = object(root, "subject");
            JsonObject summary = object(root, "summary");
            out.append("SQL 性能分析报告\n\n")
                    .append(text(subject, "namespace")).append(".")
                    .append(text(subject, "statementId")).append("\n")
                    .append("严重度：").append(text(summary, "severity")).append("\n")
                    .append("结论：").append(text(summary, "headline")).append("\n")
                    .append("影响范围：").append(text(summary, "impactScope")).append("\n\n");

            out.append("风险\n");
            var risks = root.getAsJsonArray("risks");
            if (risks == null || risks.isEmpty()) out.append("- 未发现结构性风险\n");
            else for (var item : risks) {
                JsonObject risk = item.getAsJsonObject();
                out.append("- [").append(text(risk, "type")).append("] ")
                        .append(text(risk, "title")).append("\n");
            }

            out.append("\n数据画像\n");
            var distributions = root.getAsJsonArray("dataDistribution");
            if (distributions == null || distributions.isEmpty()) out.append("- 无可用画像\n");
            else for (var item : distributions) {
                JsonObject distribution = item.getAsJsonObject();
                out.append("- ").append(text(distribution, "schema")).append(".")
                        .append(text(distribution, "table")).append(".")
                        .append(text(distribution, "column"))
                        .append(" | nullRatio=").append(text(distribution, "nullRatio"))
                        .append(" | distinct≈").append(text(distribution, "approxDistinct"))
                        .append(" | policy=").append(text(distribution, "sensitivityPolicy"))
                        .append("\n");
            }

            out.append("\n执行计划（普通只读 EXPLAIN）\n");
            var plans = root.getAsJsonArray("executionPlans");
            if (plans == null || plans.isEmpty()) out.append("- 无可用执行计划\n");
            else for (var item : plans) {
                JsonObject plan = item.getAsJsonObject();
                out.append("- 场景 ").append(text(plan, "scenarioId"))
                        .append(" | evidence=").append(text(plan, "evidenceId"))
                        .append(" | ").append(abbreviate(
                                plan.has("plan") ? plan.get("plan").toString() : "", 500))
                        .append("\n");
            }

            JsonObject enhancement = object(root, "agentEnhancement");
            out.append("\nAgentScope 增强：").append(text(enhancement, "status")).append("\n");
            if (!text(enhancement, "content").isBlank()) {
                out.append(abbreviate(text(enhancement, "content"), 1_500)).append("\n");
            }
            if (!text(enhancement, "error").isBlank()) {
                out.append("失败原因：").append(text(enhancement, "error")).append("\n");
            }

            out.append("\n证据：")
                    .append(root.has("evidenceCatalog") ? root.getAsJsonArray("evidenceCatalog").size() : 0)
                    .append(" 条\n报告 ID：").append(text(root, "reportId")).append("\n");
            return out.toString();
        } catch (Exception error) {
            return "报告无法解析：\n" + reportJson;
        }
    }

    private static JsonObject object(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonObject()
                ? root.getAsJsonObject(field) : new JsonObject();
    }

    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "";
    }

    private static String abbreviate(String text, int limit) {
        if (text == null || text.length() <= limit) return text == null ? "" : text;
        return text.substring(0, limit) + "…";
    }
}
