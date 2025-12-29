package com.biz.sccba.sqlanalyzer.model.dto;

import java.util.List;

/**
 * 列统计信息DTO（临时数据结构）
 * 用于从MySQL的information_schema.COLUMN_STATISTICS读取并临时存储统计信息
 * 不持久化到数据库
 */
public class ColumnStatisticsDTO {
    
    private String datasourceName;
    private String databaseName;
    private String tableName;
    private String columnName;
    private String histogramType;
    private Integer bucketCount;
    private String minValue;
    private String maxValue;
    private String histogramData;
    private List<Object> sampleValues;
    
    public ColumnStatisticsDTO() {
    }
    
    public ColumnStatisticsDTO(String datasourceName, String databaseName, String tableName, String columnName) {
        this.datasourceName = datasourceName;
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.columnName = columnName;
    }
    
    public String getDatasourceName() {
        return datasourceName;
    }
    
    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getColumnName() {
        return columnName;
    }
    
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }
    
    public String getHistogramType() {
        return histogramType;
    }
    
    public void setHistogramType(String histogramType) {
        this.histogramType = histogramType;
    }
    
    public Integer getBucketCount() {
        return bucketCount;
    }
    
    public void setBucketCount(Integer bucketCount) {
        this.bucketCount = bucketCount;
    }
    
    public String getMinValue() {
        return minValue;
    }
    
    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }
    
    public String getMaxValue() {
        return maxValue;
    }
    
    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }
    
    public String getHistogramData() {
        return histogramData;
    }
    
    public void setHistogramData(String histogramData) {
        this.histogramData = histogramData;
    }
    
    public List<Object> getSampleValues() {
        return sampleValues;
    }
    
    public void setSampleValues(List<Object> sampleValues) {
        this.sampleValues = sampleValues;
    }
}

