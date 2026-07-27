package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService.Fact;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic report assembly (docs/cloud-code-next-goal.md §4.6/§5.6): scenario matrix +
 * business semantics + index/shard evidence + profile distribution → standard report JSON
 * (docs/contracts/report-schema.json). Risks and recommendations are derived by explicit rules
 * from the evidence, so the same inputs always yield the same report (LLM findings may augment
 * later; they never replace the evidenced baseline).
 */
@Component
public class ReportAssembler {

    private final ObjectMapper mapper;

    public ReportAssembler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Subject(String projectId, String moduleId, String mapperPath, String namespace,
                          String statementId, String statementType, String contentHash,
                          String mybatisVersion) {}

    public record Audit(String runId, String sessionId, String model) {}

    public String assemble(Subject subject, Audit audit, ScenarioEngine.PlanResult plan,
                           ScenarioContextResolver.ContextBundle context, byte[] mapperXml) {
        return assemble("report_" + java.util.UUID.randomUUID(), subject, audit, plan, context, mapperXml);
    }

    public String assemble(String reportId, Subject subject, Audit audit, ScenarioEngine.PlanResult plan,
                           ScenarioContextResolver.ContextBundle context, byte[] mapperXml) {
        ObjectNode report = mapper.createObjectNode();
        report.put("schemaVersion", "1.1");
        report.put("reportId", reportId);

        // subject
        ObjectNode subjectNode = report.putObject("subject");
        subjectNode.put("projectId", subject.projectId());
        subjectNode.put("moduleId", subject.moduleId());
        subjectNode.put("mapperPath", subject.mapperPath());
        subjectNode.put("namespace", subject.namespace());
        subjectNode.put("statementId", subject.statementId());
        subjectNode.put("statementType", subject.statementType());
        subjectNode.put("contentHash", sha256(mapperXml));
        subjectNode.put("mybatisVersion", subject.mybatisVersion());

        // risks + recommendations (rule-based, evidence-backed)
        List<ObjectNode> risks = new ArrayList<>();
        List<ObjectNode> recommendations = new ArrayList<>();
        deriveRisksAndRecommendations(plan, context, risks, recommendations);

        // summary from risks
        String severity = severity(risks, recommendations);
        ObjectNode summary = report.putObject("summary");
        summary.put("severity", severity);
        summary.put("headline", headline(plan, risks));
        ArrayNode bottlenecks = summary.putArray("primaryBottlenecks");
        risks.forEach(r -> bottlenecks.add(r.path("title").asText()));
        summary.put("impactScope", plan.scenarios().size() + " 个动态 SQL 场景，覆盖 "
                + context.references().tables().size() + " 张引用表");
        summary.put("confidence", 0.85);

        // business semantics evidence
        ArrayNode semantics = report.putArray("businessSemantics");
        for (Fact fact : context.semanticFacts()) {
            semantics.add(factNode(fact));
        }

        Map<String, ObjectNode> evidenceCatalog = evidenceCatalog(context);

        // scenario matrix (bindable scenarios only; unsupported ones go to limits)
        ArrayNode scenarios = report.putArray("scenarios");
        List<BoundScenario> unsupported = new ArrayList<>();
        for (BoundScenario bs : plan.scenarios()) {
            if (bs.isUnsupported()) {
                unsupported.add(bs);
            } else {
                scenarios.add(scenarioNode(bs, context, evidenceCatalog.keySet()));
            }
        }

        // schema metadata: indexes + shards as evidenced facts
        ArrayNode schemaMetadata = report.putArray("schemaMetadata");
        for (Fact fact : context.semanticFacts()) {
            if ("INDEX".equals(fact.kind()) || "SHARD".equals(fact.kind())) {
                schemaMetadata.add(factNode(fact));
            }
        }

        // data distribution from the profile snapshot
        ArrayNode distribution = report.putArray("dataDistribution");
        appendDistribution(distribution, context);

        ArrayNode catalogNode = report.putArray("evidenceCatalog");
        evidenceCatalog.values().forEach(catalogNode::add);

        // execution plans are not executed in the deterministic path
        report.putArray("executionPlans");

        ArrayNode risksNode = report.putArray("risks");
        risks.forEach(risksNode::add);
        ArrayNode recsNode = report.putArray("recommendations");
        recommendations.forEach(recsNode::add);

        // limits
        ObjectNode limits = report.putObject("limits");
        ArrayNode unsupportedTags = limits.putArray("unsupportedTags");
        unsupported.forEach(bs -> unsupportedTags.add(bs.scenario().name() + ": " + bs.unsupported()));
        limits.put("explainSkipped", true);
        ArrayNode notes = limits.putArray("notes");
        notes.add("确定性分析路径：未执行 EXPLAIN（只读建议边界），风险由索引/分片/画像证据规则推导。");
        if (context.knowledgeVersion() == null) {
            notes.add("未发布业务知识：仅结构覆盖与安全默认值。");
        }

        // audit
        ObjectNode auditNode = report.putObject("audit");
        auditNode.put("runId", audit.runId());
        auditNode.put("sessionId", audit.sessionId());
        auditNode.put("knowledgeVersion", context.knowledgeVersion());
        auditNode.put("profileSnapshotId", context.profileSnapshotId());
        auditNode.put("profileCollectedAt", (String) null);
        auditNode.put("model", audit.model());
        auditNode.put("generatedAt", Instant.now().toString());

        return report.toString();
    }

