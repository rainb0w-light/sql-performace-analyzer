package com.example.sqlanalyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prompt_templates")
public class PromptTemplateDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板类型标识，用于代码中查找，如 "MYSQL", "GOLDENDB"
     */
    @Column(nullable = false, unique = true)
    private String templateType;

    /**
     * 模板名称，用于显示
     */
    @Column(nullable = false)
    private String templateName;

    /**
     * 模板内容
     */
    @Lob
    @Column(nullable = false, length = 10000)
    private String templateContent;

    /**
     * 描述
     */
    private String description;

    @CreationTimestamp
    private LocalDateTime gmtCreated;

    @UpdateTimestamp
    private LocalDateTime gmtModified;
}

