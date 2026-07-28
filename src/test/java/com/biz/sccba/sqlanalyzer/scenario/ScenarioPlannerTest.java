package com.biz.sccba.sqlanalyzer.scenario;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.StatementStructure;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnProfile;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.IndexInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ShardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario generation is bounded, explainable and coverage-driven (development-guide §6.3):
 * greedy coverage-goal compression instead of 2^N, default cap 20, priority order preserved.
 */
class ScenarioPlannerTest {

    private static final String MAPPER = """
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.OrderMapper">
              <select id="search" resultType="map">
                SELECT id FROM orders
                <where>
                  <if test="status != null">AND status = #{status}</if>
                  <if test="region != null">AND region = #{region}</if>
                  <if test="amount != null">AND amount &gt; #{amount}</if>
                  <choose>
                    <when test="byEmail != null">AND email = #{byEmail}</when>
                    <when test="byPhone != null">AND phone = #{byPhone}</when>
                    <otherwise>AND 1 = 1</otherwise>
                  </choose>
                  AND id IN
                  <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
                </where>
              </select>
            </mapper>
            """;

    private final DynamicNodeCatalog catalog = new DynamicNodeCatalog();
    private final ScenarioPlanner planner = new ScenarioPlanner();

    private StatementStructure structure() {
        return catalog.scan(MAPPER).statements().get(0);
    }

    private PlannerInput input(int cap) {
        return new PlannerInput(
                Map.of(
                        "status", new ParamInfo("status", ParamKind.STRING, true),
                        "region", new ParamInfo("region", ParamKind.STRING, true),
                        "amount", new ParamInfo("amount", ParamKind.DOUBLE, true),
                        "byEmail", new ParamInfo("byEmail", ParamKind.STRING, true),
                        "byPhone", new ParamInfo("byPhone", ParamKind.STRING, true),
                        "ids", new ParamInfo("ids", ParamKind.LIST, true)),
                List.of(new ColumnKnowledge("status", false, List.of("PAID", "NEW"), List.of("ARCHIVED"))),
                List.of(new ColumnProfile("amount", List.of(), List.of("10", "50", "90"), "1", "100", 0.0, 500L)),
                List.of(new IndexInfo("idx_status_region", List.of("status", "region"))),
                List.of(new ShardInfo("region", null)),
                List.of(Map.of("status", "PAID", "region", "east")),
                "kv-1", "snap-1", cap);
    }

    @Test
    void mainPathComesFirst() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        assertTrue(scenarios.size() >= 2);
        assertEquals("业务主路径", scenarios.get(0).name(), "business main path must be the first scenario");
        assertEquals(0, scenarios.get(0).priority());
    }

    @Test
    void defaultCapIsTwenty() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(0));
        assertTrue(scenarios.size() <= 20, "default cap must be 20, got " + scenarios.size());
    }

    @Test
    void hardCapIsRespected() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(6));
        assertEquals(6, scenarios.size(), "cap must be respected exactly when candidates exceed it");
    }

    @Test
    void scenarioIdsAreDeterministicForTheSameSnapshot() {
        PlannerInput snapshot = input(20).withContentHash("mapper-hash");
        var first = planner.plan(structure(), snapshot).stream()
                .map(ParameterScenario::scenarioId).toList();
        var second = planner.plan(structure(), snapshot).stream()
                .map(ParameterScenario::scenarioId).toList();
        assertEquals(first, second);
    }

    @Test
    void ifBranchesCoveredTrueAndFalse() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        var goals = scenarios.stream().flatMap(s -> s.coverageGoals().stream()).toList();
        for (var node : structure().nodes()) {
            if (node.type().equals("if")) {
                assertTrue(goals.contains("IF_TRUE@" + node.nodeId()), "missing true coverage for " + node.nodeId());
                assertTrue(goals.contains("IF_FALSE@" + node.nodeId()), "missing false coverage for " + node.nodeId());
            }
        }
    }

    @Test
    void chooseArmsAndOtherwiseCovered() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        var goals = scenarios.stream().flatMap(s -> s.coverageGoals().stream()).toList();
        long whenArms = goals.stream().filter(g -> g.startsWith("WHEN_ARM@")).distinct().count();
        assertTrue(whenArms >= 3, "both when arms plus otherwise must be covered, got " + whenArms);
    }

    @Test
    void foreachSizeClassesCovered() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        var goals = scenarios.stream().flatMap(s -> s.coverageGoals().stream()).toList();
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_EMPTY@")));
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_SINGLE@")));
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_MULTI@")));
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_LARGE@")));
    }

    @Test
    void enumShardRangeAndUserScenariosPresent() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        var names = scenarios.stream().map(ParameterScenario::name).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("枚举(status) 高频枚举值")));
        assertTrue(names.stream().anyMatch(n -> n.contains("未知枚举值")));
        assertTrue(names.stream().anyMatch(n -> n.contains("命中单分片")));
        assertTrue(names.stream().anyMatch(n -> n.contains("跨分片扫描")));
        assertTrue(names.stream().anyMatch(n -> n.contains("范围(amount) MIN")));
        assertTrue(names.stream().anyMatch(n -> n.contains("用户样例")));
    }

    @Test
    void scenariosCarryProvenance() {
        List<ParameterScenario> scenarios = planner.plan(structure(), input(20));
        for (ParameterScenario s : scenarios) {
            assertEquals("kv-1", s.knowledgeVersion());
            assertEquals("snap-1", s.profileSnapshotId());
            assertTrue(s.confidence() > 0 && s.confidence() <= 1);
            assertTrue(s.scenarioId().startsWith("scn_"));
        }
    }
}
