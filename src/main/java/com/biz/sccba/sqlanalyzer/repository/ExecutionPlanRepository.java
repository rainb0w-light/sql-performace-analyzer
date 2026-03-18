package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SQL 执行计划记录 Repository
 */
@Repository
public interface ExecutionPlanRepository extends JpaRepository<ExecutionPlan, Long> {

    /**
     * 根据原始 SQL 查找执行计划
     */
    List<ExecutionPlan> findByOriginalSql(String originalSql);

    /**
     * 根据填充后的 SQL 查找执行计划
     */
    List<ExecutionPlan> findByFilledSql(String filledSql);
}
