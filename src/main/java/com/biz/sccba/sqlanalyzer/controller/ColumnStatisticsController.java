package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlanComparison;
import com.biz.sccba.sqlanalyzer.service.ColumnStatisticsCollectorService;
import com.biz.sccba.sqlanalyzer.service.ExecutionPlanComparisonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列统计信息控制器
 * 提供数据分布收集、SQL分析和索引建议的API
 */
@RestController
@RequestMapping("/api/statistics")
public class ColumnStatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticsController.class);

    @Autowired
    private ColumnStatisticsCollectorService collectorService;

    @Autowired
    private ExecutionPlanComparisonService comparisonService;

    /**
     * 收集指定表的列统计信息
     * POST /api/statistics/collect
     * 
     * 请求体：
     * {
     *   "tableName": "user",
     *   "datasourceName": "mysql-primary",
     *   "columns": ["id", "name", "email"],  // 可选，如果不指定则收集所有列
     *   "bucketCount": 100  // 可选，默认100
     * }
     */
    @PostMapping("/collect")
    public ResponseEntity<?> collectStatistics(@RequestBody CollectStatisticsRequest request) {
        try {
            if (request.getTableName() == null || request.getTableName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("表名不能为空"));
            }

            String tableName = request.getTableName().trim();
            String datasourceName = request.getDatasourceName();
            List<String> columns = request.getColumns();
            Integer bucketCount = request.getBucketCount() != null ? request.getBucketCount() : 100;

            logger.info("收到收集统计信息请求: table={}, datasource={}, columns={}, buckets={}", 
                       tableName, datasourceName, columns, bucketCount);

            List<ColumnStatistics> statistics = collectorService.collectTableStatistics(
                tableName, datasourceName, columns, bucketCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tableName", tableName);
            response.put("datasourceName", datasourceName);
            response.put("count", statistics.size());
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("收集统计信息失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("收集统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 批量收集多个表的统计信息
     * POST /api/statistics/collect/batch
     */
    @PostMapping("/collect/batch")
    public ResponseEntity<?> collectBatchStatistics(@RequestBody BatchCollectRequest request) {
        try {
            if (request.getTableNames() == null || request.getTableNames().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("表名列表不能为空"));
            }

            String datasourceName = request.getDatasourceName();
            Integer bucketCount = request.getBucketCount() != null ? request.getBucketCount() : 100;

            logger.info("收到批量收集统计信息请求: tables={}, datasource={}, buckets={}", 
                       request.getTableNames(), datasourceName, bucketCount);

            List<ColumnStatistics> statistics = collectorService.collectMultipleTablesStatistics(
                request.getTableNames(), datasourceName, bucketCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("datasourceName", datasourceName);
            response.put("totalCount", statistics.size());
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("批量收集统计信息失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("批量收集统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 分析SQL并对比不同场景下的执行计划
     * POST /api/statistics/analyze
     * 
     * 请求体：
     * {
     *   "sql": "SELECT * FROM user WHERE id = #{id}",
     *   "datasourceName": "mysql-primary"
     * }
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeSqlWithDistribution(@RequestBody AnalyzeSqlRequest request) {
        try {
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("SQL语句不能为空"));
            }

            String sql = request.getSql().trim();
            String datasourceName = request.getDatasourceName();

            logger.info("收到SQL分析请求: sql={}, datasource={}", sql, datasourceName);

            ExecutionPlanComparison comparison = comparisonService.compareExecutionPlans(sql, datasourceName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("comparison", comparison);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("SQL分析失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("SQL分析失败: " + e.getMessage()));
        }
    }

    /**
     * 收集统计信息请求
     */
    public static class CollectStatisticsRequest {
        private String tableName;
        private String datasourceName;
        private List<String> columns;
        private Integer bucketCount;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getDatasourceName() {
            return datasourceName;
        }

        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public Integer getBucketCount() {
            return bucketCount;
        }

        public void setBucketCount(Integer bucketCount) {
            this.bucketCount = bucketCount;
        }
    }

    /**
     * 批量收集请求
     */
    public static class BatchCollectRequest {
        private List<String> tableNames;
        private String datasourceName;
        private Integer bucketCount;

        public List<String> getTableNames() {
            return tableNames;
        }

        public void setTableNames(List<String> tableNames) {
            this.tableNames = tableNames;
        }

        public String getDatasourceName() {
            return datasourceName;
        }

        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }

        public Integer getBucketCount() {
            return bucketCount;
        }

        public void setBucketCount(Integer bucketCount) {
            this.bucketCount = bucketCount;
        }
    }

    /**
     * SQL分析请求
     */
    public static class AnalyzeSqlRequest {
        private String sql;
        private String datasourceName;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String getDatasourceName() {
            return datasourceName;
        }

        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }
    }

    /**
     * 错误响应
     */
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
