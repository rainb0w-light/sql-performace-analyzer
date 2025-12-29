package com.biz.sccba.sqlanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LLM 预测的 SQL 风险评估结果
 * 用于接收 Stage 1 Predictor 的 JSON 输出
 */
@Data
public class SqlRiskPrediction {
    
    /**
     * 预估风险等级：LOW, MEDIUM, HIGH, CRITICAL
     */
    @JsonProperty("riskLevel")
    private String riskLevel;
    
    /**
     * 预估扫描行数
     */
    @JsonProperty("estimatedRowsExamined")
    private Long estimatedRowsExamined;
    
    /**
     * 预期是否使用索引
     */
    @JsonProperty("expectedIndexUsage")
    private Boolean expectedIndexUsage;
    
    /**
     * 预期使用的索引名称
     */
    @JsonProperty("expectedIndexName")
    private String expectedIndexName;
    
    /**
     * 预期的访问类型（如：ALL, index, range, ref, eq_ref, const）
     */
    @JsonProperty("expectedAccessType")
    private String expectedAccessType;
    
    /**
     * 建议的测试参数值（用于填充 SQL 占位符）
     * Key: 参数名或列名, Value: 建议值
     */
    @JsonProperty("suggestedParameters")
    private Map<String, Object> suggestedParameters;
    
    /**
     * 预测理由
     */
    @JsonProperty("reasoning")
    private String reasoning;
    
    /**
     * 优化建议列表
     */
    @JsonProperty("recommendations")
    private List<String> recommendations;
    
    /**
     * 预估查询成本
     */
    @JsonProperty("estimatedQueryCost")
    private Double estimatedQueryCost;
}




