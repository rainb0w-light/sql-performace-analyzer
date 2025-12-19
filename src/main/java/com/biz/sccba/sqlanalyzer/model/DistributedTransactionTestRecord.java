package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 分布式事务测试执行记录实体
 */
@Entity
@Table(name = "distributed_transaction_test_record", indexes = {
    @Index(name = "idx_test_id", columnList = "testId"),
    @Index(name = "idx_scenario_name", columnList = "scenarioName"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class DistributedTransactionTestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 测试ID（唯一标识一次测试执行）
     */
    @Column(nullable = false, unique = true, length = 64)
    private String testId;

    /**
     * 场景名称
     */
    @Column(nullable = false, length = 255)
    private String scenarioName;

    /**
     * 测试结果JSON（包含所有执行步骤的结果）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String resultJson;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

