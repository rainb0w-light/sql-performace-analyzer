package com.biz.sccba.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Mapper参数实体
 * 存储用于解析动态SQL的参数（JSON格式）
 */
@Entity
@Table(name = "mapper_parameter",
    indexes = {
        @Index(name = "idx_mapper_id", columnList = "mapperId", unique = true)
    }
)
@Data
public class MapperParameter {

    /**
     * Mapper ID（主键，格式：namespace.statementId）
     */
    @Id
    @Column(nullable = false, length = 700)
    private String mapperId;

    /**
     * 参数JSON内容（可解析为Map<String,Object>）
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String parameterJson;

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
