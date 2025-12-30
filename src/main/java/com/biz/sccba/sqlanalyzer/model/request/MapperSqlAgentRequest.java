package com.biz.sccba.sqlanalyzer.model.request;

import lombok.Data;

/**
 * Mapper XML SQL Agent 分析请求
 */
@Data
public class MapperSqlAgentRequest {
    private String xmlContent;
    private String namespace;
    private String datasourceName;
    private String llmName;
}



