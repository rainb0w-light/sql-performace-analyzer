package com.biz.sccba.sqlanalyzer.model.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端消息类型枚举测试
 */
class ServerMessageTypeTest {

    @Test
    @DisplayName("测试服务端消息类型枚举值")
    void testEnumValues() {
        ServerMessageType[] expectedTypes = {
            ServerMessageType.ANALYSIS_START,
            ServerMessageType.ANALYSIS_PROGRESS,
            ServerMessageType.ANALYSIS_COMPLETE,
            ServerMessageType.ANALYSIS_ERROR,
            ServerMessageType.DDL_CONFIRMATION,
            ServerMessageType.SESSION_CREATED,
            ServerMessageType.SESSION_UPDATED,
            ServerMessageType.SESSION_DELETED,
            ServerMessageType.PONG,
            ServerMessageType.ERROR
        };

        for (ServerMessageType type : expectedTypes) {
            assertNotNull(type);
        }
    }

    @Test
    @DisplayName("测试 valueOf 方法")
    void testValueOf() {
        assertEquals(ServerMessageType.ANALYSIS_START, ServerMessageType.valueOf("ANALYSIS_START"));
        assertEquals(ServerMessageType.ANALYSIS_COMPLETE, ServerMessageType.valueOf("ANALYSIS_COMPLETE"));
        assertEquals(ServerMessageType.DDL_CONFIRMATION, ServerMessageType.valueOf("DDL_CONFIRMATION"));
        assertEquals(ServerMessageType.PONG, ServerMessageType.valueOf("PONG"));
    }

    @Test
    @DisplayName("测试无效类型的异常处理")
    void testInvalidType() {
        assertThrows(IllegalArgumentException.class, () -> {
            ServerMessageType.valueOf("INVALID_TYPE");
        });
    }
}
