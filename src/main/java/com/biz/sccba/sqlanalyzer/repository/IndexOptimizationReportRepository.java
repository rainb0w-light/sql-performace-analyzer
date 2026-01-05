package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.IndexOptimizationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 索引优化报告 Repository
 */
@Repository
public interface IndexOptimizationReportRepository extends JpaRepository<IndexOptimizationReport, Long> {
    
    /**
     * 根据 mapperId 查找
     */
    Optional<IndexOptimizationReport> findByMapperId(String mapperId);
    
    /**
     * 根据 namespace 查找
     */
    List<IndexOptimizationReport> findByNamespace(String namespace);
}

