package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.evidence.ReadOnlyEvidenceDao;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime.ParameterMappingView;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine.PlanResult;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionPlanCollectorTest {

    @Test
    void selectUsesPreparedArgumentsAndProducesExplainEvidence() {
        ProfilingRepository profiling = mock(ProfilingRepository.class);
        ReadOnlyEvidenceDao evidence = mock(ReadOnlyEvidenceDao.class);
        var profile = new DatasourceProfile("dsp_1", "client_1", "library", "MYSQL",
                "jdbc:mysql://localhost/library", "reader", "TARGET_PASSWORD", true, Instant.now());
        when(profiling.findProfile("dsp_1", "client_1")).thenReturn(java.util.Optional.of(profile));
        when(evidence.explain(eq("select * from loan where member_id = ?"), eq(List.of(42L)), any()))
                .thenReturn(new ReadOnlyEvidenceDao.Evidence(
                        "EXPLAIN_PLAN", true, "[{\"table\":\"loan\",\"type\":\"ref\"}]", null));

        var collector = collector(profiling, evidence,
                new MockEnvironment().withProperty("TARGET_PASSWORD", "secret"));
        var result = collector.collect("client_1", "dsp_1", "SELECT",
                plan("select * from loan where member_id = ?", "memberId", 42L));

        assertEquals(1, result.plans().size());
        assertEquals("scenario_main", result.plans().getFirst().scenarioId());
        assertTrue(result.plans().getFirst().evidenceId().startsWith("ev_explain_"));
        assertFalse(result.explainSkipped());
        assertTrue(result.missingPermissions().isEmpty());
        verify(evidence).explain(eq("select * from loan where member_id = ?"), eq(List.of(42L)),
                org.mockito.ArgumentMatchers.argThat(values ->
                        "jdbc:mysql://localhost/library".equals(values.get("jdbcUrl"))
                                && "reader".equals(values.get("username"))
                                && "secret".equals(values.get("password"))));
    }

    @Test
    void updateIsNeverExplainedAndCarriesVisibleReadOnlyReason() {
        ProfilingRepository profiling = mock(ProfilingRepository.class);
        ReadOnlyEvidenceDao evidence = mock(ReadOnlyEvidenceDao.class);
        var collector = collector(profiling, evidence, new MockEnvironment());

        var result = collector.collect("client_1", "dsp_1", "UPDATE",
                plan("update loan set status = ? where id = ?", "status", "RETURNED"));

        assertTrue(result.explainSkipped());
        assertTrue(result.plans().isEmpty());
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("UPDATE") && note.contains("静态")));
        verify(evidence, never()).explain(any(), any(), any());
    }

    @Test
    void unavailableTargetDegradesWithoutThrowing() {
        ProfilingRepository profiling = mock(ProfilingRepository.class);
        ReadOnlyEvidenceDao evidence = mock(ReadOnlyEvidenceDao.class);
        when(profiling.findProfile("dsp_missing", "client_1")).thenReturn(java.util.Optional.empty());
        var collector = collector(profiling, evidence, new MockEnvironment());

        var result = collector.collect("client_1", "dsp_missing", "SELECT",
                plan("select * from loan", null, null));

        assertTrue(result.explainSkipped());
        assertTrue(result.plans().isEmpty());
        assertTrue(result.missingPermissions().stream().anyMatch(message -> message.contains("数据源")));
        verify(evidence, never()).explain(any(), any(), any());
    }

    private static ExecutionPlanCollector collector(ProfilingRepository profiling,
                                                    ReadOnlyEvidenceDao evidence,
                                                    MockEnvironment environment) {
        var factory = new StaticListableBeanFactory(Map.of("readOnlyEvidenceDao", evidence));
        return new ExecutionPlanCollector(profiling,
                factory.getBeanProvider(ReadOnlyEvidenceDao.class), environment, true);
    }

    private static PlanResult plan(String sql, String property, Object value) {
        Map<String, Object> parameters = property == null ? Map.of() : Map.of(property, value);
        List<ParameterMappingView> mappings = property == null ? List.of()
                : List.of(new ParameterMappingView(property, "IN", value.getClass().getName(), null));
        var scenario = new ParameterScenario("scenario_main", "主路径", "主路径",
                ParameterSource.BOUNDARY_GENERATED, parameters, List.of(), List.of(), List.of("MAIN"),
                0.9, null, null, 1);
        var bound = new BoundScenario(scenario, sql, "fingerprint", mappings, Map.of(),
                List.of(), false, null, List.of("MAIN"));
        return new PlanResult("library.Mapper", "find", List.of(bound), null);
    }
}
