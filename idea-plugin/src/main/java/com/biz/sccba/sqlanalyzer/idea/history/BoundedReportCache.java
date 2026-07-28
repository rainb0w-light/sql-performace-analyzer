package com.biz.sccba.sqlanalyzer.idea.history;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded display cache only. Clearing it never calls a server delete endpoint. */
public final class BoundedReportCache {
    private final int maxEntries;
    private final long maxBytes;
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>(16, .75f, true);
    private long bytes;

    public BoundedReportCache(int maxEntries, long maxBytes) {
        this.maxEntries = Math.max(1, maxEntries);
        this.maxBytes = Math.max(1024, maxBytes);
    }

    public synchronized void put(String reportId, String json) {
        if (reportId == null || reportId.isBlank() || json == null) return;
        String prior = values.remove(reportId);
        if (prior != null) bytes -= size(prior);
        values.put(reportId, json);
        bytes += size(json);
        while (values.size() > maxEntries || bytes > maxBytes) {
            Map.Entry<String, String> eldest = values.entrySet().iterator().next();
            bytes -= size(eldest.getValue());
            values.remove(eldest.getKey());
        }
    }

    public synchronized String get(String reportId) { return values.get(reportId); }
    public synchronized int size() { return values.size(); }
    public synchronized long bytes() { return bytes; }
    public synchronized void clear() { values.clear(); bytes = 0; }
    private static long size(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
