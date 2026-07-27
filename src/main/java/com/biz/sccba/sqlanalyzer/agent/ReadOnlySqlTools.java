package com.biz.sccba.sqlanalyzer.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.biz.sccba.sqlanalyzer.evidence.ReadOnlyEvidenceDao;
import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;
import io.agentscope.core.tool.ToolExecutionContext;
import org.springframework.beans.factory.ObjectProvider;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure, read-only tools. Target-database evidence is accessed only through DAO ports. */
@Component
public class ReadOnlySqlTools {
    private static final Pattern TABLE = Pattern.compile("\\b(?:from|join|update|into|delete\\s+from)\\s+([a-zA-Z0-9_.$]+)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ReadOnlyEvidenceDao evidenceDao;
    private final List<SlowLogSource> slowLogSources;
    private final SlowLogEvidenceSink slowLogEvidenceSink;

    public ReadOnlySqlTools() { this(null, List.of(), (SlowLogEvidenceSink) null); }

    @Autowired
    public ReadOnlySqlTools(ReadOnlyEvidenceDao evidenceDao, List<SlowLogSource> slowLogSources,
                            ObjectProvider<SlowLogEvidenceSink> slowLogEvidenceSink) {
        this.evidenceDao = evidenceDao;
        this.slowLogSources = slowLogSources == null ? List.of() : slowLogSources;
        this.slowLogEvidenceSink = slowLogEvidenceSink.getIfAvailable();
    }

    public ReadOnlySqlTools(ReadOnlyEvidenceDao evidenceDao) { this(evidenceDao, List.of(), (SlowLogEvidenceSink) null); }

    public ReadOnlySqlTools(ReadOnlyEvidenceDao evidenceDao, List<SlowLogSource> slowLogSources) {
        this(evidenceDao, slowLogSources, (SlowLogEvidenceSink) null);
    }

    public ReadOnlySqlTools(ReadOnlyEvidenceDao evidenceDao, List<SlowLogSource> slowLogSources,
                            SlowLogEvidenceSink slowLogEvidenceSink) {
        this.evidenceDao = evidenceDao;
        this.slowLogSources = slowLogSources == null ? List.of() : slowLogSources;
        this.slowLogEvidenceSink = slowLogEvidenceSink;
    }

    @Tool(name = "analyze_sql_shape", description = "对 SQL 做只读静态分析，识别表、连接、过滤条件和高风险模式")
    public String analyzeSqlShape(@ToolParam(name = "sql", description = "待分析的 SQL") String sql) {
        if (sql == null || sql.isBlank()) return "{\"error\":\"sql 不能为空\"}";
        String normalized = sql.replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("normalizedSql", normalized);
        result.put("tables", tables(normalized));
        result.put("joinCount", count(lower, " join "));
        result.put("wherePresent", lower.contains(" where "));
        result.put("selectStar", lower.matches("select\\s+\\*.*"));
        result.put("functionOnPredicateRisk", lower.matches(".*\\b(lower|upper|date|date_format|coalesce|cast)\\s*\\(.*"));
        result.put("subqueryCount", count(lower, "select ") - 1);
        try { return mapper.writeValueAsString(result); }
        catch (Exception e) { return "{\"error\":\"无法序列化 SQL 分析结果\"}"; }
    }

    @Tool(name = "extract_sql_tables", description = "从 SQL 中提取表名，只读，不执行 SQL")
    public String extractSqlTables(@ToolParam(name = "sql", description = "待解析的 SQL") String sql) {
        try { return mapper.writeValueAsString(tables(sql == null ? "" : sql)); }
        catch (Exception e) { return "[]"; }
    }

    @Tool(name = "explain_sql", description = "在配置的 MySQL/GoldenDB 目标库上只读获取 SQL 执行计划")
    public String explainSql(@ToolParam(name = "sql", description = "只读 SELECT/WITH SQL") String sql,
                             @ToolParam(name = "datasourceProfile", description = "数据源配置 JSON") String datasourceProfile) {
        if (evidenceDao == null) return "{\"success\":false,\"error\":\"证据 DAO 未配置\"}";
        try {
            Map<String, String> profile = mapper.readValue(datasourceProfile, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            var evidence = evidenceDao.explain(sql, profile);
            return mapper.writeValueAsString(evidence);
        } catch (Exception e) { return "{\"success\":false,\"error\":\"数据源配置 JSON 非法\"}"; }
    }

    @Tool(name = "inspect_table", description = "在配置的 MySQL/GoldenDB 目标库上只读读取表结构")
    public String inspectTable(@ToolParam(name = "tableName", description = "表名") String tableName,
                               @ToolParam(name = "datasourceProfile", description = "数据源配置 JSON") String datasourceProfile) {
        if (evidenceDao == null) return "{\"success\":false,\"error\":\"证据 DAO 未配置\"}";
        try {
            Map<String, String> profile = mapper.readValue(datasourceProfile, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            var evidence = evidenceDao.tableStructure(tableName, profile);
            return mapper.writeValueAsString(evidence);
        } catch (Exception e) { return "{\"success\":false,\"error\":\"数据源配置 JSON 非法\"}"; }
    }

    @Tool(name = "search_slow_logs", description = "从已保存日志或私有日志平台读取慢 SQL 样本，只读")
    public String searchSlowLogs(@ToolParam(name = "source", description = "ARTIFACT 或 HTTP") String source,
                                 @ToolParam(name = "options", description = "来源配置 JSON") String options,
                                 ToolExecutionContext executionContext) {
        try {
            Map<String, String> values = mapper.readValue(options, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            values = new java.util.HashMap<>(values);
            AgentExecutionContext toolContext = executionContext == null ? null : executionContext.get(AgentExecutionContext.class);
            if (toolContext != null && toolContext.clientId() != null) {
                // Server-stamped tenant identity: slow-log sources must never trust client input for ownership.
                values.put("clientId", toolContext.clientId());
            }
            SlowLogSource selected = slowLogSources.stream().filter(s -> s.getClass().getSimpleName().toUpperCase(Locale.ROOT).startsWith(source.toUpperCase(Locale.ROOT))).findFirst().orElse(null);
            if (selected == null) return "{\"source\":\"" + json(source) + "\",\"entries\":[]}";
            SlowLogSource.SlowLogBatch batch = selected.fetch(new SlowLogSource.Query(null, null, null, 100, values));
            ObjectNode result = mapper.valueToTree(batch);
            AgentExecutionContext context = executionContext == null ? null : executionContext.get(AgentExecutionContext.class);
            if (slowLogEvidenceSink != null && context != null && !batch.entries().isEmpty()) {
                try {
                    result.put("artifactId", slowLogEvidenceSink.persist(context, batch));
                    result.put("evidencePersisted", true);
                } catch (Exception persistError) {
                    result.put("evidencePersisted", false);
                    result.put("persistError", persistError.getMessage() == null ? "无法保存慢日志证据" : persistError.getMessage());
                }
            }
            return mapper.writeValueAsString(result);
        } catch (Exception e) { return "{\"entries\":[],\"error\":\"日志来源配置 JSON 非法\"}"; }
    }

    /** Direct-call compatibility for unit tests and non-AgentScope callers. */
    public String searchSlowLogs(String source, String options) {
        return searchSlowLogs(source, options, ToolExecutionContext.empty());
    }

    private static String json(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static java.util.List<String> tables(String sql) {
        java.util.List<String> values = new java.util.ArrayList<>();
        Matcher matcher = TABLE.matcher(sql);
        while (matcher.find() && !values.contains(matcher.group(1))) values.add(matcher.group(1));
        return values;
    }

    private static int count(String value, String token) {
        int count = 0, from = 0;
        while ((from = value.indexOf(token, from)) >= 0) { count++; from += token.length(); }
        return count;
    }
}
