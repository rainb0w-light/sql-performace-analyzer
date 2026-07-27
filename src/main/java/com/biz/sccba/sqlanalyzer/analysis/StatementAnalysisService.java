package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.service.RecommendationProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End-to-end statement analysis (docs/cloud-code-next-goal.md §5.6): server-side context
 * resolution → official BoundSql scenario matrix → deterministic evidence-based report →
 * schema validation → persistence → recommendation projection → AG-UI custom events.
 * The client contributes nothing trusted beyond mapper, statementId and optional samples.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class StatementAnalysisService {

    private final StatementReferenceResolver references;
    private final ScenarioContextResolver contextResolver;
    private final ScenarioEngine scenarioEngine;
    private final ReportAssembler assembler;
    private final ReportSchemaValidator validator;
    private final MarkdownReportRenderer renderer;
    private final AnalysisReportRepository reports;
    private final RecommendationProjector recommendationProjector;
    private final RunEventRepository events;
    private final ObjectMapper objectMapper;

    public StatementAnalysisService(StatementReferenceResolver references, ScenarioContextResolver contextResolver,
                                    ScenarioEngine scenarioEngine, ReportAssembler assembler,
                                    ReportSchemaValidator validator, MarkdownReportRenderer renderer,
                                    AnalysisReportRepository reports, RecommendationProjector recommendationProjector,
                                    RunEventRepository events, ObjectMapper objectMapper) {
        this.references = references;
        this.contextResolver = contextResolver;
        this.scenarioEngine = scenarioEngine;
        this.assembler = assembler;
        this.validator = validator;
        this.renderer = renderer;
        this.reports = reports;
        this.recommendationProjector = recommendationProjector;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public record AnalysisResult(AnalysisReportRepository.Report report, int recommendationCount) {}

    public interface ProgressListener {
        ProgressListener NOOP = new ProgressListener() {};

        default void scenariosReady(ScenarioEngine.PlanResult plan) {}
        default void assemblingReport() {}
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public AnalysisResult analyze(String clientId, String runId, String sessionId, String projectId,
                                  byte[] mapperXml, String mapperPath, String statementId,
                                  String mybatisConfigXml, String databaseId,
                                  List<Map<String, Object>> userSamples, int maxScenarios) {
        String reportId = "report_" + UUID.randomUUID();
        AnalysisResult result = analyzeWithReportId(clientId, runId, sessionId, projectId, mapperXml,
                mapperPath, statementId, mybatisConfigXml, databaseId, userSamples, maxScenarios,
                reportId, ProgressListener.NOOP);
        emitEvents(runId, reportId, result.recommendationCount());
        return result;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public AnalysisResult analyzeWithReportId(String clientId, String runId, String sessionId, String projectId,
                                              byte[] mapperXml, String mapperPath, String statementId,
                                              String mybatisConfigXml, String databaseId,
                                              List<Map<String, Object>> userSamples, int maxScenarios,
                                              String reportId, ProgressListener progress) {
        return analyzeWithReportId(clientId, runId, sessionId, projectId, mapperXml, mapperPath,
                statementId, mybatisConfigXml, databaseId, userSamples, maxScenarios, null,
                "public", reportId, progress);
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public AnalysisResult analyzeWithReportId(String clientId, String runId, String sessionId, String projectId,
                                              byte[] mapperXml, String mapperPath, String statementId,
                                              String mybatisConfigXml, String databaseId,
                                              List<Map<String, Object>> userSamples, int maxScenarios,
                                              String datasourceProfileId, String schemaName,
                                              String reportId, ProgressListener progress) {
        var refs = references.resolve(mapperXml, mapperPath, statementId, mybatisConfigXml, databaseId);
        var bundle = contextResolver.resolve(clientId, datasourceProfileId, schemaName,
                refs, userSamples, maxScenarios);
        var plan = scenarioEngine.plan(mapperXml, mapperPath, statementId, bundle.plannerInput(),
                mybatisConfigXml, databaseId);
        if (plan.loadError() != null) {
            throw new IllegalArgumentException("Mapper 无法解析：" + plan.loadError());
        }
        ProgressListener listener = progress == null ? ProgressListener.NOOP : progress;
        listener.scenariosReady(plan);
        listener.assemblingReport();

        String reportJson = assembler.assemble(reportId,
                new ReportAssembler.Subject(projectId == null ? "default" : projectId, null, mapperPath,
                        refs.namespace(), statementId, refs.statementType(), null, null),
                new ReportAssembler.Audit(runId, sessionId, "deterministic-analysis"),
                plan, bundle, mapperXml);
        validator.validate(reportJson);
        String markdown = renderer.render(reportJson);

        String severity = readSeverity(reportJson);
        var report = new AnalysisReportRepository.Report(reportId, clientId, runId, sessionId,
                refs.namespace(), statementId, "1.1", severity, reportJson, markdown, Instant.now());
        reports.save(report);

        int recommendations = 0;
        try {
            recommendations = recommendationProjector.project(runId, sessionId, reportJson);
        } catch (Exception e) {
            throw new IllegalStateException("建议投影失败：" + e.getMessage(), e);
        }

        return new AnalysisResult(report, recommendations);
    }

    private void emitEvents(String runId, String reportId, int recommendationCount) {
        try {
            events.append(runId, "CUSTOM", objectMapper.writeValueAsString(Map.of(
                    "name", "spa.report_ready", "reportId", reportId)));
            events.append(runId, "CUSTOM", objectMapper.writeValueAsString(Map.of(
                    "name", "spa.recommendations_ready", "reportId", reportId,
                    "count", recommendationCount)));
        } catch (Exception e) {
            throw new IllegalStateException("AG-UI 事件持久化失败：" + e.getMessage(), e);
        }
    }

    private String readSeverity(String reportJson) {
        try {
            return objectMapper.readTree(reportJson).path("summary").path("severity").asText("INFO");
        } catch (Exception e) {
            return "INFO";
        }
    }
}
