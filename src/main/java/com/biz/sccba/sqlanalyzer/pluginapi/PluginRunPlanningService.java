package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.analysis.ScenarioContextResolver;
import com.biz.sccba.sqlanalyzer.analysis.StatementReferenceResolver;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CostLevel;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.MainScenario;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TransientRule;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ValueType;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates and restores immutable Run planning snapshots. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginRunPlanningService {

    private final ArtifactService artifacts;
    private final StatementReferenceResolver references;
    private final ScenarioContextResolver contexts;
    private final ScenarioEngine scenarios;
    private final DynamicNodeCatalog catalog;
    private final ObjectMapper objectMapper;

    public PluginRunPlanningService(ArtifactService artifacts,
                                    StatementReferenceResolver references,
                                    ScenarioContextResolver contexts,
                                    ScenarioEngine scenarios, DynamicNodeCatalog catalog,
                                    ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.references = references;
        this.contexts = contexts;
        this.scenarios = scenarios;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public PlannedRun planAndStore(String clientId, String runId, String sessionId,
                                   JsonNode payload) {
        String artifactId = required(payload, "artifactId");
        String statementId = required(payload, "statementId");
        String datasourceProfileId = required(payload, "datasourceProfileId");
        String schema = text(payload, "schemaName", "public");
        String mybatisConfig = nullable(payload, "mybatisConfigXml");
        String databaseId = nullable(payload, "databaseId");
        int max = Math.max(1, Math.min(payload.path("maxScenarios").asInt(20), 100));
        byte[] mapper = artifacts.read(clientId, artifactId);
        var refs = references.resolve(mapper, "artifact:" + artifactId, statementId,
                mybatisConfig, databaseId);

        List<Map<String, Object>> samples = new ArrayList<>();
        MainScenario main = payload.path("mainScenario").isMissingNode()
                || payload.path("mainScenario").isNull() ? null
                : objectMapper.convertValue(payload.path("mainScenario"), MainScenario.class);
        if (main != null) {
            samples.add(parameters(main));
        }
        if (payload.path("userSamples").isArray()) {
            samples.addAll(objectMapper.convertValue(payload.path("userSamples"),
                    new TypeReference<List<Map<String, Object>>>() { }));
        }
        if (payload.path("transientRules").isArray()) {
            List<TransientRule> rules = objectMapper.convertValue(payload.path("transientRules"),
                    new TypeReference<List<TransientRule>>() { });
            samples.addAll(ruleSamples(rules));
        }

        var context = contexts.resolve(clientId, datasourceProfileId, schema, refs, samples, max);
        PlannerInput plannerInput = context.plannerInput();
        var plan = scenarios.plan(mapper, "artifact:" + artifactId, statementId,
                plannerInput, mybatisConfig, databaseId);
        Set<String> required = new LinkedHashSet<>();
        plan.scenarios().stream().filter(item -> item.scenario().name().equals("业务主路径"))
                .findFirst().ifPresent(item -> required.add(item.scenario().scenarioId()));
        if (main != null) {
            plan.scenarios().stream().filter(item -> item.scenario().name().equals("用户样例 1"))
                    .findFirst().ifPresent(item -> required.add(item.scenario().scenarioId()));
        }
        boolean mainScenarioMissing = main != null && required.size() < 2;
        List<String> guards = guards(plan, context,
                hasTypeConflict(mapper, statementId, refs.parameters()),
                plan.scenarios().size() >= max || mainScenarioMissing,
                payload.path("costThreshold").asText("MEDIUM"));
        String fingerprint = fingerprint(mapper, datasourceProfileId, context.knowledgeVersion(),
                context.profileSnapshotId());
        StoredRunPlan stored = new StoredRunPlan(runId, sessionId, clientId, artifactId,
                statementId, datasourceProfileId, nullable(payload, "projectId"),
                nullable(payload, "moduleId"), mybatisConfig, databaseId, schema,
                refs.statementType(), fingerprint, plan, context,
                List.copyOf(required), guards, cost(plan), true);
        try {
            byte[] json = objectMapper.writeValueAsBytes(stored);
            var snapshot = artifacts.ingest(clientId, sessionId, "PLUGIN_RUN_PLAN",
                    "run-plan.json", "application/json", json,
                    objectMapper.writeValueAsString(Map.of("runId", runId)));
            return new PlannedRun(snapshot.id(), stored);
        } catch (Exception e) {
            throw new IllegalStateException("无法保存 Run 规划快照", e);
        }
    }

    public StoredRunPlan read(String clientId, String planArtifactId) {
        try {
            StoredRunPlan plan = objectMapper.readValue(artifacts.read(clientId, planArtifactId),
                    StoredRunPlan.class);
            if (!clientId.equals(plan.clientId())) {
                throw new IllegalArgumentException("Run 规划快照不属于当前客户端");
            }
            return plan;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Run 规划快照无效", e);
        }
    }

    private static List<String> guards(ScenarioEngine.PlanResult plan,
                                       ScenarioContextResolver.ContextBundle context,
                                       boolean typeConflict,
                                       boolean scenarioLimitReached,
                                       String threshold) {
        Set<String> result = new LinkedHashSet<>();
        if (plan.loadError() != null) {
            result.add("LANGUAGE_DRIVER_UNSUPPORTED");
        }
        if (typeConflict
                || plan.scenarios().stream().anyMatch(item -> item.unsupported() != null)) {
            result.add("CRITICAL_PARAMETER_TYPE");
        }
        if (!dollarWhitelisted(context)) {
            result.add("DOLLAR_WHITELIST_REQUIRED");
        }
        CostLevel actual = cost(plan);
        CostLevel allowed;
        try {
            allowed = CostLevel.valueOf(threshold);
        } catch (Exception ignored) {
            allowed = CostLevel.MEDIUM;
        }
        if (scenarioLimitReached || actual.ordinal() > allowed.ordinal()) {
            result.add("SCENARIO_OR_COST_LIMIT");
        }
        return List.copyOf(result);
    }

    private boolean hasTypeConflict(byte[] mapper, String statementId,
                                    Map<String, com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo> params) {
        var structure = catalog.scan(new String(mapper, StandardCharsets.UTF_8));
        var statement = structure.statements().stream()
                .filter(item -> item.statementId().equals(statementId)
                        || (structure.namespace() + "." + item.statementId()).equals(statementId))
                .findFirst().orElse(null);
        if (statement == null) return false;
        Map<String, Set<ParamKind>> kinds = new LinkedHashMap<>();
        for (var node : statement.nodes()) {
            String path;
            ParamKind kind;
            if ("foreach".equals(node.type())) {
                path = node.attributes().getOrDefault("collection", "list");
                kind = ParamKind.LIST;
            } else if (!node.referencedNames().isEmpty()) {
                path = node.referencedNames().get(0).split("[.\\[]", 2)[0];
                String test = node.test() == null ? "" : node.test().toLowerCase();
                if (test.contains(".size") || test.contains(".isempty")) {
                    kind = ParamKind.LIST;
                } else if (test.matches(".*(?:<|>|<=|>=)\\s*-?\\d+(?:\\.\\d+)?.*")) {
                    kind = test.matches(".*\\d+\\.\\d+.*") ? ParamKind.DOUBLE : ParamKind.INT;
                } else {
                    kind = params.containsKey(path) ? params.get(path).kind() : ParamKind.UNKNOWN;
                }
            } else {
                continue;
            }
            kinds.computeIfAbsent(path, ignored -> new LinkedHashSet<>()).add(kind);
        }
        return kinds.values().stream().anyMatch(values -> values.size() > 1);
    }

    private static boolean dollarWhitelisted(ScenarioContextResolver.ContextBundle context) {
        if (context.references().dollarExpressions().isEmpty()) return true;
        for (String expression : context.references().dollarExpressions()) {
            String name = expression.split("[.\\[]", 2)[0];
            boolean found = context.plannerInput().knowledge().stream()
                    .anyMatch(item -> item.columnName().equals(name)
                            && item.frequentValues() != null
                            && !item.frequentValues().isEmpty());
            if (!found) return false;
        }
        return true;
    }

    private static CostLevel cost(ScenarioEngine.PlanResult plan) {
        int count = plan.scenarios().size();
        if (count <= 5) return CostLevel.LOW;
        if (count <= 12) return CostLevel.MEDIUM;
        if (count <= 20) return CostLevel.HIGH;
        return CostLevel.EXTREME;
    }

    private static Map<String, Object> parameters(MainScenario scenario) {
        Map<String, Object> result = new LinkedHashMap<>();
        scenario.parameters().forEach((path, value) ->
                PluginMapperPreparationService.setPath(result, path,
                        PluginMapperPreparationService.javaValue(value)));
        return result;
    }

    private static List<Map<String, Object>> ruleSamples(List<TransientRule> rules) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TransientRule rule : rules) {
            if (rule.values().isEmpty()) {
                throw new IllegalArgumentException("临时规则缺少类型化值：" + rule.ruleId());
            }
            if (rule.kind() == PluginApiModels.RuleKind.USER_SAMPLE) {
                var value = rule.values().get(0);
                if (value.type() != ValueType.OBJECT) {
                    throw new IllegalArgumentException("USER_SAMPLE 必须是 OBJECT");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> sample = (Map<String, Object>)
                        PluginMapperPreparationService.javaValue(value);
                result.add(sample);
                continue;
            }
            int count = rule.kind() == PluginApiModels.RuleKind.RANGE
                    ? Math.min(2, rule.values().size()) : rule.values().size();
            for (int i = 0; i < count; i++) {
                Map<String, Object> sample = new LinkedHashMap<>();
                PluginMapperPreparationService.setPath(sample, rule.target(),
                        PluginMapperPreparationService.javaValue(rule.values().get(i)));
                result.add(sample);
            }
        }
        return result;
    }

    private static String fingerprint(byte[] mapper, String datasource,
                                      String knowledge, String profile) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper))
                            + "|" + datasource + "|" + knowledge + "|" + profile)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算上下文指纹", e);
        }
    }

    private static String required(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("分析任务缺少字段：" + field);
        }
        return value;
    }

    private static String nullable(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private static String text(JsonNode payload, String field, String fallback) {
        String value = payload.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    public record PlannedRun(String planArtifactId, StoredRunPlan plan) {
    }

    public record StoredRunPlan(
            String runId, String sessionId, String clientId, String sourceArtifactId,
            String statementId, String datasourceProfileId, String projectId, String moduleId,
            String mybatisConfigXml, String databaseId, String schemaName,
            String statementType, String contextFingerprint,
            ScenarioEngine.PlanResult plan, ScenarioContextResolver.ContextBundle context,
            List<String> requiredScenarioIds, List<String> blockingGuards,
            CostLevel cost, boolean readOnly) {
    }
}
