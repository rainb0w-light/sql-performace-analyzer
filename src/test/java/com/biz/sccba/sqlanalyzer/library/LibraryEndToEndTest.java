package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.analysis.StatementAnalysisService;
import contracttest.AnalysisTestConfig;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library management system end-to-end (docs/cloud-code-next-goal.md §4.6/§5.6, Docker-free,
 * runs on every build against the H2 management database):
 * mapper upload → server-side auto-load of knowledge/profile/index/shard → official BoundSql
 * scenario matrix (≤20, fingerprint-deduped) → schema-validated standard report → persistence →
 * recommendation projection → AG-UI spa.report_ready/spa.recommendations_ready events.
 * Plus the negative proofs: a tenant WITHOUT published knowledge gets structurally different
 * scenarios, and no tenant can read another tenant's report.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryEndToEndTest {

    static ConfigurableApplicationContext ctx;

    static final String CLIENT_A = "client_library_e2e_a";
    static final String CLIENT_B = "client_library_e2e_b";

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
                "contract.jdbc-url", "jdbc:h2:mem:library_e2e;DB_CLOSE_DELAY=-1",
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
        clients.create(CLIENT_A, "Library A", "TEST", null);
        clients.create(CLIENT_B, "Library B", "TEST", null);

        // Publish library business knowledge for client A only.
        var imports = ctx.getBean(KnowledgeImportService.class);
        var preview = imports.importExcel(CLIENT_A, "图书业务知识", "library-knowledge.xlsx",
                LibraryWorkbookFixtures.libraryKnowledge());
        imports.publish(CLIENT_A, preview.versionId(), "alice");

        // Index + shard metadata for client A.
        var metadata = ctx.getBean(MetadataService.class);
        metadata.upsertIndex(CLIENT_A, new IndexDef(null, CLIENT_A, "library_db", "public", "loan",
                "idx_loan_member_status_due", "NORMAL",
                "[{\"column\":\"member_id\",\"direction\":\"ASC\"},{\"column\":\"status\",\"direction\":\"ASC\"},{\"column\":\"due_at\",\"direction\":\"ASC\"}]",
                1000L, 10L, "MANUAL", "alice", null, 1, null, null, null));
        metadata.upsertShard(CLIENT_A, new ShardDef(null, CLIENT_A, "library_db", "loan", "loan_{0..15}",
                "member_id", "borrowed_at", "hash", "member_id % 16, monthly(borrowed_at)", "{}",
                "MANUAL", "alice", null, 1, null, null));

        // A completed profile snapshot for client A (values from expected-profile.json).
        seedProfileSnapshot();
    }

    @AfterAll
    void stop() {
        if (ctx != null) ctx.close();
    }

    @BeforeEach
    void beans() {
        // nothing — state is seeded once per class
    }

    private void seedProfileSnapshot() {
        var profiling = ctx.getBean(ProfilingRepository.class);
        DatasourceProfile profile = profiling.createProfile(new DatasourceProfile(
                "dsp_e2e_" + UUID.randomUUID(), CLIENT_A, "library-target", "MYSQL",
                "jdbc:mysql://library-target:3306/library", "ro", "LIBRARY_DB_PASSWORD", true, null));
        String jobId = "pjob_e2e_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, CLIENT_A, profile.id(), "{}");
        var snapshot = profiling.createSnapshot("snap_e2e_" + UUID.randomUUID(), jobId, profile.id(), "{}");
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

    private StatementAnalysisService.AnalysisResult analyze(String clientId, String statementId, byte[] mapper) {
        String sessionId = sessions.create("session_" + UUID.randomUUID(), clientId, "e2e").id();
        String runId = "run_" + UUID.randomUUID();
        runs.create(runId, sessionId, "deterministic-analysis");
        return analysis.analyze(clientId, runId, sessionId, "library", mapper,
                "fixtures/library/mapper/LoanMapper.xml", statementId, null, null, List.of(), 20);
    }

    @Test
    void knowledgeAndEvidenceDriveScenarioMatrixAndReport() throws Exception {
        var result = analyze(CLIENT_A, "findOverdueLoans", loanMapper);
        JsonNode report = json.readTree(result.report().reportJson());

        // Standard schema fields (validated by the service before persistence).
        assertEquals("1.1", report.path("schemaVersion").asText());
        assertEquals("findOverdueLoans", report.at("/subject/statementId").asText());
        assertEquals("图书业务知识@1", report.at("/audit/knowledgeVersion").asText(),
                "report must reference the published knowledge version");
        assertEquals(snapshotId, report.at("/audit/profileSnapshotId").asText(),
                "report must reference the profile snapshot");
        assertNotNull(report.at("/summary/severity").asText());

        // Scenario matrix: shard + foreach coverage, deduped, capped, per-scenario traceability.
        JsonNode scenarios = report.path("scenarios");
        assertTrue(scenarios.size() > 0 && scenarios.size() <= 20, "scenario count within cap: " + scenarios.size());
        Set<String> fingerprints = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (JsonNode s : scenarios) {
            fingerprints.add(s.path("sqlFingerprint").asText());
            names.add(s.path("name").asText());
            assertEquals("图书业务知识@1", s.path("knowledgeVersion").asText(),
                    "every scenario must carry knowledgeVersion");
            assertEquals(snapshotId, s.path("profileSnapshotId").asText(),
                    "every scenario must carry profileSnapshotId");
            assertTrue(s.path("evidenceIds").isArray() && s.path("evidenceIds").size() > 0,
                    "every scenario must carry evidenceIds");
            assertFalse(s.path("reason").asText().isBlank(), "every scenario must carry a reason");
            assertFalse(s.path("boundSql").asText().isBlank());
        }
        assertEquals(fingerprints.size(), scenarios.size(), "fingerprints must be deduped");

        // Coverage goals are planning semantics, not evidence references. A goal whose SQL shape
        // equals another scenario's is merged into that scenario's dedicated coverageGoals.
        Set<String> coverageGoals = new HashSet<>();
        for (JsonNode s : scenarios) {
            for (JsonNode goal : s.path("coverageGoals")) coverageGoals.add(goal.asText());
        }
        assertTrue(coverageGoals.contains("SHARD_SINGLE"), "single-shard coverage: " + coverageGoals);
        assertTrue(coverageGoals.contains("SHARD_CROSS"), "cross-shard coverage: " + coverageGoals);
        assertTrue(coverageGoals.contains("SHARD_SECONDARY_MISSING"), "secondary shard coverage: " + coverageGoals);
        assertTrue(coverageGoals.stream().anyMatch(g -> g.startsWith("FOREACH_EMPTY")), "foreach empty: " + coverageGoals);
        assertTrue(coverageGoals.stream().anyMatch(g -> g.startsWith("FOREACH_SINGLE")), "foreach single: " + coverageGoals);
        assertTrue(coverageGoals.stream().anyMatch(g -> g.startsWith("FOREACH_MULTI")), "foreach multi: " + coverageGoals);
        assertTrue(names.stream().anyMatch(n -> n.contains("跨分片")), "cross-shard scenario: " + names);
        assertTrue(names.stream().anyMatch(n -> n.contains("二级分片")), "secondary shard scenario: " + names);

        // The scenario carrying SHARD_SINGLE filters by the shard key; the one carrying
        // SHARD_CROSS omits it.
        boolean singleHasShardKey = false, crossLacksShardKey = false;
        for (JsonNode s : scenarios) {
            String sql = s.path("boundSql").asText();
            Set<String> goals = new HashSet<>();
            for (JsonNode e : s.path("coverageGoals")) goals.add(e.asText());
            if (goals.contains("SHARD_SINGLE") && sql.contains("member_id = ?")) singleHasShardKey = true;
            if (goals.contains("SHARD_CROSS") && !sql.contains("member_id = ?")) crossLacksShardKey = true;
        }
        assertTrue(singleHasShardKey, "single-shard BoundSql must filter by member_id");
        assertTrue(crossLacksShardKey, "cross-shard BoundSql must omit the member_id filter");

        // Risks and recommendations derived from the evidence.
        assertTrue(report.path("risks").size() > 0, "risks must be derived");
        boolean crossShardRisk = false;
        for (JsonNode r : report.path("risks")) {
            if ("CROSS_SHARD".equals(r.path("type").asText())) crossShardRisk = true;
        }
        assertTrue(crossShardRisk, "CROSS_SHARD risk must be present");
        assertTrue(result.recommendationCount() > 0, "recommendations must be projected");
        assertTrue(report.path("businessSemantics").size() > 0, "business semantics evidence required");
        assertTrue(report.path("schemaMetadata").size() > 0, "index/shard evidence required");

        // Persisted and retrievable, tenant scoped.
        String runId = result.report().runId();
        var persisted = reports.findLatestByRun(CLIENT_A, runId).orElseThrow();
        assertEquals(result.report().reportJson(), persisted.reportJson());
        assertTrue(persisted.markdown().contains("跨分片"), "markdown projection must render risks");
        assertTrue(persisted.markdown().contains("findOverdueLoans"));

        // AG-UI custom events persisted on the run.
        var runEvents = events.after(CLIENT_A, runId, 0);
        assertTrue(runEvents.stream().anyMatch(e -> "CUSTOM".equals(e.type())
                        && e.payloadJson().contains("spa.report_ready")
                        && e.payloadJson().contains(result.report().id())),
                "spa.report_ready event must be persisted");
        assertTrue(runEvents.stream().anyMatch(e -> "CUSTOM".equals(e.type())
                        && e.payloadJson().contains("spa.recommendations_ready")),
                "spa.recommendations_ready event must be persisted");
    }

    @Test
    void withoutSemanticsScenariosDifferAndTenantsAreIsolated() throws Exception {
        var withKnowledge = analyze(CLIENT_A, "findOverdueLoans", loanMapper);
        var withoutKnowledge = analyze(CLIENT_B, "findOverdueLoans", loanMapper);

        JsonNode reportB = json.readTree(withoutKnowledge.report().reportJson());
        assertTrue(reportB.at("/audit/knowledgeVersion").isNull()
                        || reportB.at("/audit/knowledgeVersion").asText().isBlank(),
                "client B has no published knowledge");
        assertTrue(reportB.at("/audit/profileSnapshotId").isNull()
                        || reportB.at("/audit/profileSnapshotId").asText().isBlank(),
                "client B has no profile snapshot");

        Set<String> namesB = new HashSet<>();
        boolean crossShardInB = false;
        for (JsonNode s : reportB.path("scenarios")) {
            namesB.add(s.path("name").asText());
            if (s.path("name").asText().contains("跨分片") || s.path("name").asText().contains("二级分片")) {
                crossShardInB = true;
            }
        }
        assertFalse(crossShardInB, "without shard metadata there must be no shard scenarios: " + namesB);

        Set<String> namesA = new HashSet<>();
        for (JsonNode s : json.readTree(withKnowledge.report().reportJson()).path("scenarios")) {
            namesA.add(s.path("name").asText());
        }
        assertFalse(namesA.equals(namesB),
                "semantic vs non-semantic scenario sets must differ\nA=" + namesA + "\nB=" + namesB);

        // Tenant isolation of reports.
        assertTrue(reports.findById(CLIENT_B, withKnowledge.report().id()).isEmpty(),
                "client B must not read client A's report by id");
        assertTrue(reports.findLatestByRun(CLIENT_B, withKnowledge.report().runId()).isEmpty(),
                "client B must not read client A's report by run");
        assertTrue(events.after(CLIENT_B, withKnowledge.report().runId(), 0).isEmpty(),
                "client B must not read client A's run events");
    }

    @Test
    void analysisIsDeterministicForIdenticalInputs() throws Exception {
        var first = analyze(CLIENT_A, "findOverdueLoans", loanMapper);
        var second = analyze(CLIENT_A, "findOverdueLoans", loanMapper);
        assertEquals(fingerprintsOf(first), fingerprintsOf(second),
                "identical inputs must yield an identical scenario fingerprint set");
    }

    @Test
    void reportSampleIsGenerated() throws Exception {
        var result = analyze(CLIENT_A, "findOverdueLoans", loanMapper);
        java.nio.file.Path dir = java.nio.file.Path.of("build/library-report-sample");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("library-findOverdueLoans-report.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(result.report().reportJson())));
        java.nio.file.Files.writeString(dir.resolve("library-findOverdueLoans-report.md"), result.report().markdown());
        assertTrue(java.nio.file.Files.size(dir.resolve("library-findOverdueLoans-report.json")) > 0);
        assertTrue(java.nio.file.Files.size(dir.resolve("library-findOverdueLoans-report.md")) > 0);
    }

    private Set<String> fingerprintsOf(StatementAnalysisService.AnalysisResult result) throws Exception {
        Set<String> out = new java.util.TreeSet<>();
        for (JsonNode s : json.readTree(result.report().reportJson()).path("scenarios")) {
            out.add(s.path("name").asText() + "|" + s.path("sqlFingerprint").asText());
        }
        return out;
    }
}
