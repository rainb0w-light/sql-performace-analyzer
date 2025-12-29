package com.biz.sccba.sqlanalyzer.model;

/**
 * 统计类型枚举
 * 定义列统计值的类型
 */
public enum StatisticType {
    /**
     * 最小值
     */
    MIN,
    
    /**
     * 最大值
     */
    MAX,
    
    /**
     * 中位值（50%分位数）
     */
    MEDIAN,
    
    /**
     * 四分之一中位值（25%分位数）
     */
    PERCENTILE_25,
    
    /**
     * 四分之三中位值（75%分位数）
     */
    PERCENTILE_75
}

