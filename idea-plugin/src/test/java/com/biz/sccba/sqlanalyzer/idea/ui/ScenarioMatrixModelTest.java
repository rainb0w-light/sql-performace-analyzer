package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Scenario matrix mapping contract (plan JSON → table rows). */
public class ScenarioMatrixModelTest {

    private static final String PLAN = """
            {
              "namespace": "com.example.OrderMapper",
              "statementId": "search",
              "loadError": null,
              "scenarios": [
                {
                  "scenario": {
                    "scenarioId": "scn_main",
                    "name": "业务主路径",
                    "source": "RULE_INFERRED",
                    "expectedBranches": ["search#if[0]:true"],
                    "priority": 0
                  },
                  "boundSql": "SELECT id FROM orders WHERE status = ?",
                  "sqlFingerprint": "ab12cd34ef560718",
                  "parameterMappings": [{"property": "status"}],
                  "additionalParameters": {},
                  "coveredNodeIds": ["search#if[0]"],
                  "hasDollarInterpolation": false,
                  "unsupported": null
                },
                {
                  "scenario": {
                    "scenarioId": "scn_dollar",
                    "name": "${orderColumn} 白名单取值",
                    "source": "RULE_INFERRED",
                    "expectedBranches": [],
                    "priority": 5
                  },
                  "boundSql": "SELECT id FROM orders ORDER BY created_at",
                  "sqlFingerprint": "9988776655443322",
                  "parameterMappings": [],
                  "additionalParameters": {},
                  "coveredNodeIds": [],
                  "hasDollarInterpolation": false,
                  "unsupported": null
                },
                {
                  "scenario": {
                    "scenarioId": "scn_bad",
                    "name": "自定义 driver",
                    "source": "BOUNDARY_GENERATED",
                    "expectedBranches": [],
                    "priority": 99
                  },
                  "boundSql": null,
                  "sqlFingerprint": null,
                  "parameterMappings": [],
                  "additionalParameters": {},
                  "coveredNodeIds": [],
                  "hasDollarInterpolation": false,
                  "unsupported": "UNSUPPORTED: 自定义 LanguageDriver"
                }
              ]
            }
            """;

    @Test
    public void planMapsToRowsWithProvenanceAndRisk() {
        List<ScenarioMatrixModel.Row> rows = ScenarioMatrixModel.rows(PLAN);
        assertEquals(3, rows.size());

        assertEquals("业务主路径", rows.get(0).name());
        assertEquals("RULE_INFERRED", rows.get(0).source());
        assertTrue(rows.get(0).branches().contains("search#if[0]:true"));
        assertEquals("ab12cd34ef560718", rows.get(0).fingerprint());
        assertTrue(rows.get(0).boundSql().contains("status = ?"));
        assertEquals("", rows.get(0).risk());

        assertEquals("whitelisted ${} scenario must not carry the risk flag", "", rows.get(1).risk());

        assertEquals("UNSUPPORTED", rows.get(2).risk());
        assertTrue(rows.get(2).boundSql().contains("UNSUPPORTED"));
    }

    @Test
    public void loadErrorRendersSingleUnsupportedRow() {
        List<ScenarioMatrixModel.Row> rows = ScenarioMatrixModel.rows(
                "{\"loadError\":\"UNSUPPORTED: Mapper 加载失败\",\"scenarios\":[]}");
        assertEquals(1, rows.size());
        assertEquals("UNSUPPORTED", rows.get(0).risk());
    }

    @Test
    public void malformedPlanRendersNothingAndTableDataMatchesColumns() {
        assertTrue(ScenarioMatrixModel.rows("not-json").isEmpty());
        Object[][] data = ScenarioMatrixModel.tableData(ScenarioMatrixModel.rows(PLAN));
        assertEquals(3, data.length);
        assertEquals(ScenarioMatrixModel.COLUMNS.length, data[0].length);
    }
}
