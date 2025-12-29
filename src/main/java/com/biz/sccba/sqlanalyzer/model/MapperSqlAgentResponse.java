package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class MapperSqlAgentResponse {
    private String mapperNamespace;
    private List<SqlRiskAssessmentResponse> results;
    private String overallSummary;
}

