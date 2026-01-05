package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.SqlPlanAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SQL 执行计划解析结果 Repository
 */
@Repository
public interface SqlPlanAnalysisResultRepository extends JpaRepository<SqlPlanAnalysisResult, Long> {
    
    /**
     * 根据 mapperId 查找
     */
    List<SqlPlanAnalysisResult> findByMapperId(String mapperId);
    
    /**
     * 根据 namespace 查找（通过 mapperId 前缀匹配）
     */
    @Query("SELECT s FROM SqlPlanAnalysisResult s WHERE s.mapperId LIKE :namespace%")
    List<SqlPlanAnalysisResult> findByNamespace(@Param("namespace") String namespace);
    
    /**
     * 根据分类查找
     */
    List<SqlPlanAnalysisResult> findByCategory(String category);
}

