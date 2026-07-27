package com.biz.sccba.sqlanalyzer.agui;

import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Projects persisted {@code run_event} rows into a continuous SSE stream. The stream is a pure
 * read model of the database: events are already persisted by the executor before emission, so a
 * client reconnecting with {@code Last-Event-ID} replays exactly the missed events and then
 * follows the live tail — no event is lost or duplicated, and the terminal RUN_FINISHED arrives
 * exactly once per connection.
 *
 * <p>SSE wire format: {@code id: <run_event.id>} / {@code event: <AG-UI event type>} /
 * {@code data: <AguiEvent JSON>}; comment heartbeats every 15s keep proxies open.
 */
@Component
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AguiEventStreamer {

    private static final long POLL_IDLE_MS = 100L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final long STATUS_CHECK_INTERVAL_MS = 1_000L;
    private static final Set<String> TERMINAL_EVENT_TYPES = Set.of("RUN_FINISHED");
    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final RunEventRepository events;
    private final AgentRunRepository runs;

    public AguiEventStreamer(RunEventRepository events, AgentRunRepository runs) {
        this.events = events;
        this.runs = runs;
    }

    public SseEmitter stream(String clientId, String runId, long lastEventId) {
        if (!runs.belongsToClient(runId, clientId)) {
            throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
        }
        SseEmitter emitter = new SseEmitter(0L); // no server-side timeout; terminal event closes it
        AtomicBoolean alive = new AtomicBoolean(true);
        emitter.onCompletion(() -> alive.set(false));
        emitter.onTimeout(() -> alive.set(false));
        emitter.onError(e -> alive.set(false));

        CompletableFuture.runAsync(() -> tail(emitter, alive, clientId, runId, lastEventId));
        return emitter;
    }

    private void tail(SseEmitter emitter, AtomicBoolean alive, String clientId, String runId, long cursor) {
        long lastHeartbeat = System.currentTimeMillis();
        long lastStatusCheck = System.currentTimeMillis();
        try {
            while (alive.get()) {
                List<RunEventRepository.RunEvent> batch = events.after(clientId, runId, cursor);
                for (RunEventRepository.RunEvent event : batch) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.id()))
                            .name(event.type())
                            .data(event.payloadJson(), MediaType.APPLICATION_JSON));
                    cursor = event.id();
                    lastHeartbeat = System.currentTimeMillis();
                    if (TERMINAL_EVENT_TYPES.contains(event.type())) {
                        emitter.complete();
                        return;
                    }
                }
                if (batch.isEmpty()) {
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                        emitter.send(SseEmitter.event().comment("ping"));
                        lastHeartbeat = now;
                    }
                    // Belt-and-braces: if the run is already terminal but no terminal event was
                    // observed (e.g. crash before RUN_FINISHED persistence), stop cleanly.
                    if (now - lastStatusCheck >= STATUS_CHECK_INTERVAL_MS) {
                        lastStatusCheck = now;
                        var run = runs.findById(runId);
                        if (run.isPresent() && TERMINAL_RUN_STATUSES.contains(run.get().status())
                                && events.after(clientId, runId, cursor).isEmpty()) {
                            emitter.complete();
                            return;
                        }
                    }
                    Thread.sleep(POLL_IDLE_MS);
                }
            }
        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException ignored) {
                // client already disconnected
            }
        }
    }
}
