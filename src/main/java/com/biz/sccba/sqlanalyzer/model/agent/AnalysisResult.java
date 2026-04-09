package com.biz.sccba.sqlanalyzer.model.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分析结果
 */
public class AnalysisResult {
    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * SQL 语句
     */
    private String sql;

    /**
     * 分析结果摘要
     */
    private String summary;

    /**
     * 性能问题列表
     */
    private List<String> issues;

    /**
     * 优化建议列表
     */
    private List<String> suggestions;

    /**
     * 执行计划分析
     */
    private String executionPlanAnalysis;

    /**
     * 推荐的索引
     */
    private List<Map<String, Object>> recommendedIndexes;

    /**
     * 分析耗时（毫秒）
     */
    private Long analysisDuration;

    /**
     * 分析时间
     */
    private LocalDateTime analyzedAt;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 完整报告
     */
    private String report;

    public AnalysisResult() {}

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public String getExecutionPlanAnalysis() { return executionPlanAnalysis; }
    public void setExecutionPlanAnalysis(String executionPlanAnalysis) { this.executionPlanAnalysis = executionPlanAnalysis; }

    public List<Map<String, Object>> getRecommendedIndexes() { return recommendedIndexes; }
    public void setRecommendedIndexes(List<Map<String, Object>> recommendedIndexes) { this.recommendedIndexes = recommendedIndexes; }

    public Long getAnalysisDuration() { return analysisDuration; }
    public void setAnalysisDuration(Long analysisDuration) { this.analysisDuration = analysisDuration; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    /**
     * 添加问题
     */
    public void addIssue(String issue) {
        if (issues == null) {
            issues = new ArrayList<>();
        }
        issues.add(issue);
    }

    /**
     * 添加建议
     */
    public void addSuggestion(String suggestion) {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }
        suggestions.add(suggestion);
    }

    /**
     * builder 方法
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String sql;
        private String summary;
        private List<String> issues;
        private List<String> suggestions;
        private String executionPlanAnalysis;
        private List<Map<String, Object>> recommendedIndexes;
        private Long analysisDuration;
        private LocalDateTime analyzedAt;
        private boolean success;
        private String errorMessage;
        private String report;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder sql(String sql) { this.sql = sql; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder issues(List<String> issues) { this.issues = issues; return this; }
        public Builder suggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }
        public Builder executionPlanAnalysis(String executionPlanAnalysis) { this.executionPlanAnalysis = executionPlanAnalysis; return this; }
        public Builder recommendedIndexes(List<Map<String, Object>> recommendedIndexes) { this.recommendedIndexes = recommendedIndexes; return this; }
        public Builder analysisDuration(Long analysisDuration) { this.analysisDuration = analysisDuration; return this; }
        public Builder analyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder report(String report) { this.report = report; return this; }

        public AnalysisResult build() {
            AnalysisResult result = new AnalysisResult();
            result.setSessionId(sessionId);
            result.setSql(sql);
            result.setSummary(summary);
            result.setIssues(issues);
            result.setSuggestions(suggestions);
            result.setExecutionPlanAnalysis(executionPlanAnalysis);
            result.setRecommendedIndexes(recommendedIndexes);
            result.setAnalysisDuration(analysisDuration);
            result.setAnalyzedAt(analyzedAt);
            result.setSuccess(success);
            result.setErrorMessage(errorMessage);
            result.setReport(report);
            return result;
        }
    }
}
