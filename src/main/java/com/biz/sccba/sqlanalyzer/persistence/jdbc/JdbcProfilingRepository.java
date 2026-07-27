package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Job;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Snapshot;
import com.biz.sccba.sqlanalyzer.persistence.dialect.JobClaimStrategy;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DatasourceProfileEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfilingJobEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfileColumnStatEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfileSnapshotEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.DatasourceProfileJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ProfilingJobJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ProfileColumnStatJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ProfileSnapshotJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcProfilingRepository implements ProfilingRepository {

    private static final String JOB_TABLE = "sql_analyzer.profiling_job";
    private static final int DEFAULT_LEASE_MINUTES = 10;

    private final DatasourceProfileJdbcRepository profiles;
    private final ProfilingJobJdbcRepository jobs;
    private final ProfileSnapshotJdbcRepository snapshots;
    private final ProfileColumnStatJdbcRepository stats;
    private final JobClaimStrategy claimStrategy;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcProfilingRepository(DatasourceProfileJdbcRepository profiles, ProfilingJobJdbcRepository jobs,
                                   ProfileSnapshotJdbcRepository snapshots, ProfileColumnStatJdbcRepository stats,
                                   JobClaimStrategy claimStrategy,
                                   @Qualifier("managementNamedParameterJdbcTemplate") NamedParameterJdbcTemplate namedJdbc) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.stats = stats;
        this.claimStrategy = claimStrategy;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public DatasourceProfile createProfile(DatasourceProfile p) {
        DatasourceProfileEntity entity = new DatasourceProfileEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(p.id());
        entity.setClientId(p.clientId());
        entity.setName(p.name());
        entity.setDialect(p.dialect());
        entity.setJdbcUrl(p.jdbcUrl());
        entity.setUsername(p.username());
        entity.setCredentialEnv(p.credentialEnv());
        entity.setReadOnly(p.readOnly());
        entity.markNew();
        return toProfile(profiles.save(entity));
    }

    @Override
    public Optional<DatasourceProfile> findProfile(String id, String clientId) {
        return profiles.findByIdAndClientId(id, clientId).map(JdbcProfilingRepository::toProfile);
    }

    @Override
    public List<DatasourceProfile> listProfiles(String clientId) {
        return profiles.findAllByClientId(clientId).stream().map(JdbcProfilingRepository::toProfile).toList();
    }

    @Override
    public void enqueueJob(String id, String clientId, String datasourceProfileId, String configJson) {
        ProfilingJobEntity entity = new ProfilingJobEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setDatasourceProfileId(datasourceProfileId);
        entity.setConfigJson(configJson);
        entity.setStatus("QUEUED");
        entity.setRetryCount(0);
        entity.markNew();
        jobs.save(entity);
    }

    @Override
    public Optional<Job> claimJob(String workerId) {
        Optional<Map<String, Object>> claimed = claimStrategy.claim(namedJdbc, JOB_TABLE, workerId,
                DEFAULT_LEASE_MINUTES, List.of("id", "client_id", "datasource_profile_id", "config_json",
                        "status", "leased_by", "lease_until", "retry_count", "last_error", "created_at"));
        return claimed.map(row -> {
            Map<String, Object> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            ci.putAll(row);
            return new Job(str(ci.get("id")), str(ci.get("client_id")), str(ci.get("datasource_profile_id")),
                    str(ci.get("config_json")), str(ci.get("status")), str(ci.get("leased_by")),
                    ci.get("lease_until") instanceof java.sql.Timestamp ts ? ts.toInstant() : null,
                    ci.get("retry_count") instanceof Number n ? n.intValue() : 0,
                    str(ci.get("last_error")),
                    ci.get("created_at") instanceof java.sql.Timestamp ts2 ? ts2.toInstant() : null);
        });
    }

    @Override
    public void extendJobLease(String id, int minutes) {
        namedJdbc.update("UPDATE " + JOB_TABLE + " SET lease_until = "
                        + claimStrategy.leaseUntilExpression("leaseMinutes") + " WHERE id = :id AND status = 'RUNNING'",
                new MapSqlParameterSource()
                        .addValue("leaseMinutes", Math.max(1, minutes))
                        .addValue("id", id));
    }

    @Override
    public void completeJob(String id) {
        jobs.markCompleted(id);
    }

    @Override
    public void failJob(String id, String error) {
        jobs.markFailed(id, error);
    }

    @Override
    public void cancelJob(String clientId, String id) {
        jobs.markCancelledForClient(id, clientId);
    }

    @Override
    public Optional<Job> findJob(String id, String clientId) {
        return jobs.findByIdAndClientId(id, clientId).map(JdbcProfilingRepository::toJob);
    }

    @Override
    public List<Job> listJobs(String clientId) {
        return jobs.findAllByClientId(clientId).stream().map(JdbcProfilingRepository::toJob).toList();
    }

    @Override
    public Snapshot createSnapshot(String id, String jobId, String datasourceProfileId, String configJson) {
        ProfileSnapshotEntity entity = new ProfileSnapshotEntity();
        entity.setStartedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setJobId(jobId);
        entity.setDatasourceProfileId(datasourceProfileId);
        entity.setStatus("RUNNING");
        entity.setConfigJson(configJson);
        entity.markNew();
        return toSnapshot(snapshots.save(entity));
    }

    @Override
    public void finishSnapshot(String id, String status) {
        snapshots.finishSnapshot(id, status);
    }

    @Override
    public void insertColumnStat(ColumnStat s) {
        ProfileColumnStatEntity entity = new ProfileColumnStatEntity();
        entity.setId(s.id());
        entity.setSnapshotId(s.snapshotId());
        entity.setSchemaName(s.schemaName());
        entity.setTableName(s.tableName());
        entity.setColumnName(s.columnName());
        entity.setNullRatio(s.nullRatio());
        entity.setApproxDistinct(s.approxDistinct());
        entity.setMinValue(s.minValue());
        entity.setMaxValue(s.maxValue());
        entity.setTopKJson(s.topKJson());
        entity.setBucketsJson(s.bucketsJson());
        entity.setQuantilesJson(s.quantilesJson());
        entity.setSensitivityPolicy(s.sensitivityPolicy());
        entity.setCollectedAt(s.collectedAt());
        entity.markNew();
        stats.save(entity);
    }

    @Override
    public List<Snapshot> listSnapshots(String clientId, String datasourceProfileId) {
        // A profile owned by another client is invisible: the listing is simply empty.
        if (profiles.findByIdAndClientId(datasourceProfileId, clientId).isEmpty()) {
            return List.of();
        }
        return snapshots.findForClientAndProfile(clientId, datasourceProfileId).stream()
                .map(JdbcProfilingRepository::toSnapshot).toList();
    }

    @Override
    public List<ColumnStat> latestStatsForClient(String clientId) {
        return toColumnStats(stats.findLatestStatsForClient(clientId));
    }

    @Override
    public List<ColumnStat> latestStatsForDatasource(String clientId, String datasourceProfileId) {
        if (profiles.findByIdAndClientId(datasourceProfileId, clientId).isEmpty()) return List.of();
        return toColumnStats(stats.findLatestStatsForDatasource(clientId, datasourceProfileId));
    }

    @Override
    public List<ColumnStat> snapshotStats(String clientId, String snapshotId) {
        return toColumnStats(stats.findStatsForClient(clientId, snapshotId));
    }

    private static List<ColumnStat> toColumnStats(List<ProfileColumnStatEntity> entities) {
        return entities.stream().map(e -> new ColumnStat(e.getId(),
                e.getSnapshotId(), e.getSchemaName(), e.getTableName(), e.getColumnName(), e.getNullRatio(),
                e.getApproxDistinct(), e.getMinValue(), e.getMaxValue(), e.getTopKJson(), e.getBucketsJson(),
                e.getQuantilesJson(), e.getSensitivityPolicy(), e.getCollectedAt())).toList();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static DatasourceProfile toProfile(DatasourceProfileEntity e) {
        return new DatasourceProfile(e.getId(), e.getClientId(), e.getName(), e.getDialect(), e.getJdbcUrl(),
                e.getUsername(), e.getCredentialEnv(), Boolean.TRUE.equals(e.getReadOnly()), e.getCreatedAt());
    }

    private static Job toJob(ProfilingJobEntity e) {
        return new Job(e.getId(), e.getClientId(), e.getDatasourceProfileId(), e.getConfigJson(), e.getStatus(),
                e.getLeasedBy(), e.getLeaseUntil(), e.getRetryCount() == null ? 0 : e.getRetryCount(),
                e.getLastError(), e.getCreatedAt());
    }

    private static Snapshot toSnapshot(ProfileSnapshotEntity e) {
        return new Snapshot(e.getId(), e.getJobId(), e.getDatasourceProfileId(), e.getStatus(),
                e.getConfigJson(), e.getStartedAt(), e.getFinishedAt());
    }
}
