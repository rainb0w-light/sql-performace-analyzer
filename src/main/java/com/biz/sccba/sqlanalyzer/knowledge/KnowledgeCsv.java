package com.biz.sccba.sqlanalyzer.knowledge;

/** RFC 4180 cells with spreadsheet formula-injection protection. */
public final class KnowledgeCsv {

    private KnowledgeCsv() {}

    public static String safeCell(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
    }

    public static String csvCell(String value) {
        String safe = safeCell(value);
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
