package com.biz.sccba.sqlanalyzer.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Port for slow-log platforms and saved log artifacts. */
public interface SlowLogSource {
    SlowLogBatch fetch(Query query);

    record Query(String sqlFingerprint, Instant from, Instant to, int limit, Map<String, String> options) { }
    record SlowLogBatch(String source, List<Entry> entries, String rawPayload) { }
    record Entry(String sql, long count, double avgMs, double maxMs, Instant observedAt, Map<String, String> attributes) { }
}
