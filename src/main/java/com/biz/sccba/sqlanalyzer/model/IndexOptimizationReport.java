package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 索引优化报告
 * 存储 LLM 生成的索引优化综合分析报告
 */
@Entity
@Table(name = "index_optimization_report",
    indexes = {
        @Index(name = "idx_mapper_id", columnList = "mapperId"),
        @Index(name = "idx_namespace", columnList = "namespace")
    }
)
@Data
public class IndexOptimizationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper ID（可选，null 表示整个 namespace 的报告）
     * 格式：namespace.statementId
     */
    @Column(length = 700)
    private String mapperId;

    /**
     * Mapper 命名空间
     */
    @Column(nullable = false, length = 500)
    private String namespace;

    /**
     * LLM 生成的报告内容（Markdown 格式）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String reportContent;


    /**
     * 使用的表结构信息（JSON 格式）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String tableStructuresJson;

    /**
     * 使用的执行计划解析结果（JSON 格式）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String analysisResultsJson;

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

