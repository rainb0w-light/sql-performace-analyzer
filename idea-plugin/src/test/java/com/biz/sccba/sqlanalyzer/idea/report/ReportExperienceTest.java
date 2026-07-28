package com.biz.sccba.sqlanalyzer.idea.report;

import com.biz.sccba.sqlanalyzer.idea.history.BoundedReportCache;
import com.biz.sccba.sqlanalyzer.idea.navigation.ReportNavigation;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReportExperienceTest {
    private static final String REPORT = """
            {
              "reportId":"report_1",
              "subject":{"statementId":"find","contentHash":"hash_1"},
              "contextFingerprint":{"datasourceProfileId":"dsp_1"},
              "summary":{"severity":"HIGH","headline":"跨分片扫描","confidence":0.91},
              "risks":[{"riskId":"risk_1","type":"CROSS_SHARD","title":"跨分片","scenarioIds":["scn_1"],"evidenceIds":["ev_1"]}],
              "scenarios":[{"scenarioId":"scn_1","name":"主路径","parameterSource":"USER","boundSql":"SELECT ?","sqlFingerprint":"fp","coverageGoals":["MAIN_PATH"],"evidenceIds":["ev_1"]}],
              "evidenceCatalog":[{"evidenceId":"ev_1","sourceType":"EXPLAIN","sourceId":"plan","version":"1","locator":"plan/1","confidence":0.9}],
              "executionPlans":[{"scenarioId":"scn_1","plan":{"type":"range"}}],
              "recommendations":[{"recommendationId":"rec_1","title":"候选索引","priority":"HIGH","confidence":0.8,"problem":"索引不覆盖","evidenceIds":["ev_1"]}],
              "limits":{"notes":["只读分析"]},
              "audit":{"knowledgeVersion":"k1","profileSnapshotId":"snap1"}
            }
            """;

    @Test
    public void reportProjectsStableNavigationAndRealExplainAvailability() {
        ReportViewModel model = ReportViewModel.parse(REPORT);
        assertEquals("risk_1", model.risks().get(0).riskId());
        assertEquals("scn_1", model.risks().get(0).scenarioIds().get(0));
        assertEquals("ev_1", model.evidence().get(0).evidenceId());
        assertTrue(model.scenarios().get(0).hasExplainEvidence());

        ReportNavigation navigation = new ReportNavigation();
        navigation.go(new ReportNavigation.Target(ReportNavigation.TargetType.RISK, "risk_1", ""));
        navigation.go(new ReportNavigation.Target(ReportNavigation.TargetType.SCENARIO, "scn_1", ""));
        navigation.go(new ReportNavigation.Target(ReportNavigation.TargetType.EVIDENCE, "ev_1", "plan/1"));
        assertEquals("ev_1", navigation.current().stableId());
        assertEquals("scn_1", navigation.back().stableId());
        assertEquals("risk_1", navigation.back().stableId());
    }

    @Test
    public void fullFingerprintChangeMarksReportStale() {
        ContextFingerprint report = ContextFingerprint.fromReport(REPORT);
        assertFalse(report.staleComparedWith(new ContextFingerprint("hash_1", "find", "dsp_1", "k1", "snap1")));
        assertTrue(report.staleComparedWith(new ContextFingerprint("hash_2", "find", "dsp_1", "k1", "snap1")));
        assertTrue(report.staleComparedWith(new ContextFingerprint("hash_1", "find", "dsp_2", "k1", "snap1")));
        assertTrue(report.staleComparedWith(new ContextFingerprint("hash_1", "find", "dsp_1", "k2", "snap1")));
        assertTrue(report.staleComparedWith(new ContextFingerprint("hash_1", "find", "dsp_1", "k1", "snap2")));
    }

    @Test
    public void exportsOnlyMarkdownAndStandardJson() {
        String markdown = ReportExportService.export(REPORT, ReportExportService.Format.MARKDOWN);
        assertTrue(markdown.contains("# SQL Performance Analysis Report"));
        assertTrue(markdown.contains("scn_1"));
        String json = ReportExportService.export(REPORT, ReportExportService.Format.STANDARD_JSON);
        assertTrue(json.contains("\"reportId\": \"report_1\""));
        assertEquals(2, ReportExportService.Format.values().length);
    }

    @Test
    public void localCacheIsBoundedAndClearIsLocal() {
        BoundedReportCache cache = new BoundedReportCache(2, 4096);
        cache.put("r1", "{\"v\":1}");
        cache.put("r2", "{\"v\":2}");
        cache.put("r3", "{\"v\":3}");
        assertEquals(2, cache.size());
        assertNull(cache.get("r1"));
        cache.clear();
        assertEquals(0, cache.size());
    }
}
