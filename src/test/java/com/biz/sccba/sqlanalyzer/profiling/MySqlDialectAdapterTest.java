package com.biz.sccba.sqlanalyzer.profiling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Profiling templates are deterministic and read-only (development-guide §7.2): fixed shapes,
 * bounded sampling, sensitivity policy applied in-SQL, and strict identifier validation so no
 * caller-supplied string ever reaches the target database unvalidated.
 */
class MySqlDialectAdapterTest {

    private final MySqlDialectAdapter adapter = new MySqlDialectAdapter();

    @Test
    void templatesAreDeterministic() {
        String a = adapter.nullRatioSql("db", "orders", "status", 1000);
        String b = adapter.nullRatioSql("db", "orders", "status", 1000);
        assertEquals(a, b);
        assertTrue(a.startsWith("SELECT"));
        assertTrue(a.contains("LIMIT 1000"));
    }

    @Test
    void topKRespectsSensitivityPolicy() {
        String plain = adapter.topKSql("db", "orders", "status", 100, 10, DialectAdapter.SensitivePolicy.PLAINTEXT);
        assertTrue(plain.contains("AS v"));
        assertFalse(plain.contains("SHA2"));

        String hashed = adapter.topKSql("db", "orders", "phone", 100, 10, DialectAdapter.SensitivePolicy.HASHED);
        assertTrue(hashed.contains("SHA2(CAST(`phone` AS CHAR), 256)"), "HASHED must hash values in-SQL");

        String omitted = adapter.topKSql("db", "orders", "phone", 100, 10, DialectAdapter.SensitivePolicy.OMITTED);
        assertFalse(omitted.contains("AS v"), "OMITTED must never select values");
        assertFalse(omitted.contains("SHA2"));
    }

    @Test
    void sampleSizeIsBounded() {
        String sql = adapter.nullRatioSql("db", "orders", "status", 100_000_000);
        assertTrue(sql.contains("LIMIT 1000000"), "sampling budget must be capped");
    }

    @Test
    void identifiersAreStrictlyValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.nullRatioSql("db", "orders; DROP TABLE x", "status", 100));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.nullRatioSql("db", "orders", "a` OR 1=1 --", 100));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.tableStatsSql("db'", "orders"));
    }

    @Test
    void quantileOffsetsCoverSample() {
        var quantiles = adapter.quantileSqls("db", "orders", "amount", 1000, java.util.List.of(0.5, 0.95));
        assertEquals(2, quantiles.size());
        assertTrue(quantiles.get(0).sql().contains("OFFSET 500"));
        assertTrue(quantiles.get(1).sql().contains("OFFSET 950"));
        assertTrue(quantiles.stream().allMatch(q -> q.sql().startsWith("SELECT")));
    }

    @Test
    void bucketsHandleDegenerateRange() {
        String sql = adapter.bucketsSql("db", "orders", "amount", 100, 10, 5.0, 5.0);
        assertTrue(sql.startsWith("SELECT 0 AS b"));
    }
}
