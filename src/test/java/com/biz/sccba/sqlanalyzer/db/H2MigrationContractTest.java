package com.biz.sccba.sqlanalyzer.db;

import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free local gate (docs/cloud-code-next-goal.md §3.5, §5.1): runs on EVERY build, no
 * environment switch. Migrates a clean H2 management database through the H2 baseline
 * ({@code db/migration-h2}) plus the exact same portable forward migrations
 * ({@code db/migration-common}) that PostgreSQL receives on top of its deployed history, and
 * freezes the resulting schema: all product tables in {@code sql_analyzer} under version-less
 * names, tenant-ownership columns on index/shard/conflict metadata, and the H2 AgentScope state
 * tables. Schema equivalence with PostgreSQL is asserted by {@code SchemaParityTest}.
 */
class H2MigrationContractTest {

    static final List<String> EXPECTED_TABLES = List.of(
            "client", "client_token", "analysis_session", "conversation_message",
            "agent_run", "agent_job", "run_event", "artifact", "artifact_content",
            "document", "document_chunk", "recommendation", "recommendation_feedback",
            "knowledge_source", "knowledge_version", "kb_table_def", "kb_column_def", "kb_rule",
            "kb_enum_value", "kb_alias", "index_def", "shard_def", "metadata_conflict",
            "datasource_profile", "profiling_job", "profile_snapshot", "profile_column_stat",
            "idempotency_record");

    static String jdbcUrl;

    @BeforeAll
    static void migrate() {
        jdbcUrl = "jdbc:h2:mem:migration_contract_h2;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations(ManagementDatabaseDialect.H2.flywayLocations())
                .baselineOnMigrate(true)
                .load();
        var result = flyway.migrate();
        assertTrue(result.success, "H2 Flyway migration must succeed");
        Set<String> versions = new TreeSet<>();
        for (var info : flyway.info().applied()) {
            versions.add(info.getVersion().getVersion());
        }
        // H2 baseline (1) plus the portable forward migrations shared with PostgreSQL.
        assertTrue(versions.containsAll(Set.of("1", "8", "9", "10", "11", "12", "14")),
                "applied H2 versions must include the baseline and the common forward migrations, got: " + versions);
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            c.createStatement().execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void allProductTablesExistUnderSqlAnalyzerSchema() throws Exception {
        Set<String> tables = new TreeSet<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            try (ResultSet rs = c.getMetaData().getTables(null, "SQL_ANALYZER", "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String table : EXPECTED_TABLES) {
            assertTrue(tables.contains(table), "missing sql_analyzer." + table + " on H2; got: " + tables);
        }
    }

    @Test
    void tenantOwnershipColumnsExistOnMetadataTables() throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            for (String table : List.of("index_def", "shard_def", "metadata_conflict")) {
                assertTrue(hasColumn(c, table, "client_id"),
                        "sql_analyzer." + table + " must carry client_id (Goal §3.4/§5)");
            }
        }
    }

    @Test
    void runEventIdentityAndAgentscopeStateTablesExist() throws Exception {
        Set<String> agentscopeTables = new TreeSet<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            try (ResultSet rs = c.getMetaData().getTables(null, "AGENTSCOPE", "%", new String[]{"TABLE"})) {
                while (rs.next()) agentscopeTables.add(rs.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
            }
            // run_event.id must be an auto-generated BIGINT identity (equivalent of PG BIGSERIAL).
            try (ResultSet rs = c.getMetaData().getColumns(null, "SQL_ANALYZER", "RUN_EVENT", "ID")) {
                assertTrue(rs.next(), "run_event.id column must exist");
                assertEquals("BIGINT", rs.getString("TYPE_NAME").toUpperCase(java.util.Locale.ROOT));
                assertTrue(rs.getString("IS_AUTOINCREMENT").equalsIgnoreCase("YES"),
                        "run_event.id must be auto-generated on H2");
            }
        }
        assertTrue(agentscopeTables.contains("agent_state"),
                "H2 AgentScope state store table must exist, got: " + agentscopeTables);
        assertTrue(agentscopeTables.contains("kv_store"),
                "H2 AgentScope base store table must exist, got: " + agentscopeTables);
    }

    static boolean hasColumn(Connection c, String table, String column) throws Exception {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getColumns(null, "SQL_ANALYZER", table.toUpperCase(java.util.Locale.ROOT), "%")) {
            while (rs.next()) columns.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
        }
        return columns.contains(column);
    }
}
