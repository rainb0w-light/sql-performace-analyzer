package com.biz.sccba.sqlanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 contract freeze (docs/claude-code-remediation-goal.md §4.1):
 * POST /api/v1/mapper-statements/analyze is the UNIQUE statement analysis entry point.
 *
 * <p>Contract requirements:
 * <ul>
 *   <li>Returns 202 Accepted (not 200 with inline report)</li>
 *   <li>Response: {sessionId, runId, status:"QUEUED", streamUrl}</li>
 *   <li>Server atomically creates Session + Run + Job; client does NOT pre-create them</li>
 *   <li>Request carries datasourceProfileId; server validates ownership</li>
 *   <li>Wrong datasource ownership → 404 (not 400)</li>
 *   <li>Missing/invalid Bearer token → 401 (RFC 9457)</li>
 * </ul>
 *
 * <p>These are permanent regression contracts for the canonical asynchronous command.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.persistence.jdbc-url=jdbc:h2:mem:api_contract;DB_CLOSE_DELAY=-1",
        "sql-analyzer.persistence.username=sa",
        "sql-analyzer.persistence.password=",
        "sql-analyzer.worker.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementAnalysisApiContractTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    String token;
    String clientId;
    String artifactId;
    String datasourceProfileId;

    @Autowired
    RunEventRepository events;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeAll
    void setup() throws Exception {
        // 1. Apply for a token
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/client-tokens/apply")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"API Contract Test\",\"clientType\":\"TEST\",\"deviceId\":\"test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), "token apply: " + apply.body());
        JsonNode applyBody = json.readTree(apply.body());
        token = applyBody.path("accessToken").asText();
        clientId = applyBody.path("client").path("id").asText();
        assertFalse(token.isBlank(), "accessToken must not be blank");

        // 2. Upload mapper artifact
        byte[] mapperXml;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/library/mapper/LoanMapper.xml")) {
            mapperXml = in.readAllBytes();
        }
        String xmlContent = new String(mapperXml, StandardCharsets.UTF_8);
        HttpResponse<String> artifact = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/artifacts/mybatis/index")))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                json.writeValueAsString(java.util.Map.of("xmlContent", xmlContent))))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, artifact.statusCode(), "artifact upload: " + artifact.body());
        artifactId = json.readTree(artifact.body()).path("artifactId").asText();

        // 3. Create a datasource profile
        HttpResponse<String> profile = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/datasource-profiles")))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"name\":\"test-ds\",\"dialect\":\"MYSQL\",\"jdbcUrl\":\"jdbc:mysql://test:3306/lib\",\"username\":\"ro\",\"credentialEnv\":\"TEST_PW\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profile.statusCode(), "datasource profile: " + profile.body());
        datasourceProfileId = json.readTree(profile.body()).path("id").asText();
    }

    /**
     * Goal §4.1: /analyze returns 202 with {sessionId, runId, status:"QUEUED", streamUrl}.
     *
     * Regression: the command must never return an inline synchronous report.
     */
    @Test
    void analyzeReturns202WithQueuedStatusAndStreamUrl() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-api-queued")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Goal: 202 Accepted
        assertEquals(202, response.statusCode(),
                "POST /analyze must return 202 Accepted (async); current returns " +
                response.statusCode() + ": " + truncate(response.body()));

        JsonNode responseBody = json.readTree(response.body());

        // Must contain the async command response fields
        assertTrue(responseBody.has("sessionId") && !responseBody.path("sessionId").asText().isBlank(),
                "response must contain auto-created sessionId");
        assertTrue(responseBody.has("runId") && !responseBody.path("runId").asText().isBlank(),
                "response must contain auto-created runId");
        assertEquals("QUEUED", responseBody.path("status").asText(),
                "response status must be QUEUED");
        assertTrue(responseBody.has("streamUrl") && responseBody.path("streamUrl").asText().contains("/agui/"),
                "response must contain streamUrl pointing to the AG-UI SSE endpoint");
    }

    /**
     * Goal §4.1: server atomically creates Session + Run; client does NOT pre-create.
     *
     * Regression: a client never has to pre-create the run graph.
     */
    @Test
    void analyzeAutoCreatesSessionAndRunWithoutClientPreCreation() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
                // NOTE: no sessionId, no runId — server must create them
        ));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-api-auto-create")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Must succeed (202) — not 500 from FK violation
        assertTrue(response.statusCode() == 202,
                "POST /analyze without pre-created Session/Run must return 202; " +
                "got " + response.statusCode() + ": " + truncate(response.body()) +
                " (likely FK violation: run_event.run_id references agent_run.id " +
                "but no agent_run row is created by the current controller)");

        JsonNode responseBody = json.readTree(response.body());
        String sessionId = responseBody.path("sessionId").asText();
        String runId = responseBody.path("runId").asText();

        // The auto-created session must be retrievable
        HttpResponse<String> sessionCheck = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/sessions/" + sessionId)))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, sessionCheck.statusCode(),
                "auto-created session must be retrievable");
    }

    /**
     * Goal §4.1: datasourceProfileId not belonging to the client → 404.
     *
     * Regression: datasource ownership is part of command validation.
     */
    @Test
    void analyzeWithForeignDatasourceProfileReturns404() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", "dsp_not_owned_by_this_client"
        ));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-api-foreign-datasource")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // Goal: 404 Not Found (cross-tenant resource access appears as 404 per §4.3)
        assertEquals(404, response.statusCode(),
                "datasourceProfileId not owned by the client must return 404; " +
                "got " + response.statusCode() + ": " + truncate(response.body()) +
                " (current AnalyzeRequest has no datasourceProfileId field, " +
                "so this validation does not exist)");
    }

    /**
     * Goal §C: missing/invalid Bearer token → 401 RFC 9457 Problem Details.
     *
     * Regression: authentication failures use RFC 9457 and 401.
     */
    @Test
    void missingBearerTokenReturns401() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        // No Authorization header at all
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Idempotency-Key", "phase0-api-missing-auth")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(),
                "missing Authorization header must return 401; got " + response.statusCode() +
                " (current code throws IllegalArgumentException → 400, " +
                "and MissingRequestHeaderException has no handler)");
    }

    /**
     * Goal §C: invalid Bearer token → 401 RFC 9457 Problem Details.
     *
     * Regression: invalid tokens never collapse into generic request validation.
     */
    @Test
    void invalidBearerTokenReturns401() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer invalid_token_12345")
                        .header("Idempotency-Key", "phase0-api-invalid-auth")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(),
                "invalid Bearer token must return 401 RFC 9457; got " + response.statusCode() +
                " (current: IllegalArgumentException → 400)");
        if (response.statusCode() == 401) {
            JsonNode problem = json.readTree(response.body());
            assertEquals("application/problem+json",
                    response.headers().firstValue("Content-Type").orElse("").split(";")[0],
                    "401 must use RFC 9457 Problem Details content type");
        }
    }

    @Test
    void identicalIdempotencyReplayReturnsTheOriginalRun() throws Exception {
        String body = analysisBody("findOverdueLoans");
        String key = "idem-replay-" + System.nanoTime();

        HttpResponse<String> first = analyze(body, key);
        HttpResponse<String> replay = analyze(body, key);

        assertEquals(202, first.statusCode());
        assertEquals(202, replay.statusCode());
        assertEquals(json.readTree(first.body()).path("runId").asText(),
                json.readTree(replay.body()).path("runId").asText());
        assertEquals(json.readTree(first.body()).path("sessionId").asText(),
                json.readTree(replay.body()).path("sessionId").asText());
    }

    @Test
    void reusingIdempotencyKeyForDifferentPayloadReturns409Problem() throws Exception {
        String key = "idem-conflict-" + System.nanoTime();
        assertEquals(202, analyze(analysisBody("findOverdueLoans"), key).statusCode());

        HttpResponse<String> conflict = analyze(analysisBody("findLoansByMember"), key);

        assertEquals(409, conflict.statusCode());
        JsonNode problem = json.readTree(conflict.body());
        assertEquals("IDEMPOTENCY_CONFLICT", problem.path("code").asText());
        assertEquals(false, problem.path("retryable").asBoolean());
    }

    @Test
    void concurrentIdenticalRequestsCreateOnlyOneRun() throws Exception {
        String body = analysisBody("findOverdueLoans");
        String key = "idem-concurrent-" + System.nanoTime();

        CompletableFuture<HttpResponse<String>> first =
                CompletableFuture.supplyAsync(() -> uncheckedAnalyze(body, key));
        CompletableFuture<HttpResponse<String>> second =
                CompletableFuture.supplyAsync(() -> uncheckedAnalyze(body, key));
        HttpResponse<String> a = first.get();
        HttpResponse<String> b = second.get();

        assertEquals(202, a.statusCode(), a.body());
        assertEquals(202, b.statusCode(), b.body());
        assertEquals(json.readTree(a.body()).path("runId").asText(),
                json.readTree(b.body()).path("runId").asText());
    }

    @Test
    void cancellingQueuedAnalysisEmitsStandardTerminalEvents() throws Exception {
        HttpResponse<String> started = analyze(
                analysisBody("findOverdueLoans"), "idem-cancel-" + System.nanoTime());
        String runId = json.readTree(started.body()).path("runId").asText();

        HttpResponse<String> cancelled = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/runs/" + runId + "/cancel")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "cancel-" + runId)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, cancelled.statusCode(), cancelled.body());
        assertEquals("CANCELLED", json.readTree(cancelled.body()).path("status").asText());
        var persisted = events.after(clientId, runId, 0);
        assertEquals(1, persisted.stream().filter(e -> "RUN_ERROR".equals(e.type())).count());
        assertEquals(1, persisted.stream().filter(e -> "RUN_FINISHED".equals(e.type())).count());
        assertTrue(persisted.stream().anyMatch(e -> e.payloadJson().contains("\"code\":\"CANCELLED\"")));
    }

    private String analysisBody(String statementId) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", statementId,
                "datasourceProfileId", datasourceProfileId));
    }

    private HttpResponse<String> analyze(String body, String idempotencyKey) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uncheckedAnalyze(String body, String key) {
        try {
            return analyze(body, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String truncate(String s) {
        return s == null ? "null" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
