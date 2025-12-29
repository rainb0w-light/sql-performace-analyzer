package com.biz.sccba.sqlanalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * SQL 参数填充结果
 * 包含多个测试场景的填充结果
 */
@Data
public class SqlFillingResult {
    
    /**
     * 原始 SQL 模板
     */
    @JsonProperty("originalSql")
    private String originalSql;
    
    /**
     * 多个场景的填充结果
     */
    @JsonProperty("scenarios")
    private List<FilledSqlScenario> scenarios;
    
    /**
     * LLM 的推理过程和参数选择理由
     */
    @JsonProperty("reasoning")
    private String reasoning;
}

