package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 列统计信息模型
 *
 * 用于收集数据库表的列统计信息，支持 SQL 测试数据填充
 */
@Data
@NoArgsConstructor
public class ColumnStatistics {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 列名
     */
    private String columnName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 是否为空
     */
    private boolean nullable = true;

    /**
     * 总行数
     */
    private Long totalCount;

    /**
     * 空值数量
     */
    private Long nullCount;

    /**
     * 不同值数量（基数）
     */
    private Long distinctCount;

    /**
     * 最小值
     */
    private Object minValue;

    /**
     * 最大值
     */
    private Object maxValue;

    /**
     * 平均值（仅数值类型）
     */
    private Double avgValue;

    /**
     * 采样值列表
     */
    private List<Object> sampleValues = new ArrayList<>();

    /**
     * 数据分布信息（可选）
     */
    private String distributionInfo;

    /**
     * 业务含义（如果已知）
     */
    private String businessMeaning = "";

    /**
     * 全参构造函数
     */
    public ColumnStatistics(String tableName, String columnName, String dataType, boolean nullable,
                            Long totalCount, Long nullCount, Long distinctCount,
                            Object minValue, Object maxValue, Double avgValue,
                            List<Object> sampleValues, String distributionInfo, String businessMeaning) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.dataType = dataType;
        this.nullable = nullable;
        this.totalCount = totalCount;
        this.nullCount = nullCount;
        this.distinctCount = distinctCount;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.avgValue = avgValue;
        this.sampleValues = sampleValues != null ? sampleValues : new ArrayList<>();
        this.distributionInfo = distributionInfo;
        this.businessMeaning = businessMeaning != null ? businessMeaning : "";
    }

    /**
     * 添加采样值
     */
    public void addSampleValue(Object value) {
        if (sampleValues == null) {
            sampleValues = new ArrayList<>();
        }
        if (value != null && !sampleValues.contains(value)) {
            sampleValues.add(value);
        }
    }

    /**
     * 获取非空采样值
     */
    public List<Object> getNonNullSampleValues() {
        if (sampleValues == null || sampleValues.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> result = new ArrayList<>();
        for (Object value : sampleValues) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 判断是否是数值类型
     */
    public boolean isNumericType() {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("int") ||
               type.contains("decimal") ||
               type.contains("numeric") ||
               type.contains("money") ||
               type.contains("real") ||
               type.contains("float") ||
               type.contains("double");
    }

    /**
     * 判断是否是日期类型
     */
    public boolean isDateType() {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("date") ||
               type.contains("time") ||
               type.contains("timestamp");
    }

    /**
     * 判断是否是字符串类型
     */
    public boolean isStringType() {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("char") ||
               type.contains("text") ||
               type.contains("varchar") ||
               type.contains("blob");
    }

    /**
     * 获取非空率
     */
    public double getNotNullRate() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        if (nullCount == null) {
            return 1.0;
        }
        return (double) (totalCount - nullCount) / totalCount;
    }

    /**
     * 获取数据稠密度（1-稀疏，5-稠密）
     */
    public int getDistributionDensity() {
        if (totalCount == null || totalCount == 0 || distinctCount == null || distinctCount == 0) {
            return 3; // 默认中等密度
        }
        double density = (double) distinctCount / totalCount;
        if (density < 0.01) return 1;
        if (density < 0.1) return 2;
        if (density < 0.5) return 3;
        if (density < 0.9) return 4;
        return 5;
    }
}
