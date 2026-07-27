package com.biz.sccba.sqlanalyzer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Standard analysis report persistence (docs/cloud-code-next-goal.md §完成定义): reports are
 * stored only after passing report-schema.json validation; every read is tenant scoped.
 */
public interface AnalysisReportRepository {

    void save(Report report);

    Optional<Report> findById(String clientId, String reportId);

    /** Latest report of a run, tenant scoped. */
    Optional<Report> findLatestByRun(String clientId, String runId);

    List<Report> listForClient(String clientId, int limit);

    record Report(String id, String clientId, String runId, String sessionId, String namespace,
                  String statementId, String schemaVersion, String severity, String reportJson,
                  String markdown, Instant createdAt) {}
}
