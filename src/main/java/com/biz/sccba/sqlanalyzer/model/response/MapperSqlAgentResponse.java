package com.biz.sccba.sqlanalyzer.model.response;

import lombok.Data;
import java.util.List;

/**
 * Mapper XML SQL Agent 分析响应
 */
@Data
public class MapperSqlAgentResponse {
    private String mapperNamespace;
    private List<SqlRiskAssessmentResponse> results;
    private String overallSummary;
}



