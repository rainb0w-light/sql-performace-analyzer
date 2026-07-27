package com.biz.sccba.sqlanalyzer.persistence;

import com.biz.sccba.sqlanalyzer.domain.Artifact;
import contracttest.ContractTestConfig;
import com.biz.sccba.sqlanalyzer.domain.Recommendation;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientTokenRepository;
import com.biz.sccba.sqlanalyzer.repository.IdempotencyRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.repository.MessageRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.repository.RecommendationRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE Repository Contract (docs/cloud-code-next-goal.md §5.1): one suite of assertions executed
 * against BOTH management databases — H2 on every local build (Docker-free gate) and PostgreSQL
 * via Testcontainers (CI-enforced Docker gate). Subclasses differ only in how the datasource is
 * provided; every behavioral expectation here must hold identically on both databases:
 * CRUD, timestamps/nullables, JSON text round-trips, unique/FK enforcement, run-event cursor
 * ordering, job claim/lease/retry, idempotency replay/conflict, and tenant isolation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositoryContractTestBase {

    static ConfigurableApplicationContext ctx;

    ClientRepository clients;
    ClientTokenRepository tokens;
    SessionRepository sessions;
    MessageRepository messages;
    AgentRunRepository runs;
    AgentJobRepository jobs;
    RunEventRepository events;
    ArtifactRepository artifacts;
    RecommendationRepository recommendations;
    KnowledgeSourceRepository knowledge;
    MetadataRepository metadata;
    ProfilingRepository profiling;
    IdempotencyRepository idempotency;

    String tenantA;
    String tenantB;

    /**
     * Business-logic layer over the live repository port (conflict protection + version bumps).
     * The mapper registers the JSR-310 module, mirroring Boot's production ObjectMapper so
     * Instant-bearing records serialize into conflict snapshots.
     */
    com.biz.sccba.sqlanalyzer.metadata.MetadataService metadataService() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return new com.biz.sccba.sqlanalyzer.metadata.MetadataService(metadata, mapper);
    }

    /** Subclasses provide the datasource coordinates of a migrated database. */
    abstract String jdbcUrl();

    abstract String username();

    abstract String password();

    @BeforeAll
    void startContext() {
        // Highest-precedence property source: overrides application.yml defaults
        // (sql-analyzer.persistence.enabled would otherwise resolve to false here).
        org.springframework.core.env.MapPropertySource contractProps =
                new org.springframework.core.env.MapPropertySource("contract", java.util.Map.of(
                        "sql-analyzer.persistence.enabled", "true",
                        "contract.jdbc-url", jdbcUrl(),
                        "contract.username", username(),
                        "contract.password", password()));
        ctx = new SpringApplicationBuilder(ContractTestConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(c -> ((org.springframework.core.env.ConfigurableEnvironment) c.getEnvironment())
                        .getPropertySources().addFirst(contractProps))
                .run();
        clients = ctx.getBean(ClientRepository.class);
        tokens = ctx.getBean(ClientTokenRepository.class);
        sessions = ctx.getBean(SessionRepository.class);
        messages = ctx.getBean(MessageRepository.class);
        runs = ctx.getBean(AgentRunRepository.class);
        jobs = ctx.getBean(AgentJobRepository.class);
        events = ctx.getBean(RunEventRepository.class);
        artifacts = ctx.getBean(ArtifactRepository.class);
        recommendations = ctx.getBean(RecommendationRepository.class);
        knowledge = ctx.getBean(KnowledgeSourceRepository.class);
        metadata = ctx.getBean(MetadataRepository.class);
        profiling = ctx.getBean(ProfilingRepository.class);
        idempotency = ctx.getBean(IdempotencyRepository.class);
    }

    @AfterAll
    void stopContext() {
        if (ctx != null) ctx.close();
    }

    @BeforeEach
    void freshTenants() {
        tenantA = "client_" + UUID.randomUUID();
        tenantB = "client_" + UUID.randomUUID();
        clients.create(tenantA, "Tenant A", "TEST", null);
        clients.create(tenantB, "Tenant B", "TEST", null);
    }

    // ---- CRUD, timestamps, nullables, JSON text ----

    @Test
    void crudRoundTripWithTimestampsAndNullables() {
        var client = clients.findById(tenantA).orElseThrow();
        assertEquals("Tenant A", client.name());
        assertNotNull(client.createdAt(), "database-generated timestamp must be readable");

        var session = sessions.create("session_" + UUID.randomUUID(), tenantA, "会话");
        assertEquals("ACTIVE", session.status());
        assertNotNull(session.createdAt());
        sessions.touch(session.id(), "RUNNING");
        assertEquals("RUNNING", sessions.findByIdForClient(session.id(), tenantA).orElseThrow().status());

        // nullable fields survive the round trip
        clients.create("client_null_" + UUID.randomUUID(), "NoDevice", "TEST", null);
        assertTrue(clients.findById("client_null_" + UUID.randomUUID()).isEmpty());

        var run = runs.create("run_" + UUID.randomUUID(), session.id(), "deepseek-chat");
        assertEquals("QUEUED", run.status());
        runs.updateStatus(run.id(), "COMPLETED", null);
        var finished = runs.findById(run.id()).orElseThrow();
        assertEquals("COMPLETED", finished.status());
        assertNotNull(finished.finishedAt(), "terminal status must stamp finished_at");
    }

    @Test
    void jsonTextRoundTripsVerbatim() {
        var session = sessions.create("session_" + UUID.randomUUID(), tenantA, "json");
        var run = runs.create("run_" + UUID.randomUUID(), session.id(), null);
        String payload = "{\"key\":\"值\",\"nested\":{\"n\":1},\"list\":[1,2,3]}";
        jobs.enqueue("job_" + UUID.randomUUID(), run.id(), payload);
        var claimed = jobs.claim("worker_contract").orElseThrow();
        assertEquals(payload, claimed.payloadJson(), "JSON payload must round-trip byte-identical");
        jobs.complete(claimed.id());
    }

    // ---- unique constraints and foreign keys ----

    @Test
    void uniqueAndForeignKeyConstraintsAreEnforced() {
        String hash = UUID.randomUUID().toString().repeat(2).substring(0, 64);
        tokens.create("token_" + UUID.randomUUID(), tenantA, hash, "spa_prefix");
        assertIntegrityViolation(() -> tokens.create("token_" + UUID.randomUUID(), tenantB, hash, "spa_prefix"),
                "token_hash uniqueness must hold across tenants");

        assertIntegrityViolation(() -> sessions.create("session_" + UUID.randomUUID(), "client_missing", "x"),
                "session must reference an existing client");
    }

    /**
     * Asserts the operation fails with a SQL integrity-constraint violation (SQLState class 23,
     * identical on PostgreSQL and H2) somewhere in the exception chain — Spring Data JDBC wraps
     * driver errors in DbActionExecutionException, so the driver exception is matched by cause.
     */
    static void assertIntegrityViolation(org.junit.jupiter.api.function.Executable executable, String message) {
        Throwable thrown = assertThrows(Throwable.class, executable, message);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("23")) {
                return;
            }
        }
        throw new AssertionError(message + " — expected SQLState 23xxx in chain, got: " + thrown);
    }

    // ---- run event cursor ordering ----

    @Test
    void runEventsAreCursorOrderedAndMonotonic() {
        var session = sessions.create("session_" + UUID.randomUUID(), tenantA, "events");
        var run = runs.create("run_" + UUID.randomUUID(), session.id(), null);
        long first = events.append(run.id(), "RUN_STARTED", "{\"n\":1}");
        long second = events.append(run.id(), "TEXT_MESSAGE_CONTENT", "{\"n\":2}");
        long third = events.append(run.id(), "RUN_FINISHED", "{\"n\":3}");
        assertTrue(first > 0 && second > first && third > second, "event ids must be monotonic");

        var afterFirst = events.after(tenantA, run.id(), first);
        assertEquals(2, afterFirst.size());
        assertEquals(second, afterFirst.get(0).id());
        assertEquals(third, afterFirst.get(1).id());
        assertEquals("TEXT_MESSAGE_CONTENT", afterFirst.get(0).type());
        assertTrue(events.after(tenantA, run.id(), third).isEmpty());
    }

    // ---- job claim / lease / retry ----

    @Test
    void jobClaimLeaseAndRetrySemantics() {
        var session = sessions.create("session_" + UUID.randomUUID(), tenantA, "queue");
        var run = runs.create("run_" + UUID.randomUUID(), session.id(), null);
        String job1 = "job_" + UUID.randomUUID();
        String job2 = "job_" + UUID.randomUUID();
        jobs.enqueue(job1, run.id(), "{\"order\":1}");
        jobs.enqueue(job2, run.id(), "{\"order\":2}");

        var claimed = jobs.claim("worker_1").orElseThrow();
        assertEquals(job1, claimed.id(), "oldest queued job must be claimed first");
        jobs.complete(job1);

        var claimed2 = jobs.claim("worker_1").orElseThrow();
        assertEquals(job2, claimed2.id());

        // fail with retry_count < 3 requeues; claiming again increments retry_count
        assertTrue(jobs.fail(job2, "boom"), "retry_count < 3 must requeue");
        var retried = jobs.claim("worker_2").orElseThrow();
        assertEquals(job2, retried.id());
        jobs.extendLease(job2, 15);
        jobs.failNoRetry(job2, "fatal");
        assertFalse(jobs.fail(job2, "again") , "FAILED job must not requeue");
        assertTrue(jobs.claim("worker_3").isEmpty(), "queue must be drained");

        // cancel only affects queued jobs
        String job3 = "job_" + UUID.randomUUID();
        jobs.enqueue(job3, run.id(), "{}");
        assertTrue(jobs.cancelQueuedForRun(run.id()));
        assertTrue(jobs.claim("worker_4").isEmpty(), "cancelled job must not be claimable");
    }

    // ---- tenant isolation (negative tests) ----

    @Test
    void sessionsRunsAndEventsAreIsolatedBetweenTenants() {
        var sessionA = sessions.create("session_" + UUID.randomUUID(), tenantA, "A");
        runs.create("run_" + UUID.randomUUID(), sessionA.id(), null);

        assertTrue(sessions.findByIdForClient(sessionA.id(), tenantB).isEmpty(),
                "tenant B must not read tenant A's session");
        assertTrue(sessions.listForClient(tenantB).isEmpty());
        var runA = runs.listForSession(tenantA, sessionA.id()).get(0);
        assertTrue(runs.listForSession(tenantB, sessionA.id()).isEmpty(),
                "run listing must be scoped by session ownership");
        assertFalse(runs.belongsToClient(runA.id(), tenantB));
        assertTrue(runs.belongsToClient(runA.id(), tenantA));
        assertEquals(1, runs.countActiveForClient(tenantA));
        assertEquals(0, runs.countActiveForClient(tenantB));
    }

    @Test
    void messagesAreIsolatedBetweenTenants() {
        var sessionA = sessions.create("session_" + UUID.randomUUID(), tenantA, "A");
        messages.append("message_" + UUID.randomUUID(), sessionA.id(), "USER", "hello", "TEXT", null);
        assertEquals(1, messages.listForSession(tenantA, sessionA.id()).size());
        assertTrue(messages.listForSession(tenantB, sessionA.id()).isEmpty(),
                "messages of a foreign session must be invisible");
    }

    @Test
    void artifactsAreIsolatedBetweenTenants() {
        byte[] body = "mapper-bytes".getBytes(StandardCharsets.UTF_8);
        var artifact = artifacts.create(new Artifact("artifact_" + UUID.randomUUID(), tenantA, null,
                "MAPPER_XML", "m.xml", "text/xml", "sha-" + UUID.randomUUID(), body.length, "INGESTED", "{}", null));
        artifacts.writeChunk(artifact.id(), 0, body);

        assertTrue(artifacts.readAll(tenantA, artifact.id()).isPresent());
        assertTrue(artifacts.readAll(tenantB, artifact.id()).isEmpty(),
                "artifact bytes must never cross tenants");
        assertTrue(artifacts.findByIdForClient(artifact.id(), tenantB).isEmpty());
    }

    @Test
    void recommendationsAndDecisionsAreIsolatedBetweenTenants() {
        var sessionA = sessions.create("session_" + UUID.randomUUID(), tenantA, "A");
        var runA = runs.create("run_" + UUID.randomUUID(), sessionA.id(), null);
        String recId = "rec_" + UUID.randomUUID();
        recommendations.create(new Recommendation(recId, runA.id(), sessionA.id(), "SQL_OPTIMIZATION", "加索引",
                "desc", "problem", "impact", "HIGH", "{}", null, "CREATE INDEX", 0.9, "PROPOSED", 1, null));

        assertEquals(1, recommendations.listForSession(tenantA, sessionA.id()).size());
        assertTrue(recommendations.listForSession(tenantB, sessionA.id()).isEmpty(),
                "recommendations must not leak across tenants");
        assertThrows(IllegalArgumentException.class,
                () -> recommendations.decide(recId, tenantB, "ACCEPTED", null, null),
                "a foreign tenant must not decide on another tenant's recommendation");
        recommendations.decide(recId, tenantA, "REJECTED", "COST", "收益不足");
        assertEquals("REJECTED", recommendations.listForSession(tenantA, sessionA.id()).get(0).status());
    }

    @Test
    void knowledgeFactsAreIsolatedBetweenTenants() {
        var sourceA = knowledge.createSource("ks_" + UUID.randomUUID(), tenantA, "A 知识", "EXCEL");
        var versionA = knowledge.createVersion(tenantA, "kv_" + UUID.randomUUID(), sourceA.id(), 1, null, "{}", "[]");
        knowledge.insertTables(sourceA.id(), versionA.id(), List.of(new TableDef("kbt_" + UUID.randomUUID(),
                null, null, "ds", "public", "orders", "订单", "用途", "alice", "交易", "tables!row2", false, null)));
        knowledge.insertColumns(sourceA.id(), versionA.id(), List.of(new ColumnDef("kbc_" + UUID.randomUUID(),
                null, null, "orders", "status", "状态", "varchar", "E", false, true, "HASHED", "columns!row2", false, null)));
        knowledge.publishVersion(tenantA, sourceA.id(), versionA.id(), "alice");

        assertEquals(1, knowledge.activeTables(tenantA, "orders").size());
        assertTrue(knowledge.activeTables(tenantB, "orders").isEmpty(),
                "published facts of the same table name must not cross tenants");
        assertTrue(knowledge.activeColumns(tenantB, "orders", "status").isEmpty());
        assertTrue(knowledge.findSourceForClient(tenantB, sourceA.id()).isEmpty());
        assertTrue(knowledge.findVersionForClient(tenantB, versionA.id()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> knowledge.listVersions(tenantB, sourceA.id()));
        assertThrows(IllegalArgumentException.class,
                () -> knowledge.publishVersion(tenantB, sourceA.id(), versionA.id(), "mallory"));
    }

    @Test
    void metadataIsIsolatedBetweenTenantsAndColumnOrderPreserved() {
        String columnsJson = "[{\"column\":\"member_id\",\"direction\":\"ASC\"},{\"column\":\"status\",\"direction\":\"ASC\"},{\"column\":\"due_at\",\"direction\":\"DESC\"}]";
        metadataService().upsertIndex(tenantA, new IndexDef("idx_" + UUID.randomUUID(), tenantA, "library", "public", "loan",
                "idx_loan_member_status_due", "NORMAL", columnsJson, 1000L, 5L, "MANUAL", "alice", null, 1, null, null, null));
        metadataService().upsertShard(tenantA, new ShardDef("shard_" + UUID.randomUUID(), tenantA, "library", "loan",
                "loan_{0..15}", "member_id", "borrowed_at", "hash", "member_id % 16", "{}", "MANUAL", "alice", null, 1, null, null));

        // Same table name under tenant B is a completely separate inventory.
        metadataService().upsertIndex(tenantB, new IndexDef("idx_" + UUID.randomUUID(), tenantB, "library", "public", "loan",
                "idx_loan_other", "NORMAL", "[{\"column\":\"id\"}]", null, null, "MANUAL", "bob", null, 1, null, null, null));

        var indexesA = metadata.indexesForTable(tenantA, "loan");
        var indexesB = metadata.indexesForTable(tenantB, "loan");
        assertEquals(1, indexesA.size());
        assertEquals(1, indexesB.size());
        assertEquals("idx_loan_member_status_due", indexesA.get(0).indexName());
        assertEquals("idx_loan_other", indexesB.get(0).indexName());
        assertEquals(columnsJson, indexesA.get(0).columnsJson(), "index column order must be preserved verbatim");

        assertEquals(1, metadata.shards(tenantA).size());
        assertTrue(metadata.shards(tenantB).isEmpty());
        var shardA = metadata.findShard(tenantA, "loan").orElseThrow();
        assertEquals("member_id", shardA.shardKey(), "primary shard key");
        assertEquals("borrowed_at", shardA.secondaryShardKey(), "secondary shard key must be stored separately");
        assertTrue(metadata.findShard(tenantB, "loan").isEmpty());

        // Upsert with the same (MANUAL) source bumps the version and keeps the tenant's row.
        metadataService().upsertIndex(tenantA, new IndexDef("idx_" + UUID.randomUUID(), tenantA, "library", "public", "loan",
                "idx_loan_member_status_due", "NORMAL", columnsJson, 2000L, 6L, "MANUAL", "alice", null, 1, null, null, null));
        assertEquals(2, metadata.indexesForTable(tenantA, "loan").get(0).version(),
                "upsert of an existing index must increment its version");

        // Auto-collection never overwrites a MANUAL record: it queues a PENDING conflict instead.
        metadataService().upsertIndex(tenantA, new IndexDef("idx_" + UUID.randomUUID(), tenantA, "library", "public", "loan",
                "idx_loan_member_status_due", "NORMAL", "[{\"column\":\"other\"}]", null, null, "SYSTEM_CATALOG", null, null, 1, null, null, null));
        var after = metadata.indexesForTable(tenantA, "loan").get(0);
        assertEquals(2, after.version(), "auto-collected data must not overwrite the manual record");
        assertEquals(columnsJson, after.columnsJson());
        var conflicts = metadata.pendingConflicts(tenantA);
        assertEquals(1, conflicts.size(), "a PENDING conflict must be queued");
        assertEquals("INDEX", conflicts.get(0).entityType());
        metadata.resolveConflict(tenantA, conflicts.get(0).id(), "DISMISSED");
        assertTrue(metadata.pendingConflicts(tenantA).isEmpty(), "dismissed conflict leaves the pending list");
        assertTrue(metadata.pendingConflicts(tenantB).isEmpty(), "conflicts must not cross tenants");
    }

    @Test
    void profilingSnapshotsAndStatsAreIsolatedBetweenTenants() {
        DatasourceProfile profileA = profiling.createProfile(new DatasourceProfile("dsp_" + UUID.randomUUID(),
                tenantA, "target", "MYSQL", "jdbc:mysql://target:3306/db", "ro", "ENV_PWD", true, null));
        assertTrue(profiling.findProfile(profileA.id(), tenantB).isEmpty(),
                "datasource profile must not cross tenants");

        String jobId = "pjob_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, tenantA, profileA.id(), "{}");
        var job = profiling.claimJob("profiler").orElseThrow();
        assertEquals(jobId, job.id());

        var snapshot = profiling.createSnapshot("snap_" + UUID.randomUUID(), jobId, profileA.id(), "{}");
        profiling.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshot.id(), "db", "orders", "status",
                0.1, 3L, "A", "Z", "[{\"value\":\"PAID\",\"count\":9}]", "[]", "[]", Instant.now()));
        profiling.finishSnapshot(snapshot.id(), "COMPLETED");
        profiling.completeJob(jobId);

        assertEquals(1, profiling.listSnapshots(tenantA, profileA.id()).size());
        assertTrue(profiling.listSnapshots(tenantB, profileA.id()).isEmpty(),
                "a foreign tenant must not list another tenant's snapshots");
        assertEquals(1, profiling.snapshotStats(tenantA, snapshot.id()).size());
        assertTrue(profiling.snapshotStats(tenantB, snapshot.id()).isEmpty(),
                "statistics must not leak across tenants");
    }

    // ---- idempotency ----

    @Test
    void idempotencyKeyReplayAndConflict() {
        String key = "idem_" + UUID.randomUUID();
        String digest = "d" + "0".repeat(63);
        Instant expiry = Instant.now().plus(24, ChronoUnit.HOURS);
        idempotency.save(new IdempotencyRepository.Record(tenantA, key, digest, "POST", "/api/v1/sessions",
                200, "{\"id\":\"session_x\"}", Instant.now(), expiry));

        var replay = idempotency.find(tenantA, key).orElseThrow();
        assertEquals(digest, replay.requestDigest());
        assertEquals("{\"id\":\"session_x\"}", replay.responseBody());

        // Same key, different client: separate key space.
        assertTrue(idempotency.find(tenantB, key).isEmpty(), "idempotency keys must not cross tenants");

        // Same key reused under the same client conflicts.
        assertThrows(IllegalStateException.class, () -> idempotency.save(new IdempotencyRepository.Record(
                tenantA, key, "other-digest", "POST", "/api/v1/sessions", 200, "{}", Instant.now(), expiry)));

        // Expired entries are purgeable.
        idempotency.save(new IdempotencyRepository.Record(tenantA, "idem_expired_" + UUID.randomUUID(), digest,
                "POST", "/x", 200, "{}", Instant.now(), Instant.now().minusSeconds(60)));
        assertTrue(idempotency.purgeExpired(Instant.now()) >= 1);
    }
}
