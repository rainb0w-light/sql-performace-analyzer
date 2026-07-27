package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.agui.AguiEventStreamer;
import com.biz.sccba.sqlanalyzer.agui.AguiRunService;
import com.biz.sccba.sqlanalyzer.service.TokenService;
import io.agentscope.core.agui.model.RunAgentInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AG-UI over HTTPS SSE entry point (docs/contracts/ag-ui-mapping.md).
 *
 * <p>{@code POST /api/v1/agui/runs} starts a run and immediately streams persisted AG-UI events;
 * {@code GET /api/v1/agui/runs/{runId}/stream} resumes from {@code Last-Event-ID}. Clients keep a
 * single continuous connection per run and reconnect with the cursor on interruption.
 */
@RestController
@RequestMapping("/api/v1/agui")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AguiController {

    private final TokenService tokens;
    private final AguiRunService aguiRuns;
    private final AguiEventStreamer streamer;

    public AguiController(TokenService tokens, AguiRunService aguiRuns, AguiEventStreamer streamer) {
        this.tokens = tokens;
        this.aguiRuns = aguiRuns;
        this.streamer = streamer;
    }

    @PostMapping(value = "/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestHeader("Authorization") String authorization,
                          @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                          @RequestBody RunAgentInput input) {
        String clientId = clientId(authorization);
        AguiRunService.RunHandle handle = aguiRuns.submit(clientId, input);
        return streamer.stream(clientId, handle.runId(), parseEventId(lastEventId));
    }

    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("Authorization") String authorization,
                             @PathVariable String runId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return streamer.stream(clientId(authorization), runId, parseEventId(lastEventId));
    }

    private static long parseEventId(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String clientId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 必须使用 Bearer Token");
        }
        return tokens.resolveClientId(authorization.substring("Bearer ".length()).trim());
    }
}
