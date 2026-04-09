package com.biz.sccba.sqlanalyzer.model.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析会话
 * 记录分析过程中的状态和历史
 */
public class AnalysisSession {
    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户请求
     */
    private String userRequest;

    /**
     * 会话状态
     */
    private SessionStatus status;

    /**
     * 当前 SQL
     */
    private String currentSql;

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * LLM 名称
     */
    private String llmName;

    /**
     * 推理步骤历史
     */
    private List<ReasoningStep> reasoningSteps;

    /**
     * 工具调用历史
     */
    private List<ToolCallRecord> toolCalls;

    /**
     * 上下文变量
     */
    private Map<String, Object> context;

    /**
     * 分析结果
     */
    private AnalysisResult result;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    public AnalysisSession() {}

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserRequest() { return userRequest; }
    public void setUserRequest(String userRequest) { this.userRequest = userRequest; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public String getCurrentSql() { return currentSql; }
    public void setCurrentSql(String currentSql) { this.currentSql = currentSql; }

    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }

    public String getLlmName() { return llmName; }
    public void setLlmName(String llmName) { this.llmName = llmName; }

    public List<ReasoningStep> getReasoningSteps() { return reasoningSteps; }
    public void setReasoningSteps(List<ReasoningStep> reasoningSteps) { this.reasoningSteps = reasoningSteps; }

    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public AnalysisResult getResult() { return result; }
    public void setResult(AnalysisResult result) { this.result = result; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 添加推理步骤
     */
    public void addReasoningStep(ReasoningStep step) {
        if (reasoningSteps == null) {
            reasoningSteps = new ArrayList<>();
        }
        reasoningSteps.add(step);
    }

    /**
     * 添加工具调用记录
     */
    public void addToolCall(ToolCallRecord record) {
        if (toolCalls == null) {
            toolCalls = new ArrayList<>();
        }
        toolCalls.add(record);
    }

    /**
     * 设置上下文变量
     */
    public void setContextValue(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
    }

    /**
     * 获取上下文变量
     */
    public Object getContextValue(String key) {
        return context != null ? context.get(key) : null;
    }

    /**
     * _builder 方法用于链式创建
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String userId;
        private String userRequest;
        private SessionStatus status;
        private String currentSql;
        private String datasourceName;
        private String llmName;
        private List<ReasoningStep> reasoningSteps;
        private List<ToolCallRecord> toolCalls;
        private Map<String, Object> context;
        private AnalysisResult result;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder userRequest(String userRequest) { this.userRequest = userRequest; return this; }
        public Builder status(SessionStatus status) { this.status = status; return this; }
        public Builder currentSql(String currentSql) { this.currentSql = currentSql; return this; }
        public Builder datasourceName(String datasourceName) { this.datasourceName = datasourceName; return this; }
        public Builder llmName(String llmName) { this.llmName = llmName; return this; }
        public Builder reasoningSteps(List<ReasoningStep> reasoningSteps) { this.reasoningSteps = reasoningSteps; return this; }
        public Builder toolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }
        public Builder result(AnalysisResult result) { this.result = result; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public AnalysisSession build() {
            AnalysisSession session = new AnalysisSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setUserRequest(userRequest);
            session.setStatus(status);
            session.setCurrentSql(currentSql);
            session.setDatasourceName(datasourceName);
            session.setLlmName(llmName);
            session.setReasoningSteps(reasoningSteps);
            session.setToolCalls(toolCalls);
            session.setContext(context);
            session.setResult(result);
            session.setCreatedAt(createdAt);
            session.setUpdatedAt(updatedAt);
            return session;
        }
    }

    /**
     * 推理步骤
     */
    public static class ReasoningStep {
        private Integer stepNumber;
        private String reasoning;
        private String decision;
        private String thought;
        private String action;
        private String observation;
        private LocalDateTime timestamp;

        public ReasoningStep() {}

        public Integer getStepNumber() { return stepNumber; }
        public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }

        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }

        public String getThought() { return thought; }
        public void setThought(String thought) { this.thought = thought; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getObservation() { return observation; }
        public void setObservation(String observation) { this.observation = observation; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Integer stepNumber;
            private String reasoning;
            private String decision;
            private String thought;
            private String action;
            private String observation;
            private LocalDateTime timestamp;

            public Builder stepNumber(Integer stepNumber) { this.stepNumber = stepNumber; return this; }
            public Builder reasoning(String reasoning) { this.reasoning = reasoning; return this; }
            public Builder decision(String decision) { this.decision = decision; return this; }
            public Builder thought(String thought) { this.thought = thought; return this; }
            public Builder action(String action) { this.action = action; return this; }
            public Builder observation(String observation) { this.observation = observation; return this; }
            public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

            public ReasoningStep build() {
                ReasoningStep step = new ReasoningStep();
                step.setStepNumber(stepNumber);
                step.setReasoning(reasoning);
                step.setDecision(decision);
                step.setThought(thought);
                step.setAction(action);
                step.setObservation(observation);
                step.setTimestamp(timestamp);
                return step;
            }
        }
    }

    /**
     * 工具调用记录
     */
    public static class ToolCallRecord {
        private String toolName;
        private Map<String, Object> parameters;
        private String result;
        private Long duration;
        private LocalDateTime timestamp;

        public ToolCallRecord() {}

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public Long getDuration() { return duration; }
        public void setDuration(Long duration) { this.duration = duration; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        PENDING("待处理"),
        ACTIVE("进行中"),
        COMPLETED("已完成"),
        FAILED("失败");

        private final String displayName;

        SessionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
