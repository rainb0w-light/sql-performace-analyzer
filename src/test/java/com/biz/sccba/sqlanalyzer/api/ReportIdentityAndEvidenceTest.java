package com.biz.sccba.sqlanalyzer.api;

import com.biz.sccba.sqlanalyzer.analysis.StatementAnalysisService;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import com.biz.sccba.sqlanalyzer.library.LibraryWorkbookFixtures;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import contracttest.AnalysisTestConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 contract freeze (docs/claude-code-remediation-goal.md §6):
 * reportId must be generated ONCE and be identical across JSON body, database primary key,
 * REST response, spa.report_ready event, and spa.recommendations_ready event.
 *
 * <p>Additionally: each scenario must carry separate coverageGoals and evidenceIds fields;
 * evidenceIds must reference entries in a top-level evidenceCatalog; dataDistribution
 * must project real profile metrics (nullRatio, approxDistinct, min/max, Top-K, quantiles).
 *
 * <p>Permanent regression contract for Report Schema 1.1 identity, evidence and distribution.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportIdentityAndEvidenceTest {

    static ConfigurableApplicationContext ctx;

    static final String CLIENT = "client_report_identity";

    StatementAnalysisService analysis;
    AnalysisReportRepository reports;
    RunEventRepository events;
    SessionRepository sessions;
    AgentRunRepository runs;
    ObjectMapper json;

    String snapshotId;
    byte[] loanMapper;

    @BeforeAll
    void startAndSeed() throws Exception {
        MapPropertySource props = new MapPropertySource("contract", Map.of(
                "sql-analyzer.persistence.enabled", "true",
                "contract.jdbc-url", "jdbc:h2:mem:report_identity;DB_CLOSE_DELAY=-1",
                "contract.username", "sa",
                "contract.password", ""));
        ctx = new SpringApplicationBuilder(AnalysisTestConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(c -> ((ConfigurableEnvironment) c.getEnvironment())
                        .getPropertySources().addFirst(props))
                .run();

        analysis = ctx.getBean(StatementAnalysisService.class);
        reports = ctx.getBean(AnalysisReportRepository.class);
        events = ctx.getBean(RunEventRepository.class);
        sessions = ctx.getBean(SessionRepository.class);
        runs = ctx.getBean(AgentRunRepository.class);
        json = ctx.getBean(ObjectMapper.class);

        try (InputStream in = getClass().getResourceAsStream("/fixtures/library/mapper/LoanMapper.xml")) {
            loanMapper = in.readAllBytes();
        }

        var clients = ctx.getBean(ClientRepository.class);
        clients.create(CLIENT, "Report Identity Test", "TEST", null);

        var imports = ctx.getBean(KnowledgeImportService.class);
        var preview = imports.importExcel(CLIENT, "图书业务知识", "library-knowledge.xlsx",
                LibraryWorkbookFixtures.libraryKnowledge());
        imports.publish(CLIENT, preview.versionId(), "alice");

        var metadata = ctx.getBean(MetadataService.class);
        metadata.upsertIndex(CLIENT, new IndexDef(null, CLIENT, "library_db", "public", "loan",
                "idx_loan_member_status_due", "NORMAL",
                "[{\"column\":\"member_id\",\"direction\":\"ASC\"},{\"column\":\"status\",\"direction\":\"ASC\"},{\"column\":\"due_at\",\"direction\":\"ASC\"}]",
                1000L, 10L, "MANUAL", "alice", null, 1, null, null, null));
        metadata.upsertShard(CLIENT, new ShardDef(null, CLIENT, "library_db", "loan", "loan_{0..15}",
                "member_id", "borrowed_at", "hash", "member_id % 16, monthly(borrowed_at)", "{}",
                "MANUAL", "alice", null, 1, null, null));

        seedProfileSnapshot();
    }

    @AfterAll
    void stop() {
        if (ctx != null) ctx.close();
    }

    private void seedProfileSnapshot() {
        var profiling = ctx.getBean(ProfilingRepository.class);
        DatasourceProfile profile = profiling.createProfile(new DatasourceProfile(
                "dsp_ri_" + UUID.randomUUID(), CLIENT, "library-target", "MYSQL",
                "jdbc:mysql://library-target:3306/library", "ro", "LIBRARY_DB_PASSWORD", true, null));
        String jobId = "pjob_ri_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, CLIENT, profile.id(), "{}");
        var snapshot = profiling.createSnapshot("snap_ri_" + UUID.randomUUID(), jobId, profile.id(), "{}");
        snapshotId = snapshot.id();
        Instant now = Instant.now();
        profiling.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshotId, "library", "loan",
                "status", 0.0, 2L, "ACTIVE", "RETURNED",
                "[{\"value\":\"ACTIVE\",\"count\":4},{\"value\":\"RETURNED\",\"count\":2}]", "[]", "[]", now));
        profiling.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshotId, "library", "loan",
                "due_at", 0.0, 6L, "2026-02-15", "2026-08-01", "[]", "[]",
                "[{\"q\":0.25,\"value\":\"2026-03-15\"},{\"q\":0.5,\"value\":\"2026-05-15\"},{\"q\":0.75,\"value\":\"2026-06-15\"}]", now));
        profiling.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshotId, "library", "loan",
                "returned_at", 0.6667, 3L, null, null, "[]", "[]", "[]", now));
        profiling.finishSnapshot(snapshotId, "COMPLETED");
        profiling.completeJob(jobId);
    }

    private StatementAnalysisService.AnalysisResult analyze(String statementId) {
        String sessionId = sessions.create("session_" + UUID.randomUUID(), CLIENT, "report-identity").id();
        String runId = "run_" + UUID.randomUUID();
        runs.create(runId, sessionId, "deterministic-analysis");
        return analysis.analyze(CLIENT, runId, sessionId, "library", loanMapper,
                "fixtures/library/mapper/LoanMapper.xml", statementId, null, null, List.of(), 20);
    }

    /**
     * Goal §6.1: reportId generated once, identical across five surfaces.
     *
     * FAILS because ReportAssembler.assemble():47 generates one UUID for the JSON body,
     * and StatementAnalysisService.analyze():79 generates a different UUID for the DB row.
     */
    @Test
    void reportIdIsIdenticalAcrossJsonDatabaseRestAndEvents() throws Exception {
        var result = analyze("findOverdueLoans");

        // 1. reportId inside the JSON body
        JsonNode reportJson = json.readTree(result.report().reportJson());
        String jsonReportId = reportJson.path("reportId").asText();

        // 2. reportId as database primary key
        String dbReportId = result.report().id();

        // These two MUST be equal — currently they are different UUIDs
        assertEquals(jsonReportId, dbReportId,
                "reportId inside report JSON must equal the database primary key; " +
                "currently ReportAssembler:47 and StatementAnalysisService:79 each generate a separate UUID");

        // 3. reportId in spa.report_ready event
        String runId = result.report().runId();
        var runEvents = events.after(CLIENT, runId, 0);
        String eventReportId = null;
        for (var event : runEvents) {
            JsonNode payload = json.readTree(event.payloadJson());
            if ("spa.report_ready".equals(payload.path("name").asText())) {
                eventReportId = payload.path("reportId").asText();
            }
        }
        assertNotNull(eventReportId, "spa.report_ready event must exist");
        assertEquals(jsonReportId, eventReportId,
                "reportId in spa.report_ready event must equal the JSON reportId");

        // 4. reportId in spa.recommendations_ready event
        String recEventReportId = null;
        for (var event : runEvents) {
            JsonNode payload = json.readTree(event.payloadJson());
            if ("spa.recommendations_ready".equals(payload.path("name").asText())) {
                recEventReportId = payload.path("reportId").asText();
            }
        }
        assertNotNull(recEventReportId, "spa.recommendations_ready event must exist");
        assertEquals(jsonReportId, recEventReportId,
                "reportId in spa.recommendations_ready event must equal the JSON reportId");

        // 5. reportId retrievable via repository using the JSON id
        assertTrue(reports.findById(CLIENT, jsonReportId).isPresent(),
                "report must be retrievable by the reportId found in the JSON body");
    }

    /**
     * Goal §6.2: coverageGoals and evidenceIds are separate; evidenceIds references
     * entries in a top-level evidenceCatalog.
     *
     * FAILS because ReportAssembler.scenarioNode():284 puts mergedCoverageGoals()
     * into evidenceIds, and no evidenceCatalog structure exists.
     */
    @Test
    void scenariosSeparateCoverageGoalsFromEvidenceIds() throws Exception {
        var result = analyze("findOverdueLoans");
        JsonNode report = json.readTree(result.report().reportJson());

        JsonNode scenarios = report.path("scenarios");
        assertTrue(scenarios.size() > 0, "report must contain scenarios");

        for (JsonNode scenario : scenarios) {
            // coverageGoals must be a separate array (may overlap with evidence but is NOT the same field)
            assertTrue(scenario.has("coverageGoals") && scenario.path("coverageGoals").isArray(),
                    "each scenario must have a dedicated coverageGoals array (separate from evidenceIds); " +
                    "currently coverage goals are incorrectly stored inside evidenceIds");

            // evidenceIds must reference real evidence entries
            assertTrue(scenario.has("evidenceIds") && scenario.path("evidenceIds").isArray(),
                    "each scenario must have evidenceIds");

            // Coverage goals like SHARD_SINGLE must NOT appear as evidenceIds
            for (JsonNode eid : scenario.path("evidenceIds")) {
                String id = eid.asText();
                assertFalse(id.startsWith("SHARD_") || id.startsWith("FOREACH_"),
                        "evidenceId '" + id + "' is a coverage goal, not an evidence reference; " +
                        "coverageGoals and evidenceIds must be separate");
            }
        }
    }

    /**
     * Goal §6.2: every evidenceId must be resolvable in a top-level evidenceCatalog.
     *
     * FAILS because no evidenceCatalog exists in the current report structure.
     */
    @Test
    void evidenceCatalogContainsAllReferencedEvidence() throws Exception {
        var result = analyze("findOverdueLoans");
        JsonNode report = json.readTree(result.report().reportJson());

        // The report must have a top-level evidenceCatalog
        assertTrue(report.has("evidenceCatalog") && report.path("evidenceCatalog").isArray(),
                "report must contain a top-level evidenceCatalog array; " +
                "currently no such structure exists");

        // Collect all evidenceIds referenced by scenarios
        java.util.Set<String> referencedIds = new java.util.HashSet<>();
        for (JsonNode scenario : report.path("scenarios")) {
            for (JsonNode eid : scenario.path("evidenceIds")) {
                referencedIds.add(eid.asText());
            }
        }

        // Collect all evidenceIds in the catalog
        java.util.Set<String> catalogIds = new java.util.HashSet<>();
        for (JsonNode entry : report.path("evidenceCatalog")) {
            catalogIds.add(entry.path("evidenceId").asText());
            // Each catalog entry must have the required fields per §6.2
            assertFalse(entry.path("evidenceId").asText().isBlank(), "evidenceId required");
            assertFalse(entry.path("sourceType").asText().isBlank(), "sourceType required");
            assertFalse(entry.path("sourceId").asText().isBlank(), "sourceId required");
            assertTrue(entry.has("version"), "version required");
            assertFalse(entry.path("locator").asText().isBlank(), "locator required");
            assertFalse(entry.path("collectedAt").asText().isBlank(), "collectedAt required");
            assertTrue(entry.has("confidence"), "confidence required");
        }

        // Every referenced evidenceId must be in the catalog
        for (String ref : referencedIds) {
            assertTrue(catalogIds.contains(ref),
                    "evidenceId '" + ref + "' referenced by a scenario must exist in evidenceCatalog");
        }
    }

    /**
     * Goal §6.3: dataDistribution must project real profile metrics from ProfileSnapshot.
     *
     * FAILS because ReportAssembler.appendDistribution() filters COLUMN/TABLE Facts
     * from semanticFacts, but ScenarioContextResolver does not produce these from
     * profile data. The actual profile metrics (nullRatio, approxDistinct, etc.)
     * are only in the PlannerInput, not in the report's dataDistribution.
     */
    @Test
    void dataDistributionProjectsProfileMetrics() throws Exception {
        var result = analyze("findOverdueLoans");
        JsonNode report = json.readTree(result.report().reportJson());

        JsonNode distribution = report.path("dataDistribution");
        assertTrue(distribution.isArray() && distribution.size() > 0,
                "dataDistribution must be a non-empty array from profile snapshot data");

        // Find the distribution entry for the "status" column (seeded with known profile data)
        boolean foundStatusDistribution = false;
        for (JsonNode entry : distribution) {
            String column = entry.path("column").asText("");
            if ("status".equals(column) || entry.toString().contains("status")) {
                foundStatusDistribution = true;

                // Must contain real profile metrics
                assertTrue(entry.has("nullRatio"),
                        "dataDistribution must include nullRatio from ProfileSnapshot");
                assertTrue(entry.has("approxDistinct") || entry.has("approximateDistinct"),
                        "dataDistribution must include approxDistinct from ProfileSnapshot");

                // Top-K values must be present
                assertTrue(entry.has("topK") && entry.path("topK").isArray(),
                        "dataDistribution must include Top-K values from ProfileSnapshot");
                assertTrue(entry.has("buckets") && entry.path("buckets").isArray(),
                        "dataDistribution must include bucket projection");
                assertTrue(entry.has("quantiles") && entry.path("quantiles").isArray(),
                        "dataDistribution must include quantile projection");
                assertTrue(entry.has("min") && entry.has("max"),
                        "dataDistribution must include min/max projection");
                assertEquals("PLAINTEXT", entry.path("sensitivityPolicy").asText(),
                        "dataDistribution must disclose the applied sensitivity policy");
                assertEquals("PROFILE_SNAPSHOT", entry.at("/evidence/sourceType").asText(),
                        "distribution evidence must identify the immutable profile snapshot");
            }
        }
        assertTrue(foundStatusDistribution,
                "dataDistribution must contain an entry for the 'status' column from the profile snapshot; " +
                "currently appendDistribution() only filters COLUMN/TABLE Facts, not profile metrics");
    }
}
