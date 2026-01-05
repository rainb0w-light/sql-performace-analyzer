package com.biz.sccba.sqlanalyzer.model.request;

import lombok.Data;

/**
 * 多 SQL Agent 分析响应
 */
@Data
public class MultiSqlAgentResponse {
    
    /**
     * Markdown 格式的报告内容
     */
    private String reportContent;
}

