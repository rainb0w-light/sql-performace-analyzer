package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.domain.AgentRun;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CostLevel;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ExcludedScenario;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ScenarioConfirmation;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.IdempotencyRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class PluginRunLifecycleServiceTest {

    @Test
    void rejectsRequiredExclusionAndQueuesConfirmedSnapshot() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentJobRepository jobs = mock(AgentJobRepository.class);
        RunEventRepository events = mock(RunEventRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        MemoryIdempotency idempotency = new MemoryIdempotency();
        PluginRunPlanningService planning = mock(PluginRunPlanningService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(runs.belongsToClient("run_1", "client")).thenReturn(true);
        when(runs.findById("run_1")).thenReturn(Optional.of(new AgentRun(
                "run_1", "session", "AWAITING_CONFIRMATION", "", null,
                null, Instant.now(), null)));
        when(events.after("client", "run_1", 0)).thenReturn(List.of(
                new RunEventRepository.RunEvent(7, "run_1", "CUSTOM",
                        "{\"name\":\"spa.scenarios_ready\",\"planArtifactId\":\"artifact_plan\"}")));
        when(planning.read("client", "artifact_plan")).thenReturn(plan());
        var service = new PluginRunLifecycleService(runs, jobs, events, sessions,
                idempotency, planning, mapper);

        assertThrows(IllegalArgumentException.class, () -> service.confirm(
                "client", "run_1", "key_bad",
                new ScenarioConfirmation(List.of("scn_optional"),
                        List.of(new ExcludedScenario("scn_main", "cost")))));

        var result = service.confirm("client", "run_1", "key_ok",
                new ScenarioConfirmation(List.of("scn_main"),
                        List.of(new ExcludedScenario("scn_optional", "cost"))));

        assertEquals("QUEUED", result.status());
        assertEquals(result, service.confirm("client", "run_1", "key_ok",
                new ScenarioConfirmation(List.of("scn_main"),
                        List.of(new ExcludedScenario("scn_optional", "cost")))));
        verify(jobs).enqueue(anyString(), org.mockito.ArgumentMatchers.eq("run_1"),
                org.mockito.ArgumentMatchers.contains("\"planArtifactId\":\"artifact_plan\""));
        verify(jobs, times(1)).enqueue(anyString(),
                org.mockito.ArgumentMatchers.eq("run_1"), anyString());
        verify(runs).updateStatus("run_1", "QUEUED", null);
        assertEquals(1, idempotency.values.size());
    }

    private static PluginRunPlanningService.StoredRunPlan plan() {
        var main = scenario("scn_main", "业务主路径");
        var optional = scenario("scn_optional", "可选路径");
        return new PluginRunPlanningService.StoredRunPlan(
                "run_1", "session", "client", "mapper", "find", "ds",
                "p", "m", null, null, "public", "SELECT", "fp",
                new ScenarioEngine.PlanResult("demo.M", "find",
                        List.of(main, optional), null),
                null, List.of("scn_main"), List.of(), CostLevel.LOW, true);
    }

    private static BoundScenario scenario(String id, String name) {
        var scenario = new ParameterScenario(id, name, "", ParameterSource.RULE_INFERRED,
                Map.of(), List.of(), List.of(), List.of(), 1.0, null, null, 0);
        return new BoundScenario(scenario, "select 1", "fp_" + id,
                List.of(), Map.of(), List.of(), false, null, List.of());
    }

    private static final class MemoryIdempotency implements IdempotencyRepository {
        private final Map<String, Record> values = new LinkedHashMap<>();
        @Override public Optional<Record> find(String clientId, String idempotencyKey) {
            return Optional.ofNullable(values.get(clientId + "|" + idempotencyKey));
        }
        @Override public void save(Record record) {
            values.put(record.clientId() + "|" + record.idempotencyKey(), record);
        }
        @Override public int purgeExpired(Instant now) { return 0; }
    }
}
