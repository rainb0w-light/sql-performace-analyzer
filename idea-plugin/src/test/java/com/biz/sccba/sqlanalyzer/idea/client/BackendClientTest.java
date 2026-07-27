package com.biz.sccba.sqlanalyzer.idea.client;

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

import static org.junit.Assert.*;

public class BackendClientTest {
    private HttpServer server;
    private final List<RequestRecord> requests = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v1/client-tokens/apply", exchange -> respond(exchange, 200,
                "{\"accessToken\":\"spa_test_token\"}"));
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
                "[{\"id\":\"dsp_test\",\"name\":\"library\",\"dialect\":\"H2\"}]"));
        server.createContext("/api/v1/mapper-statements/analyze", exchange -> respond(exchange, 202,
                "{\"sessionId\":\"session_analysis\",\"runId\":\"run_analysis\","
                        + "\"status\":\"QUEUED\",\"streamUrl\":\"/api/v1/agui/runs/run_analysis/stream\"}"));
        server.createContext("/api/v1/reports/report_test", exchange -> respond(exchange, 200,
                "{\"reportId\":\"report_test\",\"scenarios\":[]}"));
        server.createContext("/api/v1/runs/run_test/cancel", exchange -> respond(exchange, 200,
                "{\"runId\":\"run_test\",\"status\":\"CANCELLED\"}"));
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

        IllegalStateException error;
        try {
            client.recommendations("any");
            fail("expected HTTP error");
            return;
        } catch (IllegalStateException expected) {
            error = expected;
        }

        assertTrue(error.getMessage().contains("HTTP 401"));
        assertTrue(error.getMessage().contains("unauthorized"));
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

    private BackendClient client(String token) {
        return new BackendClient(baseUrl(), token);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        requests.add(new RequestRecord(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("X-Request-Id"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record RequestRecord(String method, String path, String authorization,
                                 String idempotencyKey, String requestId, String body) {}
}
