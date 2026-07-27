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
}
