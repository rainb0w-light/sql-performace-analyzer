package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.websocket.WebSocketMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 消息模型单元测试
 */
class WebSocketMessageTest {

    @Test
    @DisplayName("测试创建 WebSocket 消息")
    void testConstructor() {
        WebSocketMessage message = new WebSocketMessage("TEST_TYPE", "session-123");

        assertNotNull(message);
        assertEquals("TEST_TYPE", message.getType());
        assertEquals("session-123", message.getSessionId());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("测试静态工厂方法")
    void testStaticFactoryMethod() {
        WebSocketMessage message = WebSocketMessage.of("ANALYZE_SQL", "session-456");

        assertNotNull(message);
        assertEquals("ANALYZE_SQL", message.getType());
        assertEquals("session-456", message.getSessionId());
    }

    @Test
    @DisplayName("测试添加负载数据")
    void testWithPayload() {
        WebSocketMessage message = new WebSocketMessage("TEST", "session-789");

        // 测试 withPayload 方法
        message.withPayload(java.util.Map.of("key1", "value1", "key2", 123));

        assertNotNull(message.getPayload());
        assertEquals("value1", message.getPayload().get("key1"));
        assertEquals(123, message.getPayload().get("key2"));
    }

    @Test
    @DisplayName("测试逐个添加负载数据")
    void testAddPayload() {
        WebSocketMessage message = new WebSocketMessage("TEST", "session-000");

        message.addPayload("sql", "SELECT * FROM users");
        message.addPayload("datasource", "test-db");
        message.addPayload("limit", 100);

        assertNotNull(message.getPayload());
        assertEquals("SELECT * FROM users", message.getPayload().get("sql"));
        assertEquals("test-db", message.getPayload().get("datasource"));
        assertEquals(100, message.getPayload().get("limit"));
    }

    @Test
    @DisplayName("测试 null 负载处理")
    void testNullPayload() {
        WebSocketMessage message = new WebSocketMessage("TEST", "session-null");

        // 初始负载应为 null
        assertNull(message.getPayload());

        // 添加第一个负载时应自动创建 Map
        message.addPayload("key", "value");
        assertNotNull(message.getPayload());
        assertEquals(1, message.getPayload().size());
    }

    @Test
    @DisplayName("测试消息类型")
    void testMessageTypes() {
        String[] types = {
            "ANALYZE_SQL",
            "ANALYZE_TABLE",
            "PARSE_MAPPER",
            "CONFIRM_DDL",
            "GET_SESSION",
            "LIST_SESSIONS",
            "PING"
        };

        for (String type : types) {
            WebSocketMessage message = new WebSocketMessage(type, "session");
            assertEquals(type, message.getType());
        }
    }

    @Test
    @DisplayName("测试会话 ID 设置器")
    void testSessionIdSetter() {
        WebSocketMessage message = new WebSocketMessage();
        message.setSessionId("new-session-id");

        assertEquals("new-session-id", message.getSessionId());
    }

    @Test
    @DisplayName("测试类型设置器")
    void testTypeSetter() {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("NEW_TYPE");

        assertEquals("NEW_TYPE", message.getType());
    }

    @Test
    @DisplayName("测试时间戳设置器")
    void testTimestampSetter() {
        WebSocketMessage message = new WebSocketMessage();
        java.time.LocalDateTime newTime = java.time.LocalDateTime.now();
        message.setTimestamp(newTime);

        assertEquals(newTime, message.getTimestamp());
    }

    @Test
    @DisplayName("测试 TUI 命令结果消息类型")
    void testTuiCommandResultType() {
        WebSocketMessage message = new WebSocketMessage("TUI_COMMAND_RESULT", "session-tui");

        assertNotNull(message);
        assertEquals("TUI_COMMAND_RESULT", message.getType());
        assertEquals("session-tui", message.getSessionId());
    }

    @Test
    @DisplayName("测试 TUI 命令结果负载")
    void testTuiCommandResultPayload() {
        WebSocketMessage message = new WebSocketMessage("TUI_COMMAND_RESULT", "session-tui");

        message.addPayload("success", true);
        message.addPayload("message", "命令执行成功");
        message.addPayload("sessionId", "session-tui");

        assertNotNull(message.getPayload());
        assertEquals(true, message.getPayload().get("success"));
        assertEquals("命令执行成功", message.getPayload().get("message"));
        assertEquals("session-tui", message.getPayload().get("sessionId"));
    }
}
