package com.biz.sccba.sqlanalyzer.llm.context;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
import com.biz.sccba.sqlanalyzer.data.TableStructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt Renderer（渲染层）
 *
 * <p>把稳定的语义快照渲染成"给人/LLM 看得懂"的文本（Markdown / bullets）。</p>
 */
public final class PromptRenderer {

    private PromptRenderer() {}

    public static String renderHistogramContexts(List<LlmSemanticContext.ColumnStats> columns) {
        if (columns == null || columns.isEmpty()) {
            return "无直方图数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### Column histogram summary\n\n");

        for (LlmSemanticContext.ColumnStats col : columns) {
            if (col == null) {
                continue;
            }
            sb.append("- **").append(col.table()).append(".").append(col.column()).append("**");
            if (col.dataType() != null) {
                sb.append(" (").append(col.dataType()).append(")");
            }
            sb.append("\n");

            LlmSemanticContext.HistogramSummary hs = col.histogramSummary();
            if (hs == null) {
                sb.append("  - no histogram\n\n");
                continue;
            }

            if (hs.type() != null) {
                sb.append("  - type: ").append(hs.type()).append("\n");
            }
            if (hs.bucketCount() != null) {
                sb.append("  - bucket_count: ").append(hs.bucketCount()).append("\n");
            }
            if (hs.range() != null) {
                sb.append("  - range: [").append(hs.range().min()).append(", ").append(hs.range().max()).append("]\n");
            }

            if (hs.topValues() != null && !hs.topValues().isEmpty()) {
                sb.append("  - top_values:\n");
                for (LlmSemanticContext.TopValue tv : hs.topValues()) {
                    if (tv == null) continue;
                    sb.append("    - value: ").append(tv.value())
                            .append(", row_fraction: ").append(formatPct(tv.rowFraction()))
                            .append("\n");
                }
            }

            if (hs.longTail() != null) {
                sb.append("  - long_tail: ").append(hs.longTail().range())
                        .append(", row_fraction: ").append(formatPct(hs.longTail().rowFraction()))
                        .append("\n");
            }

            if (hs.cdf() != null) {
                sb.append("  - cdf: p50=").append(hs.cdf().p50())
                        .append(", p90=").append(hs.cdf().p90())
                        .append(", p99=").append(hs.cdf().p99())
                        .append("\n");
            }

            if (hs.skewness() != null) {
                sb.append("  - skewness: top1=").append(formatPct(hs.skewness().top1Fraction()))
                        .append(", top3=").append(formatPct(hs.skewness().top3Fraction()))
                        .append(", highly_skewed=").append(hs.skewness().highlySkewed())
                        .append("\n");
            }

            if (col.freshness() != null) {
                if (col.freshness().samplingRate() != null) {
                    sb.append("  - sampling_rate: ").append(col.freshness().samplingRate()).append("\n");
                }
                if (col.freshness().lastUpdated() != null) {
                    sb.append("  - last_updated: ").append(col.freshness().lastUpdated()).append("\n");
                }
                if (col.freshness().estimationRisk() != null) {
                    sb.append("  - estimation_risk: ").append(col.freshness().estimationRisk()).append("\n");
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private static String formatPct(double v) {
        double pct = v * 100.0d;
        return String.format(java.util.Locale.ROOT, "%.2f%%", pct);
    }

    /**
     * 渲染直方图数据为 JSON 格式（用于参数填充场景）
     * 
     * <p>输出包含详细 buckets 信息的 JSON 格式，符合新的 prompt 要求。</p>
     * 
     * @param histograms 直方图数据列表
     * @param tableStructures 表结构列表（可选，用于获取 tableRows）
     * @return JSON 格式的直方图数据字符串
     */
    public static String renderHistogramAsJson(List<ColumnHistogram> histograms, List<TableStructure> tableStructures) {
        if (histograms == null || histograms.isEmpty()) {
            return "[]";
        }

        // 构建表名到行数的映射
        Map<String, Long> tableRowsMap = new HashMap<>();
        if (tableStructures != null) {
            for (TableStructure structure : tableStructures) {
                if (structure != null && structure.getStatistics() != null && structure.getStatistics().getRows() != null) {
                    tableRowsMap.put(structure.getTableName(), structure.getStatistics().getRows());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < histograms.size(); i++) {
            ColumnHistogram histogram = histograms.get(i);
            if (histogram == null) {
                continue;
            }

            if (i > 0) {
                sb.append(",\n");
            }

            sb.append("  {\n");
            sb.append("    \"columnName\": ").append(escapeJson(histogram.columnName())).append(",\n");
            sb.append("    \"dataType\": ").append(escapeJson(histogram.dataType())).append(",\n");
            
            if (histogram.minValue() != null) {
                sb.append("    \"minValue\": ").append(escapeJson(histogram.minValue())).append(",\n");
            }
            if (histogram.maxValue() != null) {
                sb.append("    \"maxValue\": ").append(escapeJson(histogram.maxValue())).append(",\n");
            }
            
            // nullFraction - 目前 ColumnHistogram 中没有，设为 null
            sb.append("    \"nullFraction\": null,\n");

            // buckets
            List<ColumnHistogram.Bucket> buckets = histogram.buckets();
            Long tableRows = tableRowsMap.get(histogram.tableName());
            
            sb.append("    \"buckets\": [\n");
            if (buckets != null && !buckets.isEmpty()) {
                for (int j = 0; j < buckets.size(); j++) {
                    ColumnHistogram.Bucket bucket = buckets.get(j);
                    if (bucket == null) {
                        continue;
                    }

                    if (j > 0) {
                        sb.append(",\n");
                    }

                    sb.append("      {\n");
                    sb.append("        \"lowerBound\": ").append(escapeJson(bucket.lower())).append(",\n");
                    sb.append("        \"upperBound\": ").append(escapeJson(bucket.upper())).append(",\n");
                    
                    // 计算 rowCount 和 frequency
                    double rowFraction = bucket.rowFraction() != null ? bucket.rowFraction() : 0.0;
                    Long rowCount = null;
                    if (tableRows != null && tableRows > 0) {
                        rowCount = Math.round(rowFraction * tableRows);
                    }
                    
                    if (rowCount != null) {
                        sb.append("        \"rowCount\": ").append(rowCount).append(",\n");
                    } else {
                        sb.append("        \"rowCount\": null,\n");
                    }
                    
                    sb.append("        \"frequency\": ").append(String.format(java.util.Locale.ROOT, "%.6f", rowFraction)).append(",\n");
                    
                    // sampleValues（可选）
                    if (histogram.sampleValues() != null && !histogram.sampleValues().isEmpty()) {
                        sb.append("        \"sampleValues\": [\n");
                        for (int k = 0; k < histogram.sampleValues().size(); k++) {
                            if (k > 0) {
                                sb.append(",\n");
                            }
                            sb.append("          ").append(escapeJson(histogram.sampleValues().get(k)));
                        }
                        sb.append("\n        ]\n");
                    } else {
                        sb.append("        \"sampleValues\": null\n");
                    }
                    
                    sb.append("      }");
                }
            }
            sb.append("\n    ]\n");
            sb.append("  }");
        }

        sb.append("\n]");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "null";
        }
        // 简单的 JSON 字符串转义
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}


