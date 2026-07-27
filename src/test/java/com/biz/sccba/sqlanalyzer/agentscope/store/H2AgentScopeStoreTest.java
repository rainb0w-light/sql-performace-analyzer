package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import com.zaxxer.hikari.HikariDataSource;
import io.agentscope.harness.agent.DistributedStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;

/**
 * Docker-free gate (runs on every build): the AgentScope store contract on the persistent H2
 * combination, including restart recovery over a FILE database — a genuinely durable restart,
 * not an in-memory illusion.
 */
class H2AgentScopeStoreTest extends AgentScopeStoreContractTestBase {

    static String jdbcUrl;
    static HikariDataSource dataSource;

    @BeforeAll
    static void migrate() throws Exception {
        jdbcUrl = "jdbc:h2:file:" + Files.createTempDirectory("agentscope-store-h2").resolve("agentscope")
                + ";DB_CLOSE_DELAY=-1";
        dataSource = pool();
        Flyway.configure()
                .dataSource(dataSource)
                .locations(ManagementDatabaseDialect.H2.flywayLocations())
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @AfterAll
    static void cleanup() {
        if (dataSource != null) dataSource.close();
    }

    static HikariDataSource pool() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Override
    DistributedStore newStore() {
        if (dataSource == null || dataSource.isClosed()) {
            dataSource = pool();
        }
        return new H2AgentScopeStoreProvider().create(dataSource);
    }

    @Override
    void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
