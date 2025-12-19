package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 解析后的SQL查询实体
 * 存储从MyBatis Mapper XML解析出的SQL查询
 */
@Entity
@Table(name = "parsed_sql_query",
    indexes = {
        @Index(name = "idx_mapper_namespace", columnList = "mapperNamespace"),
        @Index(name = "idx_table_name", columnList = "tableName"),
        @Index(name = "idx_query_type", columnList = "queryType")
    }
)
@Data
public class ParsedSqlQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper命名空间
     */
    @Column(nullable = false, length = 500)
    private String mapperNamespace;

    /**
     * SQL语句ID（在Mapper中的方法名）
     */
    @Column(nullable = false, length = 200)
    private String statementId;

    /**
     * SQL类型（select, insert, update, delete）
     */
    @Column(nullable = false, length = 20)
    private String queryType;

    /**
     * 解析后的SQL语句
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sql;

    /**
     * 原始SQL片段（包含动态SQL标签）
     */
    @Column(columnDefinition = "TEXT")
    private String originalSqlFragment;

    /**
     * 涉及的表名（逗号分隔）
     */
    @Column(length = 500)
    private String tableName;

    /**
     * 动态SQL条件描述（JSON格式，描述生成此SQL的条件组合）
     */
    @Column(columnDefinition = "TEXT")
    private String dynamicConditions;

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

