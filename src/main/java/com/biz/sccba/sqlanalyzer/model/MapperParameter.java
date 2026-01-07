package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Mapper参数实体
 * 存储用于解析动态SQL的参数（参数名和参数值）
 */
@Entity
@Table(name = "mapper_parameter")
@Data
public class MapperParameter {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mapper ID（格式：namespace.statementId）
     */
    @Column
    private String mapperId;

    /**
     * 参数名称
     */
    @Column
    private String parameterName;

    /**
     * 参数值（存储为字符串，可包含复杂对象序列化后的JSON）
     */
    @Column(columnDefinition = "TEXT")
    private String parameterValue;

    /**
     * test 表达式（OGNL表达式，用于动态SQL条件判断）
     */
    @Column(length = 1000)
    private String testExpression;

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
