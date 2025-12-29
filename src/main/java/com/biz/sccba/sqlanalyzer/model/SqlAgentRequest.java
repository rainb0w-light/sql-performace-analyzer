package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

@Data
public class SqlAgentRequest {
    private String sql;
    private String datasourceName;
    private String llmName;
}

