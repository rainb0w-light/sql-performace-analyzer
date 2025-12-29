package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.repository.ParsedSqlQueryRepository;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 单表查询综合分析服务
 * 针对单个表的所有查询进行综合分析优化
 */
@Service
public class TableQueryAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TableQueryAnalysisService.class);

    @Autowired
    private ParsedSqlQueryRepository parsedSqlQueryRepository;

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private SqlParameterReplacerService sqlParameterReplacerService;

    @Autowired
    private ColumnStatisticsCollectorService columnStatisticsCollectorService;

    @Autowired(required = false)
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    /**
     * 分析指定表的所有查询
     */
    public TableAnalysisResult analyzeTable(String tableName, String datasourceName) {
        logger.info("开始分析表的所有查询: tableName={}, datasourceName={}", tableName, datasourceName);

        TableAnalysisResult result = new TableAnalysisResult();
        result.setTableName(tableName);
        result.setDatasourceName(datasourceName);

        try {
            // 1. 获取表的所有SQL查询
            List<ParsedSqlQuery> queries = parsedSqlQueryRepository.findByTableName(tableName);
            if (queries.isEmpty()) {
                result.setMessage("未找到该表相关的SQL查询");
                return result;
            }

            result.setQueryCount(queries.size());
            logger.info("找到 {} 个相关SQL查询", queries.size());

            // 2. 获取表结构信息
            String testSql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(testSql, datasourceName);
            TableStructure tableStructure = null;
            if (!structures.isEmpty()) {
                tableStructure = structures.get(0);
                result.setTableStructure(tableStructure);
            }

            // 2.5. 统一执行ANALYZE TABLE更新表的统计信息（可选，如果需要更新统计信息）
            if (tableStructure != null && tableStructure.getColumns() != null && !tableStructure.getColumns().isEmpty()) {
                try {
                    List<String> allColumnNames = new ArrayList<>();
                    for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                        allColumnNames.add(col.getColumnName());
                    }
                    logger.info("执行ANALYZE TABLE更新表 {} 的统计信息，共 {} 列", tableName, allColumnNames.size());
                    columnStatisticsCollectorService.analyzeTable(tableName, datasourceName, allColumnNames, null);
                    logger.info("列统计信息收集完成");
                } catch (Exception e) {
                    logger.warn("收集表列统计信息失败，将继续使用已有统计信息: {}", e.getMessage());
                }
            }

            // 3. 分析每个查询的执行计划
            List<QueryAnalysis> queryAnalyses = new ArrayList<>();
            for (ParsedSqlQuery query : queries) {
                try {
                    QueryAnalysis analysis = analyzeQuery(query, datasourceName);
                    queryAnalyses.add(analysis);
                } catch (Exception e) {
                    logger.warn("分析查询失败: {}", query.getSql(), e);
                    QueryAnalysis analysis = new QueryAnalysis();
                    analysis.setQueryId(query.getId());
                    analysis.setSql(query.getSql());
                    analysis.setError("分析失败: " + e.getMessage());
                    queryAnalyses.add(analysis);
                }
            }
            result.setQueryAnalyses(queryAnalyses);

            // 4. 综合分析，生成优化建议
            OptimizationSuggestions suggestions = generateOptimizationSuggestions(
                    result.getTableStructure(), queryAnalyses, tableName, datasourceName);
            result.setSuggestions(suggestions);

            result.setSuccess(true);
            result.setMessage("分析完成");

            return result;

        } catch (Exception e) {
            logger.error("分析表查询失败", e);
            result.setSuccess(false);
            result.setMessage("分析失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 分析单个查询
     */
    private QueryAnalysis analyzeQuery(ParsedSqlQuery query, String datasourceName) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setQueryId(query.getId());
        analysis.setMapperNamespace(query.getMapperNamespace());
        analysis.setStatementId(query.getStatementId());
        analysis.setQueryType(query.getQueryType());
        analysis.setSql(query.getSql());
        analysis.setDynamicConditions(query.getDynamicConditions());

        try {
            // 替换SQL中的占位符为实际值
            String executableSql = sqlParameterReplacerService.replaceParametersSmart(query.getSql(), datasourceName);
            analysis.setExecutableSql(executableSql);

            // 获取执行计划
            ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(executableSql, datasourceName);
            analysis.setExecutionPlan(plan);

            // 分析执行计划
            if (plan != null && plan.getQueryBlock() != null && plan.getQueryBlock().getTable() != null) {
                ExecutionPlan.TableInfo tableInfo = plan.getQueryBlock().getTable();
                
                analysis.setUsesIndex(tableInfo.getKey() != null && !tableInfo.getKey().isEmpty());
                analysis.setIndexName(tableInfo.getKey());
                analysis.setAccessType(tableInfo.getAccessType());
                analysis.setRowsExamined(tableInfo.getRowsExaminedPerScan());
                
                // 慢查询判断交给AI完成，这里不再硬编码判断逻辑
                analysis.setSlowQuery(false);
            }

        } catch (Exception e) {
            analysis.setError("获取执行计划失败: " + e.getMessage());
        }

        return analysis;
    }

    /**
     * 生成优化建议
     */
    private OptimizationSuggestions generateOptimizationSuggestions(
            TableStructure tableStructure, List<QueryAnalysis> queryAnalyses, 
            String tableName, String datasourceName) {
        
        OptimizationSuggestions suggestions = new OptimizationSuggestions();

        // 仅保留基本的查询数量统计（用于结果展示）
        long totalQueries = queryAnalyses.size();
        suggestions.setTotalQueries(totalQueries);
        // 删除硬编码的统计逻辑，这些统计交给AI完成
        suggestions.setSlowQueries(0);
        suggestions.setQueriesWithoutIndex(0);

        // 调用大模型生成优化建议
        if (llmManagerService != null) {
            try {
                String aiSuggestions = generateAiOptimizationSuggestions(
                        tableStructure, queryAnalyses, tableName, datasourceName);
                if (aiSuggestions != null && !aiSuggestions.trim().isEmpty()) {
                    suggestions.setAiSuggestions(aiSuggestions);
                    // 将AI建议同时设置到SQL建议中，保持向后兼容
                    List<String> sqlSuggestions = new ArrayList<>();
                    sqlSuggestions.add(aiSuggestions);
                    suggestions.setSqlSuggestions(sqlSuggestions);
                } else {
                    suggestions.setSqlSuggestions(new ArrayList<>());
                }
            } catch (Exception e) {
                logger.warn("调用AI生成优化建议失败", e); 
                suggestions.setSqlSuggestions(new ArrayList<>());
            }
        } else {
            logger.warn("未配置大模型服务，无法生成优化建议");
            suggestions.setSqlSuggestions(new ArrayList<>());
        }

        suggestions.setIndexSuggestions(new ArrayList<>());

        return suggestions;
    }

    /**
     * 调用大模型生成智能优化建议
     */
    private String generateAiOptimizationSuggestions(
            TableStructure tableStructure, List<QueryAnalysis> queryAnalyses, 
            String tableName, String datasourceName) {
        
        try {
            // 从数据库读取prompt模板
            String template = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_TABLE_QUERY_ANALYSIS);
            
            // 格式化表结构信息
            String tableStructureText = formatTableStructure(tableStructure);
            
            // 格式化所有SQL语句
            String allSqlsText = formatAllSqls(queryAnalyses);
            
            // 格式化所有执行计划
            String allExecutionPlansText = formatAllExecutionPlans(queryAnalyses);
            
            // 使用占位符替换生成最终的prompt
            String prompt = template
                    .replace("{table_name}", tableName != null ? tableName : "")
                    .replace("{table_structure}", tableStructureText)
                    .replace("{all_sqls}", allSqlsText)
                    .replace("{all_execution_plans}", allExecutionPlansText);
            
            // 调用AI模型
            if (llmManagerService != null) {
                var chatClient = llmManagerService.getChatClient(null);
                String result = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
                
                logger.info("AI生成优化建议成功");
                return result;
            }
            
        } catch (Exception e) {
            logger.error("调用AI生成优化建议失败", e);
            throw e;
        }
        
        return null;
    }

    /**
     * 格式化表结构信息
     */
    private String formatTableStructure(TableStructure tableStructure) {
        if (tableStructure == null) {
            return "表结构信息不可用";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 列信息
        if (tableStructure.getColumns() != null && !tableStructure.getColumns().isEmpty()) {
            sb.append("### 列信息\n\n");
            sb.append("| 列名 | 数据类型 | 是否可空 | 主键 | 默认值 | 额外信息 |\n");
            sb.append("|------|----------|----------|------|--------|----------|\n");
            
            for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                    col.getColumnName() != null ? col.getColumnName() : "",
                    col.getDataType() != null ? col.getDataType() : "",
                    "YES".equals(col.getIsNullable()) ? "是" : "否",
                    "PRI".equals(col.getColumnKey()) ? "是" : "否",
                    col.getColumnDefault() != null ? col.getColumnDefault() : "",
                    col.getExtra() != null ? col.getExtra() : ""));
            }
            sb.append("\n");
        }
        
        // 索引信息
        if (tableStructure.getIndexes() != null && !tableStructure.getIndexes().isEmpty()) {
            sb.append("### 索引信息\n\n");
            // 按索引名分组
            Map<String, List<TableStructure.IndexInfo>> indexMap = new LinkedHashMap<>();
            for (TableStructure.IndexInfo idx : tableStructure.getIndexes()) {
                String indexName = idx.getIndexName() != null ? idx.getIndexName() : "";
                indexMap.computeIfAbsent(indexName, k -> new ArrayList<>()).add(idx);
            }
            
            for (Map.Entry<String, List<TableStructure.IndexInfo>> entry : indexMap.entrySet()) {
                String indexName = entry.getKey();
                List<TableStructure.IndexInfo> indexes = entry.getValue();
                Collections.sort(indexes, Comparator.comparingInt(TableStructure.IndexInfo::getSeqInIndex));
                
                sb.append(String.format("**索引名**: %s\n", indexName));
                sb.append("**列**: ");
                List<String> columns = new ArrayList<>();
                for (TableStructure.IndexInfo idx : indexes) {
                    columns.add(idx.getColumnName());
                }
                sb.append(String.join(", ", columns));
                sb.append("\n");
                sb.append(String.format("**类型**: %s\n", indexes.get(0).getIndexType() != null ? indexes.get(0).getIndexType() : ""));
                sb.append(String.format("**唯一性**: %s\n\n", indexes.get(0).getNonUnique() == 0 ? "唯一" : "非唯一"));
            }
        }
        
        // 表统计信息
        if (tableStructure.getStatistics() != null) {
            TableStructure.TableStatistics stats = tableStructure.getStatistics();
            sb.append("### 表统计信息\n\n");
            sb.append(String.format("- 行数: %d\n", stats.getRows() != null ? stats.getRows() : 0));
            sb.append(String.format("- 数据长度: %d 字节\n", stats.getDataLength() != null ? stats.getDataLength() : 0));
            sb.append(String.format("- 索引长度: %d 字节\n", stats.getIndexLength() != null ? stats.getIndexLength() : 0));
            sb.append(String.format("- 存储引擎: %s\n\n", stats.getEngine() != null ? stats.getEngine() : ""));
        }
        
        return sb.toString();
    }

    /**
     * 格式化所有SQL语句
     */
    private String formatAllSqls(List<QueryAnalysis> queryAnalyses) {
        if (queryAnalyses == null || queryAnalyses.isEmpty()) {
            return "未找到SQL查询";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(queryAnalyses.size()).append(" 个查询\n\n");
        
        for (int i = 0; i < queryAnalyses.size(); i++) {
            QueryAnalysis analysis = queryAnalyses.get(i);
            sb.append(String.format("### 查询 #%d (ID: %d)\n\n", i + 1, 
                analysis.getQueryId() != null ? analysis.getQueryId() : 0));
            
            if (analysis.getMapperNamespace() != null) {
                sb.append(String.format("**Mapper命名空间**: %s\n", analysis.getMapperNamespace()));
            }
            if (analysis.getStatementId() != null) {
                sb.append(String.format("**Statement ID**: %s\n", analysis.getStatementId()));
            }
            if (analysis.getQueryType() != null) {
                sb.append(String.format("**查询类型**: %s\n", analysis.getQueryType()));
            }
            
            sb.append("**原始SQL**:\n```sql\n");
            sb.append(analysis.getSql() != null ? analysis.getSql() : "");
            sb.append("\n```\n\n");
            
            if (analysis.getExecutableSql() != null && 
                !analysis.getExecutableSql().equals(analysis.getSql())) {
                sb.append("**可执行SQL** (参数替换后):\n```sql\n");
                sb.append(analysis.getExecutableSql());
                sb.append("\n```\n\n");
            }
            
            if (analysis.getDynamicConditions() != null && !analysis.getDynamicConditions().isEmpty()) {
                sb.append("**动态条件**: ").append(analysis.getDynamicConditions()).append("\n\n");
            }
            
            if (analysis.getError() != null) {
                sb.append("**错误**: ").append(analysis.getError()).append("\n\n");
            }
            
            sb.append("---\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 格式化所有执行计划
     */
    private String formatAllExecutionPlans(List<QueryAnalysis> queryAnalyses) {
        if (queryAnalyses == null || queryAnalyses.isEmpty()) {
            return "未找到执行计划";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(queryAnalyses.size()).append(" 个执行计划\n\n");
        
        for (int i = 0; i < queryAnalyses.size(); i++) {
            QueryAnalysis analysis = queryAnalyses.get(i);
            sb.append(String.format("### 执行计划 #%d (查询ID: %d)\n\n", i + 1,
                analysis.getQueryId() != null ? analysis.getQueryId() : 0));
            
            if (analysis.getExecutionPlan() == null) {
                sb.append("**状态**: 执行计划获取失败");
                if (analysis.getError() != null) {
                    sb.append(" - ").append(analysis.getError());
                }
                sb.append("\n\n---\n\n");
                continue;
            }
            
            ExecutionPlan plan = analysis.getExecutionPlan();
            
            // 执行计划原始JSON
            if (plan.getRawJson() != null) {
                sb.append("**执行计划JSON**:\n```json\n");
                sb.append(plan.getRawJson());
                sb.append("\n```\n\n");
            }
            
            // 关键执行计划信息
            if (plan.getQueryBlock() != null && plan.getQueryBlock().getTable() != null) {
                ExecutionPlan.TableInfo tableInfo = plan.getQueryBlock().getTable();
                
                sb.append("**关键信息**:\n");
                sb.append(String.format("- 表名: %s\n", 
                    tableInfo.getTableName() != null ? tableInfo.getTableName() : "未知"));
                sb.append(String.format("- 访问类型: %s\n", 
                    tableInfo.getAccessType() != null ? tableInfo.getAccessType() : "未知"));
                sb.append(String.format("- 使用索引: %s\n", 
                    tableInfo.getKey() != null && !tableInfo.getKey().isEmpty() ? tableInfo.getKey() : "无"));
                sb.append(String.format("- 扫描行数: %s\n", 
                    tableInfo.getRowsExaminedPerScan() != null ? tableInfo.getRowsExaminedPerScan() : "未知"));
                sb.append(String.format("- 连接产生行数: %s\n", 
                    tableInfo.getRowsProducedPerJoin() != null ? tableInfo.getRowsProducedPerJoin() : "未知"));
                
                if (tableInfo.getUsedColumns() != null && tableInfo.getUsedColumns().length > 0) {
                    sb.append(String.format("- 使用的列: %s\n", 
                        String.join(", ", tableInfo.getUsedColumns())));
                }
                sb.append("\n");
            }
            
            // 查询成本信息
            if (plan.getQueryBlock() != null && plan.getQueryBlock().getCostInfo() != null) {
                ExecutionPlan.CostInfo costInfo = plan.getQueryBlock().getCostInfo();
                sb.append("**成本信息**:\n");
                if (costInfo.getQueryCost() != null) {
                    sb.append(String.format("- 查询成本: %s\n", costInfo.getQueryCost()));
                }
                if (costInfo.getReadCost() != null) {
                    sb.append(String.format("- 读取成本: %s\n", costInfo.getReadCost()));
                }
                sb.append("\n");
            }
            
            // 其他执行计划信息
            if (plan.getQueryCost() != null) {
                sb.append(String.format("**查询成本**: %s\n", plan.getQueryCost()));
            }
            if (plan.getRowsExamined() != null) {
                sb.append(String.format("**扫描行数**: %d\n", plan.getRowsExamined()));
            }
            if (plan.getUsesIndex() != null) {
                sb.append(String.format("**使用索引**: %s\n", plan.getUsesIndex() ? "是" : "否"));
            }
            if (plan.getIndexName() != null) {
                sb.append(String.format("**索引名**: %s\n", plan.getIndexName()));
            }
            if (plan.getJoinType() != null) {
                sb.append(String.format("**连接类型**: %s\n", plan.getJoinType()));
            }
            if (plan.getUsesTemporary() != null) {
                sb.append(String.format("**使用临时表**: %s\n", plan.getUsesTemporary() ? "是" : "否"));
            }
            if (plan.getUsesFilesort() != null) {
                sb.append(String.format("**使用文件排序**: %s\n", plan.getUsesFilesort() ? "是" : "否"));
            }
            
            sb.append("\n---\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 表分析结果
     */
    public static class TableAnalysisResult {
        private boolean success;
        private String message;
        private String tableName;
        private String datasourceName;
        private int queryCount;
        private TableStructure tableStructure;
        private List<QueryAnalysis> queryAnalyses;
        private OptimizationSuggestions suggestions;

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

        public int getQueryCount() {
            return queryCount;
        }

        public void setQueryCount(int queryCount) {
            this.queryCount = queryCount;
        }

        public TableStructure getTableStructure() {
            return tableStructure;
        }

        public void setTableStructure(TableStructure tableStructure) {
            this.tableStructure = tableStructure;
        }

        public List<QueryAnalysis> getQueryAnalyses() {
            return queryAnalyses;
        }

        public void setQueryAnalyses(List<QueryAnalysis> queryAnalyses) {
            this.queryAnalyses = queryAnalyses;
        }

        public OptimizationSuggestions getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(OptimizationSuggestions suggestions) {
            this.suggestions = suggestions;
        }
    }

    /**
     * 查询分析结果
     */
    public static class QueryAnalysis {
        private Long queryId;
        private String mapperNamespace;
        private String statementId;
        private String queryType;
        private String sql;
        private String executableSql; // 替换参数后的可执行SQL
        private String dynamicConditions;
        private ExecutionPlan executionPlan;
        private boolean usesIndex;
        private String indexName;
        private String accessType;
        private Long rowsExamined;
        private boolean slowQuery;
        private String error;

        // Getters and Setters
        public Long getQueryId() {
            return queryId;
        }

        public void setQueryId(Long queryId) {
            this.queryId = queryId;
        }

        public String getMapperNamespace() {
            return mapperNamespace;
        }

        public void setMapperNamespace(String mapperNamespace) {
            this.mapperNamespace = mapperNamespace;
        }

        public String getStatementId() {
            return statementId;
        }

        public void setStatementId(String statementId) {
            this.statementId = statementId;
        }

        public String getQueryType() {
            return queryType;
        }

        public void setQueryType(String queryType) {
            this.queryType = queryType;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String getDynamicConditions() {
            return dynamicConditions;
        }

        public void setDynamicConditions(String dynamicConditions) {
            this.dynamicConditions = dynamicConditions;
        }

        public ExecutionPlan getExecutionPlan() {
            return executionPlan;
        }

        public void setExecutionPlan(ExecutionPlan executionPlan) {
            this.executionPlan = executionPlan;
        }

        public boolean isUsesIndex() {
            return usesIndex;
        }

        public void setUsesIndex(boolean usesIndex) {
            this.usesIndex = usesIndex;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getAccessType() {
            return accessType;
        }

        public void setAccessType(String accessType) {
            this.accessType = accessType;
        }

        public Long getRowsExamined() {
            return rowsExamined;
        }

        public void setRowsExamined(Long rowsExamined) {
            this.rowsExamined = rowsExamined;
        }

        public boolean isSlowQuery() {
            return slowQuery;
        }

        public void setSlowQuery(boolean slowQuery) {
            this.slowQuery = slowQuery;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getExecutableSql() {
            return executableSql;
        }

        public void setExecutableSql(String executableSql) {
            this.executableSql = executableSql;
        }
    }

    /**
     * 优化建议
     */
    public static class OptimizationSuggestions {
        private long totalQueries;
        private long slowQueries;
        private long queriesWithoutIndex;
        private List<String> indexSuggestions;
        private List<String> sqlSuggestions;
        private String aiSuggestions; // AI生成的智能优化建议

        // Getters and Setters
        public long getTotalQueries() {
            return totalQueries;
        }

        public void setTotalQueries(long totalQueries) {
            this.totalQueries = totalQueries;
        }

        public long getSlowQueries() {
            return slowQueries;
        }

        public void setSlowQueries(long slowQueries) {
            this.slowQueries = slowQueries;
        }

        public long getQueriesWithoutIndex() {
            return queriesWithoutIndex;
        }

        public void setQueriesWithoutIndex(long queriesWithoutIndex) {
            this.queriesWithoutIndex = queriesWithoutIndex;
        }

        public List<String> getIndexSuggestions() {
            return indexSuggestions;
        }

        public void setIndexSuggestions(List<String> indexSuggestions) {
            this.indexSuggestions = indexSuggestions;
        }

        public List<String> getSqlSuggestions() {
            return sqlSuggestions;
        }

        public void setSqlSuggestions(List<String> sqlSuggestions) {
            this.sqlSuggestions = sqlSuggestions;
        }

        public String getAiSuggestions() {
            return aiSuggestions;
        }

        public void setAiSuggestions(String aiSuggestions) {
            this.aiSuggestions = aiSuggestions;
        }
    }
}

