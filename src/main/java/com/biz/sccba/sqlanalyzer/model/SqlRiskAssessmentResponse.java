package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 风险评估响应
 * 包含双阶段验证的完整结果
 * 支持多场景测试
 */
@Data
public class SqlRiskAssessmentResponse {
    
    /**
     * 原始 SQL 语句
     */
    private String originalSql;
    
    /**
     * 填充参数后的 SQL（用于实际执行）
     * 向后兼容字段，返回第一个场景的 SQL
     */
    private String filledSql;
    
    /**
     * 使用的直方图数据摘要
     */
    private List<HistogramSummary> histogramData;
    
    /**
     * Stage 1: Predictor 预测结果
     */
    private SqlRiskPrediction predictorResult;
    
    /**
     * Stage 2: Verifier 实际执行计划
     * 向后兼容字段，返回第一个场景的执行计划
     */
    private ExecutionPlan actualExplainPlan;
    
    /**
     * LLM 生成的多场景 SQL 填充结果
     */
    private SqlFillingResult fillingResult;
    
    /**
     * 所有场景的验证结果
     */
    private List<com.biz.sccba.sqlanalyzer.service.SqlAgentService.ScenarioVerification> scenarioVerifications;
    
    /**
     * 验证对比结果（预测 vs 实际）
     */
    private VerificationComparison verificationComparison;
    
    /**
     * 是否进行了修正（LLM Refinement）
     */
    private Boolean refinementApplied;
    
    /**
     * 修正后的预测结果（如果进行了修正）
     */
    private SqlRiskPrediction refinedResult;
    
    /**
     * 最终风险等级：LOW, MEDIUM, HIGH, CRITICAL
     */
    private String finalRiskLevel;
    
    /**
     * 最终优化建议列表
     */
    private List<String> recommendations;
    
    /**
     * 处理时间（毫秒）
     */
    private Long processingTimeMs;
    
    /**
     * 直方图数据摘要（用于显示）
     */
    @Data
    public static class HistogramSummary {
        private String tableName;
        private String columnName;
        private String histogramType;
        private Integer bucketCount;
        private String minValue;
        private String maxValue;
        private Integer sampleCount;
    }
    
    /**
     * 验证对比结果
     */
    @Data
    public static class VerificationComparison {
        /**
         * 是否匹配（预测与实际是否一致）
         */
        private Boolean matched;
        
        /**
         * 对比详情
         */
        private Map<String, ComparisonDetail> details;
        
        /**
         * 对比总结
         */
        private String summary;
        
        /**
         * 偏差严重程度：NONE, MINOR, MODERATE, SEVERE
         */
        private String deviationSeverity;
    }
    
    /**
     * 单项对比详情
     */
    @Data
    public static class ComparisonDetail {
        private String metric;          // 指标名称
        private Object predictedValue;  // 预测值
        private Object actualValue;     // 实际值
        private Boolean matched;        // 是否匹配
        private String deviation;       // 偏差描述
    }
}




