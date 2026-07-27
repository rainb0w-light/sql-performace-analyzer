package com.biz.sccba.sqlanalyzer.persistence;

/**
 * Docker-free local gate (docs/cloud-code-next-goal.md §5.1): the full repository contract runs
 * on H2 on EVERY build, unconditionally — no environment switch, no skip.
 */
class H2RepositoryContractTest extends RepositoryContractTestBase {

    private static final String JDBC_URL =
            "jdbc:h2:mem:repository_contract;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";

    @Override
    String jdbcUrl() {
        return JDBC_URL;
    }

    @Override
    String username() {
        return "sa";
    }

    @Override
    String password() {
        return "";
    }
}
