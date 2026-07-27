package com.biz.sccba.sqlanalyzer.agui;

import com.biz.sccba.sqlanalyzer.service.AgentScopeLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 2 contract (Docker gate: RUN_POSTGRES_INTEGRATION_TESTS=true, runs in CI).
 *
 * End-to-end AG-UI over SSE with a canned model (LLM layer mocked, everything else real:
 * Flyway, DAOs, shared HarnessAgent, PostgresDistributedStore, persist-first event pipeline):
 * - POST /api/v1/agui/runs streams standard AG-UI events (RUN_STARTED → TEXT_MESSAGE_* →
 *   RUN_FINISHED) with strictly increasing SSE ids;
 * - reconnecting with Last-Event-ID replays exactly the events after the cursor;
 * - the run settles COMPLETED and the assistant message is projected to conversation_message.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.worker.enabled=true",
        "sql-analyzer.worker.poll-delay-ms=100"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class AguiSseContractTest {

    static final String AGENT_REPLY = "ok from agent";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("sql-analyzer.persistence.jdbc-url", postgres::getJdbcUrl);
        registry.add("sql-analyzer.persistence.username", postgres::getUsername);
        registry.add("sql-analyzer.persistence.password", postgres::getPassword);
    }

    /** Canned model: one text reply, no tools. */
    static final class CannedModel implements Model {
        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.just(ChatResponse.builder()
                    .id("resp_" + UUID.randomUUID())
                    .content(List.of(TextBlock.builder().text(AGENT_REPLY).build()))
                    .usage(new ChatUsage(8, 4, 0.0))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "canned-model";
        }
    }

    @MockitoBean
    AgentScopeLlmService models;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    record SseEvent(String id, String type, String data) {}

    @Test
    void aguiRunStreamsPersistsAndResumes() throws Exception {
        when(models.getModel(any())).thenReturn(Optional.of(new CannedModel()));
        when(models.getDefaultModel()).thenReturn(Optional.of(new CannedModel()));

        // 1. Token + session via the frozen REST contracts.
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(uri("/api/v1/client-tokens/apply"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"AG-UI Contract\",\"clientType\":\"IDEA\",\"deviceId\":\"test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), apply.body());
        String token = json.readTree(apply.body()).path("accessToken").asText();

        HttpResponse<String> created = http.send(authed(token, "/api/v1/sessions")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"agui\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, created.statusCode(), created.body());
        String sessionId = json.readTree(created.body()).path("id").asText();

        // 2. Start the AG-UI run and consume the continuous SSE stream until RUN_FINISHED.
        // The client supplies the runId (frozen contract: Idempotency-Key = clientKey+threadId+nonce);
        // the AG-UI model requires it to be present.
        String requestedRunId = "run_" + UUID.randomUUID();
        String inputJson = "{\"threadId\":\"" + sessionId + "\",\"runId\":\"" + requestedRunId
                + "\",\"messages\":[{\"id\":\"m1\",\"role\":\"user\",\"content\":\"hello\"}]}";
        HttpResponse<java.io.InputStream> sse = http.send(authed(token, "/api/v1/agui/runs")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(inputJson))
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, sse.statusCode());
        List<SseEvent> events = readUntilTerminal(sse.body(), 30_000L);

        assertFalse(events.isEmpty(), "stream must deliver events");
        assertEquals("RUN_STARTED", events.get(0).type(), "first event must be RUN_STARTED");
        assertEquals("RUN_FINISHED", events.get(events.size() - 1).type(), "last event must be RUN_FINISHED");
        assertTrue(events.stream().anyMatch(e -> "TEXT_MESSAGE_CONTENT".equals(e.type())
                        && e.data().contains(AGENT_REPLY)),
                "text increments must carry the agent reply");

        // SSE ids are strictly increasing run_event ids (cursor-safe).
        long previous = 0;
        String runId = null;
        for (SseEvent e : events) {
            long id = Long.parseLong(e.id());
            assertTrue(id > previous, "SSE ids must strictly increase: " + e.id() + " after " + previous);
            previous = id;
            if (runId == null) {
                runId = json.readTree(e.data()).path("runId").asText(null);
            }
        }
        assertTrue(runId != null && runId.startsWith("run_"));
        assertEquals(requestedRunId, runId, "streamed events must carry the client-requested runId");
        String firstId = events.get(0).id();

        // 3. Resume with Last-Event-ID: replays only events after the cursor, ending at RUN_FINISHED.
        HttpResponse<java.io.InputStream> resumed = http.send(authed(token, "/api/v1/agui/runs/" + runId + "/stream")
                        .header("Accept", "text/event-stream")
                        .header("Last-Event-ID", firstId)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, resumed.statusCode());
        List<SseEvent> replay = readUntilTerminal(resumed.body(), 15_000L);
        assertFalse(replay.isEmpty());
        for (SseEvent e : replay) {
            assertTrue(Long.parseLong(e.id()) > Long.parseLong(firstId),
                    "replayed events must all be after the cursor");
        }
        assertEquals("RUN_FINISHED", replay.get(replay.size() - 1).type());

        // 4. Run settles COMPLETED and the assistant reply is projected into conversation messages.
        awaitRunStatus(token, sessionId, runId, "COMPLETED");
        HttpResponse<String> msgs = http.send(authed(token, "/api/v1/sessions/" + sessionId + "/messages")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, msgs.statusCode());
        boolean hasAssistantReply = false;
        for (JsonNode m : json.readTree(msgs.body())) {
            if ("ASSISTANT".equals(m.path("role").asText()) && m.path("content").asText().contains(AGENT_REPLY)) {
                hasAssistantReply = true;
            }
        }
        assertTrue(hasAssistantReply, "assistant message projection must contain the agent reply");

        // 5. Cancelling a finished run is a clean NOT_CANCELLABLE (no terminal duplication).
        HttpResponse<String> cancel = http.send(authed(token, "/api/v1/runs/" + runId + "/cancel")
                        .POST(HttpRequest.BodyPublishers.ofString("")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancel.statusCode());
        assertEquals("NOT_CANCELLABLE", json.readTree(cancel.body()).path("status").asText());
    }

    private List<SseEvent> readUntilTerminal(java.io.InputStream body, long timeoutMs) throws Exception {
        List<SseEvent> out = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String type = null;
            StringBuilder data = new StringBuilder();
            while (System.currentTimeMillis() < deadline) {
                if (!reader.ready()) {
                    Thread.sleep(25);
                    continue;
                }
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                } else if (line.startsWith("event:")) {
                    type = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                } else if (line.isEmpty() && type != null) {
                    out.add(new SseEvent(id, type, data.toString()));
                    boolean terminal = "RUN_FINISHED".equals(type);
                    id = null;
                    type = null;
                    data.setLength(0);
                    if (terminal) break;
                }
            }
        }
        return out;
    }

    private void awaitRunStatus(String token, String sessionId, String runId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> runs = http.send(authed(token, "/api/v1/sessions/" + sessionId + "/runs")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            for (JsonNode r : json.readTree(runs.body())) {
                if (runId.equals(r.path("id").asText()) && expected.equals(r.path("status").asText())) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("run " + runId + " did not reach " + expected + " in time");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest.Builder authed(String token, String path) {
        return HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }
}
