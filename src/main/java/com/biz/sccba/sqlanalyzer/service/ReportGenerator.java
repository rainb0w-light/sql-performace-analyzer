package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.biz.sccba.sqlanalyzer.model.response.SqlAnalysisResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportGenerator {

    /**
     * 生成Markdown格式的报告
     */
    public String generateMarkdownReport(SqlAnalysisResponse response) {
        StringBuilder report = new StringBuilder();
        
        // 报告标题
        report.append("# SQL性能分析报告\n\n");
        report.append("**生成时间**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        report.append("---\n\n");
        
        // 1. SQL语句
        report.append("## 1. SQL语句\n\n");
        report.append("```sql\n");
        report.append(response.getSql()).append("\n");
        report.append("```\n\n");
        
        // 2. 执行计划摘要
        report.append("## 2. 执行计划摘要\n\n");
        ExecutionPlan executionPlan = response.getExecutionPlan();
        if (executionPlan != null && executionPlan.getQueryBlock() != null) {
            ExecutionPlan.QueryBlock queryBlock = executionPlan.getQueryBlock();
            
            report.append("| 指标 | 值 |\n");
            report.append("|------|-----|\n");
            
            if (queryBlock.getSelectId() != null) {
                report.append("| 查询ID | ").append(queryBlock.getSelectId()).append(" |\n");
            }
            
            if (queryBlock.getCostInfo() != null) {
                ExecutionPlan.CostInfo costInfo = queryBlock.getCostInfo();
                if (costInfo.getQueryCost() != null) {
                    report.append("| 查询成本 | ").append(costInfo.getQueryCost()).append(" |\n");
                }
                if (costInfo.getReadCost() != null) {
                    report.append("| 读取成本 | ").append(costInfo.getReadCost()).append(" |\n");
                }
            }
            
            if (queryBlock.getTable() != null) {
                ExecutionPlan.TableInfo tableInfo = queryBlock.getTable();
                if (tableInfo.getTableName() != null) {
                    report.append("| 表名 | ").append(tableInfo.getTableName()).append(" |\n");
                }
                if (tableInfo.getAccessType() != null) {
                    report.append("| 访问类型 | ").append(tableInfo.getAccessType()).append(" |\n");
                }
                if (tableInfo.getKey() != null && !tableInfo.getKey().isEmpty()) {
                    report.append("| 使用的索引 | ").append(tableInfo.getKey()).append(" |\n");
                } else {
                    report.append("| 使用的索引 | 无 |\n");
                }
                if (tableInfo.getRowsExaminedPerScan() != null) {
                    report.append("| 扫描行数 | ").append(tableInfo.getRowsExaminedPerScan()).append(" |\n");
                }
                if (tableInfo.getRowsProducedPerJoin() != null) {
                    report.append("| 产生行数 | ").append(tableInfo.getRowsProducedPerJoin()).append(" |\n");
                }
                if (tableInfo.getUsedColumns() != null && tableInfo.getUsedColumns().length > 0) {
                    report.append("| 使用的列 | ").append(String.join(", ", tableInfo.getUsedColumns())).append(" |\n");
                }
            }
            
            report.append("\n");
            
            // 执行计划JSON（折叠）
            if (executionPlan.getRawJson() != null) {
                report.append("<details>\n");
                report.append("<summary>查看完整执行计划（JSON）</summary>\n\n");
                report.append("```json\n");
                report.append(executionPlan.getRawJson()).append("\n");
                report.append("```\n\n");
                report.append("</details>\n\n");
            }
        } else {
            report.append("无法获取执行计划信息。\n\n");
        }
        
        // 3. 表结构信息
        report.append("## 3. 表结构信息\n\n");
        List<TableStructure> tableStructures = response.getTableStructures();
        if (tableStructures != null && !tableStructures.isEmpty()) {
            for (TableStructure structure : tableStructures) {
                report.append("### 表: ").append(structure.getTableName()).append("\n\n");
                
                // 列信息
                if (structure.getColumns() != null && !structure.getColumns().isEmpty()) {
                    report.append("#### 列信息\n\n");
                    report.append("| 列名 | 数据类型 | 可空 | 键 | 默认值 | 额外 |\n");
                    report.append("|------|---------|------|-----|--------|------|\n");
                    
                    for (TableStructure.ColumnInfo column : structure.getColumns()) {
                        report.append("| ")
                              .append(column.getColumnName()).append(" | ")
                              .append(column.getDataType()).append(" | ")
                              .append(column.getIsNullable()).append(" | ")
                              .append(column.getColumnKey() != null ? column.getColumnKey() : "").append(" | ")
                              .append(column.getColumnDefault() != null ? column.getColumnDefault() : "").append(" | ")
                              .append(column.getExtra() != null ? column.getExtra() : "").append(" |\n");
                    }
                    report.append("\n");
                }
                
                // 索引信息
                if (structure.getIndexes() != null && !structure.getIndexes().isEmpty()) {
                    report.append("#### 索引信息\n\n");
                    report.append("| 索引名 | 列名 | 唯一性 | 位置 | 类型 |\n");
                    report.append("|--------|------|--------|------|------|\n");
                    
                    for (TableStructure.IndexInfo index : structure.getIndexes()) {
                        report.append("| ")
                              .append(index.getIndexName()).append(" | ")
                              .append(index.getColumnName()).append(" | ")
                              .append(index.getNonUnique() == 0 ? "唯一" : "非唯一").append(" | ")
                              .append(index.getSeqInIndex()).append(" | ")
                              .append(index.getIndexType()).append(" |\n");
                    }
                    report.append("\n");
                }
                
                // 统计信息
                if (structure.getStatistics() != null) {
                    TableStructure.TableStatistics stats = structure.getStatistics();
                    report.append("#### 统计信息\n\n");
                    report.append("| 指标 | 值 |\n");
                    report.append("|------|-----|\n");
                    report.append("| 行数 | ").append(stats.getRows() != null ? stats.getRows() : "未知").append(" |\n");
                    report.append("| 数据大小 | ").append(formatBytes(stats.getDataLength())).append(" |\n");
                    report.append("| 索引大小 | ").append(formatBytes(stats.getIndexLength())).append(" |\n");
                    report.append("| 存储引擎 | ").append(stats.getEngine() != null ? stats.getEngine() : "未知").append(" |\n");
                    report.append("\n");
                }
            }
        } else {
            report.append("未找到相关表结构信息。\n\n");
        }
        
        // 4. MySQL InnoDB性能分析
        report.append("## 4. MySQL InnoDB性能分析\n\n");
        if (response.getMysqlAnalysisResult() != null && !response.getMysqlAnalysisResult().isEmpty()) {
            report.append(response.getMysqlAnalysisResult()).append("\n\n");
        } else if (response.getAnalysisResult() != null && !response.getAnalysisResult().isEmpty()) {
            // 向后兼容：如果没有mysqlAnalysisResult，使用analysisResult
            report.append(response.getAnalysisResult()).append("\n\n");
        } else {
            report.append("未能获取MySQL InnoDB性能分析结果。\n\n");
        }
        
        // 5. GoldenDB分布式性能分析
        report.append("---\n\n");
        report.append("## 5. GoldenDB分布式性能分析\n\n");
        if (response.getGoldenDbAnalysisResult() != null && !response.getGoldenDbAnalysisResult().isEmpty()) {
            report.append(response.getGoldenDbAnalysisResult()).append("\n\n");
        } else {
            report.append("未能获取GoldenDB分布式性能分析结果。\n\n");
        }
        
        // 6. 分析对比总结（如果两个分析都成功）
        if ((response.getMysqlAnalysisResult() != null && !response.getMysqlAnalysisResult().isEmpty() && 
             !response.getMysqlAnalysisResult().startsWith("MySQL分析失败")) &&
            (response.getGoldenDbAnalysisResult() != null && !response.getGoldenDbAnalysisResult().isEmpty() &&
             !response.getGoldenDbAnalysisResult().startsWith("GoldenDB分析失败"))) {
            report.append("---\n\n");
            report.append("## 6. 分析对比总结\n\n");
            report.append("本报告同时提供了MySQL InnoDB和GoldenDB分布式数据库的性能分析。\n\n");
            report.append("**主要区别：**\n\n");
            report.append("- **MySQL InnoDB分析**：重点关注单机数据库的索引优化、查询执行计划、InnoDB引擎特性等。\n\n");
            report.append("- **GoldenDB分析**：重点关注分布式场景下的分片策略、跨分片查询优化、分布式事务、读写分离等。\n\n");
            report.append("**建议：**\n\n");
            report.append("- 如果您的数据库是单机MySQL，请重点关注MySQL InnoDB分析部分的优化建议。\n\n");
            report.append("- 如果您的数据库是GoldenDB分布式数据库，请重点关注GoldenDB分析部分的优化建议。\n\n");
            report.append("- 两个分析结果可以相互参考，了解SQL在不同数据库架构下的性能表现差异。\n\n");
        }
        
        // 7. 报告结束
        report.append("---\n\n");
        report.append("*报告由SQL性能分析系统自动生成*\n");
        
        return report.toString();
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

