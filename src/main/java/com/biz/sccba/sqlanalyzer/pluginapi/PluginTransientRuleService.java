package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.analysis.ScenarioContextResolver;
import com.biz.sccba.sqlanalyzer.analysis.StatementReferenceResolver;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CostLevel;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.FieldError;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.GuardChange;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TransientRule;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TransientRuleImpact;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TransientRulePreviewRequest;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TypedValue;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ValueType;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Side-effect-free deterministic impact preview for Run-scoped transient rules. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginTransientRuleService {

    private final ArtifactService artifacts;
    private final StatementReferenceResolver references;
    private final ScenarioContextResolver contexts;
    private final ScenarioEngine scenarios;

    public PluginTransientRuleService(ArtifactService artifacts,
                                      StatementReferenceResolver references,
                                      ScenarioContextResolver contexts,
                                      ScenarioEngine scenarios) {
        this.artifacts = artifacts;
        this.references = references;
        this.contexts = contexts;
        this.scenarios = scenarios;
    }

    public TransientRuleImpact preview(String clientId, TransientRulePreviewRequest request) {
        byte[] mapper = artifacts.read(clientId, request.artifactId());
        int max = request.maxScenarios() == null ? 20
                : Math.max(1, Math.min(request.maxScenarios(), 100));
        var refs = references.resolve(mapper, "artifact:" + request.artifactId(),
                request.statementId(), null, null);
        var context = contexts.resolve(clientId, request.datasourceProfileId(), "public",
                refs, List.of(), max);
        var before = scenarios.plan(mapper, "artifact:" + request.artifactId(),
                request.statementId(), context.plannerInput(), null, null);

        List<FieldError> errors = new ArrayList<>();
        List<Map<String, Object>> samples = samples(request, errors);
        PlannerInput original = context.plannerInput();
        List<ColumnKnowledge> changedKnowledge = new ArrayList<>(original.knowledge());
        for (TransientRule rule : request.transientRules()) {
            if (rule.kind() == PluginApiModels.RuleKind.ALLOWED_VALUES) {
                List<String> values = rule.values().stream()
                        .map(PluginMapperPreparationService::javaValue)
                        .map(String::valueOf).toList();
                changedKnowledge.add(new ColumnKnowledge(rule.target(), true, values, List.of()));
            }
        }
        PlannerInput changed = new PlannerInput(original.parameters(), changedKnowledge,
                original.profiles(), original.indexes(), original.shards(), samples,
                original.knowledgeVersion(), original.profileSnapshotId(),
                original.maxScenarios(), original.contentHash());
        var after = errors.isEmpty()
                ? scenarios.plan(mapper, "artifact:" + request.artifactId(),
                request.statementId(), changed, null, null) : before;

        Set<String> beforeIds = ids(before);
        Set<String> afterIds = ids(after);
        Set<String> beforeGoals = goals(before);
        Set<String> afterGoals = goals(after);
        Set<String> beforeGuards = guards(before, context.references().dollarExpressions(),
                original.knowledge());
        Set<String> afterGuards = guards(after, context.references().dollarExpressions(),
                changed.knowledge());
        List<GuardChange> changes = new ArrayList<>();
        Set<String> allGuards = new LinkedHashSet<>(beforeGuards);
        allGuards.addAll(afterGuards);
        for (String guard : allGuards) {
            String oldState = beforeGuards.contains(guard) ? "BLOCKING" : "SATISFIED";
            String newState = afterGuards.contains(guard) ? "BLOCKING" : "SATISFIED";
            if (!oldState.equals(newState)) {
                changes.add(new GuardChange(guard, oldState, newState));
            }
        }
        return new TransientRuleImpact(difference(afterIds, beforeIds),
                difference(beforeIds, afterIds), difference(afterGoals, beforeGoals),
                difference(beforeGoals, afterGoals), changes, cost(before), cost(after), errors);
    }

    private static List<Map<String, Object>> samples(
            TransientRulePreviewRequest request, List<FieldError> errors) {
        List<Map<String, Object>> samples = new ArrayList<>();
        if (request.mainScenario() != null && !request.mainScenario().parameters().isEmpty()) {
            Map<String, Object> main = new LinkedHashMap<>();
            request.mainScenario().parameters().forEach((path, value) -> {
                try {
                    PluginMapperPreparationService.setPath(main, path,
                            PluginMapperPreparationService.javaValue(value));
                } catch (IllegalArgumentException e) {
                    errors.add(new FieldError(path, "", "TYPE_MISMATCH", e.getMessage()));
                }
            });
            samples.add(main);
        }
        for (TransientRule rule : request.transientRules()) {
            try {
                switch (rule.kind()) {
                    case PARAMETER_FACT -> samples.add(sample(rule.target(),
                            requiredValue(rule, 0)));
                    case ALLOWED_VALUES -> {
                        for (TypedValue value : rule.values()) {
                            samples.add(sample(rule.target(),
                                    PluginMapperPreparationService.javaValue(value)));
                        }
                    }
                    case RANGE -> {
                        if (rule.values().size() < 2) {
                            throw new IllegalArgumentException("范围规则至少需要两个端点");
                        }
                        samples.add(sample(rule.target(),
                                PluginMapperPreparationService.javaValue(rule.values().get(0))));
                        samples.add(sample(rule.target(),
                                PluginMapperPreparationService.javaValue(rule.values().get(1))));
                    }
                    case USER_SAMPLE -> {
                        TypedValue value = rule.values().isEmpty() ? null : rule.values().get(0);
                        if (value == null || value.type() != ValueType.OBJECT) {
                            throw new IllegalArgumentException("用户样例必须是 OBJECT");
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> object = (Map<String, Object>)
                                PluginMapperPreparationService.javaValue(value);
                        samples.add(object);
                    }
                }
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError(rule.target(), "", "RULE_VALIDATION", e.getMessage()));
            }
        }
        return List.copyOf(samples);
    }

    private static Object requiredValue(TransientRule rule, int index) {
        if (rule.values().size() <= index) {
            throw new IllegalArgumentException("规则缺少类型化值");
        }
        return PluginMapperPreparationService.javaValue(rule.values().get(index));
    }

    private static Map<String, Object> sample(String target, Object value) {
        Map<String, Object> sample = new LinkedHashMap<>();
        PluginMapperPreparationService.setPath(sample, target, value);
        return sample;
    }

    private static Set<String> ids(ScenarioEngine.PlanResult plan) {
        Set<String> result = new LinkedHashSet<>();
        plan.scenarios().forEach(item -> result.add(item.scenario().scenarioId()));
        return result;
    }

    private static Set<String> goals(ScenarioEngine.PlanResult plan) {
        Set<String> result = new LinkedHashSet<>();
        plan.scenarios().forEach(item -> result.addAll(item.mergedCoverageGoals()));
        return result;
    }

    private static Set<String> guards(ScenarioEngine.PlanResult plan,
                                      List<String> dollarExpressions,
                                      List<ColumnKnowledge> knowledge) {
        Set<String> result = new LinkedHashSet<>();
        if (plan.loadError() != null) {
            result.add("LANGUAGE_DRIVER_UNSUPPORTED");
        }
        if (plan.scenarios().stream().anyMatch(item -> item.unsupported() != null)) {
            result.add("CRITICAL_PARAMETER_TYPE");
        }
        if (!dollarWhitelisted(dollarExpressions, knowledge)) {
            result.add("DOLLAR_WHITELIST_REQUIRED");
        }
        return result;
    }

    private static boolean dollarWhitelisted(List<String> expressions,
                                             List<ColumnKnowledge> knowledge) {
        if (expressions == null || expressions.isEmpty()) return true;
        for (String expression : expressions) {
            String name = expression.split("[.\\[]", 2)[0];
            boolean found = knowledge.stream().anyMatch(item -> item.columnName().equals(name)
                    && item.frequentValues() != null && !item.frequentValues().isEmpty());
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

    private static List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(item -> !right.contains(item)).toList();
    }
}
