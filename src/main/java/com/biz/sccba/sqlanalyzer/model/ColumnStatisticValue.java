package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 列统计值实体
 * 存储针对某个表某个列的统计值（最小值、最大值、中位值等）
 * 支持从ColumnStatistics自动计算或用户手工添加
 */
@Entity
@Table(name = "column_statistic_value",
    indexes = {
        @Index(name = "idx_stat_table_column", columnList = "datasourceName,tableName,columnName"),
        @Index(name = "idx_stat_type", columnList = "statisticType")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stat_datasource_table_column_type", 
            columnNames = {"datasourceName", "tableName", "columnName", "statisticType"})
    }
)
@Data
public class ColumnStatisticValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据源名称
     */
    @Column(nullable = false, length = 100)
    private String datasourceName;

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
     * 统计类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatisticType statisticType;

    /**
     * 统计值（存储为字符串，支持各种数据类型）
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String statisticValue;

    /**
     * 是否手工添加（true表示用户手工添加，false表示从ColumnStatistics自动计算）
     */
    @Column(nullable = false)
    private Boolean isManual = false;

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

