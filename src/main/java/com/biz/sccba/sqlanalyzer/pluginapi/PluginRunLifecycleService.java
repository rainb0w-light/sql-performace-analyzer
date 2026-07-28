package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.api.IdempotencyConflictException;
import com.biz.sccba.sqlanalyzer.domain.AgentRun;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ScenarioConfirmation;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.IdempotencyRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Confirmation and structured recovery for Plugin statement-analysis runs. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginRunLifecycleService {

    private final AgentRunRepository runs;
    private final AgentJobRepository jobs;
    private final RunEventRepository events;
    private final SessionRepository sessions;
    private final IdempotencyRepository idempotency;
    private final PluginRunPlanningService planning;
    private final ObjectMapper objectMapper;

    public PluginRunLifecycleService(AgentRunRepository runs, AgentJobRepository jobs,
                                     RunEventRepository events, SessionRepository sessions,
                                     IdempotencyRepository idempotency,
                                     PluginRunPlanningService planning,
                                     ObjectMapper objectMapper) {
        this.runs = runs;
        this.jobs = jobs;
        this.events = events;
        this.sessions = sessions;
        this.idempotency = idempotency;
        this.planning = planning;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public ConfirmationResult confirm(String clientId, String runId, String key,
                                      ScenarioConfirmation confirmation) {
        requireKey(key);
        String path = "/api/v1/runs/" + runId + "/confirm";
        String digest = digest(confirmation);
        var replay = idempotency.find(clientId, key);
        if (replay.isPresent()) {
            var stored = replay.get();
            if (!path.equals(stored.path()) || !digest.equals(stored.requestDigest())) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key 已用于不同的 Run 确认请求");
            }
            return readResult(stored.responseBody());
        }
        AgentRun run = ownedRun(clientId, runId);
        if (!"AWAITING_CONFIRMATION".equals(run.status())) {
            throw new IllegalStateException("Run 当前状态不允许确认：" + run.status());
        }
        String planArtifactId = planArtifactId(clientId, runId);
        var plan = planning.read(clientId, planArtifactId);
        if (!plan.blockingGuards().isEmpty()) {
            throw new IllegalArgumentException(
                    "Run 存在未解决的强制守卫：" + String.join(",", plan.blockingGuards()));
        }
        Set<String> available = new LinkedHashSet<>();
        plan.plan().scenarios().forEach(item ->
                available.add(item.scenario().scenarioId()));
        Set<String> included = new LinkedHashSet<>(confirmation.includedScenarioIds());
        if (!available.containsAll(included)) {
            throw new IllegalArgumentException("确认包含未知 scenarioId");
        }
        if (!included.containsAll(plan.requiredScenarioIds())) {
            throw new IllegalArgumentException("mainPath/required 场景不可排除");
        }
        Set<String> excluded = new LinkedHashSet<>();
        confirmation.excludedScenarios().forEach(item -> {
            if (item.reason() == null || item.reason().isBlank()) {
                throw new IllegalArgumentException("排除场景必须填写原因");
            }
            excluded.add(item.scenarioId());
        });
        if (!available.containsAll(excluded)) {
            throw new IllegalArgumentException("确认排除未知 scenarioId");
        }
        Set<String> overlap = new LinkedHashSet<>(included);
        overlap.retainAll(excluded);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("同一场景不能同时包含和排除");
        }
        Set<String> decided = new LinkedHashSet<>(included);
        decided.addAll(excluded);
        if (!decided.equals(available)) {
            throw new IllegalArgumentException("确认必须覆盖规划中的全部场景");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("protocol", "STATEMENT_ANALYSIS");
        payload.put("phase", "EXECUTE");
        payload.put("clientId", clientId);
        payload.put("sessionId", run.sessionId());
        payload.put("planArtifactId", planArtifactId);
        payload.set("includedScenarioIds", objectMapper.valueToTree(included));
        jobs.enqueue("job_" + UUID.randomUUID(), runId, payload.toString());
        runs.updateStatus(runId, "QUEUED", null);
        sessions.touch(run.sessionId(), "RUNNING");
        append(runId, "CUSTOM", Map.of("name", "spa.run_confirmed",
                "includedScenarioIds", included, "excludedScenarioIds", excluded));
        append(runId, "RUN_QUEUED", Map.of("runId", runId, "phase", "EXECUTE"));

        ConfirmationResult result = new ConfirmationResult(runId, "QUEUED");
        String body = write(result);
        Instant now = Instant.now();
        idempotency.save(new IdempotencyRepository.Record(clientId, key, digest,
                "POST", path, 202, body, now, now.plus(24, ChronoUnit.HOURS)));
        return result;
    }

    public RunStatus status(String clientId, String runId) {
        AgentRun run = ownedRun(clientId, runId);
        List<RunEventRepository.RunEvent> history = events.after(clientId, runId, 0);
        long last = history.isEmpty() ? 0 : history.get(history.size() - 1).id();
        String reportId = null;
        for (var event : history) {
            try {
                JsonNode payload = objectMapper.readTree(event.payloadJson());
                if ("spa.report_ready".equals(payload.path("name").asText())) {
                    reportId = payload.path("reportId").asText(null);
                }
            } catch (Exception ignored) {
                // Invalid historical payload cannot change the persisted Run state.
            }
        }
        boolean cancellable = Set.of("PLANNING", "AWAITING_CONFIRMATION", "QUEUED",
                "RUNNING", "RETRYING").contains(run.status());
        return new RunStatus(runId, run.status(), String.valueOf(last), reportId, cancellable);
    }

    private AgentRun ownedRun(String clientId, String runId) {
        if (!runs.belongsToClient(runId, clientId)) {
            throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
        }
        return runs.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run 不存在"));
    }

    private String planArtifactId(String clientId, String runId) {
        String result = null;
        for (var event : events.after(clientId, runId, 0)) {
            try {
                JsonNode payload = objectMapper.readTree(event.payloadJson());
                if ("spa.scenarios_ready".equals(payload.path("name").asText())) {
                    result = payload.path("planArtifactId").asText(null);
                }
            } catch (Exception ignored) {
                // Continue scanning older valid persisted events.
            }
        }
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("Run 缺少规划快照");
        }
        return result;
    }

    private void append(String runId, String type, Map<String, ?> payload) {
        try {
            events.append(runId, type, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("无法持久化 Run 事件", e);
        }
    }

    private String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(value)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算确认摘要", e);
        }
    }

    private String write(ConfirmationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("无法保存确认响应", e);
        }
    }

    private ConfirmationResult readResult(String json) {
        try {
            return objectMapper.readValue(json, ConfirmationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("已保存的确认响应无效", e);
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空");
        }
        if (key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key 长度不能超过 200");
        }
    }

    public record ConfirmationResult(String runId, String status) {
    }

    public record RunStatus(String runId, String status, String lastEventId,
                            String reportId, boolean cancellable) {
    }
}
