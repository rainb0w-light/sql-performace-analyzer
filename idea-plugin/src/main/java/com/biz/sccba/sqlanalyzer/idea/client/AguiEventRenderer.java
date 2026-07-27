package com.biz.sccba.sqlanalyzer.idea.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pure AG-UI event → display text mapping (development-guide §8.2): incremental text deltas,
 * tool-call markers, step and error lines. Terminal detection is by event type, never by
 * substring matching on raw payloads.
 */
public final class AguiEventRenderer {

    public static final String TYPE_RUN_FINISHED = "RUN_FINISHED";
    public static final String TYPE_RUN_ERROR = "RUN_ERROR";

    private AguiEventRenderer() {}

    public static boolean isTerminal(String type) {
        return TYPE_RUN_FINISHED.equals(type);
    }

    /** @return text to append, or null when the event renders nothing in the stream view. */
    public static String render(String type, String json) {
        if (type == null) return null;
        switch (type) {
            case "RUN_STARTED":
                return "\n--- 运行开始 ---\n";
            case "TEXT_MESSAGE_CONTENT":
                return stringField(json, "delta");
            case "TOOL_CALL_START":
                return "\n🔧 工具调用：" + stringField(json, "toolCallName") + "\n";
            case "TOOL_CALL_END":
                return "";
            case "STEP_STARTED":
                return "\n▶ " + stringField(json, "stepName") + "\n";
            case "STEP_FINISHED":
                return "";
            case "RUN_ERROR":
                return "\n❌ 错误[" + stringField(json, "code") + "]：" + stringField(json, "message") + "\n";
            case "RUN_FINISHED":
                return "\n✅ 运行结束\n";
            case "CUSTOM":
                String name = stringField(json, "name");
                return name.isEmpty() ? null : "\n📎 " + name + "\n";
            default:
                return null;
        }
    }

    private static String stringField(String json, String field) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
