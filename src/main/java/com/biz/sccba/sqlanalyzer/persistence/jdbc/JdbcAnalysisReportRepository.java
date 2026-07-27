package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AnalysisReportEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.AnalysisReportJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcAnalysisReportRepository implements AnalysisReportRepository {

    private final AnalysisReportJdbcRepository jdbc;

    public JdbcAnalysisReportRepository(AnalysisReportJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Report report) {
        AnalysisReportEntity entity = new AnalysisReportEntity();
        entity.setId(report.id());
        entity.setClientId(report.clientId());
        entity.setRunId(report.runId());
        entity.setSessionId(report.sessionId());
        entity.setNamespace(report.namespace());
        entity.setStatementId(report.statementId());
        entity.setSchemaVersion(report.schemaVersion());
        entity.setSeverity(report.severity());
        entity.setReportJson(report.reportJson());
        entity.setMarkdown(report.markdown());
        entity.setCreatedAt(report.createdAt());
        entity.markNew();
        jdbc.save(entity);
    }

    @Override
    public Optional<Report> findById(String clientId, String reportId) {
        return jdbc.findByIdForClient(clientId, reportId).map(JdbcAnalysisReportRepository::toDomain);
    }

    @Override
    public Optional<Report> findLatestByRun(String clientId, String runId) {
        return jdbc.findByRunForClient(clientId, runId).stream().findFirst().map(JdbcAnalysisReportRepository::toDomain);
    }

    @Override
    public List<Report> listForClient(String clientId, int limit) {
        return jdbc.findAllForClient(clientId, Math.max(1, limit)).stream().map(JdbcAnalysisReportRepository::toDomain).toList();
    }

    private static Report toDomain(AnalysisReportEntity e) {
        return new Report(e.getId(), e.getClientId(), e.getRunId(), e.getSessionId(), e.getNamespace(),
                e.getStatementId(), e.getSchemaVersion(), e.getSeverity(), e.getReportJson(),
                e.getMarkdown(), e.getCreatedAt());
    }
}
