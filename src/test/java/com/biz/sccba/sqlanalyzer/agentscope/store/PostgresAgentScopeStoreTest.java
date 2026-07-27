package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.harness.agent.DistributedStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Docker gate (RUN_POSTGRES_INTEGRATION_TESTS=true, CI-enforced): the same store contract
 * against the PostgreSQL composition (official AgentStateStore + portable KV store). Flyway
 * runs the deployed history plus the common forward migrations first, exactly like production,
 * so {@code agentscope.kv_store} exists before the store is used.
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresAgentScopeStoreTest extends AgentScopeStoreContractTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    static HikariDataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = pool();
        Flyway.configure()
                .dataSource(dataSource)
                .locations(ManagementDatabaseDialect.POSTGRESQL.flywayLocations())
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) dataSource.close();
        POSTGRES.stop();
    }

    static HikariDataSource pool() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Override
    DistributedStore newStore() {
        if (dataSource == null || dataSource.isClosed()) {
            dataSource = pool();
        }
        return new PostgreSqlAgentScopeStoreProvider().create(dataSource);
    }

    @Override
    void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
