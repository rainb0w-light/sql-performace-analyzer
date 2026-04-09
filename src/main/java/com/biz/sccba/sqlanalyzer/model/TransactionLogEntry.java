package com.biz.sccba.sqlanalyzer.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 事务日志条目
 */
public class TransactionLogEntry {
    /**
     * 事务 ID
     */
    private String transactionId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 操作类型
     */
    private ActionType actionType;

    /**
     * 事务状态
     */
    private TransactionStatus status;

    /**
     * 结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 操作数据
     */
    private Map<String, Object> actionData;

    /**
     * 回滚数据
     */
    private Map<String, Object> rollbackData;

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getActionData() { return actionData; }
    public void setActionData(Map<String, Object> actionData) { this.actionData = actionData; }

    public Map<String, Object> getRollbackData() { return rollbackData; }
    public void setRollbackData(Map<String, Object> rollbackData) { this.rollbackData = rollbackData; }

    /**
     * 操作类型枚举
     */
    public enum ActionType {
        CREATE_INDEX,
        DROP_INDEX,
        ALTER_TABLE,
        ANALYZE_SQL,
        QUERY_KNOWLEDGE
    }

    /**
     * 事务状态枚举
     */
    public enum TransactionStatus {
        STARTED,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private String transactionId;
        private String sessionId;
        private ActionType actionType;
        private TransactionStatus status;
        private String result;
        private String errorMessage;
        private LocalDateTime timestamp;
        private Map<String, Object> actionData;
        private Map<String, Object> rollbackData;

        public Builder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder actionType(ActionType actionType) { this.actionType = actionType; return this; }
        public Builder status(TransactionStatus status) { this.status = status; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder actionData(Map<String, Object> actionData) { this.actionData = actionData; return this; }
        public Builder rollbackData(Map<String, Object> rollbackData) { this.rollbackData = rollbackData; return this; }

        public TransactionLogEntry build() {
            TransactionLogEntry entry = new TransactionLogEntry();
            entry.setTransactionId(transactionId);
            entry.setSessionId(sessionId);
            entry.setActionType(actionType);
            entry.setStatus(status);
            entry.setResult(result);
            entry.setErrorMessage(errorMessage);
            entry.setTimestamp(timestamp);
            entry.setActionData(actionData);
            entry.setRollbackData(rollbackData);
            return entry;
        }
    }

    /**
     * builder 方法
     */
    public static Builder builder() {
        return new Builder();
    }
}
