package com.biz.sccba.sqlanalyzer.model.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客户端消息类型枚举测试
 */
class ClientMessageTypeTest {

    @Test
    @DisplayName("测试客户端消息类型枚举值")
    void testEnumValues() {
        ClientMessageType[] expectedTypes = {
            ClientMessageType.ANALYZE_SQL,
            ClientMessageType.ANALYZE_TABLE,
            ClientMessageType.PARSE_MAPPER,
            ClientMessageType.CONFIRM_DDL,
            ClientMessageType.CANCEL_DDL,
            ClientMessageType.GET_SESSION,
            ClientMessageType.LIST_SESSIONS,
            ClientMessageType.CLEAR_SESSION,
            ClientMessageType.PING,
            ClientMessageType.UNKNOWN
        };

        for (ClientMessageType type : expectedTypes) {
            assertNotNull(type);
        }
    }

    @Test
    @DisplayName("测试 valueOf 方法")
    void testValueOf() {
        assertEquals(ClientMessageType.ANALYZE_SQL, ClientMessageType.valueOf("ANALYZE_SQL"));
        assertEquals(ClientMessageType.ANALYZE_TABLE, ClientMessageType.valueOf("ANALYZE_TABLE"));
        assertEquals(ClientMessageType.PARSE_MAPPER, ClientMessageType.valueOf("PARSE_MAPPER"));
        assertEquals(ClientMessageType.CONFIRM_DDL, ClientMessageType.valueOf("CONFIRM_DDL"));
        assertEquals(ClientMessageType.PING, ClientMessageType.valueOf("PING"));
    }

    @Test
    @DisplayName("测试无效类型的异常处理")
    void testInvalidType() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClientMessageType.valueOf("INVALID_TYPE");
        });
    }
}
