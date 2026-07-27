package com.biz.sccba.sqlanalyzer.persistence.dialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Management-database dialect (docs/cloud-code-next-goal.md §3.2/§3.5).
 *
 * <p>The management database is the product's own business database. It can be PostgreSQL
 * (production) or H2 (Docker-free local gate and development). The dialect is detected at startup
 * through JDBC metadata and selects: Flyway migration locations and the small set of genuinely
 * database-specific SQL (job-queue claim/lease). Portable SQL stays in {@code migration-common}
 * and the repositories; vendor SQL never leaks into services.
 */
public enum ManagementDatabaseDialect {

    /** PostgreSQL: historical migrations {@code db/migration} + portable {@code db/migration-common}. */
    POSTGRESQL("classpath:db/migration", "classpath:db/migration-common"),

    /** H2: clean-install baseline {@code db/migration-h2} + the same portable {@code db/migration-common}. */
    H2("classpath:db/migration-h2", "classpath:db/migration-common");

    private final String[] flywayLocations;

    ManagementDatabaseDialect(String... flywayLocations) {
        this.flywayLocations = flywayLocations;
    }

    public String[] flywayLocations() {
        return flywayLocations.clone();
    }

    /** Detects the dialect via JDBC {@code DatabaseMetaData.getDatabaseProductName()} (Goal §3.5). */
    public static ManagementDatabaseDialect detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return fromProductName(product);
        } catch (SQLException e) {
            throw new IllegalStateException("无法检测管理数据库类型", e);
        }
    }

    /** URL-based detection (fallback when a live connection is not desirable). */
    public static ManagementDatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL 不能为空");
        }
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:postgresql:")) return POSTGRESQL;
        if (url.startsWith("jdbc:h2:")) return H2;
        throw new IllegalArgumentException("不支持的管理数据库 JDBC URL：" + jdbcUrl);
    }

    static ManagementDatabaseDialect fromProductName(String productName) {
        String product = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (product.contains("postgres")) return POSTGRESQL;
        if (product.contains("h2")) return H2;
        throw new IllegalStateException("不支持的管理数据库：" + productName
                + "（本阶段仅支持 PostgreSQL 与 H2，见 docs/cloud-code-next-goal.md §3）");
    }
}
