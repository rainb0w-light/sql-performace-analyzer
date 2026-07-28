package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.analysis.ScenarioContextResolver;
import com.biz.sccba.sqlanalyzer.analysis.StatementReferenceResolver;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CostLevel;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRunPlanningSnapshotTest {

    @Test
    void completePlanAndContextRoundTripWithoutReplanning() throws Exception {
        var refs = new StatementReferenceResolver.References("demo.M", "find", "UPDATE",
                List.of("loan"), Map.of(), List.of());
        var input = PlannerInput.defaults(20).withContentHash("hash");
        var context = new ScenarioContextResolver.ContextBundle(input, refs, List.of(),
                List.of(), List.of(), List.of(), "kb@1", "snap_1");
        var stored = new PluginRunPlanningService.StoredRunPlan(
                "run", "session", "client", "artifact", "find", "ds",
                "project", "module", null, null, "public", "UPDATE", "fingerprint",
                new ScenarioEngine.PlanResult("demo.M", "find", List.of(), null),
                context, List.of("scn_main"), List.of(), CostLevel.LOW, true);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        byte[] bytes = mapper.writeValueAsBytes(stored);
        var restored = mapper.readValue(bytes,
                PluginRunPlanningService.StoredRunPlan.class);

        assertEquals("fingerprint", restored.contextFingerprint());
        assertEquals("kb@1", restored.context().knowledgeVersion());
        assertEquals("UPDATE", restored.statementType());
        assertTrue(restored.readOnly(), "DML plans remain static read-only analysis");
    }
}
