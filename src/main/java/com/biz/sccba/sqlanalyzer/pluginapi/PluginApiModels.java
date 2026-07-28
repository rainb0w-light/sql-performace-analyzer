package com.biz.sccba.sqlanalyzer.pluginapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Wire models frozen by idea-plugin/docs/p1-plugin-api-contract.md. */
public final class PluginApiModels {

    private PluginApiModels() {
    }

    public enum NodeKind { IF, CHOOSE_WHEN, CHOOSE_OTHERWISE, FOREACH, STRUCTURE }

    public enum ConditionCategory { ROUTING, FILTER, SORT_PAGE, JOIN, OTHER }

    public enum CategorySource { SERVER_EXPLAINED, STRUCTURE_FALLBACK }

    public enum ValueType {
        NULL, STRING, BOOLEAN, INTEGER, DECIMAL, DATE_TIME, ENUM, COLLECTION, OBJECT
    }

    public enum CollectionMode { EMPTY, SINGLE, MULTIPLE }

    public enum ExecutionMode { AUTO, REVIEW }

    public enum CostLevel { UNKNOWN, LOW, MEDIUM, HIGH, EXTREME }

    public enum RuleKind { PARAMETER_FACT, ALLOWED_VALUES, RANGE, USER_SAMPLE }

    public record TypedValue(ValueType type, String value, List<TypedValue> values,
                             Map<String, TypedValue> fields) {
        public TypedValue {
            type = type == null ? ValueType.NULL : type;
            value = value == null ? "" : value;
            values = values == null ? List.of() : List.copyOf(values);
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }

    public record SuggestionRequest(@NotBlank String artifactId, @NotBlank String statementId,
                                    @NotBlank String datasourceProfileId, String projectId,
                                    String moduleId, String contentHash) {
    }

    public record SuggestionNode(String nodeId, NodeKind kind, String testExpression,
                                 String parameterPath, String parameterType, String parentNodeId,
                                 String chooseGroupId, ConditionCategory category,
                                 CategorySource categorySource, boolean assignable,
                                 boolean suggestedEnabled, TypedValue suggestedValue, String source,
                                 String version, String locator, double confidence, String reason) {
    }

    public record SuggestionSet(String suggestionSetId, String contextVersion,
                                List<SuggestionNode> nodes) {
        public SuggestionSet {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record NodeSelection(@NotBlank String nodeId, boolean selected,
                                CollectionMode collectionMode) {
    }

    public record MainScenario(@NotBlank String suggestionSetId,
                               List<@Valid NodeSelection> selections,
                               Map<String, @Valid TypedValue> parameters) {
        public MainScenario {
            selections = selections == null ? List.of() : List.copyOf(selections);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record BoundSqlPreviewRequest(@NotBlank String suggestionSetId,
                                         List<@Valid NodeSelection> selections,
                                         Map<String, @Valid TypedValue> parameters) {
        public BoundSqlPreviewRequest {
            selections = selections == null ? List.of() : List.copyOf(selections);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record FieldError(String field, String nodeId, String code, String message) {
    }

    public record ParameterMapping(String property, String jdbcType) {
    }

    public record BoundSqlPreview(String boundSql, List<String> hitNodeIds,
                                  List<ParameterMapping> parameterMappings,
                                  List<FieldError> validationErrors, boolean redacted) {
    }

    public record TransientRule(@NotBlank String ruleId, @NotNull RuleKind kind,
                                @NotBlank String target, String operator,
                                List<@Valid TypedValue> values) {
        public TransientRule {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record TransientRulePreviewRequest(@NotBlank String artifactId,
                                              @NotBlank String statementId,
                                              @NotBlank String datasourceProfileId,
                                              String projectId, String moduleId,
                                              MainScenario mainScenario,
                                              List<@Valid TransientRule> transientRules,
                                              Integer maxScenarios, CostLevel costThreshold) {
        public TransientRulePreviewRequest {
            transientRules = transientRules == null ? List.of() : List.copyOf(transientRules);
        }
    }

    public record GuardChange(String guard, String before, String after) {
    }

    public record TransientRuleImpact(List<String> addedScenarioIds,
                                      List<String> removedScenarioIds,
                                      List<String> addedCoverageGoals,
                                      List<String> removedCoverageGoals,
                                      List<GuardChange> guardChanges,
                                      CostLevel costBefore, CostLevel costAfter,
                                      List<FieldError> fieldErrors) {
    }

    public record AnalyzeRequest(@NotBlank String statementId, @NotBlank String artifactId,
                                 @NotBlank String datasourceProfileId, String sessionId,
                                 String projectId, String moduleId, String mybatisConfigXml,
                                 String databaseId, String schemaName, ExecutionMode executionMode,
                                 MainScenario mainScenario,
                                 List<@Valid TransientRule> transientRules,
                                 Integer maxScenarios, CostLevel costThreshold,
                                 List<Map<String, Object>> userSamples) {
        public AnalyzeRequest {
            executionMode = executionMode == null ? ExecutionMode.AUTO : executionMode;
            transientRules = transientRules == null ? List.of() : List.copyOf(transientRules);
            userSamples = userSamples == null ? List.of() : List.copyOf(userSamples);
        }
    }

    public record ExcludedScenario(@NotBlank String scenarioId, @NotBlank String reason) {
    }

    public record ScenarioConfirmation(List<String> includedScenarioIds,
                                       List<@Valid ExcludedScenario> excludedScenarios) {
        public ScenarioConfirmation {
            includedScenarioIds = includedScenarioIds == null
                    ? List.of() : List.copyOf(includedScenarioIds);
            excludedScenarios = excludedScenarios == null
                    ? List.of() : List.copyOf(excludedScenarios);
        }
    }
}
