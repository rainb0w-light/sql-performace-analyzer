package com.biz.sccba.sqlanalyzer.model.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务语义模型
 *
 * 存储数据库表和字段的业务含义，用于：
 * - SQL 参数填充时生成符合业务规范的测试数据
 * - 理解 MyBatis test 条件表达式的业务含义
 * - 判断 SQL 模板是否符合金融业务逻辑
 */
public class BusinessSemantics {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * 业务域
     */
    private BusinessDomain businessDomain;

    /**
     * 表业务描述
     */
    private String description;

    /**
     * 字段语义映射：字段名 -> 字段语义
     */
    private Map<String, FieldSemantics> fieldSemantics = new HashMap<>();

    /**
     * 表级约束说明
     */
    private Map<String, String> constraints = new HashMap<>();

    /**
     * 敏感字段列表
     */
    private List<String> sensitiveFields = new ArrayList<>();

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdatedAt;

    /**
     * 更新来源
     */
    private UpdateSource updatedBy;

    // Getters and Setters
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }

    public BusinessDomain getBusinessDomain() { return businessDomain; }
    public void setBusinessDomain(BusinessDomain businessDomain) { this.businessDomain = businessDomain; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, FieldSemantics> getFieldSemantics() { return fieldSemantics; }
    public void setFieldSemantics(Map<String, FieldSemantics> fieldSemantics) { this.fieldSemantics = fieldSemantics; }

    public Map<String, String> getConstraints() { return constraints; }
    public void setConstraints(Map<String, String> constraints) { this.constraints = constraints; }

    public List<String> getSensitiveFields() { return sensitiveFields; }
    public void setSensitiveFields(List<String> sensitiveFields) { this.sensitiveFields = sensitiveFields; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public UpdateSource getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UpdateSource updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 添加字段语义
     */
    public void addFieldSemantics(String fieldName, FieldSemantics semantics) {
        if (fieldSemantics == null) {
            fieldSemantics = new HashMap<>();
        }
        fieldSemantics.put(fieldName, semantics);
    }

    /**
     * 获取字段语义
     */
    public FieldSemantics getFieldSemantics(String fieldName) {
        return fieldSemantics != null ? fieldSemantics.get(fieldName) : null;
    }

    /**
     * 获取所有字段语义映射
     */
    public Map<String, FieldSemantics> getFieldSemanticsMap() {
        return fieldSemantics;
    }

    /**
     * 业务域枚举
     */
    public enum BusinessDomain {
        CUSTOMER("客户"),
        ACCOUNT("账户"),
        TRANSACTION("交易"),
        PRODUCT("产品"),
        HOLDING("持仓"),
        SYSTEM("系统"),
        OTHER("其他");

        private final String displayName;

        BusinessDomain(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * 从字符串解析业务域
         */
        public static BusinessDomain fromString(String value) {
            if (value == null) return OTHER;
            try {
                return BusinessDomain.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OTHER;
            }
        }
    }

    /**
     * 更新来源枚举
     */
    public enum UpdateSource {
        MANUAL("人工维护"),
        LLM_INFERRED("LLM 推测"),
        DICTIONARY("字典表导入"),
        SCHEMA_EXTRACTED("元数据提取");

        private final String displayName;

        UpdateSource(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
