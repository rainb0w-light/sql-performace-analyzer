package com.biz.sccba.sqlanalyzer.profiling;

import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Job;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Snapshot;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Deterministic read-only profiling execution (development-guide §7.2):
 * fixed dialect templates, bounded sampling, per-statement timeout, immutable snapshot, and a
 * sensitivity policy for Top-K values (plaintext / SHA-256 / omitted) resolved from the
 * published business knowledge.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ProfilingService {

    private static final Pattern READ_ONLY_SQL = Pattern.compile("(?is)^\\s*(select|with)\\b.*");
    private static final int MAX_TABLES = 50;
    private static final int MAX_COLUMNS_PER_TABLE = 20;
    private static final List<Double> QUANTILES = List.of(0.25, 0.5, 0.75, 0.95);

    private final ProfilingRepository dao;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<KnowledgeQueryService> knowledgeProvider;
    private final int sampleRows;
    private final int topK;
    private final int statementTimeoutMs;

    public ProfilingService(ProfilingRepository dao, Environment environment, ObjectMapper objectMapper,
                            ObjectProvider<KnowledgeQueryService> knowledgeProvider,
                            @Value("${sql-analyzer.profiling.sample-rows:50000}") int sampleRows,
                            @Value("${sql-analyzer.profiling.top-k:10}") int topK,
                            @Value("${sql-analyzer.profiling.statement-timeout-ms:10000}") int statementTimeoutMs) {
        this.dao = dao;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.knowledgeProvider = knowledgeProvider;
        this.sampleRows = Math.max(100, sampleRows);
        this.topK = Math.max(1, topK);
        this.statementTimeoutMs = Math.max(1000, statementTimeoutMs);
    }

    public void runJob(Job job) {
        Snapshot snapshot = null;
        try {
            DatasourceProfile profile = dao.findProfile(job.datasourceProfileId(), job.clientId())
                    .orElseThrow(() -> new IllegalStateException("数据源配置不存在"));
            if (!profile.readOnly()) throw new IllegalStateException("数据源配置必须为只读");
            String password = profile.credentialEnv() == null || profile.credentialEnv().isBlank()
                    ? "" : String.valueOf(environment.getProperty(profile.credentialEnv(), ""));
            JsonNode config = objectMapper.readTree(job.configJson() == null ? "{}" : job.configJson());
            String schema = config.path("schema").asText("");
            List<String> tables = requestedTables(config);

            snapshot = dao.createSnapshot("snap_" + UUID.randomUUID(), job.id(), profile.id(), job.configJson());
            DialectAdapter adapter = new MySqlDialectAdapter();

            try (Connection conn = DriverManager.getConnection(profile.jdbcUrl(), profile.username(), password)) {
                if (tables.isEmpty()) {
                    tables = discoverTables(conn, adapter, schema);
                }
                for (String table : tables) {
                    profileTable(conn, adapter, profile, snapshot, schema, table);
                }
            }
            dao.finishSnapshot(snapshot.id(), "COMPLETED");
            dao.completeJob(job.id());
        } catch (Exception e) {
            if (snapshot != null) dao.finishSnapshot(snapshot.id(), "FAILED");
            dao.failJob(job.id(), e.getMessage());
        }
    }

    private void profileTable(Connection conn, DialectAdapter adapter, DatasourceProfile profile,
                              Snapshot snapshot, String schema, String table) throws Exception {
        List<String[]> columns = query(conn, adapter.columnsSql(schema, table), rs ->
                new String[] { rs.getString(1), rs.getString(2), rs.getString(3) });
        int columnBudget = Math.min(columns.size(), MAX_COLUMNS_PER_TABLE);
        for (int i = 0; i < columnBudget; i++) {
            String column = columns.get(i)[0];
            String dataType = columns.get(i)[1];
            DialectAdapter.SensitivePolicy policy = policyFor(profile.clientId(), table, column);

            Double nullRatio = null;
            var nulls = query(conn, adapter.nullRatioSql(schema, table, column, sampleRows),
                    rs -> new long[] { rs.getLong(1), rs.getLong(2) });
            if (!nulls.isEmpty() && nulls.get(0)[0] > 0) {
                nullRatio = (double) nulls.get(0)[1] / nulls.get(0)[0];
            }

            Long distinct = null;
            String min = null;
            String max = null;
            Double minNum = null;
            Double maxNum = null;
            var dmm = query(conn, adapter.distinctMinMaxSql(schema, table, column, sampleRows),
                    rs -> new String[] { String.valueOf(rs.getLong(1)), rs.getString(2), rs.getString(3) });
            if (!dmm.isEmpty()) {
                distinct = Long.parseLong(dmm.get(0)[0]);
                min = dmm.get(0)[1];
                max = dmm.get(0)[2];
                minNum = tryParse(min);
                maxNum = tryParse(max);
            }

            String topKJson = topKJson(conn, adapter, schema, table, column, policy);
            String bucketsJson = "[]";
            if (minNum != null && maxNum != null && isNumericType(dataType)) {
                var buckets = query(conn, adapter.bucketsSql(schema, table, column, sampleRows, 10, minNum, maxNum),
                        rs -> new String[] { String.valueOf(rs.getLong(1)), String.valueOf(rs.getLong(2)) });
                bucketsJson = objectMapper.writeValueAsString(buckets.stream()
                        .map(b -> Map.of("bucket", (Object) b[0], "count", (Object) b[1])).toList());
            }

            List<Map<String, Object>> quantileValues = new ArrayList<>();
            if (minNum != null && maxNum != null && isNumericType(dataType)) {
                for (var quantile : adapter.quantileSqls(schema, table, column, sampleRows, QUANTILES)) {
                    var rows = query(conn, quantile.sql(), rs -> rs.getString(1));
                    if (policy != DialectAdapter.SensitivePolicy.OMITTED) {
                        String value = rows.isEmpty() ? "" : rows.get(0);
                        quantileValues.add(Map.of("q", quantile.q(), "value",
                                policy == DialectAdapter.SensitivePolicy.HASHED ? sha256(value) : value));
                    }
                }
            }

            String storedMin = protectedValue(min, policy);
            String storedMax = protectedValue(max, policy);
            dao.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshot.id(), schema, table, column,
                    nullRatio, distinct, storedMin, storedMax, topKJson, bucketsJson,
                    objectMapper.writeValueAsString(quantileValues), policy.name(),
                    java.time.Instant.now()));
        }
    }

    private String topKJson(Connection conn, DialectAdapter adapter, String schema, String table,
                            String column, DialectAdapter.SensitivePolicy policy) throws Exception {
        String sql = adapter.topKSql(schema, table, column, sampleRows, topK, policy);
        if (policy == DialectAdapter.SensitivePolicy.OMITTED) {
            var counts = query(conn, sql, rs -> rs.getLong(1));
            return objectMapper.writeValueAsString(Map.of("omitted", true, "topFrequencies", counts));
        }
        var rows = query(conn, sql, rs -> new String[] { rs.getString(1), String.valueOf(rs.getLong(2)) });
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] row : rows) {
            out.add(Map.of("value", row[0] == null ? "" : row[0], "count", (Object) Long.parseLong(row[1])));
        }
        return objectMapper.writeValueAsString(out);
    }

    private DialectAdapter.SensitivePolicy policyFor(String clientId, String table, String column) {
        var knowledge = knowledgeProvider.getIfAvailable();
        if (knowledge == null) return DialectAdapter.SensitivePolicy.PLAINTEXT;
        try {
            String policy = knowledge.sensitivityPolicy(clientId, table, column);
            return DialectAdapter.SensitivePolicy.valueOf(policy);
        } catch (RuntimeException e) {
            return DialectAdapter.SensitivePolicy.HASHED;
        }
    }

    private List<String> discoverTables(Connection conn, DialectAdapter adapter, String schema) throws Exception {
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'"
                + (schema.isBlank() ? " AND TABLE_SCHEMA=DATABASE()" : " AND TABLE_SCHEMA=" + MySqlDialectAdapter.literal(schema))
                + " ORDER BY TABLE_NAME LIMIT " + MAX_TABLES;
        return query(conn, sql, rs -> rs.getString(1));
    }

    private List<String> requestedTables(JsonNode config) {
        List<String> tables = new ArrayList<>();
        JsonNode node = config.path("tables");
        if (node.isArray()) {
            for (JsonNode t : node) {
                String name = t.asText("");
                if (!name.isBlank()) tables.add(name);
            }
        }
        return tables.size() > MAX_TABLES ? tables.subList(0, MAX_TABLES) : tables;
    }

    private <T> List<T> query(Connection conn, String sql, RowMapper<T> mapper) throws Exception {
        if (!READ_ONLY_SQL.matcher(sql).matches()) {
            throw new IllegalStateException("只允许只读 SELECT 查询");
        }
        List<T> out = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(Math.max(1, statementTimeoutMs / 1000));
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) out.add(mapper.map(rs));
            }
        }
        return out;
    }

    private static boolean isNumericType(String dataType) {
        if (dataType == null) return false;
        String t = dataType.toLowerCase(java.util.Locale.ROOT);
        return t.contains("int") || t.contains("decimal") || t.contains("numeric")
                || t.contains("float") || t.contains("double") || t.contains("real");
    }

    private static Double tryParse(String value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String protectedValue(String value, DialectAdapter.SensitivePolicy policy) {
        if (value == null || policy == DialectAdapter.SensitivePolicy.OMITTED) return null;
        return policy == DialectAdapter.SensitivePolicy.HASHED ? sha256(value) : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws Exception;
    }
}
