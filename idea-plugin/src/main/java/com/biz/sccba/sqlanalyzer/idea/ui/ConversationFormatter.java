package com.biz.sccba.sqlanalyzer.idea.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Formats persisted session messages for the compact Tool Window history view. */
public final class ConversationFormatter {
    private ConversationFormatter() {
    }

    public static String format(String json) {
        if (json == null || json.isBlank()) return "暂无会话消息。";
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray() || root.getAsJsonArray().isEmpty()) return "暂无会话消息。";
            StringBuilder output = new StringBuilder();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                var item = element.getAsJsonObject();
                String role = item.has("role") ? item.get("role").getAsString() : "UNKNOWN";
                String content = item.has("content") ? item.get("content").getAsString() : "";
                output.append("[").append(role).append("] ").append(content).append("\n\n");
            }
            return output.toString().trim();
        } catch (RuntimeException ignored) {
            return json;
        }
    }
}
