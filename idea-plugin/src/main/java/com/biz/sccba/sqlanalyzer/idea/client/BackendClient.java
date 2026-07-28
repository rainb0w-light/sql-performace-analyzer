package com.biz.sccba.sqlanalyzer.idea.client;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Typed REST client for the IDEA Plugin.
 *
 * <p>Retry decisions are based on transport/status/Problem Details facts. Every retry of a
 * mutation reuses the original idempotency key; authentication, validation, unsupported and
 * non-idempotent conflicts are never retried.</p>
 */
public final class BackendClient {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final RetryPolicy retryPolicy;

    public BackendClient(String baseUrl, String token) {
        this(baseUrl, token, RetryPolicy.defaults());
    }

    public BackendClient(String baseUrl, String token, RetryPolicy retryPolicy) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.token = token == null ? "" : token;
        this.retryPolicy = retryPolicy == null ? RetryPolicy.defaults() : retryPolicy;
    }

    public String applyToken(String clientName) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("clientName", clientName);
        body.addProperty("clientType", "IDEA");
        body.addProperty("deviceId", "idea");
        JsonObject result = jsonRequest("POST", "/api/v1/client-tokens/apply", body, false, true);
        return requiredText(result, "accessToken");
    }

    public ClientStatus clientStatus() throws Exception {
        JsonObject result = jsonRequest("GET", "/api/v1/client", null, true, false);
        return new ClientStatus(text(result, "id"), text(result, "name"),
                result.has("expiresAt") && !result.get("expiresAt").isJsonNull()
                        ? result.get("expiresAt").getAsString() : null);
    }

    public String createSession(String title) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        return requiredText(jsonRequest("POST", "/api/v1/sessions", body, true, true), "id");
    }

    public String submitMessage(String sessionId, String content) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        body.addProperty("messageType", "TEXT");
        return requiredText(jsonRequest("POST", "/api/v1/sessions/" + id(sessionId) + "/messages",
                body, true, true), "runId");
    }

    public String indexMyBatisMapper(String sessionId, String xmlContent, String namespace) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("namespace", namespace);
        body.addProperty("xmlContent", xmlContent);
        return requiredText(jsonRequest("POST", "/api/v1/artifacts/mybatis/index", body, true, true),
                "artifactId");
    }

    public String indexMyBatisAnnotation(String sessionId, String javaContent,
                                         String namespace, String methodName) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("namespace", namespace);
        body.addProperty("methodName", methodName);
        body.addProperty("javaContent", javaContent);
        return requiredText(jsonRequest("POST", "/api/v1/artifacts/mybatis/annotation-index",
                body, true, true), "artifactId");
    }

    public SuggestionSet suggestDefaultParameters(SuggestionRequest request) throws Exception {
        return typedRequest("POST", "/api/v1/mapper-statements/default-parameters/suggest",
                request, true, true, SuggestionSet.class);
    }

    public BoundSqlPreview previewBoundSql(BoundSqlPreviewRequest request) throws Exception {
        return typedRequest("POST", "/api/v1/mapper-statements/default-parameters/preview",
                request, true, true, BoundSqlPreview.class);
    }

    public TransientRuleImpact previewTransientRules(Object request) throws Exception {
        return typedRequest("POST", "/api/v1/mapper-statements/transient-rules/preview",
                request, true, true, TransientRuleImpact.class);
    }

    public AnalysisHandle analyzeStatement(AnalyzeRequest request, String idempotencyKey) throws Exception {
        JsonObject body = GSON.toJsonTree(request).getAsJsonObject();
        removeBlank(body, "projectId");
        removeBlank(body, "moduleId");
        removeBlank(body, "sessionId");
        if (request.mainScenario() == null) body.remove("mainScenario");
        JsonObject response = jsonRequest("POST", "/api/v1/mapper-statements/analyze",
                body, true, true, stableKey(idempotencyKey));
        return handle(response);
    }

    /** Compatibility overload for the baseline client contract. */
    public AnalysisHandle analyzeStatement(String artifactId, String statementId,
                                           String datasourceProfileId, String projectId,
                                           String moduleId, String sessionId) throws Exception {
        AnalyzeRequest request = new AnalyzeRequest(artifactId, statementId, datasourceProfileId,
                projectId, moduleId, sessionId, ExecutionMode.AUTO, null, List.of(), 20,
                CostLevel.MEDIUM);
        return analyzeStatement(request, "idea-analyze-" + UUID.randomUUID());
    }

    public void confirmRun(String runId, ScenarioConfirmation confirmation, String idempotencyKey) throws Exception {
        jsonRequest("POST", "/api/v1/runs/" + id(runId) + "/confirm", GSON.toJsonTree(confirmation),
                true, true, stableKey(idempotencyKey));
    }

    public RunStatus runStatus(String runId) throws Exception {
        return typedRequest("GET", "/api/v1/runs/" + id(runId), null, true, false, RunStatus.class);
    }

    public String cancelRun(String runId) throws Exception {
        JsonObject result = jsonRequest("POST", "/api/v1/runs/" + id(runId) + "/cancel",
                new JsonObject(), true, true);
        return text(result, "status").isBlank() ? "CANCELLED" : text(result, "status");
    }

    public List<DatasourceProfile> datasourceProfiles() throws Exception {
        JsonElement result = rawJsonRequest("GET", "/api/v1/datasource-profiles", null, true, false, null);
        JsonArray items = result.isJsonArray() ? result.getAsJsonArray()
                : result.getAsJsonObject().has("items") ? result.getAsJsonObject().getAsJsonArray("items")
                : new JsonArray();
        List<DatasourceProfile> profiles = new ArrayList<>();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            profiles.add(new DatasourceProfile(text(item, "id"), text(item, "name"), text(item, "dialect"),
                    text(item, "schemaName"), text(item, "bindingSource"), nullableText(item, "latestProfileAt")));
        }
        return List.copyOf(profiles);
    }

    /**
     * Uses the configured binding only when it is visible. Zero/multiple matches remain explicit
     * UI guards; the client never silently picks or persists a module binding.
     */
    public DatasourceResolution resolveDatasource(String statementTemporaryId, String moduleDefaultId,
                                                  String projectDefaultId) throws Exception {
        List<DatasourceProfile> profiles = datasourceProfiles();
        Map<String, DatasourceProfile> visible = new LinkedHashMap<>();
        profiles.forEach(profile -> visible.put(profile.id(), profile));
        for (BindingCandidate candidate : List.of(
                new BindingCandidate(statementTemporaryId, "STATEMENT_TEMPORARY"),
                new BindingCandidate(moduleDefaultId, "MODULE_DEFAULT"),
                new BindingCandidate(projectDefaultId, "PROJECT_DEFAULT"))) {
            if (candidate.id() != null && !candidate.id().isBlank() && visible.containsKey(candidate.id())) {
                return DatasourceResolution.resolved(visible.get(candidate.id()), candidate.source(), profiles);
            }
        }
        if (profiles.isEmpty()) return DatasourceResolution.missing();
        if (profiles.size() == 1) return DatasourceResolution.resolved(profiles.get(0), "ONLY_VISIBLE", profiles);
        return DatasourceResolution.ambiguous(profiles);
    }

    /** Compatibility helper. Prefer {@link #resolveDatasource(String, String, String)}. */
    public String resolveDatasourceProfile(String configuredId) throws Exception {
        DatasourceResolution resolution = resolveDatasource("", configuredId, "");
        if (resolution.status() == DatasourceResolutionStatus.RESOLVED) return resolution.selected().id();
        if (resolution.status() == DatasourceResolutionStatus.MISSING) {
            throw new IllegalStateException("当前客户端没有数据源配置，请先在服务端创建 datasource profile");
        }
        throw new IllegalStateException("检测到多个数据源，需要明确选择当前 statement 的分析数据源");
    }

    public String report(String reportId) throws Exception {
        return stringRequest("GET", "/api/v1/reports/" + id(reportId), null, true, false,
                "application/json", null);
    }

    public String reportMarkdown(String reportId) throws Exception {
        return stringRequest("GET", "/api/v1/reports/" + id(reportId), null, true, false,
                "text/markdown", null);
    }

    public String reports(HistoryFilter filter) throws Exception {
        StringBuilder path = new StringBuilder("/api/v1/reports?");
        appendQuery(path, "projectId", filter == null ? "" : filter.projectId());
        appendQuery(path, "moduleId", filter == null ? "" : filter.moduleId());
        appendQuery(path, "statement", filter == null ? "" : filter.statement());
        appendQuery(path, "datasourceProfileId", filter == null ? "" : filter.datasourceProfileId());
        appendQuery(path, "severity", filter == null ? "" : filter.severity());
        appendQuery(path, "stale", filter == null || filter.stale() == null ? "" : filter.stale().toString());
        appendQuery(path, "page", String.valueOf(filter == null ? 0 : filter.page()));
        appendQuery(path, "size", String.valueOf(filter == null ? 10 : filter.size()));
        return stringRequest("GET", path.substring(0, path.length() - 1), null, true, false,
                "application/json", null);
    }

    public String runs(String sessionId) throws Exception {
        return stringRequest("GET", "/api/v1/sessions/" + id(sessionId) + "/runs",
                null, true, false, "application/json", null);
    }

    public String messages(String sessionId) throws Exception {
        return stringRequest("GET", "/api/v1/sessions/" + id(sessionId) + "/messages",
                null, true, false, "application/json", null);
    }

    public String recommendations(String sessionId) throws Exception {
        return stringRequest("GET", "/api/v1/sessions/" + id(sessionId) + "/recommendations",
                null, true, false, "application/json", null);
    }

    public void decideRecommendation(String recommendationId, String decision,
                                     String category, String reason) throws Exception {
        if ("REJECTED".equals(decision) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("拒绝建议时 reason 必填");
        }
        JsonObject body = new JsonObject();
        body.addProperty("decision", decision);
        body.addProperty("category", category);
        body.addProperty("reason", reason);
        stringRequest("POST", "/api/v1/recommendations/" + id(recommendationId) + "/decision",
                body, true, true, "application/json", null);
    }

    private <T> T typedRequest(String method, String path, Object body, boolean authenticated,
                               boolean idempotentMutation, Class<T> type) throws Exception {
        JsonElement json = body == null ? null : GSON.toJsonTree(body);
        JsonElement response = rawJsonRequest(method, path, json, authenticated, idempotentMutation, null);
        return GSON.fromJson(response, type);
    }

    private JsonObject jsonRequest(String method, String path, JsonElement body, boolean authenticated,
                                   boolean idempotentMutation) throws Exception {
        return jsonRequest(method, path, body, authenticated, idempotentMutation, null);
    }

    private JsonObject jsonRequest(String method, String path, JsonElement body, boolean authenticated,
                                   boolean idempotentMutation, String key) throws Exception {
        JsonElement response = rawJsonRequest(method, path, body, authenticated, idempotentMutation, key);
        return response == null || response.isJsonNull() ? new JsonObject() : response.getAsJsonObject();
    }

    private JsonElement rawJsonRequest(String method, String path, JsonElement body, boolean authenticated,
                                       boolean idempotentMutation, String key) throws Exception {
        String result = stringRequest(method, path, body, authenticated, idempotentMutation,
                "application/json", key);
        return result == null || result.isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(result);
    }

    private String stringRequest(String method, String path, JsonElement body, boolean authenticated,
                                 boolean idempotentMutation, String accept, String requestedKey) throws Exception {
        String idempotencyKey = idempotentMutation ? stableKey(requestedKey) : null;
        String requestId = "req_" + UUID.randomUUID();
        String serialized = body == null ? null : GSON.toJson(body);
        BackendException last = null;
        for (int attempt = 0; attempt < retryPolicy.maxAttempts(); attempt++) {
            try {
                HttpRequest.Builder builder = requestBuilder(method, path, serialized, authenticated, accept,
                        requestId, idempotencyKey);
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) return response.body();
                BackendException problem = problem(response.statusCode(), response.body());
                if (!retryPolicy.shouldRetry(problem, idempotencyKey != null, attempt)) throw problem;
                last = problem;
            } catch (IOException transport) {
                BackendException problem = new BackendException(0, "NETWORK", safe(transport.getMessage()),
                        true, List.of());
                if (!retryPolicy.shouldRetry(problem, idempotencyKey != null || "GET".equals(method), attempt)) {
                    throw problem;
                }
                last = problem;
            }
            retryPolicy.pause(attempt + 1);
        }
        throw last == null ? new BackendException(0, "NETWORK", "请求失败", true, List.of()) : last;
    }

    private HttpRequest.Builder requestBuilder(String method, String path, String body, boolean authenticated,
                                               String accept, String requestId, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", accept)
                .header("X-Request-Id", requestId);
        if (authenticated) builder.header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body == null) return builder.method(method, HttpRequest.BodyPublishers.noBody());
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    private BackendException problem(int status, String body) {
        String code = status == 404 ? "CAPABILITY_NOT_DEPLOYED" : "HTTP_" + status;
        String message = "后端返回 HTTP " + status;
        boolean retryable = status == 429;
        List<FieldProblem> fields = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("code")) code = root.get("code").getAsString();
            if (root.has("detail")) message = root.get("detail").getAsString();
            else if (root.has("message")) message = root.get("message").getAsString();
            if (root.has("retryable")) retryable = root.get("retryable").getAsBoolean();
            if (root.has("errors") && root.get("errors").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("errors")) {
                    JsonObject item = element.getAsJsonObject();
                    fields.add(new FieldProblem(text(item, "field"), text(item, "message")));
                }
            }
        } catch (RuntimeException ignored) {
            if (body != null && !body.isBlank()) message += ": " + body;
        }
        if (status == 401) code = "UNAUTHORIZED";
        if (status == 404 && pathCapabilityMessage(body)) message = "服务端 P1 能力尚未部署";
        return new BackendException(status, code, redact(message), retryable, fields);
    }

    private boolean pathCapabilityMessage(String body) {
        return body == null || !body.toLowerCase(Locale.ROOT).contains("not found");
    }

    private String redact(String value) {
        String result = safe(value);
        return token.isBlank() ? result : result.replace(token, "<redacted-token>");
    }

    private static AnalysisHandle handle(JsonObject response) {
        return new AnalysisHandle(requiredText(response, "sessionId"), requiredText(response, "runId"),
                requiredText(response, "status"), requiredText(response, "streamUrl"));
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("Backend URL 必须使用 http:// 或 https://");
        }
        return normalized;
    }

    private static String stableKey(String value) {
        return value == null || value.isBlank() ? "idea_" + UUID.randomUUID() : value;
    }

    private static String id(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private static String nullableText(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
    }

    private static String requiredText(JsonObject object, String field) {
        String value = text(object, field);
        if (value.isBlank()) throw new IllegalStateException("后端响应缺少字段 " + field);
        return value;
    }

    private static void removeBlank(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()
                || object.get(field).getAsString().isBlank()) object.remove(field);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static void appendQuery(StringBuilder path, String name, String value) {
        if (value != null && !value.isBlank()) {
            path.append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append("&");
        }
    }

    private record BindingCandidate(String id, String source) {}

    public record AnalysisHandle(String sessionId, String runId, String status, String streamUrl) {}
    public record ClientStatus(String id, String name, String expiresAt) {}
    public record DatasourceProfile(String id, String name, String dialect, String schemaName,
                                    String bindingSource, String latestProfileAt) {}
    public enum DatasourceResolutionStatus { RESOLVED, MISSING, AMBIGUOUS }

    public record DatasourceResolution(DatasourceResolutionStatus status, DatasourceProfile selected,
                                       String bindingSource, List<DatasourceProfile> candidates) {
        public DatasourceResolution {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
        public static DatasourceResolution resolved(DatasourceProfile profile, String source,
                                                    List<DatasourceProfile> candidates) {
            return new DatasourceResolution(DatasourceResolutionStatus.RESOLVED, profile, source, candidates);
        }
        public static DatasourceResolution missing() {
            return new DatasourceResolution(DatasourceResolutionStatus.MISSING, null, "", List.of());
        }
        public static DatasourceResolution ambiguous(List<DatasourceProfile> profiles) {
            return new DatasourceResolution(DatasourceResolutionStatus.AMBIGUOUS, null, "", profiles);
        }
    }

    public record RunStatus(String runId, String status, String lastEventId,
                            String reportId, boolean cancellable) {}
    public record HistoryFilter(String projectId, String moduleId, String statement,
                                String datasourceProfileId, String severity, Boolean stale,
                                int page, int size) {}
    public record FieldProblem(String field, String message) {}

    public static final class BackendException extends Exception {
        private final int status;
        private final String code;
        private final boolean retryable;
        private final List<FieldProblem> fieldProblems;

        public BackendException(int status, String code, String message,
                                boolean retryable, List<FieldProblem> fieldProblems) {
            super(message);
            this.status = status;
            this.code = code;
            this.retryable = retryable;
            this.fieldProblems = fieldProblems == null ? List.of() : List.copyOf(fieldProblems);
        }
        public int status() { return status; }
        public String code() { return code; }
        public boolean retryable() { return retryable; }
        public List<FieldProblem> fieldProblems() { return fieldProblems; }
        public boolean authenticationRequired() { return status == 401 || "UNAUTHORIZED".equals(code); }
    }

    public static final class RetryPolicy {
        @FunctionalInterface public interface Sleeper { void sleep(long millis) throws InterruptedException; }

        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final Sleeper sleeper;

        public RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs, Sleeper sleeper) {
            this.maxAttempts = Math.max(1, maxAttempts);
            this.initialDelayMs = Math.max(0, initialDelayMs);
            this.maxDelayMs = Math.max(this.initialDelayMs, maxDelayMs);
            this.sleeper = sleeper == null ? Thread::sleep : sleeper;
        }
        public static RetryPolicy defaults() { return new RetryPolicy(4, 250, 4_000, Thread::sleep); }
        public int maxAttempts() { return maxAttempts; }

        boolean shouldRetry(BackendException error, boolean replaySafe, int zeroBasedAttempt) {
            if (!replaySafe || zeroBasedAttempt + 1 >= maxAttempts) return false;
            if (error.authenticationRequired()) return false;
            if (error.status() == 409 || error.status() == 400 || error.status() == 422) return false;
            return error.status() == 0 || error.status() == 429
                    || (error.status() >= 500 && error.retryable());
        }

        void pause(int attempt) throws InterruptedException {
            if (initialDelayMs == 0) return;
            long ceiling = Math.min(maxDelayMs, initialDelayMs * (1L << Math.min(attempt, 20)));
            sleeper.sleep(ThreadLocalRandom.current().nextLong(Math.max(1, ceiling)));
        }
    }
}
