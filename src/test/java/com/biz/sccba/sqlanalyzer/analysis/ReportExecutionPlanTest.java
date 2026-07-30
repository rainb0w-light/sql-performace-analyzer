package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime.ParameterMappingView;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine.PlanResult;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterSource;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExecutionPlanTest {

    @Test
    void explainPlanAndEvidenceAreProjectedIntoTheMatchingScenario() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ReportAssembler assembler = new ReportAssembler(mapper);
        var scenario = new ParameterScenario("scenario_1", "主路径", "主路径",
                ParameterSource.BOUNDARY_GENERATED, Map.of("memberId", 42L),
                List.of(), List.of(), List.of("MAIN"), 0.9, null, null, 1);
        var bound = new BoundScenario(scenario,
                "select * from loan where member_id = ?", "fp_1",
                List.of(new ParameterMappingView("memberId", "IN", "java.lang.Long", null)),
                Map.of(), List.of(), false, null, List.of("MAIN"));
        var plan = new PlanResult("library.Mapper", "find", List.of(bound), null);
        var references = new StatementReferenceResolver.References(
                "library.Mapper", "find", "SELECT", List.of("loan"), Map.of(), List.of());
        var context = new ScenarioContextResolver.ContextBundle(
                PlannerInput.defaults(20), references, List.of(), List.of(), List.of(), List.of(),
                null, null);
        var explains = new ExecutionPlanCollector.Collection(List.of(
                new ExecutionPlanCollector.ExecutionPlan(
                        "scenario_1", "fp_1", "ev_explain_1", "dsp_1",
                        "[{\"table\":\"loan\",\"type\":\"ref\"}]", Instant.parse("2026-07-28T00:00:00Z"),
                        0.98)), List.of(), List.of(), false);

        String json = assembler.assemble("report_1",
                new ReportAssembler.Subject("library", "dao", "LoanMapper.xml",
                        "library.Mapper", "find", "SELECT", null, null),
                new ReportAssembler.Audit("run_1", "session_1", "deterministic-analysis"),
                plan, context, "<mapper/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), explains);
        new ReportSchemaValidator(mapper).validate(json);
        var report = mapper.readTree(json);

        assertEquals(1, report.path("executionPlans").size());
        assertEquals("scenario_1", report.at("/executionPlans/0/scenarioId").asText());
        assertEquals("EXPLAIN", report.at("/executionPlans/0/evidence/sourceType").asText());
        assertEquals("loan", report.at("/executionPlans/0/plan/0/table").asText());
        assertFalse(report.at("/limits/explainSkipped").asBoolean());

        var catalogIds = new HashSet<String>();
        report.path("evidenceCatalog").forEach(node -> catalogIds.add(node.path("evidenceId").asText()));
        assertTrue(catalogIds.contains("ev_explain_1"));
        var scenarioEvidence = new HashSet<String>();
        report.at("/scenarios/0/evidenceIds").forEach(node -> scenarioEvidence.add(node.asText()));
        assertTrue(scenarioEvidence.contains("ev_explain_1"));
    }
}
