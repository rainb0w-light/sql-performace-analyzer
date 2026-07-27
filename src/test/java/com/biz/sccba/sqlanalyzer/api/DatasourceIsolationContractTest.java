package com.biz.sccba.sqlanalyzer.api;

import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 contract freeze (docs/claude-code-remediation-goal.md §5.1):
 * metadata and profiling queries must be scoped by
 * clientId + datasourceProfileId + schema + table.
 *
 * <p>Two datasources under the SAME client with the SAME table name must not mix data.
 *
 * <p>Permanent regression contract for the full metadata lookup key.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatasourceIsolationContractTest {

    static ConfigurableApplicationContext ctx;

    static final String CLIENT = "client_ds_isolation";
    static String dspA;
    static String dspB;

    MetadataRepository metadata;
    MetadataService metadataService;
    ProfilingRepository profiling;

    @BeforeAll
    void startAndSeed() {
        MapPropertySource props = new MapPropertySource("contract", Map.of(
                "sql-analyzer.persistence.enabled", "true",
                "contract.jdbc-url", "jdbc:h2:mem:ds_isolation;DB_CLOSE_DELAY=-1",
                "contract.username", "sa",
                "contract.password", ""));
        ctx = new SpringApplicationBuilder(AnalysisTestConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(c -> ((ConfigurableEnvironment) c.getEnvironment())
                        .getPropertySources().addFirst(props))
                .run();

        metadata = ctx.getBean(MetadataRepository.class);
        metadataService = ctx.getBean(MetadataService.class);
        profiling = ctx.getBean(ProfilingRepository.class);

        var clients = ctx.getBean(ClientRepository.class);
        clients.create(CLIENT, "Datasource Isolation Test", "TEST", null);

        // Create two datasource profiles under the SAME client
        DatasourceProfile profileA = profiling.createProfile(new DatasourceProfile(
                "dsp_iso_a_" + UUID.randomUUID(), CLIENT, "production-db", "MYSQL",
                "jdbc:mysql://prod:3306/library", "ro", "PROD_PASSWORD", true, null));
        DatasourceProfile profileB = profiling.createProfile(new DatasourceProfile(
                "dsp_iso_b_" + UUID.randomUUID(), CLIENT, "staging-db", "MYSQL",
                "jdbc:mysql://staging:3306/library", "ro", "STAGING_PASSWORD", true, null));
        dspA = profileA.id();
        dspB = profileB.id();

        // Register the SAME table "loan" under BOTH datasources with DIFFERENT indexes
        seedIndexesForDatasource(dspA, "idx_prod_loan_member",
                "[{\"column\":\"member_id\",\"direction\":\"ASC\"}]");
        seedIndexesForDatasource(dspB, "idx_staging_loan_status",
                "[{\"column\":\"status\",\"direction\":\"ASC\"}]");
        metadataService.upsertIndex(CLIENT, new IndexDef(null, CLIENT, dspA, "archive", "loan",
                "idx_archive_loan_due", "NORMAL",
                "[{\"column\":\"due_at\",\"direction\":\"ASC\"}]",
                1000L, 10L, "MANUAL", "alice", null, 1, null, null, null));

        // Register shards: dspA has a shard, dspB does not
        metadataService.upsertShard(CLIENT, new ShardDef(null, CLIENT, dspA, "loan", "loan_{0..15}",
                "member_id", null, "hash", "member_id % 16", "{}",
                "MANUAL", "alice", null, 1, null, null));

        // Seed profile snapshots for both datasources with different data
        seedProfileSnapshot(dspA, "loan", "status", 0.0, 2L, "ACTIVE", "RETURNED");
        seedProfileSnapshot(dspB, "loan", "status", 0.5, 4L, "PENDING", "ARCHIVED");
    }

    @AfterAll
    void stop() {
        if (ctx != null) ctx.close();
    }

    private void seedIndexesForDatasource(String datasource, String indexName, String columnsJson) {
        metadataService.upsertIndex(CLIENT, new IndexDef(null, CLIENT, datasource, "public", "loan",
                indexName, "NORMAL", columnsJson,
                1000L, 10L, "MANUAL", "alice", null, 1, null, null, null));
    }

    private void seedProfileSnapshot(String datasourceProfileId, String table, String column,
                                      double nullRatio, long distinct, String min, String max) {
        String jobId = "pjob_iso_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, CLIENT, datasourceProfileId, "{}");
        var snapshot = profiling.createSnapshot("snap_iso_" + UUID.randomUUID(), jobId, datasourceProfileId, "{}");
        Instant now = Instant.now();
        profiling.insertColumnStat(new ColumnStat("stat_" + UUID.randomUUID(), snapshot.id(), "library", table,
                column, nullRatio, distinct, min, max,
                "[{\"value\":\"" + min + "\",\"count\":10}]", "[]", "[]", now));
        profiling.finishSnapshot(snapshot.id(), "COMPLETED");
        profiling.completeJob(jobId);
    }

    /**
     * Goal §5.1: indexesForTable must be scoped by datasourceProfileId.
     *
     * FAILS because MetadataRepository.indexesForTable(clientId, tableName) has no
     * datasourceProfileId parameter — it returns indexes from ALL datasources.
     */
    @Test
    @SuppressWarnings("unchecked")
    void indexesAreIsolatedByDatasourceProfile() throws Exception {
        var method = assertDoesNotThrow(() -> MetadataRepository.class.getMethod(
                        "indexesForTable", String.class, String.class, String.class, String.class),
                "MetadataRepository must expose client+datasource+schema+table lookup");
        List<IndexDef> prod = (List<IndexDef>) method.invoke(metadata, CLIENT, dspA, "public", "loan");
        List<IndexDef> staging = (List<IndexDef>) method.invoke(metadata, CLIENT, dspB, "public", "loan");
        assertEquals(List.of("idx_prod_loan_member"), prod.stream().map(IndexDef::indexName).toList());
        assertEquals(List.of("idx_staging_loan_status"), staging.stream().map(IndexDef::indexName).toList());
    }

    /**
     * Goal §5.1: findShard must be scoped by datasourceProfileId.
     *
     * FAILS because MetadataRepository.findShard(clientId, logicalTable) has no
     * datasourceProfileId parameter.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shardsAreIsolatedByDatasourceProfile() throws Exception {
        var method = assertDoesNotThrow(() -> MetadataRepository.class.getMethod(
                        "findShard", String.class, String.class, String.class, String.class),
                "MetadataRepository must expose client+datasource+schema+table shard lookup");
        var prod = (java.util.Optional<ShardDef>) method.invoke(metadata, CLIENT, dspA, "public", "loan");
        var staging = (java.util.Optional<ShardDef>) method.invoke(metadata, CLIENT, dspB, "public", "loan");
        assertTrue(prod.isPresent(), "dspA/public has the loan shard");
        assertTrue(staging.isEmpty(), "dspB/public must not see dspA's loan shard");
    }

    /**
     * Goal §5.1: latestStatsForClient must be scoped by datasourceProfileId.
     *
     * FAILS because ProfilingRepository.latestStatsForClient(clientId) returns
     * stats from ALL datasource profiles of the client, with no way to filter.
     */
    @Test
    @SuppressWarnings("unchecked")
    void profileStatsAreIsolatedByDatasourceProfile() throws Exception {
        var method = assertDoesNotThrow(() -> ProfilingRepository.class.getMethod(
                        "latestStatsForDatasource", String.class, String.class),
                "ProfilingRepository must expose client+datasource latest stats lookup");
        List<ColumnStat> prod = (List<ColumnStat>) method.invoke(profiling, CLIENT, dspA);
        List<ColumnStat> staging = (List<ColumnStat>) method.invoke(profiling, CLIENT, dspB);
        assertEquals("ACTIVE", prod.get(0).minValue());
        assertEquals("PENDING", staging.get(0).minValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexesAreIsolatedBySchemaWithinOneDatasource() throws Exception {
        var method = assertDoesNotThrow(() -> MetadataRepository.class.getMethod(
                        "indexesForTable", String.class, String.class, String.class, String.class),
                "MetadataRepository must include schema in index lookup");
        List<IndexDef> live = (List<IndexDef>) method.invoke(metadata, CLIENT, dspA, "public", "loan");
        List<IndexDef> archive = (List<IndexDef>) method.invoke(metadata, CLIENT, dspA, "archive", "loan");
        assertEquals(List.of("idx_prod_loan_member"), live.stream().map(IndexDef::indexName).toList());
        assertEquals(List.of("idx_archive_loan_due"), archive.stream().map(IndexDef::indexName).toList());
    }
}
