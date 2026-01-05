package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.request.FillingRecordsRequest;
import com.biz.sccba.sqlanalyzer.model.request.FillingRecordsResponse;
import com.biz.sccba.sqlanalyzer.model.request.MultiSqlAgentRequest;
import com.biz.sccba.sqlanalyzer.model.request.MultiSqlAgentResponse;
import com.biz.sccba.sqlanalyzer.service.SqlAgentService;
import com.biz.sccba.sqlanalyzer.service.SqlFillingService;
import com.biz.sccba.sqlanalyzer.model.AgentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql-agent")
public class SqlAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentController.class);

    @Autowired
    private SqlAgentService sqlAgentService;

    @Autowired
    private SqlFillingService sqlFillingService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody MultiSqlAgentRequest request) {
        try {
            // 验证请求
            if (request.getSqlItems() == null || request.getSqlItems().isEmpty()) {
                return ResponseEntity.badRequest().body("SQL 列表不能为空");
            }
            
            if (request.getDatasourceName() == null || request.getDatasourceName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("数据源名称不能为空");
            }
            
            if (request.getLlmName() == null || request.getLlmName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("LLM 名称不能为空");
            }
            
            logger.info("收到多 SQL Agent 分析请求，SQL 数量: {}", request.getSqlItems().size());
            
            // 验证所有 SQL
            List<Map<String, Object>> validationErrors = new ArrayList<>();
            for (int i = 0; i < request.getSqlItems().size(); i++) {
                MultiSqlAgentRequest.SqlItem sqlItem = request.getSqlItems().get(i);
                if (sqlItem.getSql() == null || sqlItem.getSql().trim().isEmpty()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("index", i);
                    error.put("sql", sqlItem.getSql());
                    error.put("error", "SQL 语句不能为空");
                    validationErrors.add(error);
                }
            }
            
            if (!validationErrors.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("errors", validationErrors);
                response.put("totalCount", request.getSqlItems().size());
                response.put("errorCount", validationErrors.size());
                return ResponseEntity.badRequest().body(response);
            }
            

                try {
                    MultiSqlAgentResponse result = sqlAgentService.analyzeMultipleSqlsWithWorkflow(
                            request.getSqlItems(),
                            request.getDatasourceName(),
                            request.getLlmName()
                    );
                    logger.info("多 SQL 批量分析完成，SQL 数量: {}", request.getSqlItems().size());
                    return ResponseEntity.ok(result);
                    
                } catch (AgentException e) {
                    logger.error("多 SQL 批量分析失败", e);
                    Map<String, Object> response = new HashMap<>();
                    response.put("error", e.getMessage());
                    response.put("errorCode", e.getErrorCode());
                    return ResponseEntity.internalServerError().body(response);
                }
            
        } catch (Exception e) {
            logger.error("多 SQL Agent 分析失败", e);
            return ResponseEntity.internalServerError().body("分析失败: " + e.getMessage());
        }
    }

    /**
     * 批量查询填充记录
     * POST /api/sql-agent/filling-records
     */
    @PostMapping("/filling-records")
    public ResponseEntity<?> getFillingRecords(@RequestBody FillingRecordsRequest request) {
        List<String> mapperIds = request.getMapperIds();
        String datasourceName = request.getDatasourceName();
        String llmName = request.getLlmName();
        try {
            if (mapperIds == null || mapperIds.isEmpty()) {
                return ResponseEntity.badRequest().body("mapperIds 不能为空");
            }
            
            if (datasourceName == null || datasourceName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("数据源名称不能为空");
            }
            
            if (llmName == null || llmName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("LLM 名称不能为空");
            }
            
            logger.info("收到批量查询填充记录请求，mapperIds数量: {}", mapperIds.size());
            
            FillingRecordsResponse response = sqlFillingService.getFillingRecords(
                    mapperIds, datasourceName, llmName);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询填充记录失败", e);
            return ResponseEntity.internalServerError().body("查询失败: " + e.getMessage());
        }
    }
}

