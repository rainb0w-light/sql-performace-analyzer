package com.biz.sccba.sqlanalyzer.profiling;

import java.util.List;

/**
 * Deterministic, dialect-specific read-only profiling SQL templates (development-guide §7.2).
 * The LLM never generates sampling SQL; adapters return fixed templates parameterized only by
 * already-validated identifiers and numeric budgets.
 */
public interface DialectAdapter {

    String dialect();

    /** Table-level estimate: rows, data bytes, update time. */
    String tableStatsSql(String schema, String table);

    /** Column inventory: name, data type, nullability. */
    String columnsSql(String schema, String table);

    /** COUNT(*) and NULL count over a bounded sample. */
    String nullRatioSql(String schema, String table, String column, int sampleRows);

    /** distinct/min/max over the bounded sample. */
    String distinctMinMaxSql(String schema, String table, String column, int sampleRows);

    /**
     * Top-K value frequencies over the sample; the value expression depends on the sensitivity
     * policy (plaintext / SHA-256 hash / value omitted entirely).
     */
    String topKSql(String schema, String table, String column, int sampleRows, int topK, SensitivePolicy policy);

    /** Equal-width histogram buckets over the sample for numeric columns (min/max from prior step). */
    String bucketsSql(String schema, String table, String column, int sampleRows, int bucketCount, double min, double max);

    /** Ordered-sample quantile probes (offsets computed by the caller from sampleRows). */
    List<Quantile> quantileSqls(String schema, String table, String column, int sampleRows, List<Double> quantiles);

    record Quantile(double q, String sql) {}

    enum SensitivePolicy { PLAINTEXT, HASHED, OMITTED }
}
