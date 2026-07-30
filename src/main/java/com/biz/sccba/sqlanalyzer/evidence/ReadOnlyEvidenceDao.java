package com.biz.sccba.sqlanalyzer.evidence;

import java.util.Map;
import java.util.List;

/** Port for read-only evidence collected from a MySQL/GoldenDB target database. */
public interface ReadOnlyEvidenceDao {
    Evidence explain(String sql, Map<String, String> datasourceProfile);

    /**
     * Prepared-argument variant used by the statement analysis path. Implementations must bind
     * values separately and must never interpolate them into SQL text.
     */
    default Evidence explain(String sql, List<Object> arguments, Map<String, String> datasourceProfile) {
        if (arguments != null && !arguments.isEmpty()) {
            return new Evidence("EXPLAIN_PLAN", false, "{}",
                    "当前只读证据适配器不支持参数化 EXPLAIN");
        }
        return explain(sql, datasourceProfile);
    }

    Evidence tableStructure(String tableName, Map<String, String> datasourceProfile);

    record Evidence(String type, boolean success, String payload, String error) { }
}
