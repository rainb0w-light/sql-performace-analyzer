package com.biz.sccba.sqlanalyzer.idea.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Converts the server's structured Recommendation JSON into a compact UI summary. */
public final class RecommendationFormatter {
    private RecommendationFormatter() {
    }

    public static Result format(String json) {
        if (json == null || json.isBlank()) return new Result("暂无优化建议。", "");
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return new Result(json, "");
            JsonArray items = root.getAsJsonArray();
            if (items.isEmpty()) return new Result("暂无优化建议。", "");
            StringBuilder text = new StringBuilder();
            String firstId = "";
            for (int i = 0; i < items.size(); i++) {
                JsonElement element = items.get(i);
                if (!element.isJsonObject()) continue;
                var item = element.getAsJsonObject();
                String id = value(item, "id");
                if (firstId.isBlank() && !id.isBlank()) firstId = id;
                text.append(i + 1).append(". ").append(value(item, "title", "慢 SQL 优化建议"))
                        .append(" [").append(value(item, "priority", "MEDIUM"))
                        .append(" / ").append(value(item, "status", "PROPOSED")).append("]\n")
                        .append("   ID: ").append(id).append("\n")
                        .append("   问题: ").append(value(item, "problem", value(item, "description", ""))).append("\n")
                        .append("   影响: ").append(value(item, "impact", "未提供")).append("\n")
                        .append("   建议 SQL: ").append(value(item, "suggestedSql", "未提供")).append("\n\n");
            }
            return new Result(text.toString().trim(), firstId);
        } catch (RuntimeException ignored) {
            return new Result(json, "");
        }
    }

    private static String value(com.google.gson.JsonObject object, String key) {
        return value(object, key, "");
    }

    private static String value(com.google.gson.JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    /** One structured recommendation entry (id + compact display text) for list-based UIs. */
    public record RecItem(String id, String text) {
    }

    public static java.util.List<RecItem> items(String json) {
        java.util.List<RecItem> out = new java.util.ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return out;
            JsonArray items = root.getAsJsonArray();
            for (int i = 0; i < items.size(); i++) {
                JsonElement element = items.get(i);
                if (!element.isJsonObject()) continue;
                var item = element.getAsJsonObject();
                String id = value(item, "id");
                String text = (i + 1) + ". " + value(item, "title", "慢 SQL 优化建议")
                        + " [" + value(item, "priority", "MEDIUM") + " / " + value(item, "status", "PROPOSED") + "]"
                        + " — 问题: " + value(item, "problem", value(item, "description", ""))
                        + "；影响: " + value(item, "impact", "未提供")
                        + "；建议 SQL: " + value(item, "suggestedSql", "未提供");
                out.add(new RecItem(id, text));
            }
        } catch (RuntimeException ignored) {
            // malformed payloads render nothing
        }
        return out;
    }

    public record Result(String text, String firstRecommendationId) {
    }
}
