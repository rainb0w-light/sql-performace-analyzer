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

    @Autowired(required = false)
    private LlmManagerService llmManagerService;

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
            if (!structures.isEmpty()) {
                result.setTableStructure(structures.get(0));
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
                
                // 判断是否为慢查询
                boolean isSlowQuery = false;
                if (tableInfo.getRowsExaminedPerScan() != null && tableInfo.getRowsExaminedPerScan() > 10000) {
                    isSlowQuery = true;
                }
                if ("ALL".equals(tableInfo.getAccessType())) {
                    isSlowQuery = true;
                }
                if (tableInfo.getKey() == null || tableInfo.getKey().isEmpty()) {
                    isSlowQuery = true;
                }
                analysis.setSlowQuery(isSlowQuery);
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

        // 统计信息
        long totalQueries = queryAnalyses.size();
        long slowQueries = queryAnalyses.stream()
                .filter(QueryAnalysis::isSlowQuery)
                .count();
        long queriesWithoutIndex = queryAnalyses.stream()
                .filter(q -> !q.isUsesIndex())
                .count();

        suggestions.setTotalQueries(totalQueries);
        suggestions.setSlowQueries(slowQueries);
        suggestions.setQueriesWithoutIndex(queriesWithoutIndex);

        // 调用大模型生成优化建议
        if (llmManagerService != null) {
            try {
                String aiSuggestions = generateAiOptimizationSuggestions(
                        tableStructure, queryAnalyses, tableName);
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
            TableStructure tableStructure, List<QueryAnalysis> queryAnalyses, String tableName) {
        
        try {
            // 构建分析上下文
            StringBuilder context = new StringBuilder();
            context.append("表名: ").append(tableName).append("\n\n");
            
            // 表结构信息
            if (tableStructure != null) {
                context.append("表结构:\n");
                if (tableStructure.getColumns() != null) {
                    context.append("列信息:\n");
                    for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                        context.append(String.format("  - %s (%s, %s)\n", 
                            col.getColumnName(), col.getDataType(), 
                            "YES".equals(col.getIsNullable()) ? "可空" : "非空"));
                    }
                }
                if (tableStructure.getIndexes() != null && !tableStructure.getIndexes().isEmpty()) {
                    context.append("现有索引:\n");
                    for (TableStructure.IndexInfo idx : tableStructure.getIndexes()) {
                        context.append(String.format("  - %s (%s)\n", 
                            idx.getIndexName(), idx.getColumnName()));
                    }
                }
                context.append("\n");
            }
            
            // 慢查询信息
            context.append("慢查询分析:\n");
            int slowQueryCount = 0;
            for (QueryAnalysis analysis : queryAnalyses) {
                if (analysis.isSlowQuery()) {
                    slowQueryCount++;
                    context.append(String.format("\n查询 #%d (%s):\n", 
                        analysis.getQueryId(), 
                        analysis.getStatementId() != null ? analysis.getStatementId() : "未知"));
                    context.append("SQL: ").append(analysis.getSql()).append("\n");
                    if (analysis.getExecutableSql() != null && !analysis.getExecutableSql().equals(analysis.getSql())) {
                        context.append("可执行SQL: ").append(analysis.getExecutableSql()).append("\n");
                    }
                    context.append("访问类型: ").append(analysis.getAccessType() != null ? analysis.getAccessType() : "未知").append("\n");
                    context.append("使用索引: ").append(analysis.isUsesIndex() ? "是" : "否").append("\n");
                    if (analysis.getIndexName() != null) {
                        context.append("索引名: ").append(analysis.getIndexName()).append("\n");
                    }
                    if (analysis.getRowsExamined() != null) {
                        context.append("扫描行数: ").append(analysis.getRowsExamined()).append("\n");
                    }
                }
            }
            
            if (slowQueryCount == 0) {
                return null; // 没有慢查询，不需要AI建议
            }
            
            // 构建提示词
            String prompt = String.format(
                "你是一位资深的MySQL性能优化专家。请基于以下信息，为表 %s 的所有慢查询提供综合性的优化建议。\n\n" +
                "%s\n\n" +
                "请提供以下方面的优化建议：\n" +
                "1. 索引优化建议（包括需要创建的索引、复合索引等）\n" +
                "2. SQL语句优化建议（包括查询重写、JOIN优化等）\n" +
                "3. 表结构优化建议（如需要）\n" +
                "4. 其他性能优化建议\n\n" +
                "请用中文回答，建议要具体、可操作。",
                tableName, context.toString()
            );
            
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

