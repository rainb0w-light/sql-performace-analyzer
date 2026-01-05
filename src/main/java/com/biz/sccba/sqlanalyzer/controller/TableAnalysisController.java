package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.TableAnalysisService;
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
    private TableAnalysisService tableAnalysisService;

    /**
     * 执行 ANALYZE TABLE 更新表的统计信息
     * GET /api/analysis/table/{tableName}
     */
    @GetMapping("/table/{tableName}")
    public ResponseEntity<?> analyzeTable(
            @PathVariable String tableName,
            @RequestParam(required = false) String datasourceName) {
        
        try {
            logger.info("收到 ANALYZE TABLE 请求: tableName={}, datasourceName={}", tableName, datasourceName);

            TableAnalysisService.TableAnalysisResult result =
                    tableAnalysisService.analyzeTable(tableName, datasourceName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("tableName", result.getTableName());
            response.put("datasourceName", result.getDatasourceName());
            
            // 添加 ANALYZE TABLE 详细结果
            if (result.getAnalyzeTableResult() != null) {
                Map<String, Object> analyzeResult = new HashMap<>();
                analyzeResult.put("success", result.getAnalyzeTableResult().isSuccess());
                analyzeResult.put("errorMessage", result.getAnalyzeTableResult().getErrorMessage());
                analyzeResult.put("messages", result.getAnalyzeTableResult().getMessages());
                response.put("analyzeTableResult", analyzeResult);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("执行 ANALYZE TABLE 失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("执行失败: " + e.getMessage()));
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

