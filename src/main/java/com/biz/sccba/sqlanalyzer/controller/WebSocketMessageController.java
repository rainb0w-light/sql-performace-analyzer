package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.agent.SQLAnalysisOrchestrator;
import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.model.websocket.ClientMessageType;
import com.biz.sccba.sqlanalyzer.model.websocket.ServerMessageType;
import com.biz.sccba.sqlanalyzer.model.websocket.WebSocketMessage;
import com.biz.sccba.sqlanalyzer.tui.TuiCommandHandler;
import com.biz.sccba.sqlanalyzer.tui.TuiCommandResult;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket 消息控制器
 * 处理来自前端的 STOMP 消息
 */
@Controller
public class WebSocketMessageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionMemoryService sessionMemory;
    private final SQLAnalysisOrchestrator orchestrator;
    private final TuiCommandHandler tuiCommandHandler;

    public WebSocketMessageController(
            SimpMessagingTemplate messagingTemplate,
            SessionMemoryService sessionMemory,
            SQLAnalysisOrchestrator orchestrator,
            TuiCommandHandler tuiCommandHandler) {
        this.messagingTemplate = messagingTemplate;
        this.sessionMemory = sessionMemory;
        this.orchestrator = orchestrator;
        this.tuiCommandHandler = tuiCommandHandler;
    }

    /**
     * 处理客户端请求
     * 路径：/app/message - 通用消息处理
     */
    @MessageMapping("/message")
    @SendTo("/topic/response")
    public WebSocketMessage handleMessage(@Payload WebSocketMessage message) {
        System.out.println("[WebSocket] 收到消息：" + message.getType());

        try {
            ClientMessageType type = ClientMessageType.valueOf(message.getType());

            return switch (type) {
                case ANALYZE_SQL -> handleAnalyzeSql(message);
                case ANALYZE_TABLE -> handleAnalyzeTable(message);
                case PARSE_MAPPER -> handleParseMapper(message);
                case GET_SESSION -> handleGetSession(message);
                case LIST_SESSIONS -> handleListSessions(message);
                case PING -> handlePing(message);
                case TUI_COMMAND -> handleTuiCommand(message);
                default -> createError(message.getSessionId(), "未知的消息类型：" + type);
            };

        } catch (IllegalArgumentException e) {
            return createError(message.getSessionId(), "无效的消息类型：" + message.getType());
        } catch (Exception e) {
            return createError(message.getSessionId(), "处理消息失败：" + e.getMessage());
        }
    }

    /**
     * 处理 SQL 分析请求
     */
    private WebSocketMessage handleAnalyzeSql(WebSocketMessage request) {
        String sessionId = request.getSessionId();
        Map<String, Object> payload = request.getPayload();

        if (payload == null || !payload.containsKey("sql")) {
            return createError(sessionId, "缺少必需参数：sql");
        }

        String sql = (String) payload.get("sql");
        String datasourceName = (String) payload.get("datasourceName");
        String llmName = (String) payload.get("llmName");

        // 发送分析开始通知
        sendAnalysisStart(sessionId, sql);

        try {
            // 执行分析
            AnalysisResult result = orchestrator.analyze(sql, datasourceName, llmName).block();

            // 发送分析完成通知
            return createAnalysisComplete(sessionId, result);

        } catch (Exception e) {
            return createAnalysisError(sessionId, e.getMessage());
        }
    }

    /**
     * 处理表分析请求
     */
    private WebSocketMessage handleAnalyzeTable(WebSocketMessage request) {
        String sessionId = request.getSessionId();
        Map<String, Object> payload = request.getPayload();

        if (payload == null || !payload.containsKey("tableName")) {
            return createError(sessionId, "缺少必需参数：tableName");
        }

        String tableName = (String) payload.get("tableName");
        String datasourceName = (String) payload.get("datasourceName");
        String llmName = (String) payload.get("llmName");

        // 发送分析开始通知
        sendAnalysisStart(sessionId, "分析表：" + tableName);

        try {
            // 执行分析
            AnalysisResult result = orchestrator.analyze("Analyze table: " + tableName, datasourceName, llmName).block();

            // 发送分析完成通知
            return createAnalysisComplete(sessionId, result);

        } catch (Exception e) {
            return createAnalysisError(sessionId, e.getMessage());
        }
    }

    /**
     * 处理 MyBatis Mapper 解析请求
     */
    private WebSocketMessage handleParseMapper(WebSocketMessage request) {
        String sessionId = request.getSessionId();
        Map<String, Object> payload = request.getPayload();

        if (payload == null || !payload.containsKey("filePath")) {
            return createError(sessionId, "缺少必需参数：filePath");
        }

        String filePath = (String) payload.get("filePath");
        String llmName = (String) payload.get("llmName");

        // 发送分析开始通知
        sendAnalysisStart(sessionId, "解析 Mapper 文件：" + filePath);

        try {
            // 执行解析
            AnalysisResult result = orchestrator.analyze("Parse mapper: " + filePath, null, llmName).block();

            // 发送分析完成通知
            return createAnalysisComplete(sessionId, result);

        } catch (Exception e) {
            return createAnalysisError(sessionId, e.getMessage());
        }
    }

    /**
     * 处理获取会话请求
     */
    private WebSocketMessage handleGetSession(WebSocketMessage request) {
        String sessionId = request.getSessionId();
        Map<String, Object> payload = request.getPayload();

        if (payload == null || !payload.containsKey("sessionId")) {
            return createError(sessionId, "缺少必需参数：sessionId");
        }

        String targetSessionId = (String) payload.get("sessionId");

        try {
            var session = sessionMemory.getSession(targetSessionId);
            if (session == null) {
                return createError(sessionId, "会话不存在：" + targetSessionId);
            }

            WebSocketMessage response = new WebSocketMessage(
                ServerMessageType.SESSION_UPDATED.name(),
                sessionId
            );
            response.addPayload("session", session);
            return response;

        } catch (Exception e) {
            return createError(sessionId, "获取会话失败：" + e.getMessage());
        }
    }

    /**
     * 处理列出会话请求
     */
    private WebSocketMessage handleListSessions(WebSocketMessage request) {
        String sessionId = request.getSessionId();

        try {
            var sessions = sessionMemory.getActiveSessions();

            WebSocketMessage response = new WebSocketMessage(
                ServerMessageType.SESSION_UPDATED.name(),
                sessionId
            );
            response.addPayload("sessions", sessions);
            return response;

        } catch (Exception e) {
            return createError(sessionId, "列出会话失败：" + e.getMessage());
        }
    }

    /**
     * 处理心跳请求
     */
    private WebSocketMessage handlePing(WebSocketMessage request) {
        WebSocketMessage response = new WebSocketMessage(
            ServerMessageType.PONG.name(),
            request.getSessionId()
        );
        response.addPayload("serverTime", System.currentTimeMillis());
        return response;
    }

    /**
     * 处理 TUI 命令请求
     */
    private WebSocketMessage handleTuiCommand(WebSocketMessage request) {
        String sessionId = request.getSessionId();
        Map<String, Object> payload = request.getPayload();

        if (payload == null || !payload.containsKey("command")) {
            return createError(sessionId, "缺少必需参数：command");
        }

        String commandInput = (String) payload.get("command");

        try {
            TuiCommandResult result = tuiCommandHandler.handleCommand(commandInput, sessionId);

            WebSocketMessage response = new WebSocketMessage(
                ServerMessageType.TUI_COMMAND_RESULT.name(),
                sessionId
            );
            response.addPayload("success", result.isSuccess());
            response.addPayload("message", result.getMessage());
            response.addPayload("sessionId", result.getSessionId());

            if (result.getData() != null && !result.getData().isEmpty()) {
                response.addPayload("data", result.getData());
            }

            return response;

        } catch (Exception e) {
            return createError(sessionId, "命令执行失败：" + e.getMessage());
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 发送分析开始通知
     */
    private void sendAnalysisStart(String sessionId, String description) {
        WebSocketMessage message = new WebSocketMessage(
            ServerMessageType.ANALYSIS_START.name(),
            sessionId
        );
        message.addPayload("description", description);
        message.addPayload("status", "running");

        messagingTemplate.convertAndSend("/topic/session/" + sessionId, message);
    }

    /**
     * 创建分析完成消息
     */
    private WebSocketMessage createAnalysisComplete(String sessionId, AnalysisResult result) {
        WebSocketMessage response = new WebSocketMessage(
            ServerMessageType.ANALYSIS_COMPLETE.name(),
            sessionId
        );
        response.addPayload("success", result.isSuccess());
        response.addPayload("report", result.getReport());
        response.addPayload("sessionId", sessionId);
        return response;
    }

    /**
     * 创建分析错误消息
     */
    private WebSocketMessage createAnalysisError(String sessionId, String error) {
        WebSocketMessage response = new WebSocketMessage(
            ServerMessageType.ANALYSIS_ERROR.name(),
            sessionId
        );
        response.addPayload("error", error);
        return response;
    }

    /**
     * 创建错误消息
     */
    private WebSocketMessage createError(String sessionId, String error) {
        WebSocketMessage response = new WebSocketMessage(
            ServerMessageType.ERROR.name(),
            sessionId
        );
        response.addPayload("error", error);
        return response;
    }
}
