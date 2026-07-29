package com.biz.sccba.sqlanalyzer.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the zero-dependency developer runtime: the application starts with a durable H2
 * management database and an active worker unless a deployment explicitly overrides them.
 */
class DefaultRuntimeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void defaultsToDurableFileH2AndEnablesTheWorker() {
        contextRunner.run(context -> {
            var environment = context.getEnvironment();

            assertThat(environment.getProperty("sql-analyzer.persistence.enabled", Boolean.class))
                    .isTrue();
            assertThat(environment.getProperty("sql-analyzer.worker.enabled", Boolean.class))
                    .isTrue();
            assertThat(environment.getRequiredProperty("sql-analyzer.persistence.jdbc-url"))
                    .startsWith("jdbc:h2:file:")
                    .contains("/.sql-performance-analyzer/data/management")
                    .contains("AUTO_SERVER=TRUE")
                    .doesNotContain("DB_CLOSE_ON_EXIT=FALSE");
            assertThat(environment.getRequiredProperty("sql-analyzer.persistence.username"))
                    .isEqualTo("sa");
            assertThat(environment.getRequiredProperty("sql-analyzer.persistence.password"))
                    .isEmpty();
            assertThat(environment.getProperty(
                    "sql-analyzer.local-h2.datasource-bootstrap.enabled", Boolean.class))
                    .isTrue();
            assertThat(environment.getRequiredProperty(
                    "sql-analyzer.local-h2.datasource-bootstrap.jdbc-url"))
                    .startsWith("jdbc:h2:file:")
                    .contains("/.sql-performance-analyzer/data/local-target")
                    .contains("AUTO_SERVER=TRUE");
        });
    }
}
