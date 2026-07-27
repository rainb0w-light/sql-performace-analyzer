package com.biz.sccba.sqlanalyzer.agui;

import com.biz.sccba.sqlanalyzer.adapter.agentscope.HarnessAgentRuntime;
import com.biz.sccba.sqlanalyzer.adapter.agentscope.UserBindingAgents;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.MessageRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEventType;
import io.agentscope.core.agui.model.RunAgentInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes AG-UI protocol jobs: drives the shared HarnessAgent through the official
 * {@link AguiAgentAdapter}, persists every {@link AguiEvent} to {@code run_event} BEFORE it can
 * reach any SSE consumer (persist-first contract), and settles the run on the terminal
 * {@code RUN_FINISHED} event. Client reconnections replay from the persisted cursor, so events
 * are never lost and the terminal report is never duplicated by transport retries.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AguiExecutor {

    private final AgentRunRepository runs;
    private final AgentJobRepository jobs;
    private final RunEventRepository events;
    private final MessageRepository messages;
    private final HarnessAgentRuntime runtime;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public AguiExecutor(AgentRunRepository runs, AgentJobRepository jobs, RunEventRepository events, MessageRepository messages,
                        org.springframework.beans.factory.ObjectProvider<HarnessAgentRuntime> runtimeProvider,
                        ObjectMapper objectMapper) {
        this.runs = runs;
        this.jobs = jobs;
        this.events = events;
        this.messages = messages;
        this.runtime = runtimeProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    private record ActiveRun(AtomicReference<Disposable> disposable, AtomicBoolean settled,
                             AgentJobRepository.Job job, StringBuilder text, AtomicBoolean sawError,
                             AtomicReference<String> errorMessage) {}

    /** Starts the streaming execution asynchronously; the claimed job lease is extended up front. */
    public void executeAsync(AgentJobRepository.Job job) {
        Flux.defer(() -> {
            try {
                execute(job);
            } catch (Exception e) {
                return Flux.error(e);
            }
            return Flux.empty();
        }).subscribeOn(Schedulers.boundedElastic()).subscribe(
                unused -> { },
                error -> { /* failures are settled inside execute() */ });
    }

    private void execute(AgentJobRepository.Job job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.payloadJson());
        String clientId = payload.path("clientId").asText();
        String sessionId = payload.path("sessionId").asText();
        String runId = payload.path("runId").asText();
        String modelName = payload.path("modelName").asText("");
        RunAgentInput input = objectMapper.readValue(payload.path("input").asText("{}"), RunAgentInput.class);

        jobs.extendLease(job.id(), 60);
        runs.updateStatus(runId, "RUNNING", null);

        ActiveRun active = new ActiveRun(new AtomicReference<>(), new AtomicBoolean(false), job,
                new StringBuilder(), new AtomicBoolean(false), new AtomicReference<>(""));
        activeRuns.put(runId, active);

        if (runtime == null) {
            settle(runId, active, "FAILED", "Agent 运行时不可用");
            return;
        }

        AguiAdapterConfig config = AguiAdapterConfig.builder()
                .enableReasoning(true)
                .emitToolCallArgs(true)
                .runTimeout(Duration.ofMinutes(5))
                .build();
        AguiAgentAdapter adapter = new AguiAgentAdapter(
                UserBindingAgents.bind(runtime.agentFor(modelName.isBlank() ? "default" : modelName), clientId),
                config);

        Disposable disposable = adapter.run(input)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> onEvent(runId, sessionId, active, event),
                        error -> {
                            appendError(runId, active, "INTERNAL", String.valueOf(error.getMessage()));
                            settle(runId, active, "FAILED", String.valueOf(error.getMessage()));
                        },
                        () -> {
                            String status = active.sawError().get() ? "FAILED" : "COMPLETED";
                            settle(runId, active, status, active.errorMessage().get());
                        });
        active.disposable().set(disposable);
    }

    private void onEvent(String runId, String sessionId, ActiveRun active, AguiEvent event) {
        if (active.settled().get()) return;
        try {
            if (event instanceof AguiEvent.TextMessageContent content) {
                synchronized (active.text()) {
                    active.text().append(content.delta() == null ? "" : content.delta());
                }
            }
            if (event.getType() == AguiEventType.RUN_ERROR) {
                active.sawError().set(true);
                if (event instanceof AguiEvent.RunError runError) {
                    active.errorMessage().set(runError.message() == null ? "Agent 执行失败" : runError.message());
                }
            }
            // Persist FIRST, then the SSE streamer may project it (persist-first contract).
            events.append(runId, event.getType().name(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            appendError(runId, active, "PERSIST_FAILED", "事件持久化失败：" + e.getMessage());
            Disposable d = active.disposable().get();
            if (d != null) d.dispose();
            settle(runId, active, "FAILED", "事件持久化失败");
        }
    }

    private void appendError(String runId, ActiveRun active, String code, String message) {
        if (active.settled().get()) return;
        try {
            events.append(runId, AguiEventType.RUN_ERROR.name(),
                    objectMapper.writeValueAsString(new AguiEvent.RunError(null, runId, message, code)));
            events.append(runId, AguiEventType.RUN_FINISHED.name(),
                    objectMapper.writeValueAsString(new AguiEvent.RunFinished(runId, runId, null, null)));
        } catch (Exception ignored) {
            // best effort; settle() still records the terminal status
        }
    }

    private void settle(String runId, ActiveRun active, String status, String error) {
        if (!active.settled().compareAndSet(false, true)) return;
        activeRuns.remove(runId);
        String text;
        synchronized (active.text()) {
            text = active.text().toString();
        }
        try {
            if (!"CANCELLED".equals(status)) {
                messages.append("message_assistant_" + runId, sessionIdOf(active), "ASSISTANT",
                        text, "COMPLETED".equals(status) ? "TEXT" : "ERROR", runId);
            }
        } catch (RuntimeException historyFailed) {
            try {
                events.append(runId, "ASSISTANT_MESSAGE_PERSIST_FAILED", "{\"error\":\"history write failed\"}");
            } catch (RuntimeException ignored) {
                // run lifecycle wins over auxiliary history projection
            }
        }
        runs.updateStatus(runId, status, "FAILED".equals(status) ? safe(error) : null);
        if ("COMPLETED".equals(status)) {
            jobs.complete(active.job().id());
        } else {
            jobs.failNoRetry(active.job().id(), safe(error));
        }
    }

    private String sessionIdOf(ActiveRun active) {
        try {
            return objectMapper.readTree(active.job().payloadJson()).path("sessionId").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }

    /**
     * Cancels a RUNNING AG-UI execution: emits the terminal RUN_ERROR(code=CANCELLED)+RUN_FINISHED
     * pair, marks the run CANCELLED and disposes the live stream. Returns false when the run is
     * not actively executing here (caller falls back to queued-job cancellation / 409).
     */
    public boolean cancel(String runId) {
        ActiveRun active = activeRuns.get(runId);
        if (active == null) return false;
        if (!active.settled().compareAndSet(false, true)) return false;
        activeRuns.remove(runId);
        try {
            events.append(runId, AguiEventType.RUN_ERROR.name(),
                    objectMapper.writeValueAsString(new AguiEvent.RunError(null, runId, "cancelled by client", "CANCELLED")));
            events.append(runId, AguiEventType.RUN_FINISHED.name(),
                    objectMapper.writeValueAsString(new AguiEvent.RunFinished(runId, runId, null, null)));
        } catch (Exception ignored) {
            // status below still records the cancellation
        }
        runs.updateStatus(runId, "CANCELLED", "cancelled by client");
        jobs.failNoRetry(active.job().id(), "cancelled by client");
        Disposable d = active.disposable().get();
        if (d != null) d.dispose();
        return true;
    }
}
