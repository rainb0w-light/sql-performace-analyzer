package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.persistence.dialect.H2DialectAdapter;
import com.biz.sccba.sqlanalyzer.persistence.dialect.JobClaimStrategy;
import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import com.biz.sccba.sqlanalyzer.persistence.dialect.PostgreSqlDialectAdapter;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Management-database persistence (docs/cloud-code-next-goal.md §3): the product's own business
 * database (sessions, runs, events, artifacts, recommendations, knowledge, profiles, metadata).
 *
 * <p>Database-neutral by construction: the dialect is detected from JDBC metadata at startup and
 * selects the Flyway locations — PostgreSQL replays the immutable deployed history
 * ({@code db/migration}) plus portable forward migrations ({@code db/migration-common}); H2 starts
 * from an equivalent clean-install baseline ({@code db/migration-h2}) plus the very same
 * {@code db/migration-common} forward migrations. Business objects live in the {@code sql_analyzer}
 * schema under version-less names on both databases; historical Flyway files are never modified.
 */
@Configuration
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
@org.springframework.transaction.annotation.EnableTransactionManagement
@org.springframework.data.jdbc.repository.config.EnableJdbcRepositories(
        basePackages = "com.biz.sccba.sqlanalyzer.persistence.jdbc.repository")
public class PersistenceConfig {

    @Bean(name = "managementDataSource")
    @ConfigurationProperties(prefix = "sql-analyzer.persistence")
    public DataSource managementDataSource() {
        // Driver class is inferred from the JDBC URL (PostgreSQL or H2), so the same config keys
        // serve both management databases; no driver is hard-coded here anymore.
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "managementDialect")
    public ManagementDatabaseDialect managementDialect(@Qualifier("managementDataSource") DataSource managementDataSource) {
        return ManagementDatabaseDialect.detect(managementDataSource);
    }

    @Bean(name = "managementJdbcTemplate")
    public JdbcTemplate managementJdbcTemplate(@Qualifier("managementDataSource") DataSource managementDataSource) {
        return new JdbcTemplate(managementDataSource);
    }

    @Bean(name = "managementNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate managementNamedParameterJdbcTemplate(
            @Qualifier("managementDataSource") DataSource managementDataSource) {
        return new NamedParameterJdbcTemplate(managementDataSource);
    }

    /**
     * Neutral identifier rendering over the detected vendor dialect: unquoted, as-is identifiers
     * fold to the correct storage case on PostgreSQL (lower case) and H2 (upper case), so one
     * set of Spring Data JDBC entities serves both management databases (Goal §3.2). Boot's
     * auto-configured dialect backs off in the presence of this bean.
     */
    @Bean
    public org.springframework.data.relational.core.dialect.Dialect jdbcDialect(
            @Qualifier("managementNamedParameterJdbcTemplate")
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations jdbcOperations) {
        return new com.biz.sccba.sqlanalyzer.persistence.jdbc.NeutralIdentifierDialect(
                org.springframework.data.jdbc.core.dialect.DialectResolver.getDialect(jdbcOperations.getJdbcOperations()));
    }

    /**
     * Also published as {@code transactionManager}: the default qualifier used by Spring Data
     * JDBC's built-in repository transactions (services keep using the explicit
     * {@code managementTransactionManager} qualifier).
     */
    @Bean(name = {"managementTransactionManager", "transactionManager"})
    public PlatformTransactionManager managementTransactionManager(@Qualifier("managementDataSource") DataSource managementDataSource) {
        return new DataSourceTransactionManager(managementDataSource);
    }

    /** Job-queue claim/lease strategy: the single sanctioned home of vendor-specific SQL (Goal §3.5). */
    @Bean(name = "jobClaimStrategy")
    public JobClaimStrategy jobClaimStrategy(@Qualifier("managementDialect") ManagementDatabaseDialect dialect,
                                             @Qualifier("managementTransactionManager") PlatformTransactionManager transactionManager) {
        return switch (dialect) {
            case POSTGRESQL -> new PostgreSqlDialectAdapter();
            case H2 -> new H2DialectAdapter(transactionManager);
        };
    }

    /** AgentScope DistributedStore selection per management database (Goal §3.6). */
    @Bean
    public com.biz.sccba.sqlanalyzer.agentscope.store.AgentScopeStoreProvider agentScopeStoreProvider(
            @Qualifier("managementDialect") ManagementDatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> new com.biz.sccba.sqlanalyzer.agentscope.store.PostgreSqlAgentScopeStoreProvider();
            case H2 -> new com.biz.sccba.sqlanalyzer.agentscope.store.H2AgentScopeStoreProvider();
        };
    }

    @Bean(initMethod = "migrate")
    public Flyway managementFlyway(@Qualifier("managementDataSource") DataSource managementDataSource,
                                   @Qualifier("managementDialect") ManagementDatabaseDialect dialect) {
        return Flyway.configure()
                .dataSource(managementDataSource)
                .locations(dialect.flywayLocations())
                .baselineOnMigrate(true)
                .load();
    }
}
