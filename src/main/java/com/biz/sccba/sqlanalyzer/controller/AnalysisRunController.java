package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.service.SessionService;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginRunLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * analysis-runs resource API (docs/contracts/rest-api.md §1).
 *
 * <p>The standard streaming entry point is AG-UI ({@link AguiController}); the 30s
 * {@code /runs/{runId}/events} emitter below is a transitional endpoint kept for clients that
 * have not migrated to the continuous AG-UI stream yet.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AnalysisRunController {

    private final SessionService sessions;
    private final PluginRunLifecycleService pluginRuns;
    private final BearerClients bearer;

    public AnalysisRunController(SessionService sessions, PluginRunLifecycleService pluginRuns,
                                 BearerClients bearer) {
        this.sessions = sessions;
        this.pluginRuns = pluginRuns;
        this.bearer = bearer;
    }

    /** Transitional: short-lived replay emitter. Prefer {@code GET /api/v1/agui/runs/{runId}/stream}. */
    @Deprecated
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader("Authorization") String authorization,
                             @PathVariable String runId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        String clientId = bearer.clientId(authorization);
        long after = parseEventId(lastEventId);
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> {
            long cursor = after;
            try {
                long deadline = System.currentTimeMillis() + 29_000L;
                while (System.currentTimeMillis() < deadline) {
                    List<RunEventRepository.RunEvent> batch = sessions.events(clientId, runId, cursor);
                    for (RunEventRepository.RunEvent event : batch) {
                        emitter.send(SseEmitter.event().id(String.valueOf(event.id())).name(event.type()).data(event.payloadJson()));
                        cursor = event.id();
                    }
                    Thread.sleep(batch.isEmpty() ? 250L : 10L);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/runs/{runId}/cancel")
    public SessionService.Cancellation cancel(@RequestHeader("Authorization") String authorization,
                                              @PathVariable String runId) {
        return sessions.cancel(bearer.clientId(authorization), runId);
    }

    @PostMapping("/runs/{runId}/confirm")
    public ResponseEntity<PluginRunLifecycleService.ConfirmationResult> confirm(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String runId,
            @Valid @RequestBody PluginApiModels.ScenarioConfirmation confirmation) {
        return ResponseEntity.accepted().body(pluginRuns.confirm(
                bearer.clientId(authorization), runId, idempotencyKey, confirmation));
    }

    @GetMapping("/runs/{runId}")
    public PluginRunLifecycleService.RunStatus status(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String runId) {
        return pluginRuns.status(bearer.clientId(authorization), runId);
    }

    private static long parseEventId(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
