package com.biz.sccba.sqlanalyzer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 填充后的 SQL 场景
 * 表示一个测试场景的 SQL 和参数
 */
@Data
public class FilledSqlScenario {
    
    /**
     * 场景名称：如 "最小值场景"、"最大值场景"、"典型值场景"
     */
    @JsonProperty("scenarioName")
    private String scenarioName;
    
    /**
     * 填充好的可执行 SQL
     */
    @JsonProperty("filledSql")
    private String filledSql;
    
    /**
     * 使用的参数值（参数名 -> 值）
     */
    @JsonProperty("parameters")
    private Map<String, Object> parameters;

}

