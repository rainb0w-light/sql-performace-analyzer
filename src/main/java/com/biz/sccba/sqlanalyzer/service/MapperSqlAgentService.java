package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.model.request.SqlAgentRequest;
import com.biz.sccba.sqlanalyzer.model.response.MapperSqlAgentResponse;
import com.biz.sccba.sqlanalyzer.model.response.SqlAgentResponse;
import com.biz.sccba.sqlanalyzer.model.response.SqlRiskAssessmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 能够分析 mapper.xml 文件中所有 sql 的，基于链式工作流程的 agent 服务
 */
@Service
public class MapperSqlAgentService {

    private static final Logger logger = LoggerFactory.getLogger(MapperSqlAgentService.class);

    @Autowired
    private MyBatisConfigurationParserService myBatisParserService;

    @Autowired
    private ColumnStatisticsParserService statisticsParserService;

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    @Autowired
    private DataSourceManagerService dataSourceManagerService;


    @Autowired
    private SqlAgentService sqlAgentService;

    /**
     * 分析 Mapper XML 中的所有 SQL
     */
    public MapperSqlAgentResponse analyzeMapperXml(String xmlContent, String namespace, String datasourceName, String llmName) {



        logger.info("开始分析 Mapper XML: namespace={}, datasource={}", namespace, datasourceName);
        
        // 1. 解析 XML
        List<ParsedSqlQuery> queries = myBatisParserService.parseMapperXml(xmlContent, namespace).getQueries();
        
        MapperSqlAgentResponse response = new MapperSqlAgentResponse();
        response.setMapperNamespace(namespace);
        List<SqlRiskAssessmentResponse> results = new ArrayList<>();

        // 2. 对每个 SQL 执行链式工作流
        for (ParsedSqlQuery query : queries) {
            SqlAgentRequest sqlAgentRequest = new SqlAgentRequest();

            sqlAgentRequest.setSql(query.getSql());
            sqlAgentRequest.setLlmName(llmName);
            sqlAgentRequest.setDatasourceName(datasourceName);
            SqlRiskAssessmentResponse sqlRiskAssessmentResponse = sqlAgentService.analyze(sqlAgentRequest);
            results.add(sqlRiskAssessmentResponse);
        }

        response.setResults(results);
//        response.setOverallSummary(generateOverallSummary(results));
        
        return response;
    }

    private String generateOverallSummary(List<SqlAgentResponse> results) {
        return "共分析了 " + results.size() + " 条 SQL 语句。";
    }
}

