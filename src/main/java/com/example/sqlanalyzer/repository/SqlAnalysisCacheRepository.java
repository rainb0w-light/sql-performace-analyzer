package com.example.sqlanalyzer.repository;

import com.example.sqlanalyzer.model.SqlAnalysisCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * SQL分析结果缓存Repository
 */
@Repository
public interface SqlAnalysisCacheRepository extends JpaRepository<SqlAnalysisCache, Long> {

    /**
     * 根据SQL哈希值、数据源名称和大模型名称查找缓存
     */
    Optional<SqlAnalysisCache> findBySqlHashAndDataSourceNameAndLlmName(
        String sqlHash, 
        String dataSourceName, 
        String llmName
    );

    /**
     * 更新访问次数和最后访问时间
     */
    @Modifying
    @Query("UPDATE SqlAnalysisCache c SET c.accessCount = c.accessCount + 1, c.lastAccessedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void incrementAccessCount(@Param("id") Long id);
}

