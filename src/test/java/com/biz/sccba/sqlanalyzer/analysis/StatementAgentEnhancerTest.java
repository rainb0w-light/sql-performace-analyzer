package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.agent.AgentRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementAgentEnhancerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void agentFailureIsRecordedButBaselineReportSurvives() throws Exception {
        AgentRuntime failing = request -> {
            throw new IllegalStateException("model unavailable");
        };
        StatementAgentEnhancer enhancer = enhancer(failing, true);

        String enhanced = enhancer.enhance("client_1", "session_1", "run_1",
                "dsp_1", baseline());

        var report = mapper.readTree(enhanced);
        assertEquals("FAILED", report.at("/agentEnhancement/status").asText());
        assertTrue(report.at("/agentEnhancement/error").asText().contains("model unavailable"));
        assertEquals("deterministic baseline", report.at("/summary/headline").asText());
        assertTrue(report.at("/limits/notes").toString().contains("AgentScope"));
    }

    @Test
    void disabledEnhancementDoesNotInvokeAgent() throws Exception {
        AgentRuntime shouldNotRun = request -> {
            throw new AssertionError("disabled enhancer invoked the agent");
        };
        StatementAgentEnhancer enhancer = enhancer(shouldNotRun, false);

        String enhanced = enhancer.enhance("client_1", "session_1", "run_1",
                "dsp_1", baseline());

        assertEquals("SKIPPED", mapper.readTree(enhanced)
                .at("/agentEnhancement/status").asText());
    }

    @Test
    void successfulEnhancementIsAdvisoryAndKeepsDeterministicFields() throws Exception {
        AgentRuntime agent = request -> new AgentRuntime.AgentOutput(
                true, "{\"summary\":\"EXPLAIN 显示索引命中\"}", request.sessionId());
        StatementAgentEnhancer enhancer = enhancer(agent, true);

        String enhanced = enhancer.enhance("client_1", "session_1", "run_1",
                "dsp_1", baseline());

        var report = mapper.readTree(enhanced);
        assertEquals("COMPLETED", report.at("/agentEnhancement/status").asText());
        assertTrue(report.at("/agentEnhancement/content").asText().contains("索引命中"));
        assertEquals("deterministic baseline", report.at("/summary/headline").asText());
    }

    private StatementAgentEnhancer enhancer(AgentRuntime runtime, boolean enabled) {
        var factory = new StaticListableBeanFactory(Map.of("agentRuntime", runtime));
        return new StatementAgentEnhancer(factory.getBeanProvider(AgentRuntime.class),
                mapper, enabled, "", 20_000);
    }

    private static String baseline() {
        return """
                {
                  "schemaVersion":"1.1",
                  "summary":{"headline":"deterministic baseline"},
                  "limits":{"notes":[]},
                  "audit":{"model":"deterministic-analysis"}
                }
                """;
    }
}
