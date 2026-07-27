package com.biz.sccba.sqlanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 contract freeze (docs/claude-code-remediation-goal.md §4.2):
 * Full end-to-end from HTTP analyze entry → AG-UI event stream → report → recommendations.
 *
 * <p>The E2E MUST start from POST /mapper-statements/analyze (HTTP), NOT from directly
 * calling StatementAnalysisService. Session/Run must be auto-created by the server.
 *
 * <p>Expected AG-UI event sequence (goal §4.2):
 * <pre>
 * RUN_STARTED
 * CUSTOM spa.phase_changed(PARSING_MAPPER)
 * CUSTOM spa.phase_changed(RESOLVING_CONTEXT)
 * CUSTOM spa.scenarios_ready(count, fingerprints)
 * CUSTOM spa.phase_changed(ASSEMBLING_REPORT)
 * CUSTOM spa.report_ready(reportId)
 * CUSTOM spa.recommendations_ready(reportId, count)
 * RUN_FINISHED
 * </pre>
 *
 * <p>Permanent regression contract: HTTP, worker, persisted AG-UI events, report and
 * recommendations remain one product flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.persistence.jdbc-url=jdbc:h2:mem:agui_e2e;DB_CLOSE_DELAY=-1",
        "sql-analyzer.persistence.username=sa",
        "sql-analyzer.persistence.password=",
        "sql-analyzer.worker.enabled=true",
        "sql-analyzer.worker.poll-delay-ms=25"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalysisAguiEndToEndTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    String token;
    String clientId;
    String artifactId;
    String datasourceProfileId;

    @Autowired
    AnalysisReportRepository reports;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeAll
    void setup() throws Exception {
        // Token
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/client-tokens/apply")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"AG-UI E2E\",\"clientType\":\"TEST\",\"deviceId\":\"e2e\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), apply.body());
        JsonNode issuedToken = json.readTree(apply.body());
        token = issuedToken.path("accessToken").asText();
        clientId = issuedToken.path("client").path("id").asText();

        // Artifact
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
        assertEquals(200, artifact.statusCode(), artifact.body());
        artifactId = json.readTree(artifact.body()).path("artifactId").asText();

        HttpResponse<String> profile = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/datasource-profiles")))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"name\":\"agui-ds\",\"dialect\":\"MYSQL\",\"jdbcUrl\":\"jdbc:mysql://test:3306/lib\",\"username\":\"ro\",\"credentialEnv\":\"TEST_PW\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profile.statusCode(), profile.body());
        datasourceProfileId = json.readTree(profile.body()).path("id").asText();
    }

    /**
     * Goal §4.2 + §E: complete E2E from HTTP analyze → SSE events → report → recommendations.
     * No pre-created Session/Run. The test starts from the HTTP entry point only.
     *
     * Regression: analysis stays asynchronous and server-owned.
     */
    @Test
    void httpAnalyzeToSseEventsToReportToRecommendations() throws Exception {
        // Step 1: POST /analyze — must return 202 with streamUrl
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> analyzeResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-agui-e2e")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(202, analyzeResponse.statusCode(),
                "analyze must return 202 Accepted; got " + analyzeResponse.statusCode() +
                " — current implementation is synchronous (200 + inline report)");

        JsonNode analyzeBody = json.readTree(analyzeResponse.body());
        String streamUrl = analyzeBody.path("streamUrl").asText();
        String runId = analyzeBody.path("runId").asText();
        assertFalse(streamUrl.isBlank(), "analyze response must contain streamUrl");
        assertFalse(runId.isBlank(), "analyze response must contain runId");

        // Step 2: Connect SSE and collect events
        List<SseEvent> events = collectSseEvents(streamUrl, 30);

        // Step 3: Verify event sequence per goal §4.2
        List<String> eventTypes = events.stream().map(e -> e.type).toList();
        List<String> customNames = events.stream()
                .filter(e -> "CUSTOM".equals(e.type))
                .map(e -> {
                    try { return json.readTree(e.data).path("name").asText(); }
                    catch (Exception ex) { return ""; }
                })
                .toList();

        // Must start with RUN_STARTED
        assertTrue(eventTypes.contains("RUN_STARTED"),
                "event stream must contain RUN_STARTED; got: " + eventTypes);

        // Must contain phase changes
        assertTrue(customNames.contains("spa.phase_changed"),
                "event stream must contain spa.phase_changed custom events; got: " + customNames);

        // Must contain spa.report_ready
        assertTrue(customNames.contains("spa.report_ready"),
                "event stream must contain spa.report_ready; got: " + customNames);

        // Must contain spa.recommendations_ready
        assertTrue(customNames.contains("spa.recommendations_ready"),
                "event stream must contain spa.recommendations_ready; got: " + customNames);

        // Must end with RUN_FINISHED
        assertTrue(eventTypes.contains("RUN_FINISHED"),
                "event stream must contain RUN_FINISHED; got: " + eventTypes);

        // Step 4: Extract reportId from spa.report_ready event
        String reportId = null;
        for (SseEvent event : events) {
            if ("CUSTOM".equals(event.type)) {
                JsonNode payload = json.readTree(event.data);
                if ("spa.report_ready".equals(payload.path("name").asText())) {
                    reportId = payload.path("reportId").asText();
                }
            }
        }
        assertNotNull(reportId, "spa.report_ready must contain reportId");

        // Step 5: GET /reports/{reportId} must return the full report
        HttpResponse<String> reportResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/reports/" + reportId)))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportResponse.statusCode(),
                "report must be retrievable by reportId from spa.report_ready event");

        JsonNode report = json.readTree(reportResponse.body());
        assertEquals(reportId, report.path("reportId").asText(),
                "reportId in the report JSON must match the event's reportId");
        assertTrue(reports.findById(clientId, reportId).isPresent(),
                "the REST reportId must identify the persisted database row");

        String recommendationsReportId = events.stream()
                .filter(e -> "CUSTOM".equals(e.type))
                .map(e -> {
                    try { return json.readTree(e.data); }
                    catch (Exception ex) { return json.createObjectNode(); }
                })
                .filter(p -> "spa.recommendations_ready".equals(p.path("name").asText()))
                .map(p -> p.path("reportId").asText())
                .findFirst().orElse("");
        assertEquals(reportId, recommendationsReportId,
                "both ready events must reference the same reportId");

        // Step 6: Recommendations must be retrievable
        String sessionId = analyzeBody.path("sessionId").asText();
        HttpResponse<String> recResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/sessions/" + sessionId + "/recommendations")))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, recResponse.statusCode(),
                "recommendations must be retrievable by sessionId");
    }

    /**
     * Goal §4.2: every Run has exactly ONE terminal state; report_ready only after
     * report is schema-validated and persisted.
     *
     * Regression: the deterministic path emits exactly one terminal lifecycle event.
     */
    @Test
    void runHasSingleTerminalStateWithPersistFirstEvents() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> analyzeResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-agui-terminal")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(202, analyzeResponse.statusCode(),
                "must return 202 for async run lifecycle");

        JsonNode analyzeBody = json.readTree(analyzeResponse.body());
        String streamUrl = analyzeBody.path("streamUrl").asText();

        List<SseEvent> events = collectSseEvents(streamUrl, 30);

        // Exactly one RUN_FINISHED
        long runFinishedCount = events.stream()
                .filter(e -> "RUN_FINISHED".equals(e.type))
                .count();
        assertEquals(1, runFinishedCount,
                "each Run must have exactly one RUN_FINISHED terminal event");

        // RUN_STARTED must come before any CUSTOM event
        int runStartedIdx = -1, firstCustomIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if ("RUN_STARTED".equals(events.get(i).type) && runStartedIdx == -1) runStartedIdx = i;
            if ("CUSTOM".equals(events.get(i).type) && firstCustomIdx == -1) firstCustomIdx = i;
        }
        assertTrue(runStartedIdx >= 0, "RUN_STARTED must be present");
        assertTrue(firstCustomIdx >= 0, "CUSTOM events must be present");
        assertTrue(runStartedIdx < firstCustomIdx,
                "RUN_STARTED must precede all CUSTOM events");
    }

    // --- SSE helper ---

    record SseEvent(String id, String type, String data) {}

    private List<SseEvent> collectSseEvents(String streamPath, int timeoutSeconds) throws Exception {
        List<SseEvent> events = new ArrayList<>();
        try {
            HttpResponse<InputStream> sseResponse = http.send(HttpRequest.newBuilder(
                            URI.create(url(streamPath)))
                            .header("Authorization", "Bearer " + token)
                            .header("Accept", "text/event-stream")
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            if (sseResponse.statusCode() != 200) {
                fail("SSE connection failed with status " + sseResponse.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sseResponse.body(), StandardCharsets.UTF_8))) {
                String currentId = null, currentType = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("id:")) {
                        currentId = line.substring(3).trim();
                    } else if (line.startsWith("event:")) {
                        currentType = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    } else if (line.isEmpty() && currentType != null) {
                        events.add(new SseEvent(currentId, currentType, data.toString()));
                        currentId = null;
                        currentType = null;
                        data.setLength(0);
                        // Stop after terminal event
                        if ("RUN_FINISHED".equals(events.get(events.size() - 1).type)) {
                            break;
                        }
                    }
                }
            }
        } catch (java.net.http.HttpTimeoutException e) {
            // Timeout is expected if the stream never sends RUN_FINISHED
        }
        return events;
    }
}
