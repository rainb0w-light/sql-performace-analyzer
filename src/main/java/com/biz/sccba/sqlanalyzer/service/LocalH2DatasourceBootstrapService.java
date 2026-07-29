package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Provides a zero-configuration static-analysis context for Docker-free local H2 startup.
 *
 * <p>The profile is created only when the management database is H2 and the client has no
 * datasource profiles at all. It never overrides or competes with an explicitly configured
 * datasource, and it is disabled automatically for PostgreSQL management deployments.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class LocalH2DatasourceBootstrapService {

    private final ProfilingRepository profiles;
    private final boolean h2ManagementDatabase;
    private final boolean enabled;
    private final String targetJdbcUrl;
    private final String targetUsername;

    public LocalH2DatasourceBootstrapService(
            ProfilingRepository profiles,
            @Value("${sql-analyzer.persistence.jdbc-url}") String managementJdbcUrl,
            @Value("${sql-analyzer.local-h2.datasource-bootstrap.enabled:true}") boolean enabled,
            @Value("${sql-analyzer.local-h2.datasource-bootstrap.jdbc-url}") String targetJdbcUrl,
            @Value("${sql-analyzer.local-h2.datasource-bootstrap.username:sa}") String targetUsername) {
        this.profiles = profiles;
        this.h2ManagementDatabase = managementJdbcUrl != null
                && managementJdbcUrl.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:h2:");
        this.enabled = enabled;
        this.targetJdbcUrl = targetJdbcUrl;
        this.targetUsername = targetUsername;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public synchronized List<DatasourceProfile> listOrBootstrap(String clientId) {
        List<DatasourceProfile> existing = profiles.listProfiles(clientId);
        if (!existing.isEmpty() || !enabled || !h2ManagementDatabase) {
            return existing;
        }
        profiles.createProfile(new DatasourceProfile(
                deterministicId(clientId),
                clientId,
                "Local H2 Static Analysis",
                "H2",
                targetJdbcUrl,
                targetUsername,
                null,
                true,
                null));
        return profiles.listProfiles(clientId);
    }

    static String deterministicId(String clientId) {
        UUID value = UUID.nameUUIDFromBytes(
                ("sql-analyzer:local-h2:" + clientId).getBytes(StandardCharsets.UTF_8));
        return "dsp_local_h2_" + value.toString().replace("-", "");
    }
}
