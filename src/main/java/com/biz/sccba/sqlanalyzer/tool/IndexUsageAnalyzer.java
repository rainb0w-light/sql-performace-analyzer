package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引使用分析工具
 */
@Component
public class IndexUsageAnalyzer {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IndexUsageAnalyzer(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    @Tool(name = "analyze_index_usage", description = "分析 SQL 查询中的索引使用情况，识别索引缺失和使用不当的问题")
    public String analyzeIndexUsage(
            @ToolParam(name = "sql", description = "SQL 语句", required = true) String sql,
            @ToolParam(name = "datasourceName", description = "数据源名称（可选）", required = false) String datasourceName,
            @ToolParam(name = "tableName", description = "表名（可选）", required = false) String tableName) {
        System.out.println("[IndexUsageAnalyzer] 分析索引使用 (数据源：" + datasourceName + ")");
        try {
            IndexUsageAnalysisResult result = analyzeIndexUsageForSql(sql, datasourceName, tableName);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public IndexUsageAnalysisResult analyzeIndexUsageForSql(String sql, String datasourceName, String tableName) {
        if (sql == null || sql.trim().isEmpty()) {
            return IndexUsageAnalysisResult.error("SQL 语句不能为空");
        }

        List<String> tables = tableName != null ? List.of(tableName) : extractTables(sql);
        
        List<TableIndexAnalysis> tableAnalyses = new ArrayList<>();
        for (String table : tables) {
            TableIndexAnalysis analysis = analyzeTableIndexUsage(sql, table, datasourceName);
            tableAnalyses.add(analysis);
        }
        
        List<String> recommendations = generateIndexRecommendations(tableAnalyses);
        int estimatedImpact = calculateEstimatedImpact(tableAnalyses);
        
        return new IndexUsageAnalysisResult(
            sql,
            tables,
            tableAnalyses,
            recommendations,
            estimatedImpact
        );
    }

    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        
        Pattern fromPattern = Pattern.compile("\\bFROM\\s+([A-Z_][A-Z0-9_$]*)", Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(sql);
        while (fromMatcher.find()) {
            tables.add(fromMatcher.group(1));
        }
        
        Pattern joinPattern = Pattern.compile("\\bJOIN\\s+([A-Z_][A-Z0-9_$]*)", Pattern.CASE_INSENSITIVE);
        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            tables.add(joinMatcher.group(1));
        }
        
        return new ArrayList<>(new HashSet<>(tables));
    }

    private TableIndexAnalysis analyzeTableIndexUsage(String sql, String tableName, String datasourceName) {
        TableIndexAnalysis analysis;
        
        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);
            List<Map<String, Object>> indexes = getTableIndexes(jdbcTemplate, tableName);
            List<IndexUsageInfo> indexUsage = analyzeIndexUsageInQuery(sql, tableName, indexes);
            List<String> missingIndexes = identifyMissingIndexes(sql, tableName, indexes, indexUsage);
            List<String> unusedIndexes = findUnusedIndexes(indexes, indexUsage);
            List<String> potentialIssues = identifyPotentialIssues(sql, tableName, indexUsage);
            
            analysis = new TableIndexAnalysis(
                tableName, indexes, indexUsage, missingIndexes, unusedIndexes, potentialIssues, null
            );
        } catch (Exception e) {
            analysis = new TableIndexAnalysis(
                tableName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), e.getMessage()
            );
        }
        
