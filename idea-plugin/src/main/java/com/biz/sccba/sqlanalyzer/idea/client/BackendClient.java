package com.biz.sccba.sqlanalyzer.idea.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class BackendClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String token;

    public BackendClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    public String applyToken(String clientName) throws Exception {
        String body = "{\"clientName\":\"" + json(clientName) + "\",\"clientType\":\"IDEA\",\"deviceId\":\"idea\"}";
        JsonObject json = request("POST", "/api/v1/client-tokens/apply", body, false);
        return json.get("accessToken").getAsString();
    }

    public String createSession(String title) throws Exception {
        JsonObject json = request("POST", "/api/v1/sessions", "{\"title\":\"" + json(title) + "\"}", true);
        return json.get("id").getAsString();
    }

    public String submitMessage(String sessionId, String content) throws Exception {
        JsonObject json = request("POST", "/api/v1/sessions/" + sessionId + "/messages",
                "{\"content\":\"" + json(content) + "\",\"messageType\":\"TEXT\"}", true);
        return json.get("runId").getAsString();
    }

    public String indexMyBatisMapper(String sessionId, String xmlContent, String namespace) throws Exception {
        String body = "{\"sessionId\":\"" + json(sessionId) + "\",\"namespace\":\"" + json(namespace)
                + "\",\"xmlContent\":\"" + json(xmlContent) + "\"}";
        JsonObject result = request("POST", "/api/v1/artifacts/mybatis/index", body, true);
        return result.get("artifactId").getAsString();
    }

    public String runs(String sessionId) throws Exception {
        HttpRequest request = requestBuilder("GET", "/api/v1/sessions/" + sessionId + "/runs", null, true).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("后端返回 HTTP " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    public String messages(String sessionId) throws Exception {
        HttpRequest request = requestBuilder("GET", "/api/v1/sessions/" + sessionId + "/messages", null, true).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("后端返回 HTTP " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    public String recommendations(String sessionId) throws Exception {
        HttpRequest request = requestBuilder("GET", "/api/v1/sessions/" + sessionId + "/recommendations", null, true).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("后端返回 HTTP " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    /**
     * Starts the canonical asynchronous statement-analysis workflow. Scenario planning and report
     * generation are server-side worker responsibilities; the plugin only follows the returned
     * persisted AG-UI stream.
     */
    public AnalysisHandle analyzeStatement(String artifactId, String statementId,
                                           String datasourceProfileId, String projectId,
                                           String moduleId, String sessionId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("artifactId", artifactId);
        body.addProperty("statementId", statementId);
        body.addProperty("datasourceProfileId", datasourceProfileId);
        addNonBlank(body, "projectId", projectId);
        addNonBlank(body, "moduleId", moduleId);
        addNonBlank(body, "sessionId", sessionId);
        body.addProperty("maxScenarios", 20);
        JsonObject response = request("POST", "/api/v1/mapper-statements/analyze",
                body.toString(), true);
        return new AnalysisHandle(
                response.get("sessionId").getAsString(),
                response.get("runId").getAsString(),
                response.get("status").getAsString(),
                response.get("streamUrl").getAsString());
    }

    public List<DatasourceProfile> datasourceProfiles() throws Exception {
        HttpRequest request = requestBuilder("GET", "/api/v1/datasource-profiles", null, true).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response);
        List<DatasourceProfile> profiles = new ArrayList<>();
        for (var element : JsonParser.parseString(response.body()).getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            profiles.add(new DatasourceProfile(
                    item.get("id").getAsString(),
                    item.has("name") ? item.get("name").getAsString() : "",
                    item.has("dialect") ? item.get("dialect").getAsString() : ""));
        }
        return List.copyOf(profiles);
    }

    /**
     * Uses a configured project binding when present. With exactly one visible profile the binding
     * can be inferred safely; choosing silently among multiple databases is forbidden.
     */
    public String resolveDatasourceProfile(String configuredId) throws Exception {
        if (configuredId != null && !configuredId.isBlank()) return configuredId.trim();
        List<DatasourceProfile> profiles = datasourceProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("当前客户端没有数据源配置，请先在服务端创建 datasource profile");
        }
        if (profiles.size() > 1) {
            throw new IllegalStateException("检测到多个数据源，请先为当前 IDEA Project 配置 datasourceProfileId");
        }
        return profiles.get(0).id();
    }

    public String report(String reportId) throws Exception {
        HttpRequest request = requestBuilder("GET", "/api/v1/reports/" + reportId, null, true)
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response);
        return response.body();
    }

    public String cancelRun(String runId) throws Exception {
        JsonObject result = request("POST", "/api/v1/runs/" + runId + "/cancel", "{}", true);
        return result.has("status") ? result.get("status").getAsString() : "CANCELLED";
    }

    public void decideRecommendation(String recommendationId, String decision, String category, String reason) throws Exception {
        String body = "{\"decision\":\"" + json(decision) + "\",\"category\":\""
                + json(category) + "\",\"reason\":\"" + json(reason) + "\"}";
        HttpRequest request = requestBuilder("POST", "/api/v1/recommendations/" + recommendationId + "/decision", body, true).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("后端返回 HTTP " + response.statusCode() + ": " + response.body());
    }

    private JsonObject request(String method, String path, String body, boolean authenticated) throws Exception {
        HttpRequest request = requestBuilder(method, path, body, authenticated).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void requireSuccess(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("后端返回 HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private HttpRequest.Builder requestBuilder(String method, String path, String body, boolean authenticated) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("X-Request-Id", "req_" + java.util.UUID.randomUUID());
        if (authenticated) builder.header("Authorization", "Bearer " + token);
        if ("POST".equals(method)) builder.header("Idempotency-Key", "idea_" + java.util.UUID.randomUUID());
        if (body == null) return builder.method(method, HttpRequest.BodyPublishers.noBody());
        return builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void addNonBlank(JsonObject object, String field, String value) {
        if (value != null && !value.isBlank()) object.addProperty(field, value);
    }

    public record AnalysisHandle(String sessionId, String runId, String status, String streamUrl) {}

    public record DatasourceProfile(String id, String name, String dialect) {}
}
