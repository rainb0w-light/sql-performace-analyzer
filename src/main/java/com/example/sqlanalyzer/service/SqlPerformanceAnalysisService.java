package com.example.sqlanalyzer.service;

import com.example.sqlanalyzer.model.ExecutionPlan;
import com.example.sqlanalyzer.model.SqlAnalysisResponse;
import com.example.sqlanalyzer.model.TableStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SqlPerformanceAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(SqlPerformanceAnalysisService.class);

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private AiClientService aiClientService;

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private SqlAnalysisCacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析SQL性能（带缓存）
     * @param sql SQL语句
     * @param datasourceName 数据源名称（可选，如果不指定则使用默认数据源）
     * @param llmName 大模型名称（可选，如果不指定则使用默认模型）
     */
    public SqlAnalysisResponse analyzeSql(String sql, String datasourceName, String llmName) {
        logger.info("开始分析SQL: {}, 数据源: {}, 大模型: {}", sql, datasourceName, llmName);
        
        // 1. 先尝试从缓存获取（缓存键包含SQL、数据源和模型信息）
        return cacheService.getCachedResult(sql, datasourceName, llmName)
            .map(cachedResponse -> {
                logger.info("使用缓存的分析结果");
                return cachedResponse;
            })
            .orElseGet(() -> {
                // 2. 缓存未命中，执行分析
                logger.info("缓存未命中，执行新的分析");
                return performAnalysis(sql, datasourceName, llmName);
            });
    }

    /**
     * 执行SQL分析
     */
    private SqlAnalysisResponse performAnalysis(String sql, String datasourceName, String llmName) {
        SqlAnalysisResponse response = new SqlAnalysisResponse();
        response.setSql(sql);

        try {
            // 1. 获取执行计划
            logger.debug("获取执行计划...");
            ExecutionPlan executionPlan = executionPlanService.getExecutionPlan(sql, datasourceName);
            response.setExecutionPlan(executionPlan);
            
            if (executionPlan == null) {
                throw new RuntimeException("无法获取执行计划");
            }

            // 2. 获取表结构信息
            logger.debug("获取表结构信息...");
            List<TableStructure> tableStructures = executionPlanService.getTableStructures(sql, datasourceName);
            response.setTableStructures(tableStructures);

            // 3. 准备执行计划和表结构的字符串表示
            String executionPlanStr = formatExecutionPlan(executionPlan);
            String tableStructuresStr = formatTableStructures(tableStructures);

            // 4. 并行调用AI模型进行MySQL和GoldenDB分析
            logger.debug("并行调用AI模型进行MySQL和GoldenDB分析...");
            AiClientService.AnalysisResultPair analysisResults = aiClientService.analyzeSqlPerformanceBoth(
                sql, 
                executionPlanStr, 
                tableStructuresStr,
                llmName
            );
            
            // 存储分析结果
            if (analysisResults.hasMysqlResult()) {
                response.setMysqlAnalysisResult(analysisResults.getMysqlResult());
                // 为了向后兼容，也设置到analysisResult字段
                response.setAnalysisResult(analysisResults.getMysqlResult());
            } else {
                logger.warn("MySQL分析失败: {}", 
                    analysisResults.getMysqlException() != null ? 
                    analysisResults.getMysqlException().getMessage() : "未知错误");
                response.setMysqlAnalysisResult("MySQL分析失败: " + 
                    (analysisResults.getMysqlException() != null ? 
                     analysisResults.getMysqlException().getMessage() : "未知错误"));
            }
            
            if (analysisResults.hasGoldenDbResult()) {
                response.setGoldenDbAnalysisResult(analysisResults.getGoldenDbResult());
            } else {
                logger.warn("GoldenDB分析失败: {}", 
                    analysisResults.getGoldenDbException() != null ? 
                    analysisResults.getGoldenDbException().getMessage() : "未知错误");
                response.setGoldenDbAnalysisResult("GoldenDB分析失败: " + 
                    (analysisResults.getGoldenDbException() != null ? 
                     analysisResults.getGoldenDbException().getMessage() : "未知错误"));
            }
            
            // 如果两个分析都失败，抛出异常
            if (!analysisResults.hasMysqlResult() && !analysisResults.hasGoldenDbResult()) {
                throw new RuntimeException("MySQL和GoldenDB分析都失败了");
            }

            // 5. 生成报告
            logger.debug("生成Markdown报告...");
            String report = reportGenerator.generateMarkdownReport(response);
            response.setReport(report);

            // 6. 保存到缓存
            logger.debug("保存分析结果到缓存...");
            cacheService.saveResult(sql, response, datasourceName, llmName);

            logger.info("SQL分析完成");
            return response;

        } catch (RuntimeException e) {
            // 重新抛出RuntimeException（包括数据库操作失败和AI分析失败）
            throw e;
        } catch (Exception e) {
            logger.error("SQL分析失败", e);
            throw new RuntimeException("SQL分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 格式化执行计划为字符串
     */
    private String formatExecutionPlan(ExecutionPlan executionPlan) {
        try {
            if (executionPlan.getRawJson() != null) {
                return executionPlan.getRawJson();
            }
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(executionPlan);
        } catch (Exception e) {
            logger.warn("格式化执行计划失败", e);
            return "执行计划格式错误";
        }
    }

    /**
     * 格式化表结构为字符串
     */
    private String formatTableStructures(List<TableStructure> tableStructures) {
        try {
            StringBuilder sb = new StringBuilder();
            for (TableStructure structure : tableStructures) {
                sb.append("表名: ").append(structure.getTableName()).append("\n");
                
                // 列信息
                if (structure.getColumns() != null && !structure.getColumns().isEmpty()) {
                    sb.append("列信息:\n");
                    for (TableStructure.ColumnInfo column : structure.getColumns()) {
                        sb.append("  - ").append(column.getColumnName())
                          .append(" (").append(column.getDataType()).append(")");
                        if (column.getColumnKey() != null && !column.getColumnKey().isEmpty()) {
                            sb.append(" [").append(column.getColumnKey()).append("]");
                        }
                        sb.append("\n");
                    }
                }
                
                // 索引信息
                if (structure.getIndexes() != null && !structure.getIndexes().isEmpty()) {
                    sb.append("索引信息:\n");
                    String currentIndex = null;
                    for (TableStructure.IndexInfo index : structure.getIndexes()) {
                        if (!index.getIndexName().equals(currentIndex)) {
                            if (currentIndex != null) {
                                sb.append("\n");
                            }
                            sb.append("  - 索引: ").append(index.getIndexName())
                              .append(" (类型: ").append(index.getIndexType()).append(")");
                            if (index.getNonUnique() == 0) {
                                sb.append(" [唯一索引]");
                            }
                            sb.append("\n");
                            currentIndex = index.getIndexName();
                        }
                        sb.append("    列: ").append(index.getColumnName())
                          .append(" (位置: ").append(index.getSeqInIndex()).append(")\n");
                    }
                }
                
                // 统计信息
                if (structure.getStatistics() != null) {
                    TableStructure.TableStatistics stats = structure.getStatistics();
                    sb.append("统计信息:\n");
                    sb.append("  - 行数: ").append(stats.getRows()).append("\n");
                    sb.append("  - 数据大小: ").append(formatBytes(stats.getDataLength())).append("\n");
                    sb.append("  - 索引大小: ").append(formatBytes(stats.getIndexLength())).append("\n");
                    sb.append("  - 存储引擎: ").append(stats.getEngine()).append("\n");
                }
                
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("格式化表结构失败", e);
            return "表结构格式错误";
        }
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(Long bytes) {
        if (bytes == null) {
            return "未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}

