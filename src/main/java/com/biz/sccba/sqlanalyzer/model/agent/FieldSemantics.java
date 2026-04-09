package com.biz.sccba.sqlanalyzer.model.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段语义模型
 *
 * 存储单个字段的详细业务信息
 */
public class FieldSemantics {

    /**
     * 字段名
     */
    private String fieldName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 业务含义（简短描述）
     */
    private String businessMeaning;

    /**
     * 详细业务描述
     */
    private String businessDescription;

    /**
     * 值约束
     */
    private ValueConstraint valueConstraint;

    /**
     * 业务规则列表
     */
    private List<BusinessRule> businessRules = new ArrayList<>();

    /**
     * 从数据库采样的实际值（用于数据驱动填充）
     */
    private List<Object> sampleValues = new ArrayList<>();

    /**
     * 常见业务值（用于业务常见场景填充）
     */
    private List<Object> commonValues = new ArrayList<>();

    /**
     * 逻辑上关联的字段
     */
    private List<String> relatedFields = new ArrayList<>();

    /**
     * 关联的字典表
     */
    private String referenceTable;

    /**
     * 关联字段
     */
    private String referenceField;

    /**
     * 是否主键
     */
    private boolean isPrimaryKey = false;

    /**
     * 是否外键
     */
    private boolean isForeignKey = false;

    /**
     * 是否可空
     */
    private boolean nullable = true;

    /**
     * 是否业务主键（如身份证号、手机号等）
     */
    private boolean isBusinessKey = false;

    /**
     * 数据稠密度：1-稀疏，5-稠密
     * 用于判断字段值的分布情况
     */
    private int distributionDensity = 3;

    // Getters and Setters
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getBusinessMeaning() { return businessMeaning; }
    public void setBusinessMeaning(String businessMeaning) { this.businessMeaning = businessMeaning; }

    public String getBusinessDescription() { return businessDescription; }
    public void setBusinessDescription(String businessDescription) { this.businessDescription = businessDescription; }

    public ValueConstraint getValueConstraint() { return valueConstraint; }
    public void setValueConstraint(ValueConstraint valueConstraint) { this.valueConstraint = valueConstraint; }

    public List<BusinessRule> getBusinessRules() { return businessRules; }
    public void setBusinessRules(List<BusinessRule> businessRules) { this.businessRules = businessRules; }

    public List<Object> getSampleValues() { return sampleValues; }
    public void setSampleValues(List<Object> sampleValues) { this.sampleValues = sampleValues; }

    public List<Object> getCommonValues() { return commonValues; }
    public void setCommonValues(List<Object> commonValues) { this.commonValues = commonValues; }

    public List<String> getRelatedFields() { return relatedFields; }
    public void setRelatedFields(List<String> relatedFields) { this.relatedFields = relatedFields; }

    public String getReferenceTable() { return referenceTable; }
    public void setReferenceTable(String referenceTable) { this.referenceTable = referenceTable; }

    public String getReferenceField() { return referenceField; }
    public void setReferenceField(String referenceField) { this.referenceField = referenceField; }

    public boolean isPrimaryKey() { return isPrimaryKey; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }

    public boolean isForeignKey() { return isForeignKey; }
    public void setForeignKey(boolean foreignKey) { isForeignKey = foreignKey; }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }

    public boolean isBusinessKey() { return isBusinessKey; }
    public void setBusinessKey(boolean businessKey) { isBusinessKey = businessKey; }

    public int getDistributionDensity() { return distributionDensity; }
    public void setDistributionDensity(int distributionDensity) { this.distributionDensity = distributionDensity; }

    /**
     * 添加业务规则
     */
    public void addBusinessRule(BusinessRule rule) {
        if (businessRules == null) {
            businessRules = new ArrayList<>();
        }
        businessRules.add(rule);
    }

    /**
     * 添加示例值
     */
    public void addSampleValue(Object value) {
        if (sampleValues == null) {
            sampleValues = new ArrayList<>();
        }
        if (!sampleValues.contains(value)) {
            sampleValues.add(value);
        }
    }

    /**
     * 添加常见值
     */
    public void addCommonValue(Object value) {
        if (commonValues == null) {
            commonValues = new ArrayList<>();
        }
        if (!commonValues.contains(value)) {
            commonValues.add(value);
        }
    }

    /**
     * 添加关联字段
     */
    public void addRelatedField(String field) {
        if (relatedFields == null) {
            relatedFields = new ArrayList<>();
        }
        if (!relatedFields.contains(field)) {
            relatedFields.add(field);
        }
    }
}
