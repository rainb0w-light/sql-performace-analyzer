package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.ColumnStatisticValue;
import com.biz.sccba.sqlanalyzer.model.StatisticType;
import com.biz.sccba.sqlanalyzer.service.ColumnStatisticValueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列统计值控制器
 * 提供统计值的查询、计算、创建、更新、删除等API
 */
@RestController
@RequestMapping("/api/statistic-values")
public class ColumnStatisticValueController {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticValueController.class);

    @Autowired
    private ColumnStatisticValueService statisticValueService;

    /**
     * 查询统计值列表
     * GET /api/statistic-values?datasourceName=&tableName=&columnName=
     */
    @GetMapping
    public ResponseEntity<?> getStatisticValues(
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String columnName) {
        try {
            List<ColumnStatisticValue> values;
            
            if (columnName != null && !columnName.trim().isEmpty()) {
                // 查询指定列的统计值
                values = statisticValueService.findByColumn(datasourceName, tableName, columnName);
            } else if (tableName != null && !tableName.trim().isEmpty()) {
                // 查询指定表的所有列的统计值
                values = statisticValueService.findByTable(datasourceName, tableName);
            } else {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("必须提供tableName或columnName参数"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", values.size());
            response.put("values", values);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查询统计值失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("查询统计值失败: " + e.getMessage()));
        }
    }

    /**
     * 从ColumnStatistics计算统计值
     * POST /api/statistic-values/calculate
     * 
     * 请求体：
     * {
     *   "datasourceName": "mysql-primary",
     *   "tableName": "user",
     *   "columnName": "id"  // 可选，如果不指定则计算所有列
     * }
     */
    @PostMapping("/calculate")
    public ResponseEntity<?> calculateStatisticValues(@RequestBody CalculateRequest request) {
        try {
            if (request.getDatasourceName() == null || request.getTableName() == null) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("datasourceName和tableName不能为空"));
            }
            
            List<ColumnStatisticValue> values = statisticValueService.calculateFromStatistics(
                request.getDatasourceName(),
                request.getTableName(),
                request.getColumnName()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", values.size());
            response.put("values", values);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("计算统计值失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("计算统计值失败: " + e.getMessage()));
        }
    }

    /**
     * 创建或更新统计值（支持手工添加）
     * POST /api/statistic-values
     * 
     * 请求体：
     * {
     *   "datasourceName": "mysql-primary",
     *   "tableName": "user",
     *   "columnName": "id",
     *   "statisticType": "MEDIAN",
     *   "statisticValue": "100",
     *   "isManual": true
     * }
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateStatisticValue(@RequestBody CreateOrUpdateRequest request) {
        try {
            if (request.getDatasourceName() == null || request.getTableName() == null ||
                request.getColumnName() == null || request.getStatisticType() == null ||
                request.getStatisticValue() == null) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("必填字段不能为空"));
            }
            
            ColumnStatisticValue value = statisticValueService.saveOrUpdateStatisticValue(
                request.getDatasourceName(),
                request.getTableName(),
                request.getColumnName(),
                request.getStatisticType(),
                request.getStatisticValue(),
                request.getIsManual() != null ? request.getIsManual() : true
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("value", value);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("创建或更新统计值失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("创建或更新统计值失败: " + e.getMessage()));
        }
    }

    /**
     * 更新统计值
     * PUT /api/statistic-values/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStatisticValue(@PathVariable Long id, @RequestBody UpdateRequest request) {
        try {
            return statisticValueService.findById(id)
                .map(value -> {
                    if (request.getStatisticValue() != null) {
                        value.setStatisticValue(request.getStatisticValue());
                    }
                    if (request.getIsManual() != null) {
                        value.setIsManual(request.getIsManual());
                    }
                    
                    ColumnStatisticValue updated = statisticValueService.saveOrUpdateStatisticValue(
                        value.getDatasourceName(),
                        value.getTableName(),
                        value.getColumnName(),
                        value.getStatisticType(),
                        value.getStatisticValue(),
                        value.getIsManual()
                    );
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("value", updated);
                    
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
                
        } catch (Exception e) {
            logger.error("更新统计值失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("更新统计值失败: " + e.getMessage()));
        }
    }

    /**
     * 删除统计值
     * DELETE /api/statistic-values/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStatisticValue(@PathVariable Long id) {
        try {
            statisticValueService.delete(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("删除统计值失败", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse("删除统计值失败: " + e.getMessage()));
        }
    }

    /**
     * 计算统计值请求
     */
    public static class CalculateRequest {
        private String datasourceName;
        private String tableName;
        private String columnName;

        public String getDatasourceName() {
            return datasourceName;
        }

        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }
    }

    /**
     * 创建或更新请求
     */
    public static class CreateOrUpdateRequest {
        private String datasourceName;
        private String tableName;
        private String columnName;
        private StatisticType statisticType;
        private String statisticValue;
        private Boolean isManual;

        public String getDatasourceName() {
            return datasourceName;
        }

        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public StatisticType getStatisticType() {
            return statisticType;
        }

        public void setStatisticType(StatisticType statisticType) {
            this.statisticType = statisticType;
        }

        public String getStatisticValue() {
            return statisticValue;
        }

        public void setStatisticValue(String statisticValue) {
            this.statisticValue = statisticValue;
        }

        public Boolean getIsManual() {
            return isManual;
        }

        public void setIsManual(Boolean isManual) {
            this.isManual = isManual;
        }
    }

    /**
     * 更新请求
     */
    public static class UpdateRequest {
        private String statisticValue;
        private Boolean isManual;

        public String getStatisticValue() {
            return statisticValue;
        }

        public void setStatisticValue(String statisticValue) {
            this.statisticValue = statisticValue;
        }

        public Boolean getIsManual() {
            return isManual;
        }

        public void setIsManual(Boolean isManual) {
            this.isManual = isManual;
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

