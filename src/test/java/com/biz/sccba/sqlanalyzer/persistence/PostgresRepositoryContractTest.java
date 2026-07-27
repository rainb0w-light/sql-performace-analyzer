package com.biz.sccba.sqlanalyzer.persistence;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Docker gate (RUN_POSTGRES_INTEGRATION_TESTS=true, enforced in CI): the identical repository
 * contract against real PostgreSQL via Testcontainers. Deliberately NOT skipped via assumeTrue —
 * the environment gate is the sanctioned CI switch (docs/cloud-code-next-goal.md: "不允许通过
 * assumeTrue 静默跳过").
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class PostgresRepositoryContractTest extends RepositoryContractTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Override
    String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    @Override
    String username() {
        return POSTGRES.getUsername();
    }

    @Override
    String password() {
        return POSTGRES.getPassword();
    }
}
