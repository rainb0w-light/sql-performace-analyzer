package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Job;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Snapshot;

import java.util.List;
import java.util.Optional;

/**
 * Datasource profiles, profiling jobs and immutable snapshots (development-guide §7.2).
 * Snapshot/stat reads verify ownership through the owning job/profile so a client can never
 * read another client's statistics by guessing a snapshot id (Goal §5.3).
 */
public interface ProfilingRepository {

    DatasourceProfile createProfile(DatasourceProfile profile);

    Optional<DatasourceProfile> findProfile(String id, String clientId);

    List<DatasourceProfile> listProfiles(String clientId);

    void enqueueJob(String id, String clientId, String datasourceProfileId, String configJson);

    /** Worker claim: dialect strategy underneath (SKIP LOCKED on PostgreSQL, row-lock on H2). */
    Optional<Job> claimJob(String workerId);

    void extendJobLease(String id, int minutes);

    void completeJob(String id);

    void failJob(String id, String error);

    void cancelJob(String clientId, String id);

    Optional<Job> findJob(String id, String clientId);

    List<Job> listJobs(String clientId);

    Snapshot createSnapshot(String id, String jobId, String datasourceProfileId, String configJson);

    void finishSnapshot(String id, String status);

    void insertColumnStat(ColumnStat stat);

    List<Snapshot> listSnapshots(String clientId, String datasourceProfileId);

    List<ColumnStat> snapshotStats(String clientId, String snapshotId);

    /** Stats of the client's latest COMPLETED snapshot (across all profiles); empty if none. */
    List<ColumnStat> latestStatsForClient(String clientId);

    /** Stats from the latest COMPLETED snapshot of one tenant-owned datasource profile. */
    default List<ColumnStat> latestStatsForDatasource(String clientId, String datasourceProfileId) {
        return List.of();
    }
}
