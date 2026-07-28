package com.biz.sccba.sqlanalyzer.idea.contract;

import java.util.List;
import java.util.Map;

/**
 * Frozen consumer-side DTOs for the Plugin P1 endpoints.
 *
 * <p>These records deliberately model server-owned MyBatis facts (node ids, types, hit nodes and
 * BoundSql). The plugin never derives SQL or pretends that client validation replaces
 * MappedStatement.getBoundSql.</p>
 */
public final class PluginApiDtos {
    private PluginApiDtos() {}

    public enum NodeKind { IF, CHOOSE_WHEN, CHOOSE_OTHERWISE, FOREACH, STRUCTURE }
    public enum ConditionCategory { ROUTING, FILTER, SORT_PAGE, JOIN, OTHER }
    public enum CategorySource { SERVER_EXPLAINED, STRUCTURE_FALLBACK }
    public enum ValueType { NULL, STRING, BOOLEAN, INTEGER, DECIMAL, DATE_TIME, ENUM, COLLECTION, OBJECT }
    public enum CollectionMode { EMPTY, SINGLE, MULTIPLE }
    public enum ExecutionMode { AUTO, REVIEW }
    public enum CostLevel { UNKNOWN, LOW, MEDIUM, HIGH, EXTREME }
    public enum RuleKind { PARAMETER_FACT, ALLOWED_VALUES, RANGE, USER_SAMPLE }

    public record TypedValue(
            ValueType type,
            String value,
            List<TypedValue> values,
            Map<String, TypedValue> fields
    ) {
        public TypedValue {
            type = type == null ? ValueType.NULL : type;
            value = value == null ? "" : value;
            values = values == null ? List.of() : List.copyOf(values);
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }

        public static TypedValue scalar(ValueType type, String value) {
            return new TypedValue(type, value, List.of(), Map.of());
        }

        public static TypedValue collection(List<TypedValue> values) {
            return new TypedValue(ValueType.COLLECTION, "", values, Map.of());
        }

        public static TypedValue nullValue() {
            return scalar(ValueType.NULL, "");
        }
    }

    public record SuggestionRequest(
            String artifactId,
            String statementId,
            String datasourceProfileId,
            String projectId,
            String moduleId,
            String contentHash
    ) {}

    public record SuggestionNode(
            String nodeId,
            NodeKind kind,
            String testExpression,
            String parameterPath,
            String parameterType,
            String parentNodeId,
            String chooseGroupId,
            ConditionCategory category,
            CategorySource categorySource,
            boolean assignable,
            boolean suggestedEnabled,
            TypedValue suggestedValue,
            String source,
            String version,
            String locator,
            double confidence,
            String reason
    ) {}

    public record SuggestionSet(
            String suggestionSetId,
            String contextVersion,
            List<SuggestionNode> nodes
    ) {
        public SuggestionSet {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record NodeSelection(String nodeId, boolean selected, CollectionMode collectionMode) {}

    public record MainScenario(
            String suggestionSetId,
            List<NodeSelection> selections,
            Map<String, TypedValue> parameters
    ) {
        public MainScenario {
            selections = selections == null ? List.of() : List.copyOf(selections);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record BoundSqlPreviewRequest(
            String suggestionSetId,
            List<NodeSelection> selections,
            Map<String, TypedValue> parameters
    ) {}

    public record FieldError(String field, String nodeId, String code, String message) {}
    public record ParameterMapping(String property, String jdbcType) {}

    public record BoundSqlPreview(
            String boundSql,
            List<String> hitNodeIds,
            List<ParameterMapping> parameterMappings,
            List<FieldError> validationErrors,
            boolean redacted
    ) {
        public BoundSqlPreview {
            hitNodeIds = hitNodeIds == null ? List.of() : List.copyOf(hitNodeIds);
            parameterMappings = parameterMappings == null ? List.of() : List.copyOf(parameterMappings);
            validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        }
    }

    public record TransientRule(
            String ruleId,
            RuleKind kind,
            String target,
            String operator,
            List<TypedValue> values
    ) {
        public TransientRule {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record TransientRulePreviewRequest(
            String artifactId,
            String statementId,
            String datasourceProfileId,
            String projectId,
            String moduleId,
            MainScenario mainScenario,
            List<TransientRule> transientRules,
            Integer maxScenarios,
            CostLevel costThreshold
    ) {
        public TransientRulePreviewRequest {
            transientRules = transientRules == null ? List.of() : List.copyOf(transientRules);
        }
    }

    public record GuardChange(String guard, String before, String after) {}

    public record TransientRuleImpact(
            List<String> addedScenarioIds,
            List<String> removedScenarioIds,
            List<String> addedCoverageGoals,
            List<String> removedCoverageGoals,
            List<GuardChange> guardChanges,
            CostLevel costBefore,
            CostLevel costAfter,
            List<FieldError> fieldErrors
    ) {
        public TransientRuleImpact {
            addedScenarioIds = copy(addedScenarioIds);
            removedScenarioIds = copy(removedScenarioIds);
            addedCoverageGoals = copy(addedCoverageGoals);
            removedCoverageGoals = copy(removedCoverageGoals);
            guardChanges = guardChanges == null ? List.of() : List.copyOf(guardChanges);
            fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        }

        private static List<String> copy(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    public record AnalyzeRequest(
            String artifactId,
            String statementId,
            String datasourceProfileId,
            String projectId,
            String moduleId,
            String sessionId,
            ExecutionMode executionMode,
            MainScenario mainScenario,
            List<TransientRule> transientRules,
            Integer maxScenarios,
            CostLevel costThreshold
    ) {
        public AnalyzeRequest {
            transientRules = transientRules == null ? List.of() : List.copyOf(transientRules);
        }
    }

    public record ScenarioConfirmation(
            List<String> includedScenarioIds,
            List<ExcludedScenario> excludedScenarios
    ) {
        public ScenarioConfirmation {
            includedScenarioIds = includedScenarioIds == null ? List.of() : List.copyOf(includedScenarioIds);
            excludedScenarios = excludedScenarios == null ? List.of() : List.copyOf(excludedScenarios);
        }
    }

    public record ExcludedScenario(String scenarioId, String reason) {}
}
