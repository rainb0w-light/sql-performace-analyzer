package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.request.SqlAnalysisRequest;
import com.biz.sccba.sqlanalyzer.response.SqlAnalysisResponse;
import com.biz.sccba.sqlanalyzer.service.DataSourceManagerService;
import com.biz.sccba.sqlanalyzer.service.LlmManagerService;
import com.biz.sccba.sqlanalyzer.service.SqlPerformanceAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/sql")
public class SqlAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisController.class);

    @Autowired
    private SqlPerformanceAnalysisService analysisService;

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    @Autowired
    private LlmManagerService llmManagerService;

    /**
     * 分析SQL性能
     * POST /api/sql/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeSql(@RequestBody SqlAnalysisRequest request) {
        try {
            // 验证请求
            if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("SQL语句不能为空"));
            }

            String sql = request.getSql().trim();
            String datasourceName = request.getDatasourceName();
            String llmName = request.getLlmName();
            logger.info("收到SQL分析请求: sql={}, datasource={}, llm={}", sql, datasourceName, llmName);

            // 执行分析
            SqlAnalysisResponse response = analysisService.analyzeSql(sql, datasourceName, llmName);

            logger.info("SQL分析完成");
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            logger.error("配置错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("配置错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("SQL分析失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("SQL分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有可用数据源列表
     * GET /api/datasources
     */
    @GetMapping("/datasources")
    public ResponseEntity<?> getDataSources() {
        try {
            List<DataSourceManagerService.DataSourceInfo> dataSources = dataSourceManagerService.getAllDataSources();
            return ResponseEntity.ok(dataSources);
        } catch (Exception e) {
            logger.error("获取数据源列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("获取数据源列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有可用大模型列表
     * GET /api/llms
     */
    @GetMapping("/llms")
    public ResponseEntity<?> getLlms() {
        try {
            List<LlmManagerService.LlmInfo> llms = llmManagerService.getAllLlms();
            return ResponseEntity.ok(llms);
        } catch (Exception e) {
            logger.error("获取大模型列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("获取大模型列表失败: " + e.getMessage()));
        }
    }

    /**
     * 下载Markdown格式的报告
     * POST /api/reports/download
     */
    @PostMapping("/reports/download")
    public ResponseEntity<?> downloadMarkdownReport(@RequestBody SqlAnalysisRequest request) {
        try {
            // 验证请求
            if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("SQL语句不能为空"));
            }

            String sql = request.getSql().trim();
            String datasourceName = request.getDatasourceName();
            String llmName = request.getLlmName();
            logger.info("收到Markdown报告下载请求: sql={}, datasource={}, llm={}", sql, datasourceName, llmName);

            // 执行分析
            SqlAnalysisResponse response = analysisService.analyzeSql(sql, datasourceName, llmName);
            
            // 获取Markdown报告
            String markdownReport = response.getReport();
            if (markdownReport == null || markdownReport.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("报告内容为空"));
            }

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "sql-analysis-report_" + timestamp + ".md";

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/markdown; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(markdownReport.getBytes(StandardCharsets.UTF_8).length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(markdownReport);

        } catch (Exception e) {
            logger.error("下载Markdown报告失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("下载Markdown报告失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     * GET /api/sql/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(new HealthResponse("OK", "SQL性能分析服务运行正常"));
    }

    /**
     * 错误响应类
     */
    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String message) {
            this.error = "ERROR";
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 健康检查响应类
     */
    public static class HealthResponse {
        private String status;
        private String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

