package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * SQL 执行计划解析结果
 * 存储对执行计划的分类分析结果
 */
@Entity
@Table(name = "sql_plan_analysis_result",
    indexes = {
        @Index(name = "idx_mapper_id", columnList = "mapperId"),
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_namespace", columnList = "mapperId")
    }
)
@Data
public class SqlPlanAnalysisResult {

    /**
     * 分类：未命中索引
     */
    public static final String CATEGORY_NO_INDEX = "NO_INDEX";

    /**
     * 分类：执行计划偏移
     */
    public static final String CATEGORY_PLAN_SHIFT = "PLAN_SHIFT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper ID（格式：namespace.statementId）
     */
    @Column(nullable = false, length = 700)
    private String mapperId;

    /**
     * 带参数的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    /**
     * 分类（"NO_INDEX" 或 "PLAN_SHIFT"）
     */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * 计划偏移详情（JSON 格式，仅 category=PLAN_SHIFT 时使用）
     * 包含：不同的索引名称列表、全表扫描标记等
     */
    @Column(columnDefinition = "CLOB")
    private String planShiftDetailsJson;

    /**
     * 关联的执行计划记录 ID 列表（JSON 数组）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String executionPlanRecordIds;

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

