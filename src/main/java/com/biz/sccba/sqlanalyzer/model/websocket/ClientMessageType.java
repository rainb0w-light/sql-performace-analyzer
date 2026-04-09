package com.biz.sccba.sqlanalyzer.model.websocket;

/**
 * 客户端消息类型枚举
 */
public enum ClientMessageType {
    // 分析请求
    ANALYZE_SQL,        // 分析 SQL
    ANALYZE_TABLE,      // 分析表
    PARSE_MAPPER,       // 解析 MyBatis Mapper

    // TUI 命令
    TUI_COMMAND,        // TUI 终端命令

    // 确认操作
    CONFIRM_DDL,        // 确认 DDL 操作
    CANCEL_DDL,         // 取消 DDL 操作

    // 会话管理
    GET_SESSION,        // 获取会话详情
    LIST_SESSIONS,      // 列出所有会话
    CLEAR_SESSION,      // 清除会话

    // 其他
    PING,               // 心跳
    UNKNOWN             // 未知类型
}
