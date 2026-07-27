package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.repository.RecommendationRepository;
import com.biz.sccba.sqlanalyzer.domain.Recommendation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Converts the agent's output contract into durable, reviewable recommendations. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class RecommendationProjector {
    private final RecommendationRepository recommendations;
    private final ObjectMapper objectMapper;

    public RecommendationProjector(RecommendationRepository recommendations, ObjectMapper objectMapper) {
        this.recommendations = recommendations;
        this.objectMapper = objectMapper;
    }

    public int project(String runId, String sessionId, String report) throws Exception {
        JsonNode root = parse(report);
        JsonNode items = root.path("recommendations");
        int count = 0;
        if (items.isArray()) {
            for (JsonNode item : items) {
                recommendations.create(new Recommendation(
                        UUID.randomUUID().toString(), runId, sessionId,
                        text(item, "type", "SQL_OPTIMIZATION"),
                        text(item, "title", "慢 SQL 优化建议"),
                        text(item, "description", report),
                        text(item, "problem", text(item, "description", report)),
                        text(item, "impact", "待评估"),
                        text(item, "priority", "MEDIUM"),
                        item.path("evidence").isMissingNode() ? "{}" : item.path("evidence").toString(),
                        nullableText(item, "suggestedSql"), nullableText(item, "suggestedDdl"),
                        item.path("confidence").asDouble(0.5), "PROPOSED", 1, Instant.now()));
                count++;
            }
        }
        if (count == 0 && report != null && !report.isBlank()) {
            recommendations.create(new Recommendation(UUID.randomUUID().toString(), runId, sessionId,
                    "ANALYSIS_REPORT", "慢 SQL 分析报告", report, report, "待评估", "MEDIUM", "{}", null, null, 0.5,
                    "PROPOSED", 1, Instant.now()));
            count = 1;
        }
        return count;
    }

    private JsonNode parse(String report) {
        if (report == null || report.isBlank()) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(report); }
        catch (Exception ignored) { return objectMapper.createObjectNode(); }
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullableText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
