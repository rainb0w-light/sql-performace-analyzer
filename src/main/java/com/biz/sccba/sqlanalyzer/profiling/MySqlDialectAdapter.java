package com.biz.sccba.sqlanalyzer.profiling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MySQL / GoldenDB (MySQL-compatible) profiling templates. Identifiers are validated with a
 * strict allowlist before interpolation; all statements are read-only SELECTs.
 */
public class MySqlDialectAdapter implements DialectAdapter {

    @Override
    public String dialect() {
        return "MYSQL";
    }

    @Override
    public String tableStatsSql(String schema, String table) {
        return "SELECT TABLE_ROWS, DATA_LENGTH, UPDATE_TIME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA=" + literal(schema) + " AND TABLE_NAME=" + literal(table);
    }

    @Override
    public String columnsSql(String schema, String table) {
        return "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA=" + literal(schema) + " AND TABLE_NAME=" + literal(table)
                + " ORDER BY ORDINAL_POSITION";
    }

    @Override
    public String nullRatioSql(String schema, String table, String column, int sampleRows) {
        return "SELECT COUNT(*) AS total, SUM(" + ident(column) + " IS NULL) AS nulls FROM ("
                + "SELECT " + ident(column) + " FROM " + qualified(schema, table) + " LIMIT " + bound(sampleRows) + ") s";
    }

    @Override
    public String distinctMinMaxSql(String schema, String table, String column, int sampleRows) {
        return "SELECT COUNT(DISTINCT " + ident(column) + ") AS d, MIN(" + ident(column) + ") AS mn, MAX(" + ident(column) + ") AS mx FROM ("
                + "SELECT " + ident(column) + " FROM " + qualified(schema, table) + " LIMIT " + bound(sampleRows) + ") s";
    }

    @Override
    public String topKSql(String schema, String table, String column, int sampleRows, int topK, SensitivePolicy policy) {
        String valueExpr = switch (policy) {
            case HASHED -> "SHA2(CAST(" + ident(column) + " AS CHAR), 256)";
            case OMITTED -> null;
            case PLAINTEXT -> "CAST(" + ident(column) + " AS CHAR)";
        };
        if (valueExpr == null) {
            // OMITTED: cardinality shape only, no values leave the target database.
            return "SELECT COUNT(*) AS c FROM (SELECT " + ident(column) + " FROM " + qualified(schema, table)
                    + " LIMIT " + bound(sampleRows) + ") s WHERE " + ident(column) + " IS NOT NULL GROUP BY "
                    + ident(column) + " ORDER BY c DESC LIMIT " + bound(topK);
        }
        return "SELECT " + valueExpr + " AS v, COUNT(*) AS c FROM (SELECT " + ident(column) + " FROM "
                + qualified(schema, table) + " LIMIT " + bound(sampleRows) + ") s WHERE " + ident(column)
                + " IS NOT NULL GROUP BY v ORDER BY c DESC, v ASC LIMIT " + bound(topK);
    }

    @Override
    public String bucketsSql(String schema, String table, String column, int sampleRows, int bucketCount, double min, double max) {
        int buckets = Math.max(1, Math.min(bucketCount, 100));
        double width = (max - min) / buckets;
        if (!(width > 0)) {
            return "SELECT 0 AS b, COUNT(*) AS c FROM (SELECT " + ident(column) + " FROM " + qualified(schema, table)
                    + " LIMIT " + bound(sampleRows) + ") s WHERE " + ident(column) + " IS NOT NULL";
        }
        return "SELECT LEAST(FLOOR((" + ident(column) + " - (" + num(min) + ")) / (" + num(width) + ")), " + (buckets - 1)
                + ") AS b, COUNT(*) AS c FROM (SELECT " + ident(column) + " FROM " + qualified(schema, table)
                + " LIMIT " + bound(sampleRows) + ") s WHERE " + ident(column) + " IS NOT NULL GROUP BY b ORDER BY b";
    }

    @Override
    public List<Quantile> quantileSqls(String schema, String table, String column, int sampleRows, List<Double> quantiles) {
        List<Quantile> out = new ArrayList<>();
        int sample = bound(sampleRows);
        for (double q : quantiles) {
            long offset = Math.max(0, Math.min(sample - 1, Math.round(sample * Math.max(0.0, Math.min(1.0, q)))));
            String sql = "SELECT " + ident(column) + " AS v FROM (SELECT " + ident(column) + " FROM "
                    + qualified(schema, table) + " WHERE " + ident(column) + " IS NOT NULL ORDER BY " + ident(column)
                    + " LIMIT " + sample + ") o LIMIT 1 OFFSET " + offset;
            out.add(new Quantile(q, sql));
        }
        return out;
    }

    /** Strict identifier allowlist: letters, digits, underscore; backticked when interpolated. */
    static String ident(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法标识符：" + name);
        }
        return "`" + name + "`";
    }

    static String qualified(String schema, String table) {
        if (schema == null || schema.isBlank()) return ident(table);
        return ident(schema) + "." + ident(table);
    }

    static String literal(String value) {
        if (value == null) return "''";
        if (!value.matches("[A-Za-z0-9_.\\-]+")) throw new IllegalArgumentException("非法字面量：" + value);
        return "'" + value + "'";
    }

    static int bound(int value) {
        return Math.max(1, Math.min(value, 1_000_000));
    }

    static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("非法数值");
        return String.format(Locale.ROOT, "%f", value);
    }
}
