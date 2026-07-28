package com.biz.sccba.sqlanalyzer.idea.client;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BackendClientTest {
    private HttpServer server;
    private final List<RequestRecord> requests = new ArrayList<>();
    private final AtomicInteger suggestionAttempts = new AtomicInteger();
    private volatile boolean multipleProfiles;
    private volatile boolean validationFailure;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v1/client-tokens/apply", exchange -> respond(exchange, 200,
                "{\"accessToken\":\"spa_test_token\"}"));
        server.createContext("/api/v1/client", exchange -> respond(exchange, 200,
                "{\"id\":\"client_1\",\"name\":\"idea\",\"expiresAt\":\"2026-08-01T00:00:00Z\"}"));
        server.createContext("/api/v1/sessions", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) {
                respond(exchange, 200, "{\"id\":\"session_test\"}");
            } else {
                respond(exchange, 404, "not found");
            }
        });
        server.createContext("/api/v1/sessions/session_test/messages", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) {
                respond(exchange, 200, "{\"runId\":\"run_test\",\"sessionId\":\"session_test\",\"status\":\"QUEUED\"}");
            } else {
                respond(exchange, 200, "[{\"role\":\"USER\",\"content\":\"select 1\"}]");
            }
        });
        server.createContext("/api/v1/artifacts/mybatis/index", exchange -> respond(exchange, 200,
                "{\"artifactId\":\"artifact_test\",\"documentId\":\"document_test\"}"));
        server.createContext("/api/v1/datasource-profiles", exchange -> respond(exchange, 200,
                multipleProfiles
                        ? "[{\"id\":\"dsp_test\",\"name\":\"library\",\"dialect\":\"H2\"},"
                        + "{\"id\":\"dsp_other\",\"name\":\"archive\",\"dialect\":\"H2\"}]"
                        : "[{\"id\":\"dsp_test\",\"name\":\"library\",\"dialect\":\"H2\"}]"));
        server.createContext("/api/v1/mapper-statements/analyze", exchange -> respond(exchange, 202,
                "{\"sessionId\":\"session_analysis\",\"runId\":\"run_analysis\","
                        + "\"status\":\"QUEUED\",\"streamUrl\":\"/api/v1/agui/runs/run_analysis/stream\"}"));
        server.createContext("/api/v1/mapper-statements/default-parameters/suggest", exchange -> {
            int attempt = suggestionAttempts.incrementAndGet();
            if (validationFailure) {
                respond(exchange, 400, "{\"code\":\"VALIDATION_FAILED\",\"retryable\":false,"
                        + "\"detail\":\"bad\",\"errors\":[{\"field\":\"statementId\",\"message\":\"required\"}]}");
                return;
            }
            if (attempt == 1) {
                respond(exchange, 429, "{\"code\":\"RATE_LIMITED\",\"retryable\":true,\"detail\":\"slow\"}");
            } else {
                respond(exchange, 200, """
                        {"suggestionSetId":"suggest_1","contextVersion":"ctx_1","nodes":[{
                          "nodeId":"if_1","kind":"IF","testExpression":"status != null",
                          "parameterPath":"status","parameterType":"java.lang.String",
                          "category":"FILTER","categorySource":"SERVER_EXPLAINED",
                          "assignable":true,"suggestedEnabled":true,
                          "suggestedValue":{"type":"STRING","value":"ACTIVE","values":[],"fields":{}},
                          "source":"PROFILE_SNAPSHOT","version":"snap_1","locator":"loan.status/top-k/0",
                          "confidence":0.9,"reason":"Top-K"}]}
                        """);
            }
        });
        server.createContext("/api/v1/mapper-statements/default-parameters/preview", exchange -> respond(exchange, 200,
                """
                {"boundSql":"SELECT * FROM loan WHERE status = ?","hitNodeIds":["if_1"],
                 "parameterMappings":[{"property":"status","jdbcType":"VARCHAR"}],
                 "validationErrors":[],"redacted":true}
                """));
        server.createContext("/api/v1/mapper-statements/transient-rules/preview", exchange -> respond(exchange, 200,
                """
                {"addedScenarioIds":["scn_dollar"],"removedScenarioIds":[],
                 "addedCoverageGoals":["DOLLAR_WHITELIST"],"removedCoverageGoals":[],
                 "guardChanges":[],"costBefore":"MEDIUM","costAfter":"MEDIUM","fieldErrors":[]}
                """));
        server.createContext("/api/v1/reports/report_test", exchange -> respond(exchange, 200,
                "{\"reportId\":\"report_test\",\"scenarios\":[]}"));
        server.createContext("/api/v1/runs/run_test/cancel", exchange -> respond(exchange, 200,
                "{\"runId\":\"run_test\",\"status\":\"CANCELLED\"}"));
        server.createContext("/api/v1/runs/run_analysis", exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 200, "{\"runId\":\"run_analysis\",\"status\":\"RUNNING\","
                        + "\"lastEventId\":\"42\",\"reportId\":null,\"cancellable\":true}");
            } else {
                respond(exchange, 404, "not found");
            }
        });
        server.createContext("/api/v1/runs/run_analysis/confirm", exchange -> respond(exchange, 204, ""));
        server.createContext("/api/v1/reports", exchange -> respond(exchange, 200,
                "{\"items\":[],\"page\":0,\"size\":10,\"total\":0}"));
        server.createContext("/api/v1/sessions/session_test/runs", exchange -> respond(exchange, 200,
                "[{\"id\":\"run_test\",\"status\":\"COMPLETED\"}]"));
        server.createContext("/api/v1/sessions/session_test/recommendations", exchange -> respond(exchange, 200,
                "[{\"id\":\"rec_test\",\"status\":\"PROPOSED\"}]"));
        server.createContext("/api/v1/recommendations/rec_test/decision", exchange -> respond(exchange, 204, ""));
        server.createContext("/api/v1/fail", exchange -> respond(exchange, 401, "unauthorized"));
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void applyTokenDoesNotSendAuthorizationAndParsesToken() throws Exception {
        BackendClient client = client("");

        assertEquals("spa_test_token", client.applyToken("idea\"client"));
        RequestRecord request = requests.get(0);
        assertEquals("POST", request.method());
        assertEquals("/api/v1/client-tokens/apply", request.path());
        assertNull(request.authorization());
        assertTrue(request.body().contains("idea\\\"client"));
        assertNotNull("POST must carry an Idempotency-Key", request.idempotencyKey());
        assertNotNull("every request must carry an X-Request-Id", request.requestId());
    }

    @Test
    public void authenticatedWorkflowSendsBearerAndEscapesPayload() throws Exception {
        BackendClient client = client("spa_saved");

        assertEquals("session_test", client.createSession("SQL 分析"));
        assertEquals("run_test", client.submitMessage("session_test", "select \"id\"\nfrom orders"));
        assertEquals("CANCELLED", client.cancelRun("run_test"));

        assertEquals("Bearer spa_saved", requests.get(0).authorization());
        assertEquals("Bearer spa_saved", requests.get(1).authorization());
        assertEquals("Bearer spa_saved", requests.get(2).authorization());
        assertTrue(requests.get(1).body().contains("select \\\"id\\\"\\nfrom orders"));
        assertEquals("artifact_test", client.indexMyBatisMapper("session_test", "<mapper namespace=\"x\">\n</mapper>", "x"));
        assertTrue(client.runs("session_test").contains("run_test"));
        assertTrue(client.messages("session_test").contains("select 1"));
        assertTrue(client.recommendations("session_test").contains("rec_test"));
        client.decideRecommendation("rec_test", "REJECTED", "NOT_APPLICABLE", "业务约束不允许");
        assertEquals("POST", requests.get(requests.size() - 1).method());
        assertTrue(requests.get(requests.size() - 1).body().contains("业务约束不允许"));
    }

    @Test
    public void non2xxResponseContainsStatusAndBody() throws Exception {
        BackendClient client = new BackendClient(baseUrl() + "/api/v1/fail", "spa_saved");

        BackendClient.BackendException error;
        try {
            client.recommendations("any");
            fail("expected HTTP error");
            return;
        } catch (BackendClient.BackendException expected) {
            error = expected;
        }

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertTrue(error.getMessage().contains("unauthorized"));
        assertTrue(error.authenticationRequired());
    }

    @Test
    public void canonicalStatementAnalysisResolvesSingleDatasourceAndParsesHandle() throws Exception {
        BackendClient client = client("spa_saved");

        assertEquals("dsp_test", client.resolveDatasourceProfile(""));
        BackendClient.AnalysisHandle handle = client.analyzeStatement(
                "artifact_test", "findOverdueLoans", "dsp_test",
                "project_1", "library-module", "");

        assertEquals("session_analysis", handle.sessionId());
        assertEquals("run_analysis", handle.runId());
        assertEquals("QUEUED", handle.status());
        assertEquals("/api/v1/agui/runs/run_analysis/stream", handle.streamUrl());
        RequestRecord analyze = requests.stream()
                .filter(request -> request.path().equals("/api/v1/mapper-statements/analyze"))
                .findFirst().orElseThrow();
        assertEquals("POST", analyze.method());
        assertEquals("Bearer spa_saved", analyze.authorization());
        assertNotNull(analyze.idempotencyKey());
        assertTrue(analyze.body().contains("\"artifactId\":\"artifact_test\""));
        assertTrue(analyze.body().contains("\"datasourceProfileId\":\"dsp_test\""));
        assertFalse("blank sessionId must be omitted so the server can create it",
                analyze.body().contains("\"sessionId\""));

        assertTrue(client.report("report_test").contains("\"reportId\":\"report_test\""));
    }

    @Test
    public void p1ContractsParseAndRetryWithSameIdempotencyKey() throws Exception {
        BackendClient client = new BackendClient(baseUrl(), "spa_saved",
                new BackendClient.RetryPolicy(3, 0, 0, millis -> {}));
        SuggestionSet suggestions = client.suggestDefaultParameters(
                new SuggestionRequest("artifact_test", "findOverdueLoans", "dsp_test",
                        "project_1", "library-module", "hash"));
        assertEquals("suggest_1", suggestions.suggestionSetId());
        assertEquals("if_1", suggestions.nodes().get(0).nodeId());
        List<RequestRecord> suggestionRequests = requests.stream()
                .filter(request -> request.path().endsWith("/default-parameters/suggest")).toList();
        assertEquals(2, suggestionRequests.size());
        assertEquals("idempotency key must be reused across 429 retry",
                suggestionRequests.get(0).idempotencyKey(), suggestionRequests.get(1).idempotencyKey());

        MainScenario main = new MainScenario("suggest_1",
                List.of(new NodeSelection("if_1", true, null)),
                java.util.Map.of("status", TypedValue.scalar(ValueType.STRING, "ACTIVE")));
        BoundSqlPreview preview = client.previewBoundSql(
                new BoundSqlPreviewRequest(main.suggestionSetId(), main.selections(), main.parameters()));
        assertTrue(preview.redacted());
        assertTrue(preview.boundSql().contains("status = ?"));
        assertEquals(List.of("if_1"), preview.hitNodeIds());

        TransientRuleImpact impact = client.previewTransientRules(java.util.Map.of(
                "rules", List.of(new TransientRule("tmp_1", RuleKind.ALLOWED_VALUES,
                        "orderBy", "IN", List.of(TypedValue.scalar(ValueType.STRING, "due_at"))))));
        assertEquals(List.of("scn_dollar"), impact.addedScenarioIds());

        BackendClient.RunStatus run = client.runStatus("run_analysis");
        assertEquals("42", run.lastEventId());
        client.confirmRun("run_analysis",
                new ScenarioConfirmation(List.of("scn_main"), List.of()), "confirm_key");
        assertTrue(client.reports(new BackendClient.HistoryFilter(
                "project_1", "library-module", "findOverdue", "", "HIGH",
                "2026-07-01T00:00:00Z", "2026-08-01T00:00:00Z", false, 0, 10))
                .contains("\"total\":0"));
        assertEquals("2026-08-01T00:00:00Z", client.clientStatus().expiresAt());
    }

    @Test
    public void datasourceResolutionHonorsTemporaryModuleProjectAndAmbiguity() throws Exception {
        BackendClient client = client("spa_saved");
        BackendClient.DatasourceResolution resolution =
                client.resolveDatasource("dsp_test", "module_default", "project_default");
        assertEquals(BackendClient.DatasourceResolutionStatus.RESOLVED, resolution.status());
        assertEquals("STATEMENT_TEMPORARY", resolution.bindingSource());
        assertEquals("dsp_test", resolution.selected().id());

        multipleProfiles = true;
        BackendClient.DatasourceResolution ambiguous = client.resolveDatasource("", "", "");
        assertEquals(BackendClient.DatasourceResolutionStatus.AMBIGUOUS, ambiguous.status());
        assertEquals(2, ambiguous.candidates().size());
        BackendClient.DatasourceResolution module = client.resolveDatasource("", "dsp_other", "dsp_test");
        assertEquals("dsp_other", module.selected().id());
        assertEquals("MODULE_DEFAULT", module.bindingSource());
    }

    @Test
    public void validationFailureIsNotRetriedAndKeepsFieldErrors() throws Exception {
        validationFailure = true;
        BackendClient client = new BackendClient(baseUrl(), "spa_saved",
                new BackendClient.RetryPolicy(4, 0, 0, millis -> {}));
        try {
            client.suggestDefaultParameters(new SuggestionRequest(
                    "a", "s", "d", "p", "m", "h"));
            fail("expected validation failure");
        } catch (BackendClient.BackendException expected) {
            assertEquals("VALIDATION_FAILED", expected.code());
            assertFalse(expected.retryable());
            assertEquals("statementId", expected.fieldProblems().get(0).field());
        }
        assertEquals(1, suggestionAttempts.get());
    }

    private BackendClient client(String token) {
        return new BackendClient(baseUrl(), token);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        synchronized (requests) {
            requests.add(new RequestRecord(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("X-Request-Id"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record RequestRecord(String method, String path, String authorization,
                                 String idempotencyKey, String requestId, String body) {}
}
