package com.biz.sccba.sqlanalyzer.scenario;

import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime;

import java.util.List;
import java.util.Map;

/** Scenario domain (development-guide §6.3): business-driven parameters in, BoundSql out. */
public final class ScenarioModels {

    private ScenarioModels() {}

    public enum ParameterSource { USER, EXCEL, PROFILE, RULE_INFERRED, BOUNDARY_GENERATED }

    public enum ParamKind { STRING, INT, LONG, DOUBLE, BOOLEAN, DATE, LIST, OBJECT, UNKNOWN }

    public record ParamInfo(String path, ParamKind kind, boolean nullable) {}

    public record ColumnKnowledge(String columnName, boolean required,
                                  List<String> frequentValues, List<String> rareValues) {}

    public record ColumnProfile(String columnName, List<String> topK, List<String> quantiles,
                                String min, String max, Double nullRatio, Long distinct) {}

    public record IndexInfo(String indexName, List<String> columns) {}

    public record ShardInfo(String shardKey, String secondaryShardKey) {}

    public record PlannerInput(Map<String, ParamInfo> parameters,
                               List<ColumnKnowledge> knowledge,
                               List<ColumnProfile> profiles,
                               List<IndexInfo> indexes,
                               List<ShardInfo> shards,
                               List<Map<String, Object>> userSamples,
                               String knowledgeVersion,
                               String profileSnapshotId,
                               int maxScenarios) {

        public static PlannerInput defaults(int maxScenarios) {
            return new PlannerInput(Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, null, maxScenarios);
        }
    }

    /** One parameter scenario; parameters are typed values (sensitive ones masked upstream). */
    public record ParameterScenario(String scenarioId, String name, String businessDescription,
                                    ParameterSource source, Map<String, Object> parameters,
                                    List<String> expectedBranches, List<String> coverageNodeIds,
                                    List<String> coverageGoals, double confidence,
                                    String knowledgeVersion, String profileSnapshotId, int priority) {}

    /**
     * Scenario bound through the official MyBatis path. When several parameter scenarios bind to
     * the same SQL fingerprint they are merged into one BoundScenario; {@code mergedCoverageGoals}
     * accumulates the coverage goals of every contributing scenario so intents like SHARD_SINGLE
     * survive dedup even when their SQL shape equals the main path.
     */
    public record BoundScenario(ParameterScenario scenario, String boundSql, String sqlFingerprint,
                                List<MyBatisStatementRuntime.ParameterMappingView> parameterMappings,
                                Map<String, Object> additionalParameters, List<String> coveredNodeIds,
                                boolean hasDollarInterpolation, String unsupported,
                                List<String> mergedCoverageGoals) {
        public boolean isUnsupported() { return unsupported != null; }
    }
}