        return analysis;
    }

    private List<Map<String, Object>> getTableIndexes(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = """
            SELECT 
                INDEX_NAME,
                COLUMN_NAME,
                SEQ_IN_INDEX,
                NON_UNIQUE,
                INDEX_TYPE
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            ORDER BY INDEX_NAME, SEQ_IN_INDEX
            """;
        
        return jdbcTemplate.queryForList(sql, tableName);
    }

    private List<IndexUsageInfo> analyzeIndexUsageInQuery(String sql, String tableName, List<Map<String, Object>> indexes) {
        List<IndexUsageInfo> usageList = new ArrayList<>();
        
        String whereClause = extractWhereClause(sql);
        String joinClause = extractJoinConditions(sql);
        
        for (Map<String, Object> index : indexes) {
            String indexName = (String) index.get("INDEX_NAME");
            String columnName = (String) index.get("COLUMN_NAME");
            int seqInIndex = ((Number) index.get("SEQ_IN_INDEX")).intValue();
            
            if (seqInIndex == 1) {
                List<String> indexColumns = getIndexColumns(indexName, indexes);
                IndexUsageInfo usage = checkIndexUsage(sql, tableName, indexName, indexColumns, whereClause, joinClause);
                usageList.add(usage);
            }
        }
        
        return usageList;
    }

    private List<String> getIndexColumns(String indexName, List<Map<String, Object>> allIndexes) {
        List<String> columns = new ArrayList<>();
        for (Map<String, Object> idx : allIndexes) {
            if (idx.get("INDEX_NAME").equals(indexName)) {
                columns.add((String) idx.get("COLUMN_NAME"));
            }
        }
        columns.sort(Comparator.comparingInt(col -> {
            for (Map<String, Object> idx : allIndexes) {
                if (idx.get("INDEX_NAME").equals(indexName) && idx.get("COLUMN_NAME").equals(col)) {
                    return ((Number) idx.get("SEQ_IN_INDEX")).intValue();
                }
            }
            return 0;
        }));
        return columns;
    }

    private IndexUsageInfo checkIndexUsage(String sql, String tableName, String indexName, 
                                           List<String> indexColumns, String whereClause, String joinClause) {
        boolean usedInWhere = whereClause != null && isColumnUsedInWhere(whereClause, indexColumns.get(0));
        boolean canUseIndex = whereClause != null && canIndexBeUsed(sql, whereClause, indexColumns.get(0));
        String usageType = "NONE";
        
        if (usedInWhere) {
            usageType = "FULL";
            if (indexColumns.size() > 1 && !allColumnsUsedInWhere(whereClause, indexColumns)) {
                usageType = "PARTIAL";
            }
        }
        
        boolean usedInJoin = joinClause != null && isColumnUsedInJoin(joinClause, indexColumns.get(0));
        if (usedInJoin) {
            canUseIndex = true;
        }
        
        boolean isCovering = isCoveringIndex(sql, indexColumns);
        
        return new IndexUsageInfo(
            indexName, indexColumns, usedInWhere, usedInJoin, canUseIndex, usageType, isCovering
        );
    }

    private String extractWhereClause(String sql) {
        Pattern wherePattern = Pattern.compile(
            "\\bWHERE\\s+(.+?)(?=\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|\\bLIMIT\\b|\\bUNION\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = wherePattern.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJoinConditions(String sql) {
        Pattern onPattern = Pattern.compile("\\bON\\s+([A-Z_][A-Z0-9_$]*\\.[A-Z_][A-Z0-9_$]*\\s*=\\s*[A-Z_][A-Z0-9_$]*\\.[A-Z_][A-Z0-9_$]*)", Pattern.CASE_INSENSITIVE);
        StringBuilder joins = new StringBuilder();
        Matcher matcher = onPattern.matcher(sql);
        while (matcher.find()) {
            joins.append(matcher.group(1)).append(" ");
        }
        return joins.length() > 0 ? joins.toString() : null;
    }

    private boolean isColumnUsedInWhere(String whereClause, String columnName) {
        return whereClause.matches("(?i).*\\b" + Pattern.quote(columnName) + "\\b\\s*(=|>|<|>=|<=|!=|<>|LIKE|IN).*") ||
               whereClause.matches("(?i).*\\b" + Pattern.quote(columnName) + "\\b\\s+BETWEEN.*");
    }

    private boolean canIndexBeUsed(String sql, String whereClause, String columnName) {
        if (whereClause == null) return false;
        
        if (whereClause.matches("(?i).*\\bLIKE\\s*'%.*" + Pattern.quote(columnName) + ".*")) {
            return false;
        }
        
        if (whereClause.matches("(?i).*\\bFUNCTION\\s*\\([^)]*" + Pattern.quote(columnName))) {
            return false;
        }
        
        return true;
    }

    private boolean allColumnsUsedInWhere(String whereClause, List<String> columns) {
        return columns.stream().allMatch(col -> isColumnUsedInWhere(whereClause, col));
    }

    private boolean isColumnUsedInJoin(String joinClause, String columnName) {
        return joinClause != null && 
               joinClause.matches("(?i).*\\b" + Pattern.quote(columnName) + "\\b\\s*=.*");
    }

    private boolean isCoveringIndex(String sql, List<String> indexColumns) {
        Pattern selectPattern = Pattern.compile("\\bSELECT\\s+(.+?)\\s+FROM\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = selectPattern.matcher(sql);
        if (!matcher.find()) return false;
        
        String selectList = matcher.group(1);
        if (selectList.equals("*")) return false;
        
        Pattern columnPattern = Pattern.compile("\\b([A-Z_][A-Z0-9_$]*)\\b", Pattern.CASE_INSENSITIVE);
        Matcher columnMatcher = columnPattern.matcher(selectList);
        
        Set<String> selectedColumns = new HashSet<>();
        while (columnMatcher.find()) {
            selectedColumns.add(columnMatcher.group(1).toUpperCase());
        }
        
        Set<String> indexColumnSet = new HashSet<>(indexColumns);
        return indexColumnSet.containsAll(selectedColumns);
    }

    private List<String> identifyMissingIndexes(String sql, String tableName, 
                                                 List<Map<String, Object>> existingIndexes,
                                                 List<IndexUsageInfo> indexUsage) {
        List<String> missing = new ArrayList<>();
        String whereClause = extractWhereClause(sql);
        
        if (whereClause == null) return missing;
        
        Pattern conditionPattern = Pattern.compile("\\b([A-Z_][A-Z0-9_$]*)\\s*(?:=|>|<|>=|<=|!=|<>)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = conditionPattern.matcher(whereClause);
        
        Set<String> existingColumns = new HashSet<>();
        for (Map<String, Object> idx : existingIndexes) {
            String colName = ((String) idx.get("COLUMN_NAME")).toUpperCase();
            if (((Number) idx.get("SEQ_IN_INDEX")).intValue() == 1) {
                existingColumns.add(colName);
            }
        }
        
        while (matcher.find()) {
            String columnName = matcher.group(1).toUpperCase();
            if (!existingColumns.contains(columnName)) {
                missing.add("Consider adding index on column: " + columnName);
            }
        }
        
        return missing;
    }

    private List<String> findUnusedIndexes(List<Map<String, Object>> indexes, List<IndexUsageInfo> indexUsage) {
        List<String> unused = new ArrayList<>();
        
        Set<String> usedIndexNames = new HashSet<>();
        for (IndexUsageInfo usage : indexUsage) {
            if (usage.usedInWhere || usage.usedInJoin) {
                usedIndexNames.add(usage.indexName);
            }
        }
        
        for (Map<String, Object> index : indexes) {
            String indexName = (String) index.get("INDEX_NAME");
            if (((Number) index.get("SEQ_IN_INDEX")).intValue() == 1 && !usedIndexNames.contains(indexName)) {
                unused.add("Index '" + indexName + "' may be unused");
            }
        }
        
        return unused;
    }

    private List<String> identifyPotentialIssues(String sql, String tableName, List<IndexUsageInfo> indexUsage) {
        List<String> issues = new ArrayList<>();
        
        for (IndexUsageInfo usage : indexUsage) {
            if (usage.usedInWhere && !usage.canUseIndex) {
                issues.add("Index '" + usage.indexName + "' cannot be used due to function or operation on column");
            }
            
            if ("PARTIAL".equals(usage.usageType)) {
                issues.add("Composite index '" + usage.indexName + "' is only partially used");
            }
        }
        
        if (sql.toUpperCase().matches(".*\\bLIKE\\s*'%.*")) {
            issues.add("Leading wildcard in LIKE prevents index usage");
        }
        
        return issues;
    }

    private List<String> generateIndexRecommendations(List<TableIndexAnalysis> tableAnalyses) {
        List<String> recommendations = new ArrayList<>();
        
        for (TableIndexAnalysis analysis : tableAnalyses) {
            if (analysis.error != null) continue;
            
            for (String missing : analysis.missingIndexes) {
                recommendations.add("[" + analysis.tableName + "] " + missing);
            }
            
            for (String unused : analysis.unusedIndexes) {
                recommendations.add("[" + analysis.tableName + "] " + unused + " - consider removing to reduce write overhead");
            }
            
            for (String issue : analysis.potentialIssues) {
                recommendations.add("[" + analysis.tableName + "] " + issue);
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Index usage looks optimal");
        }
        
        return recommendations;
    }

    private int calculateEstimatedImpact(List<TableIndexAnalysis> tableAnalyses) {
        int impact = 0;
        
        for (TableIndexAnalysis analysis : tableAnalyses) {
            if (!analysis.missingIndexes.isEmpty()) {
                impact += analysis.missingIndexes.size() * 30;
            }
            if (!analysis.potentialIssues.isEmpty()) {
                impact += analysis.potentialIssues.size() * 15;
            }
        }
        
        return Math.min(impact, 100);
    }

    public record IndexUsageAnalysisResult(
        String sql,
        List<String> analyzedTables,
        List<TableIndexAnalysis> tableAnalyses,
        List<String> recommendations,
        int estimatedPerformanceImpact
    ) {
        public static IndexUsageAnalysisResult error(String message) {
            return new IndexUsageAnalysisResult(null, List.of(), List.of(), List.of(message), 0);
        }
    }

    public record TableIndexAnalysis(
        String tableName,
        List<Map<String, Object>> existingIndexes,
        List<IndexUsageInfo> indexUsage,
        List<String> missingIndexes,
        List<String> unusedIndexes,
        List<String> potentialIssues,
        String error
    ) {
        public TableIndexAnalysis() {
            this("", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
        }
    }

    public record IndexUsageInfo(
        String indexName,
        List<String> indexColumns,
        boolean usedInWhere,
        boolean usedInJoin,
        boolean canUseIndex,
        String usageType,
        boolean isCovering
    ) {
        public IndexUsageInfo() {
            this("", List.of(), false, false, false, "NONE", false);
        }
    }

    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "analyze_index_usage");
        metadata.put("description", "分析 SQL 查询中的索引使用情况，识别索引缺失和使用不当的问题");
        metadata.put("parameters", Map.of(
            "sql", Map.of("type", "string", "description", "SQL 语句", "required", true),
            "datasourceName", Map.of("type", "string", "description", "数据源名称（可选）", "required", false),
            "tableName", Map.of("type", "string", "description", "表名（可选）", "required", false)
        ));
        return metadata;
    }
}
