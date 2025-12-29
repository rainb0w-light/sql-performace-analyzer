package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

@Data
public class MapperSqlAgentRequest {
    private String xmlContent;
    private String namespace;
    private String datasourceName;
    private String llmName;
}

