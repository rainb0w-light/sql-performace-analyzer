package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.request.MapperSqlAgentRequest;
import com.biz.sccba.sqlanalyzer.model.request.SqlAgentRequest;
import com.biz.sccba.sqlanalyzer.model.response.MapperSqlAgentResponse;
import com.biz.sccba.sqlanalyzer.model.response.SqlRiskAssessmentResponse;
import com.biz.sccba.sqlanalyzer.service.MapperSqlAgentService;
import com.biz.sccba.sqlanalyzer.service.SqlAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql-agent")
public class SqlAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentController.class);

    @Autowired
    private SqlAgentService sqlAgentService;

    @Autowired
    private MapperSqlAgentService mapperSqlAgentService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody SqlAgentRequest request) {
        try {
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("SQL 语句不能为空");
            }
            logger.info("收到 SQL Agent 风险评估请求: {}", request.getSql());
            SqlRiskAssessmentResponse response = sqlAgentService.analyze(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("SQL Agent 风险评估失败", e);
            return ResponseEntity.internalServerError().body("分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-mapper")
    public ResponseEntity<?> analyzeMapper(@RequestBody MapperSqlAgentRequest request) {
        try {
            if (request.getXmlContent() == null || request.getXmlContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("XML 内容不能为空");
            }
            if (request.getNamespace() == null || request.getNamespace().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Namespace 不能为空");
            }
            logger.info("收到 Mapper SQL Agent 分析请求: {}", request.getNamespace());
            MapperSqlAgentResponse response = mapperSqlAgentService.analyzeMapperXml(
                request.getXmlContent(), 
                request.getNamespace(), 
                request.getDatasourceName(), 
                request.getLlmName()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Mapper SQL Agent 分析失败", e);
            return ResponseEntity.internalServerError().body("分析失败: " + e.getMessage());
        }
    }
}

