package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginReportHistoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Standard report retrieval (docs/contracts/rest-api.md §1): JSON by default,
 * {@code Accept: text/markdown} returns the persisted Markdown projection. All reads are
 * tenant scoped via the authenticated clientId.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ReportController {

    private static final MediaType TEXT_MARKDOWN = MediaType.parseMediaType("text/markdown");

    private final AnalysisReportRepository reports;
    private final AgentRunRepository runs;
    private final BearerClients bearer;
    private final PluginReportHistoryService history;

    public ReportController(AnalysisReportRepository reports, AgentRunRepository runs,
                            PluginReportHistoryService history, BearerClients bearer) {
        this.reports = reports;
        this.runs = runs;
        this.history = history;
        this.bearer = bearer;
    }

    @GetMapping("/reports")
    public PluginReportHistoryService.HistoryPage reports(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String statement,
            @RequestParam(required = false) String datasourceProfileId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String completedFrom,
            @RequestParam(required = false) String completedTo,
            @RequestParam(required = false) Boolean stale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var filter = new PluginReportHistoryService.Filter(projectId, moduleId, statement,
                datasourceProfileId, severity,
                PluginReportHistoryService.instant(completedFrom, "completedFrom"),
                PluginReportHistoryService.instant(completedTo, "completedTo"),
                stale, page, size);
        return history.search(bearer.clientId(authorization), filter);
    }

    @GetMapping("/runs/{runId}/report")
    public ResponseEntity<?> reportByRun(@RequestHeader("Authorization") String authorization,
                                         @RequestHeader(value = "Accept", required = false) String accept,
                                         @PathVariable String runId) {
        String clientId = bearer.clientId(authorization);
        if (!runs.belongsToClient(runId, clientId)) {
            throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
        }
        return reports.findLatestByRun(clientId, runId)
                .map(report -> respond(report, accept))
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<?> reportById(@RequestHeader("Authorization") String authorization,
                                        @RequestHeader(value = "Accept", required = false) String accept,
                                        @PathVariable String reportId) {
        String clientId = bearer.clientId(authorization);
        return reports.findById(clientId, reportId)
                .map(report -> respond(report, accept))
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }

    private ResponseEntity<?> respond(AnalysisReportRepository.Report report, String accept) {
        if (accept != null && accept.contains("text/markdown")) {
            return ResponseEntity.ok()
                    .contentType(TEXT_MARKDOWN)
                    .body(report.markdown() == null ? "" : report.markdown());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(report.reportJson());
    }
}
