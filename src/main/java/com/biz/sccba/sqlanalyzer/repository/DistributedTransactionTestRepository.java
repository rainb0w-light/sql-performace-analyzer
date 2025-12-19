package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.DistributedTransactionTestRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分布式事务测试记录Repository
 */
@Repository
public interface DistributedTransactionTestRepository extends JpaRepository<DistributedTransactionTestRecord, Long> {

    /**
     * 根据测试ID查找记录
     */
    Optional<DistributedTransactionTestRecord> findByTestId(String testId);

    /**
     * 根据场景名称查找记录列表（按创建时间倒序）
     */
    List<DistributedTransactionTestRecord> findByScenarioNameOrderByCreatedAtDesc(String scenarioName);

    /**
     * 查找所有记录（按创建时间倒序）
     */
    List<DistributedTransactionTestRecord> findAllByOrderByCreatedAtDesc();
}

