package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.TableStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 表分析服务
 * 执行 ANALYZE TABLE 更新表的统计信息
 */
@Service
public class TableAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TableAnalysisService.class);

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private ColumnStatisticsCollectorService columnStatisticsCollectorService;

    /**
     * 执行 ANALYZE TABLE 更新表的统计信息
     */
    public TableAnalysisResult analyzeTable(String tableName, String datasourceName) {
        logger.info("开始执行 ANALYZE TABLE: tableName={}, datasourceName={}", tableName, datasourceName);

        TableAnalysisResult result = new TableAnalysisResult();
        result.setTableName(tableName);
        result.setDatasourceName(datasourceName);

        try {
            // 1. 获取表结构信息
            String testSql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(testSql, datasourceName);
            TableStructure tableStructure = null;
            if (!structures.isEmpty()) {
                tableStructure = structures.get(0);
                result.setTableStructure(tableStructure);
            }

            // 2. 执行ANALYZE TABLE更新表的统计信息
            if (tableStructure != null && tableStructure.getColumns() != null && !tableStructure.getColumns().isEmpty()) {
                try {
                    List<String> allColumnNames = new ArrayList<>();
                    for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                        allColumnNames.add(col.getColumnName());
                    }
                    logger.info("执行ANALYZE TABLE更新表 {} 的统计信息，共 {} 列", tableName, allColumnNames.size());
                    
                    // 执行ANALYZE TABLE并获取详细结果
                    ColumnStatisticsCollectorService.AnalyzeTableResult analyzeResult = 
                            columnStatisticsCollectorService.analyzeTable(tableName, datasourceName, allColumnNames, null);
                    
                    // 保存详细结果
                    result.setAnalyzeTableResult(analyzeResult);
                    result.setSuccess(analyzeResult.isSuccess());
                    
                    if (analyzeResult.isSuccess()) {
                        result.setMessage("ANALYZE TABLE执行成功");
                        logger.info("ANALYZE TABLE执行完成: table={}, messages={}", tableName, analyzeResult.getMessages().size());
                    } else {
                        result.setMessage("ANALYZE TABLE执行失败: " + 
                                (analyzeResult.getErrorMessage() != null ? analyzeResult.getErrorMessage() : "未知错误"));
                        logger.warn("ANALYZE TABLE执行失败: table={}, error={}", tableName, analyzeResult.getErrorMessage());
                    }
                } catch (Exception e) {
                    logger.error("执行ANALYZE TABLE失败", e);
                    result.setSuccess(false);
                    result.setMessage("ANALYZE TABLE执行失败: " + e.getMessage());
                }
            } else {
                result.setSuccess(false);
                result.setMessage("无法获取表结构信息");
            }

            return result;

        } catch (Exception e) {
            logger.error("执行ANALYZE TABLE失败", e);
            result.setSuccess(false);
            result.setMessage("执行失败: " + e.getMessage());
            return result;
        }
    }


    /**
     * 表分析结果
     */
    public static class TableAnalysisResult {
        private boolean success;
        private String message;
        private String tableName;
        private String datasourceName;
        private TableStructure tableStructure;
        private ColumnStatisticsCollectorService.AnalyzeTableResult analyzeTableResult;

        // Getters and Setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

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

        public TableStructure getTableStructure() {
            return tableStructure;
        }

        public void setTableStructure(TableStructure tableStructure) {
            this.tableStructure = tableStructure;
        }

        public ColumnStatisticsCollectorService.AnalyzeTableResult getAnalyzeTableResult() {
            return analyzeTableResult;
        }

        public void setAnalyzeTableResult(ColumnStatisticsCollectorService.AnalyzeTableResult analyzeTableResult) {
            this.analyzeTableResult = analyzeTableResult;
        }
    }
}

