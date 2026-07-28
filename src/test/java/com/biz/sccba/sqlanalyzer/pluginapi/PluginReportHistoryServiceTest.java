package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginReportHistoryServiceTest {

    @Test
    void filtersServerSideAndPaginatesAfterJsonFields() {
        Instant now = Instant.parse("2026-07-28T10:00:00Z");
        MemoryReports reports = new MemoryReports(List.of(
                report("r1", "p1", "m1", "findLoan", "ds1", "HIGH", false, now),
                report("r2", "p1", "m1", "findLoan", "ds1", "HIGH", true, now.minusSeconds(1)),
                report("r3", "p2", "m2", "other", "ds2", "LOW", false, now.minusSeconds(2))));
        var service = new PluginReportHistoryService(reports, new ObjectMapper());
        var filter = new PluginReportHistoryService.Filter("p1", "m1", "find",
                "ds1", "HIGH", now.minusSeconds(60), now.plusSeconds(60),
                null, 1, 1);

        var page = service.search("client", filter);

        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals("r2", page.items().get(0).reportId());
        var stale = service.search("client", new PluginReportHistoryService.Filter(
                "p1", null, null, null, null, null, null, true, 0, 10));
        assertEquals(List.of("r2"), stale.items().stream()
                .map(PluginReportHistoryService.HistoryItem::reportId).toList());
    }

    private static AnalysisReportRepository.Report report(
            String id, String project, String module, String statement,
            String datasource, String severity, boolean stale, Instant created) {
        String json = """
                {"subject":{"projectId":"%s","moduleId":"%s","namespace":"demo.M",
                "statementId":"%s","contentHash":"hash"},
                "audit":{"datasourceProfileId":"%s","knowledgeVersion":"kb@1",
                "profileSnapshotId":"snap","contextFingerprint":"fp","stale":%s}}
                """.formatted(project, module, statement, datasource, stale);
        return new AnalysisReportRepository.Report(id, "client", "run_" + id, "session",
                "demo.M", statement, "1.1", severity, json, "", created);
    }

    private static final class MemoryReports implements AnalysisReportRepository {
        private final List<Report> reports;

        private MemoryReports(List<Report> reports) {
            this.reports = new ArrayList<>(reports);
        }

        @Override public void save(Report report) { reports.add(report); }
        @Override public Optional<Report> findById(String clientId, String reportId) {
            return reports.stream().filter(item -> item.id().equals(reportId)).findFirst();
        }
        @Override public Optional<Report> findLatestByRun(String clientId, String runId) {
            return reports.stream().filter(item -> runId.equals(item.runId())).findFirst();
        }
        @Override public List<Report> listForClient(String clientId, int limit) {
            return reports.stream().limit(limit).toList();
        }
        @Override public List<Report> listForClientPage(String clientId, int offset, int limit) {
            if (offset >= reports.size()) return List.of();
            return reports.subList(offset, Math.min(reports.size(), offset + limit));
        }
    }
}
