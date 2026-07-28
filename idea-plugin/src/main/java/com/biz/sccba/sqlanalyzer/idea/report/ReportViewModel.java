package com.biz.sccba.sqlanalyzer.idea.report;

import com.google.gson.*;

import java.util.*;

/** Safe structured projection used by Report/Scenario/Evidence tabs and cross-view navigation. */
public record ReportViewModel(
        String reportId,
        String severity,
        String headline,
        double confidence,
        List<Risk> risks,
        List<Scenario> scenarios,
        List<Evidence> evidence,
        List<Recommendation> recommendations,
        List<String> limits
) {
    public ReportViewModel {
        risks = copy(risks);
        scenarios = copy(scenarios);
        evidence = copy(evidence);
        recommendations = copy(recommendations);
        limits = copy(limits);
    }

    public record Risk(String riskId, String type, String title,
                       List<String> scenarioIds, List<String> evidenceIds) {
        public Risk {
            scenarioIds = copy(scenarioIds);
            evidenceIds = copy(evidenceIds);
        }
    }
    public record Scenario(String scenarioId, String name, String source, String boundSql,
                           String fingerprint, List<String> coverageGoals, List<String> evidenceIds,
                           boolean hasExplainEvidence) {
        public Scenario {
            coverageGoals = copy(coverageGoals);
            evidenceIds = copy(evidenceIds);
        }
    }
    public record Evidence(String evidenceId, String sourceType, String sourceId, String version,
                           String locator, double confidence, String content, String scenarioId) {}
    public record Recommendation(String recommendationId, String title, String priority,
                                 double confidence, String problem, String impact,
                                 List<String> evidenceIds, String status, String decidedBy,
                                 String decidedAt) {
        public Recommendation { evidenceIds = copy(evidenceIds); }
    }

    public static ReportViewModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject summary = object(root, "summary");
        List<Evidence> evidence = evidence(root);
        Set<String> explainScenarios = new HashSet<>();
        JsonArray plans = array(root, "executionPlans");
        for (JsonElement element : plans) {
            JsonObject plan = element.getAsJsonObject();
            String scenarioId = text(plan, "scenarioId");
            if (!scenarioId.isBlank() && plan.has("plan") && !plan.get("plan").isJsonNull()) {
                explainScenarios.add(scenarioId);
            }
        }
        return new ReportViewModel(text(root, "reportId"), text(summary, "severity"),
                text(summary, "headline"), number(summary, "confidence"), risks(root),
                scenarios(root, explainScenarios), evidence, recommendations(root), limits(root));
    }

    private static List<Risk> risks(JsonObject root) {
        List<Risk> out = new ArrayList<>();
        int index = 0;
        for (JsonElement element : array(root, "risks")) {
            JsonObject item = element.getAsJsonObject();
            List<String> evidenceIds = strings(item, "evidenceIds");
            String riskId = text(item, "riskId");
            // Missing server stable IDs intentionally remain blank; navigation is then disabled.
            out.add(new Risk(riskId, text(item, "type"), text(item, "title"),
                    strings(item, "scenarioIds"), evidenceIds));
            index++;
        }
        return out;
    }

    private static List<Scenario> scenarios(JsonObject root, Set<String> explainScenarios) {
        List<Scenario> out = new ArrayList<>();
        for (JsonElement element : array(root, "scenarios")) {
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "scenarioId");
            out.add(new Scenario(id, text(item, "name"), text(item, "parameterSource"),
                    text(item, "boundSql"), text(item, "sqlFingerprint"),
                    strings(item, "coverageGoals"), strings(item, "evidenceIds"),
                    explainScenarios.contains(id)));
        }
        return out;
    }

    private static List<Evidence> evidence(JsonObject root) {
        List<Evidence> out = new ArrayList<>();
        for (JsonElement element : array(root, "evidenceCatalog")) {
            JsonObject item = element.getAsJsonObject();
            out.add(new Evidence(text(item, "evidenceId"), text(item, "sourceType"),
                    text(item, "sourceId"), text(item, "version"), text(item, "locator"),
                    number(item, "confidence"), text(item, "content"), text(item, "scenarioId")));
        }
        return out;
    }

    private static List<Recommendation> recommendations(JsonObject root) {
        List<Recommendation> out = new ArrayList<>();
        for (JsonElement element : array(root, "recommendations")) {
            JsonObject item = element.getAsJsonObject();
            out.add(new Recommendation(text(item, "recommendationId"), text(item, "title"),
                    text(item, "priority"), number(item, "confidence"), text(item, "problem"),
                    text(item, "impact"), strings(item, "evidenceIds"), text(item, "status"),
                    text(item, "decidedBy"), text(item, "decidedAt")));
        }
        return out;
    }

    private static List<String> limits(JsonObject root) {
        JsonObject limits = object(root, "limits");
        List<String> out = new ArrayList<>(strings(limits, "notes"));
        out.addAll(strings(limits, "unsupportedTags"));
        out.addAll(strings(limits, "missingPermissions"));
        out.addAll(strings(limits, "staleSnapshots"));
        if (limits.has("explainSkipped") && limits.get("explainSkipped").getAsBoolean()) {
            out.add("执行计划已跳过");
        }
        return out;
    }

    private static JsonObject object(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonObject()
                ? root.getAsJsonObject(field) : new JsonObject();
    }
    private static JsonArray array(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonArray()
                ? root.getAsJsonArray(field) : new JsonArray();
    }
    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "";
    }
    private static double number(JsonObject object, String field) {
        try { return object.has(field) ? object.get(field).getAsDouble() : 0d; }
        catch (RuntimeException ignored) { return 0d; }
    }
    private static List<String> strings(JsonObject object, String field) {
        List<String> out = new ArrayList<>();
        if (object.has(field) && object.get(field).isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray(field)) out.add(element.getAsString());
        }
        return out;
    }
    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
