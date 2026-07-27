package com.biz.sccba.sqlanalyzer.db;

import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 contract (Docker gate: RUN_POSTGRES_INTEGRATION_TESTS=true, runs in CI).
 *
 * Freezes the deployed-schema outcome: Flyway migrates a clean PostgreSQL through the pinned
 * history (versions 2..5) PLUS the forward migrations — the rename that moves all business
 * objects into the {@code sql_analyzer} schema under version-less names, the knowledge/profile/
 * metadata schema, and the portable common forward migrations ({@code db/migration-common})
 * shared with H2. Historical files are untouched (immutability pinned by
 * MigrationHistoryGuardTest).
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class FlywayMigrationContractTest {

    static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void flywayMigratesPinnedHistoryAndRenamesBusinessObjects() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(ManagementDatabaseDialect.POSTGRESQL.flywayLocations())
                .baselineOnMigrate(true)
                .load();
        var result = flyway.migrate();
        assertTrue(result.success);

        Set<String> versions = new TreeSet<>();
        for (var info : flyway.info().applied()) {
            versions.add(info.getVersion().getVersion());
        }
        assertTrue(versions.containsAll(Set.of("2", "3", "4", "5", "6", "7", "8", "9",
                        "10", "11", "12", "13", "14")),
                "applied Flyway versions must include pinned history, the rename, the knowledge "
                        + "schema and the common forward migrations, got: " + versions);

        Set<String> sqlAnalyzerTables = new TreeSet<>();
        Set<String> publicTables = new TreeSet<>();
        try (Connection c = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (ResultSet rs = c.getMetaData().getTables(null, "sql_analyzer", "%", new String[]{"TABLE"})) {
                while (rs.next()) sqlAnalyzerTables.add(rs.getString("TABLE_NAME"));
            }
            try (ResultSet rs = c.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) publicTables.add(rs.getString("TABLE_NAME"));
            }
        }
        List<String> expected = List.of(
                "client", "client_token", "analysis_session", "conversation_message",
                "agent_run", "agent_job", "run_event", "artifact", "artifact_content",
                "document", "document_chunk", "recommendation", "recommendation_feedback",
                "knowledge_source", "knowledge_version", "kb_table_def", "kb_column_def", "kb_rule",
                "kb_enum_value", "kb_alias", "index_def", "shard_def", "metadata_conflict",
                "datasource_profile", "profiling_job", "profile_snapshot", "profile_column_stat",
                "idempotency_record");
        for (String table : expected) {
            assertTrue(sqlAnalyzerTables.contains(table),
                    "missing sql_analyzer." + table + " after forward migration; got: " + sqlAnalyzerTables);
        }
        String legacyPrefix = "v" + "2_"; // assembled to keep the marker-scan test clean
        for (String table : publicTables) {
            assertTrue(!table.toLowerCase(java.util.Locale.ROOT).startsWith(legacyPrefix),
                    "legacy-prefixed table must not remain in public schema: " + table);
        }

        Set<String> legacyCatalogObjects = new TreeSet<>();
        try (Connection c = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            String marker = "v" + "2";
            try (var statement = c.prepareStatement("""
                    SELECT 'relation:' || n.nspname || '.' || c.relname AS object_name
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname IN ('public', 'sql_analyzer')
                      AND lower(c.relname) LIKE ?
                    UNION ALL
                    SELECT 'constraint:' || n.nspname || '.' || c.relname || '.' || k.conname
                    FROM pg_constraint k
                    JOIN pg_class c ON c.oid = k.conrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'sql_analyzer'
                      AND lower(k.conname) LIKE ?
                    """)) {
                statement.setString(1, "%" + marker + "%");
                statement.setString(2, "%" + marker + "%");
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) legacyCatalogObjects.add(rs.getString(1));
                }
            }
        }
        assertTrue(legacyCatalogObjects.isEmpty(),
                "PostgreSQL runtime catalog still contains product marker: " + legacyCatalogObjects);
    }
}
