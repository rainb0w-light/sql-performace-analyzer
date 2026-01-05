package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * SQL 数据填充结果记录
 * 存储 LLM 根据数据分布生成的多个填充场景
 */
@Entity
@Table(name = "sql_filling_record",
    indexes = {
        @Index(name = "idx_mapper_id", columnList = "mapperId"),
        @Index(name = "idx_namespace", columnList = "mapperId")
    }
)
@Data
public class SqlFillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper ID（格式：namespace.statementId）
     */
    @Column(nullable = false, length = 700)
    private String mapperId;

    /**
     * 原始带参数的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    /**
     * LLM 返回的完整填充结果（JSON 格式，包含所有场景）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String fillingResultJson;

    /**
     * 使用的直方图数据（JSON 格式）
     */
    @Column(columnDefinition = "CLOB")
    private String histogramDataJson;

    /**
     * 数据源名称
     */
    @Column(nullable = false, length = 100)
    private String datasourceName;

    /**
     * 使用的 LLM 名称
     */
    @Column(nullable = false, length = 100)
    private String llmName;

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

