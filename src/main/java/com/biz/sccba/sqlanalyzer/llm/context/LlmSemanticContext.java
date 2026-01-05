package com.biz.sccba.sqlanalyzer.llm.context;

import java.util.List;

/**
 * Semantic Snapshot（中间语义层，稳定 Schema）
 *
 * <p>给 LLM 的“统计语言”，与底层直方图 JSON/实现细节解耦。</p>
 */
public final class LlmSemanticContext {

    private LlmSemanticContext() {}

    public record ColumnStats(
            String schema,
            String table,
            String column,
            String dataType,
            Long tableRows,
            Double nullFraction,
            HistogramSummary histogramSummary,
            Freshness freshness
    ) {}

    public record HistogramSummary(
            String type,
            Integer bucketCount,
            List<TopValue> topValues,
            LongTail longTail,
            Cdf cdf,
            Skewness skewness,
            Range range
    ) {}

    public record Range(String min, String max) {}

    public record TopValue(String value, double rowFraction) {}

    public record LongTail(String range, double rowFraction) {}

    public record Cdf(String p50, String p90, String p99) {}

    public record Skewness(
            double top1Fraction,
            double top3Fraction,
            boolean highlySkewed
    ) {}

    public record Freshness(
            String lastUpdated,
            Double samplingRate,
            EstimationRisk estimationRisk
    ) {}

    public enum EstimationRisk {
        LOW,
        MEDIUM,
        HIGH
    }
}


