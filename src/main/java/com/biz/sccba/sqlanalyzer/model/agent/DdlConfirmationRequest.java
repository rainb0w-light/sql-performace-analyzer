package com.biz.sccba.sqlanalyzer.model.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * DDL 确认请求
 */
public class DdlConfirmationRequest {
    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * DDL 语句
     */
    private String ddlStatement;

    /**
     * DDL 类型
     */
    private DdlOperationType operationType;

    /**
     * 描述
     */
    private String description;

    /**
     * 影响分析
     */
    private String impactAnalysis;

    /**
     * 回滚语句
     */
    private String rollbackStatement;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDdlStatement() { return ddlStatement; }
    public void setDdlStatement(String ddlStatement) { this.ddlStatement = ddlStatement; }

    public DdlOperationType getType() { return operationType; }
    public void setType(DdlOperationType operationType) { this.operationType = operationType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImpactAnalysis() { return impactAnalysis; }
    public void setImpactAnalysis(String impactAnalysis) { this.impactAnalysis = impactAnalysis; }

    public String getRollbackStatement() { return rollbackStatement; }
    public void setRollbackStatement(String rollbackStatement) { this.rollbackStatement = rollbackStatement; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /**
     * DDL 操作类型
     */
    public enum DdlOperationType {
        CREATE_INDEX,
        DROP_INDEX,
        ALTER_TABLE,
        CREATE_TABLE,
        DROP_TABLE
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private String sessionId;
        private String ddlStatement;
        private DdlOperationType operationType;
        private String description;
        private String impactAnalysis;
        private String rollbackStatement;
        private Map<String, Object> metadata;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder ddlStatement(String ddlStatement) { this.ddlStatement = ddlStatement; return this; }
        public Builder operationType(DdlOperationType operationType) { this.operationType = operationType; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder impactAnalysis(String impactAnalysis) { this.impactAnalysis = impactAnalysis; return this; }
        public Builder rollbackStatement(String rollbackStatement) { this.rollbackStatement = rollbackStatement; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public DdlConfirmationRequest build() {
            DdlConfirmationRequest request = new DdlConfirmationRequest();
            request.setSessionId(sessionId);
            request.setDdlStatement(ddlStatement);
            request.setType(operationType);
            request.setDescription(description);
            request.setImpactAnalysis(impactAnalysis);
            request.setRollbackStatement(rollbackStatement);
            request.setMetadata(metadata != null ? metadata : new HashMap<>());
            return request;
        }
    }

    /**
     * builder 方法
     */
    public static Builder builder() {
        return new Builder();
    }
}
