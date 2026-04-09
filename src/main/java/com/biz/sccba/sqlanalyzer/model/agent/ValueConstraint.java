package com.biz.sccba.sqlanalyzer.model.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 值约束模型
 *
 * 定义字段值的约束条件
 */
public class ValueConstraint {

    /**
     * 约束类型
     */
    private ConstraintType type;

    /**
     * 最小值
     */
    private Object minValue;

    /**
     * 最大值
     */
    private Object maxValue;

    /**
     * 枚举值列表
     */
    private List<Object> enumValues = new ArrayList<>();

    /**
     * 正则表达式
     */
    private String regexPattern;

    /**
     * 长度约束（针对字符串）
     */
    private Integer minLength;

    /**
     * 最大长度（针对字符串）
     */
    private Integer maxLength;

    /**
     * 约束描述
     */
    private String description;

    // Getters and Setters
    public ConstraintType getType() { return type; }
    public void setType(ConstraintType type) { this.type = type; }

    public Object getMinValue() { return minValue; }
    public void setMinValue(Object minValue) { this.minValue = minValue; }

    public Object getMaxValue() { return maxValue; }
    public void setMaxValue(Object maxValue) { this.maxValue = maxValue; }

    public List<Object> getEnumValues() { return enumValues; }
    public void setEnumValues(List<Object> enumValues) { this.enumValues = enumValues; }

    public String getRegexPattern() { return regexPattern; }
    public void setRegexPattern(String regexPattern) { this.regexPattern = regexPattern; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * 添加枚举值
     */
    public void addEnumValue(Object value) {
        if (enumValues == null) {
            enumValues = new ArrayList<>();
        }
        if (!enumValues.contains(value)) {
            enumValues.add(value);
        }
    }

    /**
     * 检查值是否满足约束
     */
    public boolean isValid(Object value) {
        if (value == null) {
            return true; // null 值由 nullable 字段控制
        }

        return switch (type) {
            case RANGE -> checkRange(value);
            case ENUM -> checkEnum(value);
            case REGEX -> checkRegex(value);
            case MIN -> checkMin(value);
            case MAX -> checkMax(value);
            case LENGTH -> checkLength(value);
            case FORMAT -> checkFormat(value);
            default -> true;
        };
    }

    /**
     * 检查范围约束
     */
    private boolean checkRange(Object value) {
        if (value instanceof Number && minValue instanceof Number && maxValue instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            double min = ((Number) minValue).doubleValue();
            double max = ((Number) maxValue).doubleValue();
            return numValue >= min && numValue <= max;
        }
        if (value instanceof Comparable && minValue instanceof Comparable && maxValue instanceof Comparable) {
            //noinspection unchecked
            return ((Comparable) value).compareTo(minValue) >= 0 &&
                   ((Comparable) value).compareTo(maxValue) <= 0;
        }
        return true;
    }

    /**
     * 检查枚举约束
     */
    private boolean checkEnum(Object value) {
        return enumValues == null || enumValues.isEmpty() || enumValues.contains(value);
    }

    /**
     * 检查正则约束
     */
    private boolean checkRegex(Object value) {
        if (regexPattern == null || value == null) {
            return true;
        }
        return value.toString().matches(regexPattern);
    }

    /**
     * 检查最小值约束
     */
    private boolean checkMin(Object value) {
        if (minValue == null || value == null) {
            return true;
        }
        if (value instanceof Number && minValue instanceof Number) {
            return ((Number) value).doubleValue() >= ((Number) minValue).doubleValue();
        }
        return true;
    }

    /**
     * 检查最大值约束
     */
    private boolean checkMax(Object value) {
        if (maxValue == null || value == null) {
            return true;
        }
        if (value instanceof Number && maxValue instanceof Number) {
            return ((Number) value).doubleValue() <= ((Number) maxValue).doubleValue();
        }
        return true;
    }

    /**
     * 检查长度约束
     */
    private boolean checkLength(Object value) {
        if (value == null) {
            return true;
        }
        int length = value.toString().length();
        if (minLength != null && length < minLength) {
            return false;
        }
        if (maxLength != null && length > maxLength) {
            return false;
        }
        return true;
    }

    /**
     * 检查格式约束（预留）
     */
    private boolean checkFormat(Object value) {
        // 格式约束可以通过正则或自定义逻辑实现
        return true;
    }
}

/**
 * 约束类型枚举
 */
enum ConstraintType {
    /**
     * 范围约束（如金额 0-1000000）
     */
    RANGE,

    /**
     * 枚举约束（如状态 0,1,2,9）
     */
    ENUM,

    /**
     * 正则约束（如身份证号）
     */
    REGEX,

    /**
     * 最小值约束
     */
    MIN,

    /**
     * 最大值约束
     */
    MAX,

    /**
     * 长度约束
     */
    LENGTH,

    /**
     * 格式约束
     */
    FORMAT
}
