package com.biz.sccba.sqlanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 contract freeze (docs/claude-code-remediation-goal.md §4.4):
 * IDEA Plugin consumer contract — proves the backend exposes ONLY the routes the Plugin
 * needs for the unified analysis flow.
 *
 * <p>Plugin analysis flow (goal §4.4):
 * <pre>
 * PSI → hash-dedup upload Mapper Artifact
 *     → POST /mapper-statements/analyze
 *     → use streamUrl to connect SSE
 *     → on spa.report_ready → GET Report
 *     → on spa.recommendations_ready → refresh Recommendations
 * </pre>
 *
 * <p>Plugin must NOT call:
 * <ul>
 *   <li>/mapper-statements/plan (removed; does not exist on backend)</li>
 *   <li>Client-side knowledge/profile/index/shard assembly</li>
 * </ul>
 *
 * <p>EXPECTED FAILURES against the current implementation:
 * <ul>
 *   <li>/analyze does not return streamUrl → Plugin cannot connect SSE from analyze response</li>
 *   <li>REST analysis and AG-UI SSE are disconnected paths</li>
 *   <li>Plugin code (BackendClient.planStatement) still references /plan</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.persistence.jdbc-url=jdbc:h2:mem:plugin_contract;DB_CLOSE_DELAY=-1",
        "sql-analyzer.persistence.username=sa",
        "sql-analyzer.persistence.password=",
        "sql-analyzer.worker.enabled=true",
        "sql-analyzer.worker.poll-delay-ms=25"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PluginBackendConsumerContractTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    String token;
    String artifactId;
    String datasourceProfileId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeAll
    void setup() throws Exception {
        // Apply for token
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/client-tokens/apply")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"Plugin Contract\",\"clientType\":\"IDEA\",\"deviceId\":\"idea-test\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), apply.body());
        token = json.readTree(apply.body()).path("accessToken").asText();

        // Upload mapper artifact (simulates Plugin hash-dedup upload)
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
                                "{\"name\":\"plugin-ds\",\"dialect\":\"MYSQL\",\"jdbcUrl\":\"jdbc:mysql://test:3306/lib\",\"username\":\"ro\",\"credentialEnv\":\"TEST_PW\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profile.statusCode(), profile.body());
        datasourceProfileId = json.readTree(profile.body()).path("id").asText();
    }

    /**
     * Goal §4.4: /mapper-statements/plan must NOT exist.
     *
     * This test PASSES against the current backend (the route doesn't exist).
     * However, the Plugin code (BackendClient.planStatement) still calls this route —
     * this is a Plugin-side contract violation documented for Phase 1.
     */
    @Test
    void planEndpointDoesNotExist() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/mapper-statements/plan")))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"statementId\":\"findOverdueLoans\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // /plan must not exist: 404 or 405
        assertTrue(response.statusCode() == 404 || response.statusCode() == 405,
                "/mapper-statements/plan must not exist; got " + response.statusCode());
    }

    /**
     * Goal §4.4: Plugin flow — POST /analyze → get streamUrl → connect SSE → report_ready.
     *
     * FAILS: current /analyze returns 200 with inline report, no streamUrl.
     * The Plugin cannot start an SSE connection from the analyze response.
     */
    @Test
    void analyzeResponseContainsStreamUrlForSseConnection() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-plugin-stream")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode responseBody = json.readTree(response.body());

        // The response MUST contain streamUrl for the Plugin to connect SSE
        assertTrue(responseBody.has("streamUrl"),
                "analyze response must contain streamUrl for Plugin SSE connection; " +
                "current response fields: " + fieldNames(responseBody) +
                " (the synchronous report response has no streamUrl)");

        String streamUrl = responseBody.path("streamUrl").asText();
        assertTrue(streamUrl.startsWith("/api/v1/agui/runs/"),
                "streamUrl must point to the AG-UI SSE endpoint");

        // The Plugin must be able to connect to the stream URL
        HttpResponse<String> sseResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url(streamUrl)))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, sseResponse.statusCode(),
                "SSE stream must be connectable at the returned streamUrl");
    }

    /**
     * Goal §4.4: after report_ready, GET /reports/{reportId} returns the full report.
     *
     * FAILS: the current analyze path returns the report inline (synchronous) and the
     * SSE/AG-UI path is separate. There is no connected flow where analyze → SSE → report GET.
     */
    @Test
    void reportIsRetrievableAfterAnalyze() throws Exception {
        // First, run analysis
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId
        ));

        HttpResponse<String> analyzeResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/mapper-statements/analyze")))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "phase0-plugin-report")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode analyzeBody = json.readTree(analyzeResponse.body());

        // In the target contract, analyze returns 202 with runId (not inline report)
        assertEquals(202, analyzeResponse.statusCode(),
                "analyze must be async (202) so Plugin can follow the SSE flow");

        String reportId = reportIdFromStream(analyzeBody.path("streamUrl").asText());
        assertFalse(reportId.isBlank(), "spa.report_ready must carry reportId");

        // The Plugin follows the frozen resource contract using the event's reportId.
        HttpResponse<String> reportResponse = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/reports/" + reportId)))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportResponse.statusCode(),
                "report must be retrievable by reportId after spa.report_ready");

        JsonNode report = json.readTree(reportResponse.body());
        assertEquals(reportId, report.path("reportId").asText(), "event and REST reportId must match");
        assertTrue(report.has("scenarios"), "report must contain scenarios");
    }

    /**
     * Goal §4.4: Plugin code must not reference /plan.
     *
     * This is a source-level contract check on the Plugin codebase.
     * FAILS: BackendClient.java contains planStatement() calling POST /mapper-statements/plan.
     */
    @Test
    void pluginSourceDoesNotCallPlanEndpoint() throws Exception {
        java.nio.file.Path pluginClient = java.nio.file.Path.of(
                "idea-plugin/src/main/java/com/biz/sccba/sqlanalyzer/idea/client/BackendClient.java");
        assertTrue(java.nio.file.Files.exists(pluginClient),
                "Plugin BackendClient.java must exist");

        String source = java.nio.file.Files.readString(pluginClient);

        assertFalse(source.contains("/mapper-statements/plan"),
                "Plugin BackendClient must NOT call /mapper-statements/plan; " +
                "the planStatement() method and its /plan URL must be removed (Goal §4.4)");
    }

    @Test
    void p1SuggestionsAndBoundSqlPreviewUseRealBackendContracts() throws Exception {
        int sessionsBefore = sessionCount();
        String suggestBody = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId,
                "projectId", "library",
                "moduleId", "library-dao"
        ));
        HttpResponse<String> suggest = authenticatedPost(
                "/api/v1/mapper-statements/default-parameters/suggest",
                "suggest-contract", suggestBody);
        assertEquals(200, suggest.statusCode(), suggest.body());
        JsonNode suggestions = json.readTree(suggest.body());
        assertTrue(suggestions.path("suggestionSetId").asText().startsWith("artifact_"));
        assertTrue(suggestions.path("nodes").size() >= 2);
        JsonNode member = null;
        var selections = json.createArrayNode();
        for (JsonNode node : suggestions.path("nodes")) {
            boolean selected = "memberId".equals(node.path("parameterPath").asText());
            if (selected) member = node;
            var selection = selections.addObject();
            selection.put("nodeId", node.path("nodeId").asText());
            selection.put("selected", selected);
            selection.putNull("collectionMode");
        }
        assertNotNull(member);
        var previewBody = json.createObjectNode();
        previewBody.put("suggestionSetId", suggestions.path("suggestionSetId").asText());
        previewBody.set("selections", selections);
        previewBody.set("parameters", json.readTree(
                "{\"memberId\":{\"type\":\"INTEGER\",\"value\":\"7\",\"values\":[],\"fields\":{}}}"));
        HttpResponse<String> preview = authenticatedPost(
                "/api/v1/mapper-statements/default-parameters/preview",
                "preview-contract", previewBody.toString());
        assertEquals(200, preview.statusCode(), preview.body());
        JsonNode bound = json.readTree(preview.body());
        assertTrue(bound.path("boundSql").asText().contains("l.member_id = ?"));
        assertTrue(bound.path("hitNodeIds").toString()
                .contains(member.path("nodeId").asText()));
        assertTrue(bound.path("redacted").asBoolean());
        assertEquals(sessionsBefore, sessionCount(),
                "suggest/preview must not create a Session or Run");
    }

    @Test
    void staticAnnotationIndexRejectsDynamicJavaExpressions() throws Exception {
        String javaSource = """
                package demo;
                interface AnnotatedMapper {
                  @org.apache.ibatis.annotations.Select({"select * from loan",
                    "where id = #{id}"})
                  Object find(long id);
                }
                """;
        HttpResponse<String> indexed = authenticatedPost(
                "/api/v1/artifacts/mybatis/annotation-index", "annotation-static",
                json.writeValueAsString(java.util.Map.of(
                        "javaContent", javaSource,
                        "namespace", "demo.AnnotatedMapper",
                        "methodName", "find")));
        assertEquals(200, indexed.statusCode(), indexed.body());
        assertTrue(json.readTree(indexed.body()).path("artifactId").asText()
                .startsWith("artifact_"));

        String unsupported = """
                interface BadMapper {
                  String SQL = "select 1";
                  @org.apache.ibatis.annotations.Select(SQL) Object find();
                }
                """;
        HttpResponse<String> rejected = authenticatedPost(
                "/api/v1/artifacts/mybatis/annotation-index", "annotation-unsupported",
                json.writeValueAsString(java.util.Map.of(
                        "javaContent", unsupported,
                        "namespace", "BadMapper",
                        "methodName", "find")));
        assertEquals(422, rejected.statusCode(), rejected.body());
        assertEquals("UNSUPPORTED", json.readTree(rejected.body()).path("code").asText());
    }

    @Test
    void reviewRunWaitsAndConfirmationContinuesTheSameRun() throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "artifactId", artifactId,
                "statementId", "findOverdueLoans",
                "datasourceProfileId", datasourceProfileId,
                "executionMode", "REVIEW",
                "costThreshold", "EXTREME",
                "projectId", "library-project",
                "moduleId", "library-dao"
        ));
        HttpResponse<String> analyze = authenticatedPost(
                "/api/v1/mapper-statements/analyze", "review-run", body);
        assertEquals(202, analyze.statusCode(), analyze.body());
        JsonNode handle = json.readTree(analyze.body());
        String runId = handle.path("runId").asText();
        String streamUrl = handle.path("streamUrl").asText();
        JsonNode matrix = planningMatrixFromStream(streamUrl);
        assertTrue(matrix.path("requiresConfirmation").asBoolean());
        assertTrue(matrix.path("scenarios").size() > 0);

        HttpResponse<String> status = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/runs/" + runId)))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals("AWAITING_CONFIRMATION",
                json.readTree(status.body()).path("status").asText());

        var included = json.createArrayNode();
        for (JsonNode scenario : matrix.path("scenarios")) {
            included.add(scenario.path("scenarioId").asText());
        }
        var confirmation = json.createObjectNode();
        confirmation.set("includedScenarioIds", included);
        confirmation.set("excludedScenarios", json.createArrayNode());
        HttpResponse<String> confirmed = authenticatedPost(
                "/api/v1/runs/" + runId + "/confirm", "confirm-" + runId,
                confirmation.toString());
        assertEquals(202, confirmed.statusCode(), confirmed.body());
        assertEquals(runId, json.readTree(confirmed.body()).path("runId").asText());

        String reportId = reportIdFromStream(streamUrl);
        assertFalse(reportId.isBlank());
        HttpResponse<String> completed = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/runs/" + runId)))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode completedBody = json.readTree(completed.body());
        assertEquals("COMPLETED", completedBody.path("status").asText());
        assertEquals(reportId, completedBody.path("reportId").asText());

        HttpResponse<String> history = http.send(HttpRequest.newBuilder(URI.create(url(
                        "/api/v1/reports?projectId=library-project&moduleId=library-dao"
                                + "&statement=findOverdueLoans&datasourceProfileId="
                                + datasourceProfileId + "&stale=false&page=0&size=10")))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, history.statusCode(), history.body());
        JsonNode historyBody = json.readTree(history.body());
        assertTrue(historyBody.path("totalElements").asInt() >= 1);
        assertEquals(reportId, historyBody.path("items").get(0).path("reportId").asText());
    }

    private static String fieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return String.join(", ", names);
    }

    private String reportIdFromStream(String streamPath) throws Exception {
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(url(streamPath)))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(30))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode(), "SSE stream must be connectable");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("event:")) event = line.substring(6).trim();
                else if (line.startsWith("data:")) data.append(line.substring(5).trim());
                else if (line.isEmpty() && event != null) {
                    if ("CUSTOM".equals(event)) {
                        JsonNode payload = json.readTree(data.toString());
                        if ("spa.report_ready".equals(payload.path("name").asText())) {
                            return payload.path("reportId").asText();
                        }
                    }
                    if ("RUN_FINISHED".equals(event)) break;
                    event = null;
                    data.setLength(0);
                }
            }
        }
        return "";
    }

    private HttpResponse<String> authenticatedPost(String path, String key, String body)
            throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url(path)))
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode planningMatrixFromStream(String streamPath) throws Exception {
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(
                        URI.create(url(streamPath)))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        JsonNode matrix = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("event:")) event = line.substring(6).trim();
                else if (line.startsWith("data:")) data.append(line.substring(5).trim());
                else if (line.isEmpty() && event != null) {
                    if ("CUSTOM".equals(event)) {
                        JsonNode payload = json.readTree(data.toString());
                        if ("spa.scenarios_ready".equals(payload.path("name").asText())) {
                            matrix = payload;
                        }
                        if ("spa.awaiting_confirmation".equals(payload.path("name").asText())) {
                            break;
                        }
                    }
                    event = null;
                    data.setLength(0);
                }
            }
        }
        assertNotNull(matrix, "spa.scenarios_ready must precede awaiting confirmation");
        return matrix;
    }

    private int sessionCount() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/sessions")))
                        .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).size();
    }
}
