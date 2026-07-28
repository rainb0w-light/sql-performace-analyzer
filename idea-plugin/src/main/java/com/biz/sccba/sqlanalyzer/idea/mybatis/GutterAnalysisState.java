package com.biz.sccba.sqlanalyzer.idea.mybatis;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral project gutter projection; content/datasource changes make prior results stale. */
public final class GutterAnalysisState {
    public enum Status { READY, RUNNING, COMPLETED, FAILED, STALE }
    public record Entry(Status status, String contentHash, String datasourceProfileId,
                        String severity, Instant updatedAt, String message) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry get(String locator, String contentHash, String datasourceProfileId) {
        Entry entry = entries.get(locator);
        if (entry == null) return new Entry(Status.READY, contentHash, datasourceProfileId,
                "", null, "分析 SQL 性能");
        if (!same(entry.contentHash(), contentHash)
                || (!text(datasourceProfileId).isBlank()
                && !same(entry.datasourceProfileId(), datasourceProfileId))) {
            return new Entry(Status.STALE, contentHash, datasourceProfileId, entry.severity(),
                    entry.updatedAt(), "结果可能已过期；点击重新分析");
        }
        return entry;
    }

    public void mark(String locator, Status status, String contentHash, String datasourceProfileId,
                     String severity, String message) {
        if (locator == null || locator.isBlank()) return;
        entries.put(locator, new Entry(status, text(contentHash), text(datasourceProfileId),
                text(severity), Instant.now(), text(message)));
    }

    private static boolean same(String left, String right) { return text(left).equals(text(right)); }
    private static String text(String value) { return value == null ? "" : value; }
}
