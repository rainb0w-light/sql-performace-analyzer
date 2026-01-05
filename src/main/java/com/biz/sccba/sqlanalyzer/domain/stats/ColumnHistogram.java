package com.biz.sccba.sqlanalyzer.domain.stats;

import java.util.List;

/**
 * Domain Stats Object（强类型，给程序用）
 *
 * <p>目标：承载“从 MySQL COLUMN_STATISTICS 提取/归一化后的直方图信息”，避免在业务链路中传播原始 JSON。</p>
 */
public record ColumnHistogram(
        String datasourceName,
        String schemaName,
        String tableName,
        String columnName,
        String dataType,
        String histogramType,
        Integer bucketCountSpecified,
        Integer bucketCount,
        String lastUpdated,
        Double samplingRate,
        String minValue,
        String maxValue,
        List<Bucket> buckets,
        List<String> sampleValues
) {

    /**
     * 统一 bucket 结构：
     * - lower/upper: 边界值（已尽量做可读化，比如 base64 解码）
     * - cumulativeFraction: CDF（0..1）
     * - rowFraction: 当前 bucket 占比（0..1），通常由 CDF 差分得到
     */
    public record Bucket(
            String lower,
            String upper,
            Double cumulativeFraction,
            Double rowFraction,
            BucketKind kind
    ) {}

    public enum BucketKind {
        SINGLETON,
        SMALL_RANGE,
        RANGE,
        UNKNOWN
    }
}