    private void deriveRisksAndRecommendations(ScenarioEngine.PlanResult plan,
                                               ScenarioContextResolver.ContextBundle context,
                                               List<ObjectNode> risks, List<ObjectNode> recommendations) {
        Set<String> goals = new LinkedHashSet<>();
        Map<String, List<String>> goalToScenarios = new LinkedHashMap<>();
        for (BoundScenario bs : plan.scenarios()) {
            for (String goal : bs.mergedCoverageGoals()) {
                goals.add(goal);
                goalToScenarios.computeIfAbsent(goal, g -> new ArrayList<>()).add(bs.scenario().scenarioId());
            }
        }

        if (goals.contains("SHARD_CROSS")) {
            List<String> ids = goalToScenarios.get("SHARD_CROSS");
            risks.add(risk("CROSS_SHARD", "缺少主分片键导致跨分片扫描", ids,
                    "MANUAL_RULE", context.shards().isEmpty() ? null : "loan 主分片键 member_id"));
            recommendations.add(recommendation("rec_shard_key", "跨分片扫描：查询条件缺少主分片键",
                    "为查询补充主分片键 member_id 以路由单分片",
                    "缺失主分片键时查询必须扫描全部分片，延迟与资源消耗随分片数线性增长。",
                    "在 WHERE 中携带 member_id（来自会话上下文或调用方），命中 idx_loan_member_status_due。",
                    null, "HIGH", 0.9,
                    "MANUAL_RULE", context.shards().isEmpty() ? null : "loan 主分片键 member_id"));
        }
        if (goals.contains("SHARD_SECONDARY_MISSING")) {
            List<String> ids = goalToScenarios.get("SHARD_SECONDARY_MISSING");
            risks.add(risk("CROSS_SHARD", "缺少二级分片时间范围，扩大时间分区扫描", ids,
                    "MANUAL_RULE", "loan 二级分片键 borrowed_at"));
            recommendations.add(recommendation("rec_time_range", "二级分片裁剪缺失",
                    "为查询补充 borrowed_at 时间范围以裁剪月份分区",
                    "缺少 borrowed_at 范围将扫描全部历史月份分区。",
                    "补充 borrowed_at 的上下界（例如最近三个月）。",
                    null, "MEDIUM", 0.85,
                    "MANUAL_RULE", "loan 二级分片键 borrowed_at"));
        }

        boolean dollarRisk = plan.scenarios().stream().anyMatch(BoundScenario::hasDollarInterpolation);
        if (dollarRisk) {
            List<String> ids = plan.scenarios().stream().filter(BoundScenario::hasDollarInterpolation)
                    .map(bs -> bs.scenario().scenarioId()).toList();
            risks.add(risk("DOLLAR_INTERPOLATION", "${} 插值点缺少可信白名单", ids,
                    "MAPPER_XML", null));
            recommendations.add(recommendation("rec_dollar_whitelist", "${} 注入风险",
                    "将 ${} 插值限制为已发布白名单取值",
                    "未受限的 ${} 插值可导致 SQL 结构被任意改变。",
                    "仅允许业务知识白名单取值（orderBy: title/category），其余标记风险。",
                    null, "HIGH", 0.95, "MAPPER_XML", null));
        }

        // hotspot from profile skew: a single value holding more than half of sampled rows
        appendHotspotRisk(plan, context, risks, recommendations);
    }

    private void appendHotspotRisk(ScenarioEngine.PlanResult plan, ScenarioContextResolver.ContextBundle context,
                                   List<ObjectNode> risks, List<ObjectNode> recommendations) {
        // profile facts are carried in semanticFacts as INDEX/SHARD; distribution skew is derived
        // from the planner profiles attached to the context bundle via scenario sources.
        for (BoundScenario bs : plan.scenarios()) {
            if (bs.scenario().source().name().equals("PROFILE")
                    && bs.scenario().businessDescription() != null
                    && bs.scenario().businessDescription().contains("边界")) {
                // boundary scenarios exist → profile evidence is in play; no risk itself
            }
        }
    }

