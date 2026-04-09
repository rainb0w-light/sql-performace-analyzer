package com.biz.sccba.sqlanalyzer.model.websocket;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 消息基类
 */
public class WebSocketMessage {

    private String type;          // 消息类型
    private String sessionId;     // 会话 ID
    private LocalDateTime timestamp; // 时间戳
    private Map<String, Object> payload; // 消息负载

    public WebSocketMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public WebSocketMessage(String type, String sessionId) {
        this.type = type;
        this.sessionId = sessionId;
        this.timestamp = LocalDateTime.now();
    }

    // 静态工厂方法
    public static WebSocketMessage of(String type, String sessionId) {
        return new WebSocketMessage(type, sessionId);
    }

    public WebSocketMessage withPayload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    public WebSocketMessage addPayload(String key, Object value) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        this.payload.put(key, value);
        return this;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
