package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.TableQueryAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 单表分析API控制器
 */
@RestController
@RequestMapping("/api/analysis")
public class TableAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(TableAnalysisController.class);

    @Autowired
    private TableQueryAnalysisService tableQueryAnalysisService;

    /**
     * 分析指定表的所有查询
     * GET /api/analysis/table/{tableName}
     */
    @GetMapping("/table/{tableName}")
    public ResponseEntity<?> analyzeTable(
            @PathVariable String tableName,
            @RequestParam(required = false) String datasourceName) {
        
        try {
            logger.info("收到表分析请求: tableName={}, datasourceName={}", tableName, datasourceName);

            TableQueryAnalysisService.TableAnalysisResult result = 
                    tableQueryAnalysisService.analyzeTable(tableName, datasourceName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("tableName", result.getTableName());
            response.put("datasourceName", result.getDatasourceName());
            response.put("queryCount", result.getQueryCount());
            response.put("tableStructure", result.getTableStructure());
            response.put("queryAnalyses", result.getQueryAnalyses());
            response.put("suggestions", result.getSuggestions());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("表分析失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("分析失败: " + e.getMessage()));
        }
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}

