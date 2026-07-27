package com.biz.sccba.sqlanalyzer.domain.profiling;

import java.time.Instant;

/** Read-only database profiling domain (development-guide §7.2). Snapshots are immutable. */
public final class Profiling {

    private Profiling() {}

    /** Target database connection descriptor. Passwords are never stored; credentialEnv names the env/property. */
    public record DatasourceProfile(String id, String clientId, String name, String dialect, String jdbcUrl,
                                    String username, String credentialEnv, boolean readOnly, Instant createdAt) {}

    public record Job(String id, String clientId, String datasourceProfileId, String configJson, String status,
                      String leasedBy, Instant leaseUntil, int retryCount, String lastError, Instant createdAt) {}

    public record Snapshot(String id, String jobId, String datasourceProfileId, String status, String configJson,
                           Instant startedAt, Instant finishedAt) {}

    public record ColumnStat(String id, String snapshotId, String schemaName, String tableName, String columnName,
                             Double nullRatio, Long approxDistinct, String minValue, String maxValue,
                             String topKJson, String bucketsJson, String quantilesJson,
                             String sensitivityPolicy, Instant collectedAt) {
        public ColumnStat(String id, String snapshotId, String schemaName, String tableName,
                          String columnName, Double nullRatio, Long approxDistinct,
                          String minValue, String maxValue, String topKJson, String bucketsJson,
                          String quantilesJson, Instant collectedAt) {
            this(id, snapshotId, schemaName, tableName, columnName, nullRatio, approxDistinct,
                    minValue, maxValue, topKJson, bucketsJson, quantilesJson,
                    "PLAINTEXT", collectedAt);
        }
    }
}
