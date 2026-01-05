package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.SqlFillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SQL 数据填充结果记录 Repository
 */
@Repository
public interface SqlFillingRecordRepository extends JpaRepository<SqlFillingRecord, Long> {
    
    /**
     * 根据 mapperId 查找
     */
    Optional<SqlFillingRecord> findByMapperId(String mapperId);
    
    /**
     * 根据 namespace 查找（通过 mapperId 前缀匹配）
     */
    @Query("SELECT s FROM SqlFillingRecord s WHERE s.mapperId LIKE :namespace%")
    List<SqlFillingRecord> findByNamespace(@Param("namespace") String namespace);
    
    /**
     * 根据 mapperId、datasourceName 和 llmName 查找
     */
    Optional<SqlFillingRecord> findByMapperIdAndDatasourceNameAndLlmName(
            String mapperId, String datasourceName, String llmName);
}

