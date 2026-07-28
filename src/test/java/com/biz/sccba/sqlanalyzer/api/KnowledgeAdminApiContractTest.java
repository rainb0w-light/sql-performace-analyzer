package com.biz.sccba.sqlanalyzer.api;

import com.biz.sccba.sqlanalyzer.knowledge.retrieval.Embedder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.persistence.jdbc-url=jdbc:h2:mem:knowledge_admin_api;DB_CLOSE_DELAY=-1",
        "sql-analyzer.persistence.username=sa",
        "sql-analyzer.persistence.password=",
        "sql-analyzer.worker.enabled=false"
})
@Import(KnowledgeAdminApiContractTest.DeterministicEmbeddingConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeAdminApiContractTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String adminToken;
    private String agentToken;

    @BeforeAll
    void issueTokens() throws Exception {
        adminToken = issue("KNOWLEDGE_ADMIN");
        agentToken = issue("IDEA");
    }

    @Test
    void uploadDraftPublishRetrieveAndAuditUseRealSharedPath() throws Exception {
        String boundary = "----spaKnowledgeBoundary";
        byte[] multipart = multipart(boundary, "sourceName", "Loan policy",
                "file", "loan.md", "text/markdown",
                "# Loan policy\n\nloan status includes ACTIVE and CLOSED.");
        HttpResponse<String> uploaded = send(HttpRequest.newBuilder(uri("/api/v1/admin/knowledge-sources/imports"))
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "upload-loan-v1")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart)).build());
        assertEquals(202, uploaded.statusCode(), uploaded.body());
        JsonNode upload = json.readTree(uploaded.body());
        assertEquals("READY", upload.path("status").asText());
        String versionId = upload.path("versionId").asText();

        HttpResponse<String> duplicate = send(HttpRequest.newBuilder(
                        uri("/api/v1/admin/knowledge-sources/imports"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "upload-loan-v1")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart)).build());
        assertEquals(202, duplicate.statusCode(), duplicate.body());
        assertEquals(versionId, json.readTree(duplicate.body()).path("versionId").asText());
        assertTrue(json.readTree(duplicate.body()).path("idempotent").asBoolean());

        HttpResponse<String> beforePublish = get(adminToken,
                "/api/v1/knowledge/search?q=" + encoded("loan status") + "&limit=5");
        assertEquals(200, beforePublish.statusCode());
        assertEquals(0, json.readTree(beforePublish.body()).path("results").size(),
                "READY drafts must not be retrievable");

        HttpResponse<String> published = send(HttpRequest.newBuilder(
                        uri("/api/v1/admin/knowledge-versions/" + versionId + "/publish"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "publish-loan-v1")
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(200, published.statusCode(), published.body());

        HttpResponse<String> afterPublish = get(adminToken,
                "/api/v1/knowledge/search?q=" + encoded("loan status") + "&limit=5");
        assertEquals(200, afterPublish.statusCode(), afterPublish.body());
        JsonNode result = json.readTree(afterPublish.body());
        assertTrue(result.path("results").size() > 0);
        assertEquals("chunk:0", result.path("results").path(0).path("locator").asText());
        assertTrue(result.path("durationMs").asLong() < 3_000,
                "fixed healthy H2 dataset must satisfy the sampling P95 target");

        HttpResponse<String> replay = send(HttpRequest.newBuilder(
                        uri("/api/v1/admin/knowledge-versions/" + versionId + "/publish"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "publish-loan-v1")
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(200, replay.statusCode(), replay.body());

        HttpResponse<String> logs = get(adminToken,
                "/api/v1/admin/knowledge-operations?page=0&size=100");
        assertEquals(200, logs.statusCode(), logs.body());
        assertTrue(logs.body().contains("\"operationType\":\"UPLOAD\""));
        assertTrue(logs.body().contains("\"operationType\":\"PUBLISH\""));
        assertTrue(logs.body().contains("\"operationType\":\"RETRIEVE\""));
        assertFalse(logs.body().contains("loan status"),
                "Query plaintext must never enter knowledge operation logs");
    }

    @Test
    void legacyPluginTokenDefaultsToAgentAndAdminErrorsUseProblemDetails() throws Exception {
        HttpResponse<String> forbidden = send(HttpRequest.newBuilder(
                        uri("/api/v1/admin/knowledge-sources"))
                .header("Authorization", "Bearer " + agentToken).GET().build());
        assertEquals(403, forbidden.statusCode(), forbidden.body());
        assertTrue(forbidden.headers().firstValue("content-type").orElse("")
                .startsWith("application/problem+json"));
        assertEquals("FORBIDDEN", json.readTree(forbidden.body()).path("code").asText());

        HttpResponse<String> agentSearch = get(agentToken,
                "/api/v1/knowledge/search?q=" + encoded("loan") + "&limit=5");
        assertEquals(200, agentSearch.statusCode(), agentSearch.body());
        assertEquals(0, json.readTree(agentSearch.body()).path("results").size(),
                "the agent's independent client tenant must not see admin knowledge");
    }

    @Test
    void webNavigationHasExactlyTheThreeTopLevelProducts() throws Exception {
        HttpResponse<String> page = send(HttpRequest.newBuilder(
                uri("/knowledge-admin/knowledge.html")).GET().build());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("知识库"));
        assertTrue(page.body().contains("抽检工作台"));
        assertTrue(page.body().contains("观测中心"));
        assertFalse(page.body().contains("分片配置"));
        assertFalse(page.body().contains("画像配置"));
    }

    private String issue(String clientType) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/client-tokens/apply"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"clientName\":\"knowledge-api\",\"clientType\":\"" + clientType
                                + "\",\"deviceId\":\"test\"}")).build());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("accessToken").asText();
    }

    private HttpResponse<String> get(String token, String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] multipart(String boundary, String textName, String textValue,
                                    String fileName, String actualFileName, String mediaType,
                                    String content) {
        String separator = "--" + boundary + "\r\n";
        String body = separator
                + "Content-Disposition: form-data; name=\"" + textName + "\"\r\n\r\n"
                + textValue + "\r\n" + separator
                + "Content-Disposition: form-data; name=\"" + fileName
                + "\"; filename=\"" + actualFileName + "\"\r\n"
                + "Content-Type: " + mediaType + "\r\n\r\n"
                + content + "\r\n--" + boundary + "--\r\n";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration
    static class DeterministicEmbeddingConfig {
        @Bean
        @Primary
        Embedder deterministicKnowledgeEmbedder() {
            return new Embedder() {
                @Override public double[] embed(String text) {
                    String normalized = text == null ? "" : text.toLowerCase();
                    return new double[] {
                            normalized.contains("loan") ? 1 : 0,
                            normalized.contains("status") ? 1 : 0,
                            1
                    };
                }
                @Override public int dimensions() { return 3; }
                @Override public String modelName() { return "deterministic-test"; }
            };
        }
    }
}