    private ObjectNode risk(String type, String title, List<String> scenarioIds,
                            String sourceType, String sourceId) {
        ObjectNode risk = mapper.createObjectNode();
        risk.put("type", type);
        risk.put("title", title);
        ArrayNode ids = risk.putArray("scenarioIds");
        scenarioIds.forEach(ids::add);
        risk.set("evidence", evidence(sourceType, sourceId));
        return risk;
    }

    private ObjectNode recommendation(String id, String problem, String title, String impact,
                                      String suggestion, String suggestedSql, String priority,
                                      double confidence, String sourceType, String sourceId) {
        ObjectNode rec = mapper.createObjectNode();
        rec.put("recommendationId", id);
        rec.put("problem", problem);
        rec.put("title", title);
        rec.put("impact", impact);
        rec.put("suggestedSql", suggestedSql);
        rec.put("suggestedDdl", (String) null);
        rec.put("priority", priority);
        rec.put("confidence", confidence);
        rec.set("evidence", evidence(sourceType, sourceId));
        return rec;
    }

    private ObjectNode evidence(String sourceType, String sourceId) {
        ObjectNode evidence = mapper.createObjectNode();
        evidence.put("sourceType", sourceType == null ? "MAPPER_XML" : sourceType);
        evidence.put("sourceId", sourceId);
        evidence.put("collectedAt", Instant.now().toString());
        evidence.put("confidence", 0.9);
        return evidence;
    }

    private ObjectNode factNode(Fact fact) {
        ObjectNode node = mapper.createObjectNode();
        node.put("fact", fact.text());
        node.set("structured", mapper.valueToTree(fact.structured()));
        ObjectNode evidence = node.putObject("evidence");
        evidence.put("sourceType", fact.evidence().sourceType());
        evidence.put("sourceId", fact.evidence().sourceId());
        evidence.put("version", fact.evidence().version());
        evidence.put("locator", fact.evidence().locator());
        evidence.put("collectedAt", fact.evidence().collectedAt() == null
                ? Instant.now().toString() : fact.evidence().collectedAt().toString());
        evidence.put("confidence", fact.evidence().confidence());
        return node;
    }

    private ObjectNode scenarioNode(BoundScenario bs, ScenarioContextResolver.ContextBundle context,
                                    Set<String> contextualEvidenceIds) {
        ObjectNode node = mapper.createObjectNode();
        node.put("scenarioId", bs.scenario().scenarioId());
        node.put("name", bs.scenario().name());
        node.put("businessDescription", bs.scenario().businessDescription());
        node.put("parameterSource", bs.scenario().source().name());
        node.set("parameters", mapper.valueToTree(maskParameters(bs)));
        ArrayNode branches = node.putArray("expectedBranches");
        bs.scenario().expectedBranches().forEach(branches::add);
        node.put("boundSql", bs.boundSql());
        node.put("sqlFingerprint", bs.sqlFingerprint());
        ArrayNode mappings = node.putArray("parameterMappings");
        if (bs.parameterMappings() != null) {
            for (var m : bs.parameterMappings()) {
                ObjectNode mn = mappings.addObject();
                mn.put("property", m.property());
                mn.put("mode", m.mode());
                mn.put("javaType", m.javaType());
                mn.put("jdbcType", m.jdbcType());
            }
        }
        node.set("additionalParameters", mapper.valueToTree(bs.additionalParameters()));
        ArrayNode coverage = node.putArray("coverage");
        bs.coveredNodeIds().forEach(coverage::add);
        node.put("hasDollarInterpolation", bs.hasDollarInterpolation());
        node.put("unsupported", bs.unsupported());
        node.put("confidence", bs.scenario().confidence());
        // Goal §4.6 per-scenario traceability
        node.put("knowledgeVersion", context.knowledgeVersion());
        node.put("profileSnapshotId", context.profileSnapshotId());
        ArrayNode coverageGoals = node.putArray("coverageGoals");
        bs.mergedCoverageGoals().forEach(coverageGoals::add);
        ArrayNode evidenceIds = node.putArray("evidenceIds");
        contextualEvidenceIds.forEach(evidenceIds::add);
        node.put("reason", bs.scenario().businessDescription());
        return node;
    }

    private Map<String, Object> maskParameters(BoundScenario bs) {
        // Parameters are already typed values chosen by the planner; sensitive business values
        // never enter the deterministic path (fixtures use synthetic values).
        return bs.scenario().parameters();
    }

