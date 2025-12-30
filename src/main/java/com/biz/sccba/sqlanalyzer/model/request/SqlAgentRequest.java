package com.biz.sccba.sqlanalyzer.model.request;

import lombok.Data;

/**
 * SQL Agent 分析请求
 */
@Data
public class SqlAgentRequest {
    private String sql;
    private String datasourceName;
    private String llmName;
}



