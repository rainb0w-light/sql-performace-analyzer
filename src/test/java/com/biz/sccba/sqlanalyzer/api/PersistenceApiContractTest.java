package com.biz.sccba.sqlanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 0 contract baseline (Docker gate: RUN_POSTGRES_INTEGRATION_TESTS=true, runs in CI).
 *
 * Freezes the end-to-end resource API with persistence ON and worker OFF:
 * token apply -> session create/list -> message submit (Run QUEUED) -> SSE first event
 * (RUN_QUEUED, via Last-Event-ID-capable endpoint) -> recommendation listing and decision
 * validation (400s). Request/response field names are frozen for IDEA client compatibility.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.worker.enabled=false"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PersistenceApiContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("sql-analyzer.persistence.jdbc-url", postgres::getJdbcUrl);
        registry.add("sql-analyzer.persistence.username", postgres::getUsername);
        registry.add("sql-analyzer.persistence.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpRequest.Builder authed(String token, String path) {
        return HttpRequest.newBuilder(URI.create(url(path)))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    @Test
    void fullResourceApiFlow() throws Exception {
        // 1. Token apply is unauthenticated and returns client + accessToken.
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/client-tokens/apply")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"Contract Test\",\"clientType\":\"IDEA\",\"deviceId\":\"test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), apply.body());
        JsonNode applied = json.readTree(apply.body());
        String token = applied.path("accessToken").asText();
        assertFalse(token.isBlank(), "accessToken must be issued");

        // 2. Missing Authorization is a 400 (current contract; Problem Details from Phase 1).
        HttpResponse<String> noAuth = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/sessions")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"t\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, noAuth.statusCode());

        // 3. Session create/list with frozen field names.
        HttpResponse<String> created = http.send(authed(token, "/api/v1/sessions")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"contract session\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, created.statusCode(), created.body());
        String sessionId = json.readTree(created.body()).path("id").asText();
        assertTrue(sessionId.startsWith("session_"));

        HttpResponse<String> listed = http.send(authed(token, "/api/v1/sessions").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listed.statusCode());
        assertTrue(listed.body().contains(sessionId));

        // 4. Message submit returns runId/sessionId/status; worker OFF keeps the Run QUEUED.
        HttpResponse<String> submitted = http.send(authed(token, "/api/v1/sessions/" + sessionId + "/messages")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"content\":\"select 1\",\"messageType\":\"TEXT\",\"modelName\":\"\",\"artifactIds\":[],\"datasourceProfile\":{}}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, submitted.statusCode(), submitted.body());
        JsonNode submission = json.readTree(submitted.body());
        String runId = submission.path("runId").asText();
        assertTrue(runId.startsWith("run_"));
        assertEquals("QUEUED", submission.path("status").asText());
        assertEquals(sessionId, submission.path("sessionId").asText());

        HttpResponse<String> runs = http.send(authed(token, "/api/v1/sessions/" + sessionId + "/runs").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, runs.statusCode());
        assertTrue(runs.body().contains(runId));
        assertTrue(runs.body().contains("QUEUED"), "Run must stay QUEUED while worker is disabled");

        // 5. SSE endpoint streams the persisted RUN_QUEUED event (cursor-replayable).
        HttpResponse<java.io.InputStream> sse = http.send(authed(token, "/api/v1/runs/" + runId + "/events")
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, sse.statusCode());
        boolean sawRunQueued = false;
        long deadline = System.currentTimeMillis() + 10_000L;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(sse.body(), StandardCharsets.UTF_8))) {
            while (System.currentTimeMillis() < deadline) {
                if (!reader.ready()) {
                    Thread.sleep(50);
                    continue;
                }
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("event:") && line.contains("RUN_QUEUED")) {
                    sawRunQueued = true;
                    break;
                }
            }
        }
        assertTrue(sawRunQueued, "SSE stream must deliver the persisted RUN_QUEUED event");

        // 6. Recommendations list is empty but well-formed; decision validation returns 400.
        HttpResponse<String> recs = http.send(
                authed(token, "/api/v1/sessions/" + sessionId + "/recommendations").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, recs.statusCode());
        assertEquals(0, json.readTree(recs.body()).size());

        HttpResponse<String> badDecision = http.send(authed(token, "/api/v1/recommendations/rec_none/decision")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"decision\":\"MAYBE\",\"category\":\"IDEA\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, badDecision.statusCode());

        HttpResponse<String> rejectNoReason = http.send(authed(token, "/api/v1/recommendations/rec_none/decision")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"decision\":\"REJECTED\",\"category\":\"IDEA\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, rejectNoReason.statusCode());

        // 7. Run cancellation is idempotent-friendly: QUEUED run is cancellable.
        HttpResponse<String> cancel = http.send(authed(token, "/api/v1/runs/" + runId + "/cancel")
                        .POST(HttpRequest.BodyPublishers.ofString("")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cancel.statusCode());
        String cancelStatus = json.readTree(cancel.body()).path("status").asText();
        assertNotNull(cancelStatus);
        assertTrue("CANCELLED".equals(cancelStatus) || "NOT_CANCELLABLE".equals(cancelStatus),
                "cancel status must be CANCELLED or NOT_CANCELLABLE, got: " + cancelStatus);
    }
}
