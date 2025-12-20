package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 列统计信息实体
 * 存储从information_schema.column_statistics解析出的数据分布信息
 */
@Entity
@Table(name = "column_statistics",
    indexes = {
        @Index(name = "idx_table_column", columnList = "tableName,columnName"),
        @Index(name = "idx_datasource_table", columnList = "datasourceName,tableName")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_datasource_table_column", 
            columnNames = {"datasourceName", "tableName", "columnName"})
    }
)
@Data
public class ColumnStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据源名称
     */
    @Column(nullable = false, length = 100)
    private String datasourceName;

    /**
     * 数据库名
     */
    @Column(nullable = false, length = 200)
    private String databaseName;

    /**
     * 表名
     */
    @Column(nullable = false, length = 200)
    private String tableName;

    /**
     * 列名
     */
    @Column(nullable = false, length = 200)
    private String columnName;

    /**
     * 直方图类型（如：singleton, equi-height等）
     */
    @Column(length = 50)
    private String histogramType;

    /**
     * 直方图桶数量
     */
    @Column
    private Integer bucketCount;

    /**
     * 最小值
     */
    @Column(columnDefinition = "TEXT")
    private String minValue;

    /**
     * 最大值
     */
    @Column(columnDefinition = "TEXT")
    private String maxValue;

    /**
     * 空值数量
     */
    @Column
    private Long nullCount;

    /**
     * 不同值数量（基数）
     */
    @Column
    private Long distinctCount;

    /**
     * 总行数
     */
    @Column
    private Long totalRows;

    /**
     * 直方图数据（JSON格式，包含所有桶的信息）
     */
    @Column(columnDefinition = "CLOB")
    private String histogramData;

    /**
     * 原始JSON数据（从information_schema.column_statistics获取的完整JSON）
     */
    @Column(columnDefinition = "CLOB")
    private String rawJsonData;

    /**
     * 采样值列表（JSON数组格式，包含代表性的采样值）
     */
    @Column(columnDefinition = "CLOB")
    private String sampleValues;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
