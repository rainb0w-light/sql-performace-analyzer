package com.biz.sccba.sqlanalyzer.scenario;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end scenario materialization (development-guide §6.2/§6.3): planner → official
 * getBoundSql → fingerprint dedup → ${} risk flag → UNSUPPORTED handling.
 */
class ScenarioEngineFixtureTest {

    private static final String MAPPER = """
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.OrderMapper">
              <select id="search" resultType="map">
                SELECT id FROM orders
                <where>
                  <if test="status != null">AND status = #{status}</if>
                  <if test="region != null">AND region = #{region}</if>
                </where>
              </select>
              <select id="ordered" resultType="map">
                SELECT id FROM orders ORDER BY ${orderColumn}
              </select>
            </mapper>
            """;

    private final ScenarioEngine engine = new ScenarioEngine(new DynamicNodeCatalog(), new ScenarioPlanner());

    private PlannerInput input(int cap, List<ColumnKnowledge> knowledge) {
        return new PlannerInput(
                Map.of("status", new ParamInfo("status", ParamKind.STRING, true),
                        "region", new ParamInfo("region", ParamKind.STRING, true)),
                knowledge, List.of(), List.of(), List.of(), List.of(), "kv-1", "snap-1", cap);
    }

    @Test
    void plansBoundedDedupedBoundScenarios() {
        var result = engine.plan(MAPPER.getBytes(StandardCharsets.UTF_8), "test:search", "search",
                input(20, List.of()), null, null);
        assertEquals("com.example.OrderMapper", result.namespace());
        assertFalse(result.scenarios().isEmpty());
        assertTrue(result.scenarios().size() <= 20);

        Set<String> fingerprints = new HashSet<>();
        for (BoundScenario bound : result.scenarios()) {
            assertFalse(bound.isUnsupported(), String.valueOf(bound.unsupported()));
            assertNotNull(bound.boundSql());
            assertTrue(bound.boundSql().toUpperCase().contains("SELECT"));
            assertNotNull(bound.sqlFingerprint());
            assertTrue(fingerprints.add(bound.sqlFingerprint()),
                    "fingerprints must be unique after dedup: " + bound.sqlFingerprint());
        }
        // With only two boolean branches and no value-sensitive SQL, dedup must collapse many
        // candidates into the small set of distinct SQL shapes (<= 4: none/status/region/both).
        assertTrue(result.scenarios().size() <= 4,
                "fingerprint dedup must collapse same-shape SQL, got " + result.scenarios().size());
    }

    @Test
    void coverageIsMergedAcrossDedupedScenarios() {
        var result = engine.plan(MAPPER.getBytes(StandardCharsets.UTF_8), "test:search", "search",
                input(20, List.of()), null, null);
        var allCovered = result.scenarios().stream().flatMap(b -> b.coveredNodeIds().stream()).toList();
        assertTrue(allCovered.stream().anyMatch(id -> id.contains("if")),
                "merged coverage must include the if nodes");
    }

    @Test
    void dollarInterpolationIsFlaggedAsRisk() {
        var result = engine.plan(MAPPER.getBytes(StandardCharsets.UTF_8), "test:ordered", "ordered",
                input(20, List.of()), null, null);
        assertFalse(result.scenarios().isEmpty());
        assertTrue(result.scenarios().stream().allMatch(BoundScenario::hasDollarInterpolation),
                "${} statements must carry the injection risk flag when values are not whitelisted");
    }

    @Test
    void whitelistedDollarScenarioIsNotFlagged() {
        var knowledge = List.of(new ColumnKnowledge("orderColumn", false, List.of("created_at"), List.of("id")));
        var result = engine.plan(MAPPER.getBytes(StandardCharsets.UTF_8), "test:ordered", "ordered",
                new PlannerInput(Map.of("orderColumn", new ParamInfo("orderColumn", ParamKind.STRING, true)),
                        knowledge, List.of(), List.of(), List.of(), List.of(), "kv-1", "snap-1", 20),
                null, null);
        assertTrue(result.scenarios().stream()
                        .anyMatch(b -> b.scenario().name().startsWith("${orderColumn}") && !b.hasDollarInterpolation()),
                "whitelist scenario must use an approved value without the risk flag");
    }

    @Test
    void unparseableMapperIsReportedNotThrown() {
        var result = engine.plan("<mapper><select id='x'></mapper>".getBytes(StandardCharsets.UTF_8),
                "test:broken", "x", input(20, List.of()), null, null);
        assertNotNull(result.loadError());
        assertTrue(result.loadError().startsWith("UNSUPPORTED"));
        assertTrue(result.scenarios().isEmpty());
    }

    @Test
    void fingerprintIsStableAcrossWhitespace() {
        assertEquals(ScenarioEngine.fingerprint("SELECT id FROM orders WHERE status = ?"),
                ScenarioEngine.fingerprint("select  id\n from orders where status = ?"));
    }
}
