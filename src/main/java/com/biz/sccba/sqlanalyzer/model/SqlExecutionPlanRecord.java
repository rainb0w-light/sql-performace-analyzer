package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * SQL 执行计划记录
 * 存储每个填充场景的执行计划
 */
@Entity
@Table(name = "sql_execution_plan_record",
    indexes = {
        @Index(name = "idx_mapper_id", columnList = "mapperId"),
        @Index(name = "idx_filling_record_id", columnList = "fillingRecordId"),
        @Index(name = "idx_namespace", columnList = "mapperId")
    }
)
@Data
public class SqlExecutionPlanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper ID（格式：namespace.statementId）
     */
    @Column(nullable = false, length = 700)
    private String mapperId;

    /**
     * 关联的填充记录 ID（外键）
     */
    @Column(nullable = false)
    private Long fillingRecordId;

    /**
     * 场景名称（来自 FilledSqlScenario）
     */
    @Column(nullable = false, length = 200)
    private String scenarioName;

    /**
     * 填充前带参数的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    /**
     * 填充后的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String filledSql;

    /**
     * 执行计划（JSON 格式）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String executionPlanJson;

    /**
     * 访问类型（从执行计划提取，如：ALL, index, range, ref, eq_ref, const）
     */
    @Column(length = 50)
    private String accessType;

    /**
     * 使用的索引名称（从执行计划提取，null 表示全表扫描）
     */
    @Column(length = 200)
    private String indexName;

    /**
     * 扫描行数（从执行计划提取）
     */
    private Long rowsExamined;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

