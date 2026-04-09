package com.biz.sccba.sqlanalyzer.model.websocket;

/**
 * 服务端消息类型枚举
 */
public enum ServerMessageType {
    // 分析状态
    ANALYSIS_START,         // 分析开始
    ANALYSIS_PROGRESS,      // 分析进度
    ANALYSIS_COMPLETE,      // 分析完成
    ANALYSIS_ERROR,         // 分析错误

    // DDL 确认
    DDL_CONFIRMATION,       // DDL 确认请求

    // 会话状态
    SESSION_CREATED,        // 会话已创建
    SESSION_UPDATED,        // 会话已更新
    SESSION_DELETED,        // 会话已删除

    // 其他
    PONG,                   // 心跳响应
    ERROR,                  // 通用错误
    TUI_COMMAND_RESULT      // TUI 命令执行结果
}
