package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.SqlExecutionPlanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SQL 执行计划记录 Repository
 */
@Repository
public interface SqlExecutionPlanRecordRepository extends JpaRepository<SqlExecutionPlanRecord, Long> {
    
    /**
     * 根据 mapperId 查找
     */
    List<SqlExecutionPlanRecord> findByMapperId(String mapperId);
    
    /**
     * 根据填充记录 ID 查找
     */
    List<SqlExecutionPlanRecord> findByFillingRecordId(Long fillingRecordId);
    
    /**
     * 根据 namespace 查找（通过 mapperId 前缀匹配）
     */
    @Query("SELECT s FROM SqlExecutionPlanRecord s WHERE s.mapperId LIKE :namespace%")
    List<SqlExecutionPlanRecord> findByNamespace(@Param("namespace") String namespace);
}