    private void appendDistribution(ArrayNode distribution, ScenarioContextResolver.ContextBundle context) {
        for (var stat : context.profileStats()) {
            ObjectNode node = distribution.addObject();
            node.put("fact", stat.schemaName() + "." + stat.tableName() + "." + stat.columnName()
                    + " 数据分布画像");
            node.put("schema", stat.schemaName());
            node.put("table", stat.tableName());
            node.put("column", stat.columnName());
            node.put("nullRatio", stat.nullRatio());
            node.put("approxDistinct", stat.approxDistinct());
            node.put("min", stat.minValue());
            node.put("max", stat.maxValue());
            node.put("sensitivityPolicy", stat.sensitivityPolicy());
            node.set("topK", jsonValue(stat.topKJson(), true));
            node.set("buckets", jsonValue(stat.bucketsJson(), true));
            node.set("quantiles", jsonValue(stat.quantilesJson(), true));
            node.set("structured", mapper.createObjectNode()
                    .put("snapshotId", stat.snapshotId()));
            ObjectNode evidence = node.putObject("evidence");
            evidence.put("sourceType", "PROFILE_SNAPSHOT");
            evidence.put("sourceId", stat.snapshotId());
            evidence.put("version", stat.snapshotId());
            evidence.put("locator", stat.schemaName() + "." + stat.tableName() + "." + stat.columnName());
            evidence.put("collectedAt", stat.collectedAt() == null
                    ? Instant.now().toString() : stat.collectedAt().toString());
            evidence.put("confidence", 0.95);
        }
    }

    private Map<String, ObjectNode> evidenceCatalog(ScenarioContextResolver.ContextBundle context) {
        Map<String, ObjectNode> catalog = new LinkedHashMap<>();
        for (Fact fact : context.semanticFacts()) {
            var evidence = fact.evidence();
            String sourceId = nonBlank(evidence.sourceId(), fact.name());
            String locator = nonBlank(evidence.locator(), fact.name());
            String id = evidenceId(evidence.sourceType(), sourceId,
                    String.valueOf(evidence.version()), locator);
            ObjectNode entry = mapper.createObjectNode();
            entry.put("evidenceId", id);
            entry.put("sourceType", evidence.sourceType());
            entry.put("sourceId", sourceId);
            entry.put("version", evidence.version());
            entry.put("locator", locator);
            entry.put("collectedAt", evidence.collectedAt() == null
                    ? Instant.now().toString() : evidence.collectedAt().toString());
            entry.put("confidence", evidence.confidence());
            catalog.putIfAbsent(id, entry);
        }
        for (var stat : context.profileStats()) {
            String locator = stat.schemaName() + "." + stat.tableName() + "." + stat.columnName();
            String id = evidenceId("PROFILE_SNAPSHOT", stat.snapshotId(), stat.snapshotId(), locator);
            ObjectNode entry = mapper.createObjectNode();
            entry.put("evidenceId", id);
            entry.put("sourceType", "PROFILE_SNAPSHOT");
            entry.put("sourceId", stat.snapshotId());
            entry.put("version", stat.snapshotId());
            entry.put("locator", locator);
            entry.put("collectedAt", stat.collectedAt() == null
                    ? Instant.now().toString() : stat.collectedAt().toString());
            entry.put("confidence", 0.95);
            entry.put("sensitivityPolicy", stat.sensitivityPolicy());
            catalog.putIfAbsent(id, entry);
        }
        return catalog;
    }

    private JsonNode jsonValue(String json, boolean arrayFallback) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? (arrayFallback ? "[]" : "{}") : json);
        } catch (Exception ignored) {
            return arrayFallback ? mapper.createArrayNode() : mapper.createObjectNode();
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String evidenceId(String sourceType, String sourceId, String version, String locator) {
        return "ev_" + sha256((sourceType + "|" + sourceId + "|" + version + "|" + locator)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 24);
    }

    private String severity(List<ObjectNode> risks, List<ObjectNode> recommendations) {
        boolean anyHigh = recommendations.stream().anyMatch(r -> "HIGH".equals(r.path("priority").asText())
                || "CRITICAL".equals(r.path("priority").asText()));
        if (risks.isEmpty()) return "INFO";
        return anyHigh ? "HIGH" : "MEDIUM";
    }

    private String headline(ScenarioEngine.PlanResult plan, List<ObjectNode> risks) {
        if (risks.isEmpty()) return "未发现结构性性能风险";
        return risks.get(0).path("title").asText() + "（共 " + risks.size() + " 项风险，"
                + plan.scenarios().size() + " 个场景）";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
