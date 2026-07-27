package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfileColumnStatEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProfileColumnStatJdbcRepository extends CrudRepository<ProfileColumnStatEntity, String> {

    /** Stats of a snapshot, ownership derived through snapshot -> job -> client. */
    @Query("SELECT cs.* FROM sql_analyzer.profile_column_stat cs "
            + "JOIN sql_analyzer.profile_snapshot ps ON ps.id = cs.snapshot_id "
            + "JOIN sql_analyzer.profiling_job j ON j.id = ps.job_id "
            + "WHERE cs.snapshot_id = :snapshotId AND j.client_id = :clientId "
            + "ORDER BY cs.table_name, cs.column_name")
    List<ProfileColumnStatEntity> findStatsForClient(@Param("clientId") String clientId,
                                                     @Param("snapshotId") String snapshotId);

    /** Stats of the client's latest COMPLETED snapshot across all datasource profiles. */
    @Query("SELECT cs.* FROM sql_analyzer.profile_column_stat cs "
            + "JOIN sql_analyzer.profile_snapshot ps ON ps.id = cs.snapshot_id "
            + "JOIN sql_analyzer.profiling_job j ON j.id = ps.job_id "
            + "WHERE j.client_id = :clientId AND ps.status = 'COMPLETED' "
            + "AND ps.started_at = (SELECT MAX(ps2.started_at) FROM sql_analyzer.profile_snapshot ps2 "
            + "JOIN sql_analyzer.profiling_job j2 ON j2.id = ps2.job_id "
            + "WHERE j2.client_id = :clientId AND ps2.status = 'COMPLETED') "
            + "ORDER BY cs.table_name, cs.column_name")
    List<ProfileColumnStatEntity> findLatestStatsForClient(@Param("clientId") String clientId);

    @Query("SELECT cs.* FROM sql_analyzer.profile_column_stat cs "
            + "JOIN sql_analyzer.profile_snapshot ps ON ps.id = cs.snapshot_id "
            + "JOIN sql_analyzer.profiling_job j ON j.id = ps.job_id "
            + "WHERE j.client_id = :clientId AND ps.datasource_profile_id = :datasourceProfileId "
            + "AND ps.status = 'COMPLETED' "
            + "AND ps.started_at = (SELECT MAX(ps2.started_at) FROM sql_analyzer.profile_snapshot ps2 "
            + "JOIN sql_analyzer.profiling_job j2 ON j2.id = ps2.job_id "
            + "WHERE j2.client_id = :clientId AND ps2.datasource_profile_id = :datasourceProfileId "
            + "AND ps2.status = 'COMPLETED') "
            + "ORDER BY cs.table_name, cs.column_name")
    List<ProfileColumnStatEntity> findLatestStatsForDatasource(
            @Param("clientId") String clientId,
            @Param("datasourceProfileId") String datasourceProfileId);
}
