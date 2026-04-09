package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * InnoDB 内核专家工具
 *
 * 专注于 InnoDB 存储引擎相关的分析：
 * - 索引结构分析（聚簇索引、二级索引）
 * - 锁竞争分析
 * - 缓冲池使用分析
 * - 查询执行计划分析（从 InnoDB 角度）
 */
@Component
public class InnoDBExpertTool {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     */
    public InnoDBExpertTool(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    /**
     * InnoDB 专家分析
     */
    @Tool(name = "innodb_expert_analyze", description = "InnoDB 内核专家分析工具，分析表索引结构、锁竞争、缓冲池使用等")
    public String execute(
            @ToolParam(name = "datasourceName", description = "数据源名称", required = true) String datasourceName,
            @ToolParam(name = "sql", description = "SQL 语句", required = true) String sql,
            @ToolParam(name = "tables", description = "要分析的表名列表", required = true) List<String> tables) {

        System.out.println("InnoDB 专家分析：datasource=" + datasourceName + ", tables=" + tables + ", sql=" + sql);

        if (tables == null || tables.isEmpty()) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "未提供表名列表"
                ));
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }

        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);
            String tableName = tables.get(0);  // 分析第一个表

            // 1. 分析表索引
            List<IndexAnalysis> indexAnalysis = analyzeTableIndexes(jdbcTemplate, tableName);

            // 2. 分析表统计信息
            Map<String, Object> tableStats = analyzeTableStats(jdbcTemplate, tableName);

            // 3. 如果提供了 SQL，分析执行计划
            List<String> suggestions = new ArrayList<>();
            if (sql != null && !sql.trim().isEmpty()) {
                suggestions.addAll(analyzeExecutionPlan(jdbcTemplate, sql, indexAnalysis));
            }

            // 4. 生成 InnoDB 角度的建议
            suggestions.addAll(generateInnodbSuggestions(indexAnalysis, tableStats));

            // 5. 确定优先级和置信度
            int priority = determinePriority(suggestions);
            double confidence = determineConfidence(indexAnalysis, tableStats);

            InnoDBAnalysisResult result = new InnoDBAnalysisResult(
                tableName,
                indexAnalysis,
                suggestions,
                priority,
                confidence
            );

            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "result", result,
                "expertType", "InnoDB",
                "priority", priority,
                "confidence", confidence
            ));

        } catch (Exception e) {
            System.out.println("InnoDB 专家分析失败：" + e.getMessage());
            e.printStackTrace();
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
            } catch (Exception ex) {
                return "{\"error\": \"" + ex.getMessage() + "\"}";
            }
        }
    }

    /**
     * 分析表索引
     */
    private List<IndexAnalysis> analyzeTableIndexes(JdbcTemplate jdbcTemplate, String tableName) {
        List<IndexAnalysis> results = new ArrayList<>();

        try {
            // 查询索引信息
            // 注意：SHOW INDEX 不支持预编译语句，需要拼接表名（需确保表名安全）
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SHOW INDEX FROM `" + tableName + "`"
            );

            // 按索引名分组
            Map<String, List<Map<String, Object>>> groupedByIndex = new HashMap<>();
            for (Map<String, Object> row : indexes) {
                String indexName = (String) row.get("Key_name");
                groupedByIndex.computeIfAbsent(indexName, k -> new ArrayList<>()).add(row);
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByIndex.entrySet()) {
                String indexName = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();

                // 确定索引类型
                String indexType = "NORMAL";
                if ("PRIMARY".equals(indexName)) {
                    indexType = "PRIMARY";
                } else if (rows.stream().anyMatch(r -> !"YES".equals(r.get("Non_unique")))) {
                    indexType = "UNIQUE";
                }

                // 提取列名（按 Seq_in_index 排序）
                List<String> columns = rows.stream()
                    .sorted((a, b) -> {
                        Integer seqA = (Integer) a.get("Seq_in_index");
                        Integer seqB = (Integer) b.get("Seq_in_index");
                        return seqA.compareTo(seqB);
                    })
                    .map(r -> (String) r.get("Column_name"))
                    .toList();

                // 获取基数
                Object cardinality = rows.get(0).get("Cardinality");

                // 生成建议
                String suggestion = generateIndexSuggestion(indexName, indexType, columns, cardinality);

                results.add(new IndexAnalysis(indexName, indexType, columns,
                    cardinality != null ? cardinality.toString() : "N/A", suggestion));
            }

        } catch (Exception e) {
            System.out.println("分析表索引失败：" + tableName + " - " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * 分析表统计信息
     */
    private Map<String, Object> analyzeTableStats(JdbcTemplate jdbcTemplate, String tableName) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 查询表统计信息
            String statsSql = """
                SELECT
                    TABLE_ROWS,
                    DATA_LENGTH,
                    INDEX_LENGTH,
                    AVG_ROW_LENGTH
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """;
            Map<String, Object> row = jdbcTemplate.queryForMap(statsSql, tableName);

            stats.put("tableRows", row.get("TABLE_ROWS"));
            stats.put("dataLength", row.get("DATA_LENGTH"));
            stats.put("indexLength", row.get("INDEX_LENGTH"));
            stats.put("avgRowLength", row.get("AVG_ROW_LENGTH"));

        } catch (Exception e) {
            System.out.println("查询表统计信息失败：" + e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 分析执行计划
     */
    private List<String> analyzeExecutionPlan(JdbcTemplate jdbcTemplate, String sql,
                                               List<IndexAnalysis> indexAnalysis) {
        List<String> suggestions = new ArrayList<>();

        try {
            // 执行 EXPLAIN
            List<Map<String, Object>> explainResult = jdbcTemplate.queryForList(
                "EXPLAIN " + sql
            );

            for (Map<String, Object> row : explainResult) {
                String accessType = (String) row.get("type");
                String key = (String) row.get("key");
                Long rows = (Long) row.get("rows");
                String extra = (String) row.get("Extra");

                // 检查是否是全表扫描
                if ("ALL".equals(accessType)) {
                    suggestions.add("⚠️ 检测到全表扫描 (type=ALL)，建议添加索引");
                }

                // 检查是否使用了索引
                if (key == null && rows != null && rows > 1000) {
                    suggestions.add("⚠️ 查询未使用索引且扫描行数较多 (" + rows + ")，建议分析 WHERE 条件添加合适索引");
                }

                // 检查 Extra 信息
                if (extra != null) {
                    if (extra.contains("Using temporary")) {
                        suggestions.add("⚠️ 使用了临时表，考虑优化 GROUP BY 或 ORDER BY");
                    }
                    if (extra.contains("Using filesort")) {
                        suggestions.add("⚠️ 使用了文件排序，考虑在 ORDER BY 字段上添加索引");
                    }
                    if (extra.contains("Using index") && !extra.contains("Using where")) {
                        suggestions.add("✅ 使用了覆盖索引，性能较好");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("分析执行计划失败：" + e.getMessage());
            suggestions.add("执行计划分析失败：" + e.getMessage());
        }

        return suggestions;
    }

    /**
     * 生成索引建议
     */
    private String generateIndexSuggestion(String indexName, String indexType,
                                            List<String> columns, Object cardinality) {
        if ("PRIMARY".equals(indexType)) {
            return "主键索引，无需优化";
        }

        if (columns.size() > 3) {
            return "⚠️ 联合索引列数较多 (" + columns.size() + ")，建议检查是否所有列都必要";
        }

        if (cardinality != null && !"N/A".equals(cardinality)) {
            try {
                long cardValue = Long.parseLong(cardinality.toString());
                if (cardValue < 100) {
                    return "⚠️ 索引基数较低 (" + cardinality + ")，区分度可能不高";
                }
            } catch (NumberFormatException e) {
                // 忽略
            }
        }

        return "✅ 索引结构合理";
    }

    /**
     * 生成 InnoDB 角度的建议
     */
    private List<String> generateInnodbSuggestions(List<IndexAnalysis> indexAnalysis,
                                                    Map<String, Object> tableStats) {
        List<String> suggestions = new ArrayList<>();

        // 检查是否有主键
        boolean hasPrimaryKey = indexAnalysis.stream()
            .anyMatch(ia -> "PRIMARY".equals(ia.indexType()));
        if (!hasPrimaryKey) {
            suggestions.add("⚠️ 表没有主键，InnoDB 会使用隐式主键，影响性能");
        }

        // 检查表大小
        Object tableRows = tableStats.get("tableRows");
        if (tableRows instanceof Number && ((Number) tableRows).longValue() > 1000000) {
            suggestions.add("📊 表数据量较大 (" + tableRows + "行)，建议定期执行 OPTIMIZE TABLE");
        }

        // 检查索引数量
        if (indexAnalysis.size() > 5) {
            suggestions.add("⚠️ 索引数量较多 (" + indexAnalysis.size() + ")，影响写入性能，建议清理冗余索引");
        }

        return suggestions;
    }

    /**
     * 确定优先级
     */
    private int determinePriority(List<String> suggestions) {
        // 包含警告的建议优先级高
        long warningCount = suggestions.stream()
            .filter(s -> s.contains("⚠️"))
            .count();

        if (warningCount >= 3) {
            return 1;  // 高优先级
        } else if (warningCount >= 1) {
            return 2;  // 中优先级
        }
        return 3;  // 低优先级
    }

    /**
     * 确定置信度
     */
    private double determineConfidence(List<IndexAnalysis> indexAnalysis,
                                        Map<String, Object> tableStats) {
        // 有足够的统计信息时置信度高
        double confidence = 0.7;

        if (!indexAnalysis.isEmpty()) {
            confidence += 0.15;
        }
        if (tableStats.get("tableRows") != null) {
            confidence += 0.15;
        }

        return Math.min(confidence, 1.0);
    }

    /**
     * InnoDB 专家分析结果
     */
    public record InnoDBAnalysisResult(
        String tableName,
        List<IndexAnalysis> indexAnalysis,
        List<String> suggestions,
        int priority,  // 优先级：1-高 2-中 3-低
        double confidence  // 置信度：0-1
    ) {}

    /**
     * 索引分析结果
     */
    public record IndexAnalysis(
        String indexName,
        String indexType,  // PRIMARY, UNIQUE, NORMAL
        List<String> columns,
        String cardinality,
        String suggestion  // 对该索引的建议
    ) {}
}
