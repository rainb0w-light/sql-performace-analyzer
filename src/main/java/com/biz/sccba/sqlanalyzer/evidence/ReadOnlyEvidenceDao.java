package com.biz.sccba.sqlanalyzer.evidence;

import java.util.Map;

/** Port for read-only evidence collected from a MySQL/GoldenDB target database. */
public interface ReadOnlyEvidenceDao {
    Evidence explain(String sql, Map<String, String> datasourceProfile);
    Evidence tableStructure(String tableName, Map<String, String> datasourceProfile);

    record Evidence(String type, boolean success, String payload, String error) { }
}
