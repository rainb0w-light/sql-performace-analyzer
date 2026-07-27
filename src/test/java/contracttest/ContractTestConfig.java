package contracttest;

import com.biz.sccba.sqlanalyzer.persistence.dialect.H2DialectAdapter;
import com.biz.sccba.sqlanalyzer.persistence.dialect.JobClaimStrategy;
import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import com.biz.sccba.sqlanalyzer.persistence.dialect.PostgreSqlDialectAdapter;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Persistence-only context for the Repository Contract Tests: the exact production stack
 * (Flyway dual-path migrations, dialect detection, Spring Data JDBC repositories and the port
 * adapters) without controllers, Agent runtime or LLM wiring. Shared by the H2 (always-on local
 * gate) and PostgreSQL (Docker gate) contract suites, so both databases prove the same contract.
 *
 * <p>Deliberately carries no {@code @Configuration}/{@code @Component} stereotype: it is
 * registered explicitly by the contract suites, so full-application test contexts never
 * component-scan it (its {@code contract.*} placeholders must not leak into other tests).
 */
@EnableTransactionManagement
@EnableJdbcRepositories(basePackages = "com.biz.sccba.sqlanalyzer.persistence.jdbc.repository")
@ComponentScan(basePackages = "com.biz.sccba.sqlanalyzer.persistence.jdbc")
public class ContractTestConfig extends org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration {

    @Bean(name = "managementDataSource", destroyMethod = "close")
    public HikariDataSource managementDataSource(@Value("${contract.jdbc-url}") String jdbcUrl,
                                                 @Value("${contract.username}") String username,
                                                 @Value("${contract.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Bean(name = "managementDialect")
    public ManagementDatabaseDialect managementDialect(@Qualifier("managementDataSource") DataSource ds) {
        return ManagementDatabaseDialect.detect(ds);
    }

    @Bean(name = "managementJdbcTemplate")
    public JdbcTemplate managementJdbcTemplate(@Qualifier("managementDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "managementNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate managementNamedParameterJdbcTemplate(
            @Qualifier("managementDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = {"managementTransactionManager", "transactionManager"})
    public PlatformTransactionManager managementTransactionManager(@Qualifier("managementDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "jobClaimStrategy")
    public JobClaimStrategy jobClaimStrategy(@Qualifier("managementDialect") ManagementDatabaseDialect dialect,
                                             @Qualifier("managementTransactionManager") PlatformTransactionManager txm) {
        return switch (dialect) {
            case POSTGRESQL -> new PostgreSqlDialectAdapter();
            case H2 -> new H2DialectAdapter(txm);
        };
    }

    @Bean
    @Override
    public org.springframework.data.relational.core.dialect.Dialect jdbcDialect(
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations operations) {
        return new com.biz.sccba.sqlanalyzer.persistence.jdbc.NeutralIdentifierDialect(
                org.springframework.data.jdbc.core.dialect.DialectResolver.getDialect(operations.getJdbcOperations()));
    }

    @Bean(initMethod = "migrate")
    public Flyway managementFlyway(@Qualifier("managementDataSource") DataSource ds,
                                   @Qualifier("managementDialect") ManagementDatabaseDialect dialect) {
        return Flyway.configure()
                .dataSource(ds)
                .locations(dialect.flywayLocations())
                .baselineOnMigrate(true)
                .load();
    }
}
