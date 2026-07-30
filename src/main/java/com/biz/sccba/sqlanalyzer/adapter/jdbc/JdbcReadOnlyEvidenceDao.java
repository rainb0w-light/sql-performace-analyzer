package com.biz.sccba.sqlanalyzer.adapter.jdbc;

import com.biz.sccba.sqlanalyzer.evidence.ReadOnlyEvidenceDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** JDBC adapter for MySQL-compatible MySQL/GoldenDB targets. Business code sees only the DAO port. */
@Repository
public class JdbcReadOnlyEvidenceDao implements ReadOnlyEvidenceDao {
    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z0-9_$\\.]+")
;
    private static final Pattern READ_ONLY_SQL = Pattern.compile("(?is)^(?:\\s*--[^\\n]*\\n)*\\s*(select|with)\\b.*");
    private final ObjectMapper objectMapper;

    public JdbcReadOnlyEvidenceDao(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Evidence explain(String sql, Map<String, String> profile) {
        return explain(sql, List.of(), profile);
    }

    @Override
    public Evidence explain(String sql, List<Object> arguments, Map<String, String> profile) {
        if (sql == null || !READ_ONLY_SQL.matcher(sql).matches()) {
            return new Evidence("EXPLAIN_PLAN", false, "{}", "只允许对 SELECT/WITH SQL 获取执行计划");
        }
        return withJdbc(profile, jdbc -> {
            String statement = "EXPLAIN " + sql.trim();
            List<Map<String, Object>> rows = jdbc.queryForList(statement,
                    arguments == null ? new Object[0] : arguments.toArray());
            return new Evidence("EXPLAIN_PLAN", true, toText(rows), null);
        });
    }

    @Override
    public Evidence tableStructure(String tableName, Map<String, String> profile) {
        if (tableName == null || !SAFE_TABLE.matcher(tableName).matches()) {
            return new Evidence("TABLE_SCHEMA", false, "{}", "表名格式非法");
        }
        return withJdbc(profile, jdbc -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_KEY "
                            + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? "
                            + "ORDER BY ORDINAL_POSITION", tableName);
            return new Evidence("TABLE_SCHEMA", true, toText(rows), null);
        });
    }

    private Evidence withJdbc(Map<String, String> profile, JdbcOperation operation) {
        if (profile == null) return new Evidence("DATABASE_EVIDENCE", false, "{}", "未提供数据源配置");
        String url = profile.get("jdbcUrl");
        String user = profile.get("username");
        if (url == null || !url.matches("jdbc:(mysql|goldendb):.*")) {
            return new Evidence("DATABASE_EVIDENCE", false, "{}", "仅支持 MySQL/GoldenDB JDBC URL");
        }
        HikariDataSource dataSource = new HikariDataSource();
        try {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(user);
            dataSource.setPassword(profile.get("password"));
            if (profile.get("driverClassName") != null && !profile.get("driverClassName").isBlank()) {
                dataSource.setDriverClassName(profile.get("driverClassName"));
            }
            dataSource.setMaximumPoolSize(1);
            dataSource.setConnectionTimeout(5_000);
            dataSource.setValidationTimeout(3_000);
            dataSource.setReadOnly(true);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(10);
            jdbc.setMaxRows(1_000);
            return operation.run(jdbc);
        } catch (Exception e) {
            return new Evidence("DATABASE_EVIDENCE", false, "{}", e.getMessage());
        } finally {
            dataSource.close();
        }
    }

    private String toText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("数据库证据无法序列化", e);
        }
    }

    @FunctionalInterface
    private interface JdbcOperation { Evidence run(JdbcTemplate jdbc); }
}
