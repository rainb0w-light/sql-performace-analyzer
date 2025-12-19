package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SQL分析结果缓存实体
 */
@Entity
@Table(name = "sql_analysis_cache", 
    indexes = {
        @Index(name = "idx_sql_hash_ds_llm", columnList = "sqlHash,dataSourceName,llmName")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sql_hash_ds_llm", columnNames = {"sqlHash", "dataSourceName", "llmName"})
    }
)
public class SqlAnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SQL语句的哈希值（用于快速查找）
     */
    @Column(nullable = false, length = 64)
    private String sqlHash;

    /**
     * 数据源名称
     */
    @Column(nullable = false, length = 100)
    private String dataSourceName;

    /**
     * 大模型名称
     */
    @Column(nullable = false, length = 100)
    private String llmName;

    /**
     * 原始SQL语句
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sql;

    /**
     * 完整的分析结果（JSON格式）
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String analysisResult;

    /**
     * 生成的Markdown报告
     */
    @Column(nullable = false, columnDefinition = "CLOB")
    private String report;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 最后访问时间
     */
    @Column(nullable = false)
    private LocalDateTime lastAccessedAt;

    /**
     * 访问次数
     */
    @Column(nullable = false)
    private Long accessCount = 0L;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        lastAccessedAt = now;
        if (accessCount == null) {
            accessCount = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSqlHash() {
        return sqlHash;
    }

    public void setSqlHash(String sqlHash) {
        this.sqlHash = sqlHash;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(String analysisResult) {
        this.analysisResult = analysisResult;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Long getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Long accessCount) {
        this.accessCount = accessCount;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getLlmName() {
        return llmName;
    }

    public void setLlmName(String llmName) {
        this.llmName = llmName;
    }
}

