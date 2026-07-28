package com.biz.sccba.sqlanalyzer.idea.scenario;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ScenarioReviewModelTest {
    @Test
    public void mainRequiredAndGuardScenariosCannotBeCostExcluded() {
        ScenarioReviewModel model = new ScenarioReviewModel(List.of(
                new ScenarioReviewModel.Scenario("main", "主路径", false, true, false, true, "LOW"),
                new ScenarioReviewModel.Scenario("required", "必选", true, false, false, true, "HIGH"),
                new ScenarioReviewModel.Scenario("guard", "守卫", false, false, true, true, "MEDIUM"),
                new ScenarioReviewModel.Scenario("optional", "可选", false, false, false, true, "HIGH")));
        assertFalse(model.include("main", false, "cost"));
        assertFalse(model.include("required", false, "cost"));
        assertFalse(model.include("guard", false, "cost"));
        assertTrue(model.include("optional", false, "cost"));
        assertFalse(model.includedIds().contains("optional"));
        assertEquals("cost", model.exclusions().get("optional"));
    }
}

