package com.biz.sccba.sqlanalyzer.llm.context;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Feature Compressor（关键）
 *
 * <p>把 N(通常 64/100) 个 bucket 压缩到 ~5-10 个“强信号”特征：Top-N、Long-tail、CDF 关键点、Skewness、Risk。</p>
 */
public final class HistogramFeatureCompressor {

    private HistogramFeatureCompressor() {}

    public static LlmSemanticContext.ColumnStats toSemantic(ColumnHistogram histogram) {
        if (histogram == null) {
            return null;
        }

        LlmSemanticContext.HistogramSummary summary = compressHistogram(histogram);
        LlmSemanticContext.Freshness freshness = new LlmSemanticContext.Freshness(
                histogram.lastUpdated(),
                histogram.samplingRate(),
                estimateRisk(histogram, summary)
        );

        return new LlmSemanticContext.ColumnStats(
                histogram.schemaName(),
                histogram.tableName(),
                histogram.columnName(),
                histogram.dataType(),
                null,
                null,
                summary,
                freshness
        );
    }

    private static LlmSemanticContext.HistogramSummary compressHistogram(ColumnHistogram histogram) {
        List<ColumnHistogram.Bucket> buckets = histogram.buckets() == null ? List.of() : histogram.buckets();

        // 1) top values（优先 singleton）
        int topN = 3;
        double minKeepFraction = 0.05d;

        List<LlmSemanticContext.TopValue> topValues = buckets.stream()
                .filter(b -> b != null && b.kind() == ColumnHistogram.BucketKind.SINGLETON)
                .map(b -> new LlmSemanticContext.TopValue(
                        coalesce(b.lower(), b.upper()),
                        safeFraction(b.rowFraction())
                ))
                .filter(tv -> tv.rowFraction() >= minKeepFraction)
                .sorted(Comparator.comparingDouble(LlmSemanticContext.TopValue::rowFraction).reversed())
                .limit(topN)
                .toList();

        double topSum = topValues.stream().mapToDouble(LlmSemanticContext.TopValue::rowFraction).sum();
        double longTail = clamp01(1.0d - topSum);
        String longTailRange = guessLongTailRange(histogram, topValues);

        // 2) CDF percentiles（p50/p90/p99）
        LlmSemanticContext.Cdf cdf = new LlmSemanticContext.Cdf(
                percentileUpper(buckets, 0.50d),
                percentileUpper(buckets, 0.90d),
                percentileUpper(buckets, 0.99d)
        );

        // 3) skewness
        double top1 = topValues.isEmpty() ? 0.0d : topValues.get(0).rowFraction();
        double top3 = topSum;
        boolean highlySkewed = top1 > 0.30d || top3 > 0.60d;

        LlmSemanticContext.Skewness skewness = new LlmSemanticContext.Skewness(top1, top3, highlySkewed);

        return new LlmSemanticContext.HistogramSummary(
                normalizeType(histogram.histogramType()),
                histogram.bucketCount(),
                topValues,
                new LlmSemanticContext.LongTail(longTailRange, longTail),
                cdf,
                skewness,
                new LlmSemanticContext.Range(histogram.minValue(), histogram.maxValue())
        );
    }

    private static LlmSemanticContext.EstimationRisk estimateRisk(
            ColumnHistogram histogram,
            LlmSemanticContext.HistogramSummary summary
    ) {
        double samplingRate = histogram.samplingRate() == null ? -1.0d : histogram.samplingRate();
        double top1 = summary != null && summary.skewness() != null ? summary.skewness().top1Fraction() : 0.0d;

        LlmSemanticContext.EstimationRisk risk = LlmSemanticContext.EstimationRisk.LOW;
        if (samplingRate >= 0.0d && samplingRate < 0.20d) {
            risk = LlmSemanticContext.EstimationRisk.MEDIUM;
        } else if (samplingRate < 0.0d) {
            // 未知采样率：默认给一个中等风险，避免模型过度自信
            risk = LlmSemanticContext.EstimationRisk.MEDIUM;
        }
        if (top1 > 0.30d) {
            risk = LlmSemanticContext.EstimationRisk.HIGH;
        }
        return risk;
    }

    private static String normalizeType(String histogramType) {
        if (histogramType == null) {
            return null;
        }
        return histogramType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String percentileUpper(List<ColumnHistogram.Bucket> buckets, double p) {
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        for (ColumnHistogram.Bucket b : buckets) {
            if (b == null) {
                continue;
            }
            Double cdf = b.cumulativeFraction();
            if (cdf != null && cdf >= p) {
                return coalesce(b.upper(), b.lower());
            }
        }
        // 如果最后一个也没有 cdf，退化返回 max
        ColumnHistogram.Bucket last = buckets.get(buckets.size() - 1);
        return last == null ? null : coalesce(last.upper(), last.lower());
    }

    private static String guessLongTailRange(ColumnHistogram histogram, List<LlmSemanticContext.TopValue> topValues) {
        // 只是为了 LLM 解释性：如果 topValues 都是离散热点值，则 long_tail 用 (not in {..})；否则用 [min,max]。
        if (topValues != null && !topValues.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (LlmSemanticContext.TopValue tv : topValues) {
                if (tv != null && tv.value() != null) {
                    values.add(tv.value());
                }
            }
            if (!values.isEmpty()) {
                return "NOT IN (" + String.join(", ", values) + ")";
            }
        }
        return "[" + histogram.minValue() + ", " + histogram.maxValue() + "]";
    }

    private static double safeFraction(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return 0.0d;
        }
        return clamp01(v);
    }

    private static double clamp01(double v) {
        if (v < 0.0d) return 0.0d;
        if (v > 1.0d) return 1.0d;
        return v;
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}


