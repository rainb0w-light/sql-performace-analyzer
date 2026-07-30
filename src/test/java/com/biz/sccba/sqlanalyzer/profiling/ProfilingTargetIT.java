package com.biz.sccba.sqlanalyzer.profiling;

import com.biz.sccba.sqlanalyzer.analysis.ExecutionPlanCollector;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.Job;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime.ParameterMappingView;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine.PlanResult;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker gate (RUN_POSTGRES_INTEGRATION_TESTS=true, enforced in CI): controlled MySQL target +
 * PostgreSQL management database through the vendor-neutral repositories. Runs a deterministic
 * profiling job end to end and verifies the sensitive data policy: plaintext Top-K for normal
 * columns, SHA-256 values for HASHED columns, and the immutable snapshot persisted in the
 * management database. Target-database dialect behavior is exercised separately from the
 * management-database behavior (docs/cloud-code-next-goal.md §5.3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.worker.enabled=false",
        "sql-analyzer.analysis.explain-enabled=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class ProfilingTargetIT {

    private static final String CLIENT = "client_profiling_it";

    // Started eagerly: @DynamicPropertySource is evaluated during context creation,
    // before any @BeforeAll callback could start the containers.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    static {
        postgres.start();
        mysql.start();
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("sql-analyzer.persistence.jdbc-url", postgres::getJdbcUrl);
        registry.add("sql-analyzer.persistence.username", postgres::getUsername);
        registry.add("sql-analyzer.persistence.password", postgres::getPassword);
        registry.add("test.mysql.password", mysql::getPassword);
    }

    @Autowired
    ProfilingRepository profiling;
    @Autowired
    ProfilingService profilingService;
    @Autowired
    KnowledgeImportService imports;
    @Autowired
    ClientRepository clients;
    @Autowired
    ObjectMapper json;
    @Autowired
    ExecutionPlanCollector executionPlans;

    @BeforeAll
    void seedTargetAndKnowledge() throws Exception {
        clients.create(CLIENT, "Profiling IT", "TEST", null);
        try (Connection c = DriverManager.getConnection(jdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE orders (id INT PRIMARY KEY, status VARCHAR(20), phone VARCHAR(40), amount DOUBLE)");
            for (int i = 1; i <= 24; i++) {
                String status = i % 3 == 0 ? "NULL" : "'" + (i % 2 == 0 ? "PAID" : "NEW") + "'";
                st.execute("INSERT INTO orders VALUES (" + i + ", " + status + ", '1380000" + (1000 + i) + "', " + (i * 10.5) + ")");
            }
        }
        // Publish knowledge marking orders.phone as sensitive (HASHED).
        var preview = imports.importExcel(CLIENT, "画像知识", "kb.xlsx", knowledgeWorkbook());
        imports.publish(CLIENT, preview.versionId(), "alice");
    }

    private static String jdbcUrl() {
        String url = mysql.getJdbcUrl();
        return url.contains("?") ? url + "&allowPublicKeyRetrieval=true&useSSL=false"
                : url + "?allowPublicKeyRetrieval=true&useSSL=false";
    }

    @Test
    void profilesTargetWithSensitivePolicyAndImmutableSnapshot() throws Exception {
        DatasourceProfile profile = profiling.createProfile(new DatasourceProfile(
                "dsp_" + UUID.randomUUID(), CLIENT, "target-mysql", "MYSQL",
                jdbcUrl(), mysql.getUsername(), "test.mysql.password", true, null));

        String jobId = "pjob_" + UUID.randomUUID();
        profiling.enqueueJob(jobId, CLIENT, profile.id(),
                json.writeValueAsString(Map.of("schema", mysql.getDatabaseName(), "tables", List.of("orders"))));

        Job job = profiling.claimJob("test-worker").orElseThrow();
        assertEquals(jobId, job.id());
        profilingService.runJob(job);

        Job settled = profiling.findJob(jobId, CLIENT).orElseThrow();
        assertEquals("COMPLETED", settled.status(), "profiling job must complete: " + settled.lastError());

        var snapshots = profiling.listSnapshots(CLIENT, profile.id());
        assertEquals(1, snapshots.size());
        assertEquals("COMPLETED", snapshots.get(0).status());

        List<ColumnStat> stats = profiling.snapshotStats(CLIENT, snapshots.get(0).id());
        ColumnStat status = stats.stream().filter(s -> s.columnName().equals("status")).findFirst().orElseThrow();
        ColumnStat phone = stats.stream().filter(s -> s.columnName().equals("phone")).findFirst().orElseThrow();

        // status: plaintext Top-K values, some NULLs observed.
        JsonNode statusTop = json.readTree(status.topKJson());
        assertTrue(statusTop.isArray() && statusTop.size() > 0);
        boolean plainValues = false;
        for (JsonNode entry : statusTop) {
            String value = entry.path("value").asText();
            if (value.equals("PAID") || value.equals("NEW")) plainValues = true;
        }
        assertTrue(plainValues, "plaintext policy must keep readable values: " + status.topKJson());
        assertNotNull(status.nullRatio());
        assertTrue(status.nullRatio() > 0, "NULL rows must be reflected in null_ratio");

        // phone: HASHED policy — every value must be 64-char SHA-256 hex, never the raw number.
        JsonNode phoneTop = json.readTree(phone.topKJson());
        assertTrue(phoneTop.isArray() && phoneTop.size() > 0);
        for (JsonNode entry : phoneTop) {
            String value = entry.path("value").asText();
            assertTrue(value.matches("[0-9a-f]{64}"), "HASHED policy must store SHA-256 hex, got: " + value);
            assertTrue(!value.startsWith("1380000"), "raw phone value must never be stored");
        }
        assertEquals("HASHED", phone.sensitivityPolicy());
        assertTrue(phone.minValue() == null || phone.minValue().matches("[0-9a-f]{64}"));
        assertTrue(phone.maxValue() == null || phone.maxValue().matches("[0-9a-f]{64}"));

        // Tenant isolation: another client can neither list the snapshots nor read the stats.
        assertTrue(profiling.listSnapshots("client_other_tenant", profile.id()).isEmpty());
        assertTrue(profiling.snapshotStats("client_other_tenant", snapshots.get(0).id()).isEmpty(),
                "statistics must not leak across tenants");
    }

    @Test
    void parameterizedSelectProducesRealReadOnlyExplainEvidence() {
        DatasourceProfile profile = profiling.createProfile(new DatasourceProfile(
                "dsp_explain_" + UUID.randomUUID(), CLIENT, "target-mysql-explain", "MYSQL",
                jdbcUrl(), mysql.getUsername(), "test.mysql.password", true, null));
        var scenario = new ParameterScenario("scenario_explain", "按状态查询", "按状态查询",
                ParameterSource.BOUNDARY_GENERATED, Map.of("status", "PAID"),
                List.of(), List.of(), List.of("MAIN"), 0.9, null, null, 1);
        var bound = new BoundScenario(scenario,
                "SELECT * FROM orders WHERE status = ?", "fp_explain",
                List.of(new ParameterMappingView("status", "IN", "java.lang.String", "VARCHAR")),
                Map.of(), List.of(), false, null, List.of("MAIN"));

        var result = executionPlans.collect(CLIENT, profile.id(), "SELECT",
                new PlanResult("OrdersMapper", "findByStatus", List.of(bound), null));

        assertEquals(1, result.plans().size(), result.missingPermissions().toString());
        assertTrue(result.plans().getFirst().plan().contains("orders"),
                "real MySQL EXPLAIN must identify the target table: " + result.plans().getFirst().plan());
        assertTrue(result.plans().getFirst().evidenceId().startsWith("ev_explain_"));
        assertTrue(!result.plans().getFirst().plan().contains(mysql.getPassword()),
                "target credentials must never enter EXPLAIN evidence");
        assertTrue(!result.explainSkipped());
    }

    private static byte[] knowledgeWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            row(columns, 1, "orders", "status", "订单状态", "varchar", "ORDER_STATUS", "false", "false", "");
            row(columns, 2, "orders", "phone", "收件人电话", "varchar", "", "true", "false", "HASHED");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, int idx, String... values) {
        var r = sheet.createRow(idx);
        for (int i = 0; i < values.length; i++) r.createCell(i).setCellValue(values[i]);
    }
}
