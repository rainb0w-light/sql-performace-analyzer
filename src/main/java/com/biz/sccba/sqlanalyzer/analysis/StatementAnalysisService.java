package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.service.RecommendationProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ExecutionPlanCollector executionPlanCollector;
    private final StatementAgentEnhancer agentEnhancer;
    private final TransactionTemplate transactionTemplate;

    public StatementAnalysisService(StatementReferenceResolver references, ScenarioContextResolver contextResolver,
                                    ScenarioEngine scenarioEngine, ReportAssembler assembler,
                                    ReportSchemaValidator validator, MarkdownReportRenderer renderer,
                                    AnalysisReportRepository reports, RecommendationProjector recommendationProjector,
                                    RunEventRepository events, ObjectMapper objectMapper,
                                    ExecutionPlanCollector executionPlanCollector,
                                    StatementAgentEnhancer agentEnhancer,
                                    @Qualifier("managementTransactionManager")
                                    PlatformTransactionManager transactionManager) {
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
        this.executionPlanCollector = executionPlanCollector;
        this.agentEnhancer = agentEnhancer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record AnalysisResult(AnalysisReportRepository.Report report, int recommendationCount) {}

    public interface ProgressListener {
        ProgressListener NOOP = new ProgressListener() {};

        default void scenariosReady(ScenarioEngine.PlanResult plan) {}
        default void collectingExecutionPlans() {}
        default void assemblingReport() {}
        default void enhancingWithAgent() {}
    }

    public AnalysisResult analyzePrepared(
            String clientId, String runId, String sessionId, String reportId,
            String projectId, String moduleId, String datasourceProfileId,
            String contextFingerprint, byte[] mapperXml, String mapperPath,
            StatementReferenceResolver.References refs, ScenarioEngine.PlanResult plan,
            ScenarioContextResolver.ContextBundle bundle, ProgressListener progress) {
        return completeAnalysis(clientId, runId, sessionId, reportId, projectId, moduleId,
                datasourceProfileId, contextFingerprint, mapperXml, mapperPath,
                refs, plan, bundle, progress);
    }

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

    public AnalysisResult analyzeWithReportId(String clientId, String runId, String sessionId, String projectId,
                                              byte[] mapperXml, String mapperPath, String statementId,
                                              String mybatisConfigXml, String databaseId,
                                              List<Map<String, Object>> userSamples, int maxScenarios,
                                              String reportId, ProgressListener progress) {
        return analyzeWithReportId(clientId, runId, sessionId, projectId, mapperXml, mapperPath,
                statementId, mybatisConfigXml, databaseId, userSamples, maxScenarios, null,
                "public", reportId, progress);
    }

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
        return completeAnalysis(clientId, runId, sessionId, reportId, projectId, null,
                datasourceProfileId, null, mapperXml, mapperPath, refs, plan, bundle, progress);
    }

    private AnalysisResult completeAnalysis(
            String clientId, String runId, String sessionId, String reportId,
            String projectId, String moduleId, String datasourceProfileId,
            String contextFingerprint, byte[] mapperXml, String mapperPath,
            StatementReferenceResolver.References refs, ScenarioEngine.PlanResult plan,
            ScenarioContextResolver.ContextBundle bundle, ProgressListener progress) {
        if (plan.loadError() != null) {
            throw new IllegalArgumentException("Mapper 无法解析：" + plan.loadError());
        }
        ProgressListener listener = progress == null ? ProgressListener.NOOP : progress;
        listener.scenariosReady(plan);
        listener.collectingExecutionPlans();
        var executionPlans = executionPlanCollector.collect(clientId, datasourceProfileId,
                refs.statementType(), plan);
        listener.assemblingReport();

        String deterministicReport = assembler.assemble(reportId,
                new ReportAssembler.Subject(projectId == null ? "default" : projectId,
                        moduleId, mapperPath, refs.namespace(), refs.statementId(),
                        refs.statementType(), null, null),
                new ReportAssembler.Audit(runId, sessionId, "deterministic-analysis",
                        datasourceProfileId, contextFingerprint, true),
                plan, bundle, mapperXml, executionPlans);
        validator.validate(deterministicReport);
        if (agentEnhancer.enabled()) listener.enhancingWithAgent();
        String reportJson = agentEnhancer.enhance(clientId, sessionId, runId,
                datasourceProfileId, deterministicReport);
        validator.validate(reportJson);
        String markdown = renderer.render(reportJson);

        String severity = readSeverity(reportJson);
        var report = new AnalysisReportRepository.Report(reportId, clientId, runId, sessionId,
                refs.namespace(), refs.statementId(), "1.1", severity,
                reportJson, markdown, Instant.now());
        Integer recommendations = transactionTemplate.execute(status -> {
            reports.save(report);
            try {
                return recommendationProjector.project(runId, sessionId, reportJson);
            } catch (Exception e) {
                throw new IllegalStateException("建议投影失败：" + e.getMessage(), e);
            }
        });
        return new AnalysisResult(report, recommendations == null ? 0 : recommendations);
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
