package com.biz.sccba.sqlanalyzer.idea.navigation;

import java.util.ArrayDeque;
import java.util.Deque;

/** Stable-id cross-tab navigation with an explicit back path. */
public final class ReportNavigation {
    public enum TargetType { RISK, SCENARIO, EVIDENCE, MAPPER }
    public record Target(TargetType type, String stableId, String locator) {}

    private final Deque<Target> back = new ArrayDeque<>();
    private Target current;

    public Target current() { return current; }
    public void go(Target target) {
        if (target == null || target.stableId() == null || target.stableId().isBlank()) return;
        if (current != null) back.push(current);
        current = target;
    }
    public boolean canGoBack() { return !back.isEmpty(); }
    public Target back() {
        if (!back.isEmpty()) current = back.pop();
        return current;
    }
    public void clear() { back.clear(); current = null; }
}
