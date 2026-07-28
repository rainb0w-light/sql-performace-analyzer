package com.biz.sccba.sqlanalyzer.idea.scenario;

import java.util.*;

/** Stable-id scenario inclusion model. Required/main-path/guard scenarios cannot be excluded. */
public final class ScenarioReviewModel {
    public record Scenario(String scenarioId, String name, boolean required, boolean mainPath,
                           boolean guardScenario, boolean excludable, String costLevel) {
        public boolean locked() { return required || mainPath || guardScenario || !excludable; }
    }

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();
    private final Set<String> included = new LinkedHashSet<>();
    private final Map<String, String> exclusions = new LinkedHashMap<>();

    public ScenarioReviewModel(List<Scenario> values) {
        if (values != null) for (Scenario value : values) {
            scenarios.put(value.scenarioId(), value);
            included.add(value.scenarioId());
        }
    }

    public boolean include(String scenarioId, boolean value, String reason) {
        Scenario scenario = scenarios.get(scenarioId);
        if (scenario == null) return false;
        if (!value && scenario.locked()) return false;
        if (value) {
            included.add(scenarioId);
            exclusions.remove(scenarioId);
        } else {
            included.remove(scenarioId);
            exclusions.put(scenarioId, reason == null ? "" : reason);
        }
        return true;
    }

    public Set<String> includedIds() { return Set.copyOf(included); }
    public Map<String, String> exclusions() { return Map.copyOf(exclusions); }
}
