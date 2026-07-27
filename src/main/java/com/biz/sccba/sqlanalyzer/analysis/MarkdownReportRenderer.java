package com.biz.sccba.sqlanalyzer.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Markdown projection of a standard report (docs/contracts/rest-api.md §1: Accept: text/markdown).
 * Rendered from the persisted report JSON, so the projection never diverges from it.
 */
@Component
public class MarkdownReportRenderer {

    private final ObjectMapper mapper;

    public MarkdownReportRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String render(String reportJson) {
        try {
            JsonNode report = mapper.readTree(reportJson);
            StringBuilder md = new StringBuilder();
            JsonNode subject = report.path("subject");
            md.append("# SQL 性能分析报告\n\n");
            md.append("- 语句：`").append(subject.path("namespace").asText())
                    .append('.').append(subject.path("statementId").asText()).append("`\n");
            String hash = subject.path("contentHash").asText("");
            md.append("- Mapper：").append(subject.path("mapperPath").asText())
                    .append("（").append(hash.length() > 12 ? hash.substring(0, 12) : hash).append("…）\n");
            JsonNode audit = report.path("audit");
            md.append("- 知识版本：").append(audit.path("knowledgeVersion").asText("无"))
                    .append(" · 画像快照：").append(audit.path("profileSnapshotId").asText("无"))
                    .append(" · 生成时间：").append(audit.path("generatedAt").asText()).append("\n\n");

            JsonNode summary = report.path("summary");
            md.append("## 结论摘要\n\n");
            md.append("**严重度 ").append(summary.path("severity").asText()).append("**（置信度 ")
                    .append(summary.path("confidence").asDouble()).append("）：")
                    .append(summary.path("headline").asText()).append("\n\n");
            if (summary.path("primaryBottlenecks").isArray() && summary.path("primaryBottlenecks").size() > 0) {
                for (JsonNode b : summary.path("primaryBottlenecks")) {
                    md.append("- ").append(b.asText()).append("\n");
                }
                md.append("\n");
            }

            md.append("## 风险\n\n");
            appendList(md, report.path("risks"), r -> r.path("type").asText() + "：" + r.path("title").asText());

            md.append("## 场景矩阵（").append(report.path("scenarios").size()).append(" 个场景，BoundSql 均来自官方运行时）\n\n");
            md.append("| 场景 | 参数来源 | 指纹 | 风险 | 理由 |\n|---|---|---|---|---|\n");
            for (JsonNode s : report.path("scenarios")) {
                md.append("| ").append(s.path("name").asText())
                        .append(" | ").append(s.path("parameterSource").asText())
                        .append(" | ").append(s.path("sqlFingerprint").asText())
                        .append(" | ").append(s.path("hasDollarInterpolation").asBoolean() ? "${}注入" : "—")
                        .append(" | ").append(s.path("reason").asText().replace("\n", " "))
                        .append(" |\n");
            }
            md.append("\n");

            md.append("## 索引与分片分析\n\n");
            appendList(md, report.path("schemaMetadata"), f -> f.path("fact").asText());

            md.append("## 数据分布\n\n");
            appendList(md, report.path("dataDistribution"), f -> f.path("fact").asText());

            md.append("## 优化建议\n\n");
            for (JsonNode r : report.path("recommendations")) {
                md.append("### ").append(r.path("title").asText())
                        .append("（").append(r.path("priority").asText()).append("，置信度 ")
                        .append(r.path("confidence").asDouble()).append("）\n");
                md.append("- 问题：").append(r.path("problem").asText()).append("\n");
                md.append("- 影响：").append(r.path("impact").asText()).append("\n");
                md.append("- 建议：").append(firstLine(r.path("suggestedSql").asText(""))).append("\n\n");
            }

            md.append("## 限制与缺失证据\n\n");
            JsonNode limits = report.path("limits");
            for (JsonNode note : limits.path("notes")) {
                md.append("- ").append(note.asText()).append("\n");
            }
            if (limits.path("explainSkipped").asBoolean()) {
                md.append("- 未执行 EXPLAIN（只读建议边界）。\n");
            }
            return md.toString();
        } catch (Exception e) {
            throw new IllegalStateException("报告 Markdown 投影失败：" + e.getMessage(), e);
        }
    }

    private interface FactRenderer {
        String render(JsonNode node);
    }

    private void appendList(StringBuilder md, JsonNode array, FactRenderer renderer) {
        if (!array.isArray() || array.size() == 0) {
            md.append("（无）\n\n");
            return;
        }
        for (JsonNode node : array) {
            md.append("- ").append(renderer.render(node)).append("\n");
        }
        md.append("\n");
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) return "—";
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }
}
