package com.biz.sccba.sqlanalyzer.idea.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure mapping of the standard report's scenario projection into matrix rows
 * (development-guide §8.2): parameter source, dynamic branches, SQL fingerprint and Bound SQL.
 */
public final class ScenarioMatrixModel {

    public static final String[] COLUMNS = {"场景", "参数来源", "分支覆盖", "SQL 指纹", "Bound SQL", "风险"};

    public record Row(String name, String source, String branches, String fingerprint, String boundSql, String risk) {}

    private ScenarioMatrixModel() {}

    public static List<Row> rows(String planJson) {
        List<Row> rows = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(planJson).getAsJsonObject();
            String loadError = root.has("loadError") && !root.get("loadError").isJsonNull()
                    ? root.get("loadError").getAsString() : null;
            if (loadError != null) {
                rows.add(new Row("[UNSUPPORTED]", "", "", "", loadError, "UNSUPPORTED"));
                return rows;
            }
            JsonArray scenarios = root.getAsJsonArray("scenarios");
            if (scenarios == null) return rows;
            for (var element : scenarios) {
                JsonObject scenario = element.getAsJsonObject();
                JsonObject meta = scenario.has("scenario") && scenario.get("scenario").isJsonObject()
                        ? scenario.getAsJsonObject("scenario") : new JsonObject();
                String name = text(meta, "name", text(scenario, "name", "(unnamed)"));
                String source = text(meta, "source", text(scenario, "parameterSource", ""));
                List<String> branchValues = stringArray(meta, "expectedBranches");
                if (branchValues.isEmpty()) branchValues = stringArray(scenario, "expectedBranches");
                String branches = String.join(", ", branchValues);
                String fingerprint = text(scenario, "sqlFingerprint", "");
                String boundSql = text(scenario, "boundSql", text(scenario, "unsupported", ""))
                        .replaceAll("\\s+", " ").trim();
                if (boundSql.length() > 400) boundSql = boundSql.substring(0, 400) + "…";
                boolean unsupported = scenario.has("unsupported") && !scenario.get("unsupported").isJsonNull();
                boolean dollar = scenario.has("hasDollarInterpolation")
                        && scenario.get("hasDollarInterpolation").getAsBoolean();
                String risk = unsupported ? "UNSUPPORTED" : (dollar ? "${} 注入风险" : "");
                rows.add(new Row(name, source, branches, fingerprint, boundSql, risk));
            }
        } catch (Exception ignored) {
            // malformed plan payloads render nothing; the raw stream remains visible
        }
        return rows;
    }

    public static Object[][] tableData(List<Row> rows) {
        Object[][] data = new Object[rows.size()][COLUMNS.length];
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            data[i] = new Object[] { r.name(), r.source(), r.branches(), r.fingerprint(), r.boundSql(), r.risk() };
        }
        return data;
    }

    private static String text(JsonObject obj, String field, String def) {
        return obj.has(field) && !obj.get(field).isJsonNull() ? obj.get(field).getAsString() : def;
    }

    private static List<String> stringArray(JsonObject obj, String field) {
        List<String> out = new ArrayList<>();
        if (obj.has(field) && obj.get(field).isJsonArray()) {
            for (var e : obj.getAsJsonArray(field)) out.add(e.getAsString());
        }
        return out;
    }
}
