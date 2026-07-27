package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** sessions/messages resource API (docs/contracts/rest-api.md §1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class SessionController {

    private final SessionService sessions;
    private final BearerClients bearer;

    public SessionController(SessionService sessions, BearerClients bearer) {
        this.sessions = sessions;
        this.bearer = bearer;
    }

    @PostMapping("/sessions")
    public Object createSession(@RequestHeader("Authorization") String authorization,
                                @Valid @RequestBody SessionCreateRequest request) {
        return sessions.createSession(bearer.clientId(authorization), request.title());
    }

    @GetMapping("/sessions")
    public List<?> listSessions(@RequestHeader("Authorization") String authorization) {
        return sessions.listSessions(bearer.clientId(authorization));
    }

    @GetMapping("/sessions/{sessionId}")
    public Object getSession(@RequestHeader("Authorization") String authorization,
                             @PathVariable String sessionId) {
        return sessions.getSession(bearer.clientId(authorization), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<?> messages(@RequestHeader("Authorization") String authorization,
                            @PathVariable String sessionId) {
        return sessions.messages(bearer.clientId(authorization), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public List<?> runs(@RequestHeader("Authorization") String authorization,
                        @PathVariable String sessionId) {
        return sessions.runs(bearer.clientId(authorization), sessionId);
    }

    /**
     * Transitional run submission used by the legacy IDEA flow. The AG-UI path
     * ({@code POST /api/v1/agui/runs}) is the standard entry point.
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public SessionService.RunSubmission message(@RequestHeader("Authorization") String authorization,
                                                @PathVariable String sessionId,
                                                @Valid @RequestBody MessageRequest request) {
        return sessions.submit(bearer.clientId(authorization), sessionId, request.content(), request.messageType(),
                request.modelName(), request.artifactIds(), request.datasourceProfile());
    }

    public record SessionCreateRequest(String title) {}

    public record MessageRequest(@NotBlank String content, String messageType, String modelName,
                                 List<String> artifactIds, Map<String, String> datasourceProfile) {
        public MessageRequest {
            if (messageType == null || messageType.isBlank()) messageType = "TEXT";
        }
    }
}
