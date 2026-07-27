package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Job;
import com.biz.sccba.sqlanalyzer.knowledge.ExcelKnowledgeParser;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import contracttest.ContractTestConfig;
import com.biz.sccba.sqlanalyzer.profiling.ProfilingService;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.MySQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library profiling determinism (docs/cloud-code-next-goal.md §5.3): H2 as the MANAGEMENT
 * database (file-based, restart-recoverable) + MySQL as the TARGET database seeded with the
 * library fixture. Asserts exact Top-K/null-ratio/distinct values from expected-profile.json,
 * the sensitivity policy (member_no hashed, isbn plaintext), the hotspot/skew facts, snapshot
 * immutability with client ownership, and recovery after a management-database restart.
 * Target-database dialect behavior stays separated from management-database behavior.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class LibraryProfilingTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }

    static String h2Url;
    static final ObjectMapper json = new ObjectMapper();

    ConfigurableApplicationContext ctx;
    ProfilingService profilingService;
    final String clientId = "client_library_profiling";
    String profileId;
    String snapshotId;

    @BeforeAll
    void seedTargetAndProfile() throws Exception {
        // Seed the MySQL target with the library fixture.
        try (Connection c = DriverManager.getConnection(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = c.createStatement()) {
            for (String sql : split(resource("/fixtures/library/schema/library-common.sql"))) {
                st.execute(sql);
            }
            for (String sql : split(resource("/fixtures/library/schema/seed-data.sql"))) {
                st.execute(sql);
            }
        }

        // H2 file management database: durable across context restarts.
        h2Url = "jdbc:h2:file:" + Files.createTempDirectory("library-profiling-h2").resolve("management")
                + ";DB_CLOSE_DELAY=-1";

        startContext();
        // Publish library knowledge so sensitivity policies resolve (member_no HASHED, isbn PLAINTEXT).
        KnowledgeImportService imports = new KnowledgeImportService(
                new ArtifactService(ctx.getBean(ArtifactRepository.class)),
                new ExcelKnowledgeParser(),
                ctx.getBean(KnowledgeSourceRepository.class),
                new MetadataService(ctx.getBean(MetadataRepository.class), jsonWithTime()),
                jsonWithTime(),
                emptyProvider());
        ctx.getBean(ClientRepository.class).create(clientId, "Library Profiling", "TEST", null);
        var preview = imports.importExcel(clientId, "图书业务知识", "library-knowledge.xlsx",
                LibraryWorkbookFixtures.libraryKnowledge());
        imports.publish(clientId, preview.versionId(), "alice");

        // Profile the target library database.
        ProfilingRepository profiling = ctx.getBean(ProfilingRepository.class);
        DatasourceProfile profile = profiling.createProfile(new DatasourceProfile(
                "dsp_" + UUID.randomUUID(), clientId, "library-mysql", "MYSQL",
                jdbcUrl(), MYSQL.getUsername(), "test.mysql.password", true, null));
        profileId = profile.id();
        String jobId = "pjob_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, clientId, profile.id(), json.writeValueAsString(Map.of(
                "schema", MYSQL.getDatabaseName(),
                "tables", List.of("book", "book_copy", "member", "loan", "reservation"))));
        Job job = profiling.claimJob("profiler-test").orElseThrow();
        profilingService.runJob(job);
        Job settled = profiling.findJob(jobId, clientId).orElseThrow();
        assertEquals("COMPLETED", settled.status(), "profiling job must complete: " + settled.lastError());
        snapshotId = profiling.listSnapshots(clientId, profileId).get(0).id();
    }

    @AfterAll
    void stop() {
        if (ctx != null) ctx.close();
        MYSQL.stop();
    }

    private void startContext() {
        StandardEnvironment targetEnv = new StandardEnvironment();
        targetEnv.getPropertySources().addFirst(new MapPropertySource("target",
                Map.of("test.mysql.password", MYSQL.getPassword())));
        MapPropertySource props = new MapPropertySource("contract", Map.of(
                "sql-analyzer.persistence.enabled", "true",
                "contract.jdbc-url", h2Url,
                "contract.username", "sa",
                "contract.password", ""));
        ctx = new SpringApplicationBuilder(ContractTestConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(c -> ((ConfigurableEnvironment) c.getEnvironment())
                        .getPropertySources().addFirst(props))
                .run();
        KnowledgeQueryService knowledgeQuery = new KnowledgeQueryService(ctx.getBean(KnowledgeSourceRepository.class));
        profilingService = new ProfilingService(ctx.getBean(ProfilingRepository.class), targetEnv,
                jsonWithTime(), providerOf(knowledgeQuery), 50000, 10, 10000);
    }

    @Test
    void profileMatchesDeterministicExpectationsWithSensitivityPolicy() throws Exception {
        List<ColumnStat> stats = ctx.getBean(ProfilingRepository.class).snapshotStats(clientId, snapshotId);
        JsonNode expected = json.readTree(resource("/fixtures/library/profiles/expected-profile.json"));

        assertTopK(stats, "book", "category", expected.at("/tables/book/columns/category/topK"));
        assertTopK(stats, "book_copy", "branch_id", expected.at("/tables/book_copy/columns/branch_id/topK"));
        assertTopK(stats, "loan", "status", expected.at("/tables/loan/columns/status/topK"));
        assertTopK(stats, "member", "level", expected.at("/tables/member/columns/level/topK"));

        ColumnStat category = find(stats, "book", "category");
        assertEquals(3L, category.approxDistinct(), "book.category distinct");

        ColumnStat returned = find(stats, "loan", "returned_at");
        assertNotNull(returned.nullRatio());
        assertTrue(Math.abs(returned.nullRatio() - 0.6667) < 0.05,
                "4 of 6 loans are active (returned_at NULL), got " + returned.nullRatio());

        // member_no: HASHED — 64-char hex only, raw values never stored.
        ColumnStat memberNo = find(stats, "member", "member_no");
        JsonNode memberTop = json.readTree(memberNo.topKJson());
        assertTrue(memberTop.isArray() && memberTop.size() > 0);
        for (JsonNode entry : memberTop) {
            String value = entry.path("value").asText();
            assertTrue(value.matches("[0-9a-f]{64}"), "member_no Top-K must be SHA-256 hex, got: " + value);
            assertTrue(!value.contains("M-100"), "raw member_no must never be stored");
        }
        assertEquals("HASHED", memberNo.sensitivityPolicy());
        assertTrue(memberNo.minValue() == null || memberNo.minValue().matches("[0-9a-f]{64}"),
                "HASHED min must not leak raw values");
        assertTrue(memberNo.maxValue() == null || memberNo.maxValue().matches("[0-9a-f]{64}"),
                "HASHED max must not leak raw values");

        // isbn: PLAINTEXT — real ISBN values are readable.
        ColumnStat isbn = find(stats, "book", "isbn");
        assertEquals("PLAINTEXT", isbn.sensitivityPolicy());
        assertTrue(isbn.topKJson().contains("978-0-"), "isbn Top-K must be plaintext: " + isbn.topKJson());
    }

    @Test
    void snapshotsAreIsolatedBetweenTenants() {
        ProfilingRepository profiling = ctx.getBean(ProfilingRepository.class);
        assertTrue(profiling.listSnapshots("client_other", profileId).isEmpty());
        assertTrue(profiling.snapshotStats("client_other", snapshotId).isEmpty(),
                "library profiling stats must not leak across tenants");
    }

    @Test
    void profilingResultsSurviveManagementDatabaseRestart() {
        // Restart = close the context/pool, reopen over the same H2 file.
        ctx.close();
        startContext();
        ProfilingRepository profiling = ctx.getBean(ProfilingRepository.class);
        var snapshots = profiling.listSnapshots(clientId, profileId);
        assertEquals(1, snapshots.size(), "snapshot must persist across the H2 management-DB restart");
        assertEquals(snapshotId, snapshots.get(0).id());
        assertEquals("COMPLETED", snapshots.get(0).status());
        assertTrue(profiling.snapshotStats(clientId, snapshotId).size() > 0,
                "column stats must persist across the restart");
    }

    // ---- helpers ----

    private void assertTopK(List<ColumnStat> stats, String table, String column, JsonNode expectedTopK) {
        ColumnStat stat = find(stats, table, column);
        try {
            JsonNode actual = json.readTree(stat.topKJson());
            assertTrue(actual.isArray() && actual.size() > 0, table + "." + column + " topK empty");
            for (JsonNode expectedEntry : expectedTopK) {
                String value = expectedEntry.path("value").asText();
                long count = expectedEntry.path("count").asLong();
                boolean found = false;
                for (JsonNode actualEntry : actual) {
                    if (actualEntry.path("value").asText().equals(value)
                            && actualEntry.path("count").asLong() == count) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, table + "." + column + " topK must contain " + value + ":" + count
                        + " — actual: " + stat.topKJson());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ColumnStat find(List<ColumnStat> stats, String table, String column) {
        return stats.stream().filter(s -> s.tableName().equals(table) && s.columnName().equals(column))
                .findFirst().orElseThrow(() -> new AssertionError("missing stat " + table + "." + column));
    }

    private static String jdbcUrl() {
        String url = MYSQL.getJdbcUrl();
        return url.contains("?") ? url + "&allowPublicKeyRetrieval=true&useSSL=false"
                : url + "?allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = LibraryProfilingTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> split(String script) {
        // Drop "--" comment lines first: fixture comments may contain ';' inside parentheses.
        String stripped = java.util.Arrays.stream(script.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);
        return java.util.Arrays.stream(stripped.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static ObjectMapper jsonWithTime() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper;
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return providerOf(null);
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getObject() { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }
        };
    }
}
