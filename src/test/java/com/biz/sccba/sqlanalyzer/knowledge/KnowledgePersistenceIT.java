package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker gate (RUN_POSTGRES_INTEGRATION_TESTS=true, enforced in CI): real PostgreSQL management
 * database + Flyway + the vendor-neutral Spring Data JDBC repositories. Import → preview →
 * publish → query → rollback, Excel sharding rows landing in shard_def under the owning client,
 * and cross-tenant isolation of published facts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.worker.enabled=false"
})
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class KnowledgePersistenceIT {

    private static final String CLIENT = "client_knowledge_it";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("sql-analyzer.persistence.jdbc-url", postgres::getJdbcUrl);
        registry.add("sql-analyzer.persistence.username", postgres::getUsername);
        registry.add("sql-analyzer.persistence.password", postgres::getPassword);
    }

    @Autowired
    KnowledgeImportService imports;
    @Autowired
    KnowledgeQueryService query;
    @Autowired
    MetadataService metadata;
    @Autowired
    ClientRepository clients;

    @BeforeAll
    static void nothing() {
        // container + context lifecycle is managed by Spring/Testcontainers
    }

    @Test
    void importPublishQueryRollbackAgainstRealDatabase() throws Exception {
        clients.create(CLIENT, "Knowledge IT", "TEST", null);

        byte[] bytes = workbook("交易订单主表");
        var first = imports.importExcel(CLIENT, "业务知识", "kb.xlsx", bytes);
        assertFalse(first.parsed().hasErrors());

        imports.publish(CLIENT, first.versionId(), "alice");
        var facts = query.tables(CLIENT, "orders");
        assertEquals(1, facts.size());
        assertTrue(facts.get(0).text().contains("交易订单主表"));
        assertEquals("tables!row2", facts.get(0).evidence().locator());

        // Excel sharding rows land in shard_def under the owning client.
        var shards = metadata.shards(CLIENT);
        assertTrue(shards.stream().anyMatch(s -> s.logicalTable().equals("orders") && "user_id".equals(s.shardKey())),
                "shard_def must contain the Excel sharding row");

        // Publish a second version and roll back to the first.
        var second = imports.importExcel(CLIENT, "业务知识", "kb.xlsx", workbook("新版用途"));
        imports.publish(CLIENT, second.versionId(), "alice");
        assertTrue(query.tables(CLIENT, "orders").get(0).text().contains("新版用途"));

        imports.rollback(CLIENT, first.sourceId(), first.versionId());
        assertTrue(query.tables(CLIENT, "orders").get(0).text().contains("交易订单主表"),
                "rollback must reactivate the first version's facts");

        // Sensitive column policy resolves through the published knowledge.
        assertEquals("HASHED", query.sensitivityPolicy(CLIENT, "orders", "phone"));
        assertEquals("PLAINTEXT", query.sensitivityPolicy(CLIENT, "orders", "status"));

        // Tenant isolation: another client sees none of this client's facts or shards.
        assertTrue(query.tables("client_other_tenant", "orders").isEmpty(),
                "published facts must not leak across tenants");
        assertTrue(metadata.shards("client_other_tenant").isEmpty(),
                "shard definitions must not leak across tenants");
    }

    private static byte[] workbook(String purpose) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet tables = wb.createSheet("tables");
            row(tables, 0, "datasource", "schema", "table_name", "business_name", "purpose", "owner", "data_domain");
            row(tables, 1, "orders_db", "public", "orders", "订单表", purpose, "alice", "交易");
            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            row(columns, 1, "orders", "status", "订单状态", "varchar", "ORDER_STATUS", "false", "true", "");
            row(columns, 2, "orders", "phone", "收件人电话", "varchar", "", "true", "false", "HASHED");
            Sheet sharding = wb.createSheet("sharding");
            row(sharding, 0, "datasource", "logical_table", "physical_pattern", "shard_key",
                    "secondary_shard_key", "algorithm", "routing_expr");
            row(sharding, 1, "orders_db", "orders", "orders_{0..15}", "user_id", "created_month", "hash", "user_id % 16");
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
