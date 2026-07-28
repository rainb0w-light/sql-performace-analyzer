package com.biz.sccba.sqlanalyzer.idea.client;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisEvent;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.AnalysisError;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.Guard;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.GuardType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Structured AG-UI event mapping. It never inspects natural-language log text for state. */
public final class AguiStateMapper {
    private AguiStateMapper() {}

    public static List<AnalysisEvent> map(String eventId, String type, String json) {
        List<AnalysisEvent> out = new ArrayList<>();
        if (eventId != null && !eventId.isBlank()) out.add(new AnalysisEvent.EventConsumed(eventId));
        JsonObject root = object(json);
        switch (type == null ? "" : type) {
            case "RUN_STARTED" -> out.add(new AnalysisEvent.RunStarted());
            case "RUN_ERROR" -> {
                String code = text(root, "code");
                if ("CANCELLED".equals(code)) out.add(new AnalysisEvent.Cancelled());
                else out.add(new AnalysisEvent.Failed(new AnalysisError(code,
                        text(root, "message"), bool(root, "retryable"),
                        bool(root, "retryable") ? "重试" : "查看不支持原因或定位源码")));
            }
            case "RUN_FINISHED" -> {
                String status = text(root, "status");
                AnalysisEvent.TerminalStatus terminal = "CANCELLED".equals(status)
                        ? AnalysisEvent.TerminalStatus.CANCELLED
                        : "FAILED".equals(status) ? AnalysisEvent.TerminalStatus.FAILED
                        : AnalysisEvent.TerminalStatus.COMPLETED;
                out.add(new AnalysisEvent.RunFinished(terminal));
            }
            case "STATE_DELTA", "STATE_SNAPSHOT" -> {
                String stage = metadata(root, "spa.stage");
                if (!stage.isBlank()) out.add(new AnalysisEvent.PhaseChanged(stage));
            }
            case "CUSTOM" -> {
                String name = text(root, "name");
                if ("spa.phase_changed".equals(name)) {
                    out.add(new AnalysisEvent.PhaseChanged(first(root, "phase", "stage")));
                } else if ("spa.report_ready".equals(name)) {
                    out.add(new AnalysisEvent.ReportReady(first(root, "reportId", "spa.reportId")));
                } else if ("spa.scenarios_ready".equals(name) || "spa.scenario_matrix".equals(name)) {
                    boolean review = bool(root, "requiresConfirmation");
                    List<Guard> guards = guards(root);
                    out.add(new AnalysisEvent.PlanReady(guards,
                            review || guards.stream().anyMatch(Guard::blocking)));
                }
            }
            default -> {
                // Text/tool payloads are logs/evidence, not business state facts.
            }
        }
        return List.copyOf(out);
    }

    private static JsonObject object(String json) {
        try { return JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException ignored) { return new JsonObject(); }
    }
    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "";
    }
    private static boolean bool(JsonObject object, String field) {
        try { return object.has(field) && object.get(field).getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }
    private static String first(JsonObject object, String first, String second) {
        String value = text(object, first);
        return value.isBlank() ? text(object, second) : value;
    }
    private static String metadata(JsonObject object, String field) {
        if (object.has("metadata") && object.get("metadata").isJsonObject()) {
            return text(object.getAsJsonObject("metadata"), field);
        }
        return text(object, field);
    }

    private static List<Guard> guards(JsonObject root) {
        List<Guard> out = new ArrayList<>();
        if (!root.has("guards") || !root.get("guards").isJsonArray()) return out;
        for (JsonElement element : root.getAsJsonArray("guards")) {
            JsonObject item = element.getAsJsonObject();
            GuardType type;
            try { type = GuardType.valueOf(first(item, "type", "code")); }
            catch (IllegalArgumentException ignored) { continue; }
            out.add(new Guard(type, !item.has("blocking") || item.get("blocking").getAsBoolean(),
                    first(item, "message", "reason"), text(item, "locator")));
        }
        return List.copyOf(out);
    }
}
