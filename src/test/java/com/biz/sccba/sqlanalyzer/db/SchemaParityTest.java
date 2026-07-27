package com.biz.sccba.sqlanalyzer.db;

import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Schema parity gate (docs/cloud-code-next-goal.md §3.7): the normalized {@code sql_analyzer}
 * schema of a migrated PostgreSQL (deployed history + common forward migrations) must equal that
 * of a migrated H2 (H2 baseline + the same common forward migrations): same tables, columns
 * (normalized types + sizes), primary keys, foreign keys and business indexes. Runs in the
 * Docker gate (needs PostgreSQL; H2 is embedded).
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class SchemaParityTest {

    static PostgreSQLContainer<?> postgres;
    static String h2Url;

    @BeforeAll
    static void migrateBoth() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(ManagementDatabaseDialect.POSTGRESQL.flywayLocations())
                .baselineOnMigrate(true)
                .load()
                .migrate();

        h2Url = "jdbc:h2:mem:schema_parity;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(h2Url, "sa", "")
                .locations(ManagementDatabaseDialect.H2.flywayLocations())
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        if (postgres != null) postgres.stop();
    }

    /**
     * Tables intentionally present on only one management database:
     * kb_embedding_portable is the H2 portable-embedding store; PostgreSQL uses the PgVector
     * adapter's own table (created by the library, outside Flyway) instead.
     */
    private static final Set<String> H2_ONLY_TABLES = Set.of("kb_embedding_portable");

    @Test
    void normalizedSchemasAreEqual() throws Exception {
        Snapshot pg;
        Snapshot h2;
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword())) {
            pg = Snapshot.capture(c, "sql_analyzer");
        }
        try (Connection c = DriverManager.getConnection(h2Url, "sa", "")) {
            h2 = Snapshot.capture(c, "SQL_ANALYZER").excluding(H2_ONLY_TABLES);
        }

        assertEquals(pg.tables, h2.tables, "table sets must match");
        assertEquals(pg.columns, h2.columns, "columns (name/type/size/nullable) must match");
        assertEquals(pg.primaryKeys, h2.primaryKeys, "primary keys must match");
        assertEquals(pg.foreignKeys, h2.foreignKeys, "foreign keys must match");
        assertEquals(pg.indexes, h2.indexes, "business indexes (table, columns, uniqueness) must match");
    }

    /** Normalized, engine-independent view of one schema. */
    record Snapshot(Set<String> tables, Set<String> columns, Set<String> primaryKeys,
                    Set<String> foreignKeys, Set<String> indexes) {

        /** Removes intentionally database-specific tables and everything derived from them. */
        Snapshot excluding(Set<String> tableNames) {
            Set<String> t = new TreeSet<>(tables);
            Set<String> cols = new TreeSet<>();
            Set<String> pks = new TreeSet<>();
            Set<String> fks = new TreeSet<>();
            Set<String> idx = new TreeSet<>();
            for (String entry : columns) if (!startsWithAny(entry, tableNames)) cols.add(entry);
            for (String entry : primaryKeys) if (!startsWithAny(entry, tableNames)) pks.add(entry);
            for (String entry : foreignKeys) if (!startsWithAny(entry, tableNames)) fks.add(entry);
            for (String entry : indexes) if (!startsWithAny(entry, tableNames)) idx.add(entry);
            t.removeAll(tableNames);
            return new Snapshot(t, cols, pks, fks, idx);
        }

        private static boolean startsWithAny(String entry, Set<String> tableNames) {
            for (String table : tableNames) {
                if (entry.startsWith(table + ".") || entry.startsWith(table + "(")) return true;
            }
            return false;
        }

        static Snapshot capture(Connection connection, String schema) throws Exception {
            DatabaseMetaData md = connection.getMetaData();
            Set<String> tables = new TreeSet<>();
            try (ResultSet rs = md.getTables(null, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }

            Set<String> columns = new TreeSet<>();
            Map<String, Set<String>> pkColumns = new TreeMap<>();
            for (String table : tables) {
                String rawTable = rawName(md, schema, table);
                try (ResultSet rs = md.getColumns(null, schema, rawTable, "%")) {
                    while (rs.next()) {
                        String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                        String type = normalizeType(rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"));
                        String nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")) ? "null" : "notnull";
                        columns.add(table + "." + column + ":" + type + ":" + nullable);
                    }
                }
                Set<String> pk = new TreeSet<>();
                try (ResultSet rs = md.getPrimaryKeys(null, schema, rawTable)) {
                    while (rs.next()) pk.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
                pkColumns.put(table, pk);
            }

            Set<String> primaryKeys = new TreeSet<>();
            for (var entry : pkColumns.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    primaryKeys.add(entry.getKey() + "(" + String.join(",", entry.getValue()) + ")");
                }
            }

            Set<String> foreignKeys = new TreeSet<>();
            Map<String, Map<String, TreeSet<String>>> fkBackingColumns = new TreeMap<>();
            for (String table : tables) {
                String rawTable = rawName(md, schema, table);
                Map<String, String[]> fk = new TreeMap<>();
                try (ResultSet rs = md.getImportedKeys(null, schema, rawTable)) {
                    while (rs.next()) {
                        String fkName = String.valueOf(rs.getString("FK_NAME"));
                        String fkColumn = rs.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT);
                        String refTable = rs.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
                        fk.merge(fkName, new String[]{fkColumn, refTable},
                                (a, b) -> new String[]{a[0] + "+" + b[0], a[1]});
                        fkBackingColumns.computeIfAbsent(table, k -> new TreeMap<>())
                                .computeIfAbsent(fkName, k -> new TreeSet<>()).add(fkColumn);
                    }
                }
                for (var e : fk.entrySet()) {
                    foreignKeys.add(table + "." + e.getValue()[0] + "->" + e.getValue()[1]);
                }
            }

            Set<String> indexes = new TreeSet<>();
            for (String table : tables) {
                String rawTable = rawName(md, schema, table);
                Map<String, TreeSet<String>> byIndex = new TreeMap<>();
                Map<String, Boolean> uniqueness = new TreeMap<>();
                try (ResultSet rs = md.getIndexInfo(null, schema, rawTable, false, false)) {
                    while (rs.next()) {
                        String indexName = rs.getString("INDEX_NAME");
                        if (indexName == null) continue;
                        String column = rs.getString("COLUMN_NAME");
                        if (column == null) continue;
                        byIndex.computeIfAbsent(indexName, k -> new TreeSet<>())
                                .add(column.toLowerCase(Locale.ROOT));
                        uniqueness.put(indexName, !rs.getBoolean("NON_UNIQUE"));
                    }
                }
                Set<String> pk = pkColumns.getOrDefault(table, Set.of());
                java.util.Collection<TreeSet<String>> fkSets =
                        fkBackingColumns.getOrDefault(table, Map.of()).values();
                for (var e : byIndex.entrySet()) {
                    // Skip primary-key backing indexes; direction is ignored (normalized away).
                    if (e.getValue().equals(pk)) continue;
                    // H2 (unlike PostgreSQL) auto-creates backing indexes for foreign keys;
                    // they are not business indexes and are normalized away.
                    if (fkSets.contains(e.getValue())) continue;
                    indexes.add(table + "(" + String.join(",", e.getValue()) + ")"
                            + (Boolean.TRUE.equals(uniqueness.get(e.getKey())) ? ":unique" : ""));
                }
            }

            return new Snapshot(tables, columns, primaryKeys, foreignKeys, indexes);
        }

        /** H2 stores unquoted names upper-case, PostgreSQL lower-case; probe both spellings. */
        private static String rawName(DatabaseMetaData md, String schema, String lowerTable) throws Exception {
            try (ResultSet rs = md.getTables(null, schema, lowerTable, new String[]{"TABLE"})) {
                if (rs.next()) return rs.getString("TABLE_NAME");
            }
            try (ResultSet rs = md.getTables(null, schema, lowerTable.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
                if (rs.next()) return rs.getString("TABLE_NAME");
            }
            return lowerTable;
        }

        private static String normalizeType(String rawType, int size) {
            String t = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT).trim();
            // Byte strings first: H2 reports BLOB as "BINARY LARGE OBJECT" (contains "large object").
            if (t.equals("bytea") || t.contains("binary")) return "BYTES";
            // H2 realizes TEXT as CHARACTER VARYING(1000000000); PostgreSQL TEXT is unbounded:
            // treat giant varchars as TEXT.
            if (t.startsWith("varchar") || t.startsWith("character varying")) {
                return size >= 1_000_000 ? "TEXT" : "VARCHAR(" + size + ")";
            }
            if (t.equals("bpchar") || t.startsWith("char")) return "CHAR(" + size + ")";
            if (t.equals("text") || t.contains("large object") || t.equals("clob") || t.equals("nclob")) return "TEXT";
            if (t.equals("int4") || t.equals("integer") || t.equals("int")) return "INT";
            if (t.equals("int8") || t.startsWith("bigint") || t.contains("serial") || t.contains("identity")) return "BIGINT";
            if (t.contains("time zone") || t.equals("timestamptz")) return "TIMESTAMPTZ";
            if (t.equals("timestamp") || t.startsWith("timestamp")) return "TIMESTAMP";
            if (t.equals("float8") || t.contains("double")) return "DOUBLE";
            if (t.equals("numeric") || t.equals("decimal")) return "NUMERIC";
            if (t.equals("bool") || t.equals("bit") || t.equals("boolean")) return "BOOLEAN";
            return t.toUpperCase(Locale.ROOT);
        }
    }
}
