package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfileSnapshotEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileSnapshotJdbcRepository extends CrudRepository<ProfileSnapshotEntity, String> {

    @Query("SELECT ps.* FROM sql_analyzer.profile_snapshot ps "
            + "JOIN sql_analyzer.profiling_job j ON j.id = ps.job_id "
            + "WHERE ps.datasource_profile_id = :datasourceProfileId AND j.client_id = :clientId "
            + "ORDER BY ps.started_at DESC")
    List<ProfileSnapshotEntity> findForClientAndProfile(@Param("clientId") String clientId,
                                                        @Param("datasourceProfileId") String datasourceProfileId);

    @Query("SELECT ps.* FROM sql_analyzer.profile_snapshot ps "
            + "JOIN sql_analyzer.profiling_job j ON j.id = ps.job_id "
            + "WHERE ps.id = :snapshotId AND j.client_id = :clientId")
    Optional<ProfileSnapshotEntity> findByIdForClient(@Param("clientId") String clientId,
                                                      @Param("snapshotId") String snapshotId);

    @Modifying
    @Query("UPDATE sql_analyzer.profile_snapshot SET status = :status, finished_at = CURRENT_TIMESTAMP WHERE id = :id")
    void finishSnapshot(@Param("id") String id, @Param("status") String status);
}
