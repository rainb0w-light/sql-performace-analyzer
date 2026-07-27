package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic Excel template parsing (development-guide §7.1): controlled sheets, required
 * columns, row-level errors with sheet/row/column/reason — never silently dropped.
 */
class ExcelKnowledgeParserTest {

    private final ExcelKnowledgeParser parser = new ExcelKnowledgeParser();

    private static byte[] workbook(java.util.function.Consumer<XSSFWorkbook> builder) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            builder.accept(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeRow(Sheet sheet, int rowIdx, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    @Test
    void parsesAllTemplateSheets() throws Exception {
        byte[] bytes = workbook(wb -> {
            Sheet tables = wb.createSheet("tables");
            writeRow(tables, 0, "datasource", "schema", "table_name", "business_name", "purpose", "owner", "data_domain");
            writeRow(tables, 1, "orders_db", "public", "orders", "订单表", "交易订单主表", "alice", "交易");

            Sheet columns = wb.createSheet("columns");
            writeRow(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            writeRow(columns, 1, "orders", "status", "订单状态", "varchar", "ORDER_STATUS", "false", "true", "");
            writeRow(columns, 2, "orders", "phone", "收件人电话", "varchar", "", "true", "false", "HASHED");

            Sheet rules = wb.createSheet("rules");
            writeRow(rules, 0, "rule_key", "target", "description", "constraint_expr", "priority");
            writeRow(rules, 1, "R1", "orders.status", "状态必须为有效枚举", "status in ORDER_STATUS", "10");

            Sheet enums = wb.createSheet("enums");
            writeRow(enums, 0, "enum_code", "display_name", "meaning", "is_valid");
            writeRow(enums, 1, "PAID", "已支付", "买家已付款", "true");

            Sheet sharding = wb.createSheet("sharding");
            writeRow(sharding, 0, "datasource", "logical_table", "physical_pattern", "shard_key",
                    "secondary_shard_key", "algorithm", "routing_expr");
            writeRow(sharding, 1, "orders_db", "orders", "orders_{0..15}", "user_id", "created_month", "hash", "user_id % 16");

            Sheet aliases = wb.createSheet("aliases");
            writeRow(aliases, 0, "alias_type", "alias_name", "target_name");
            writeRow(aliases, 1, "TABLE", "订单", "orders");
        });

        Parsed parsed = parser.parse(bytes);
        assertFalse(parsed.hasErrors(), "clean workbook must parse without errors: " + parsed.errors());
        assertEquals(1, parsed.tables().size());
        assertEquals("orders", parsed.tables().get(0).tableName());
        assertEquals("订单表", parsed.tables().get(0).businessName());
        assertEquals("tables!row2", parsed.tables().get(0).sheetLocator());

        assertEquals(2, parsed.columns().size());
        var phone = parsed.columns().get(1);
        assertTrue(phone.sensitive());
        assertEquals("HASHED", phone.sensitivityPolicy());
        assertFalse(parsed.columns().get(0).sensitive());
        assertEquals("PLAINTEXT", parsed.columns().get(0).sensitivityPolicy());

        assertEquals(1, parsed.rules().size());
        assertEquals(10, parsed.rules().get(0).priority());
        assertEquals(1, parsed.enums().size());
        assertTrue(parsed.enums().get(0).valid());
        assertEquals(1, parsed.shards().size());
        assertEquals("user_id", parsed.shards().get(0).shardKey());
        assertEquals("created_month", parsed.shards().get(0).secondaryShardKey());
        assertEquals(1, parsed.aliases().size());
    }

    @Test
    void rowLevelErrorsAreReportedWithLocator() throws Exception {
        byte[] bytes = workbook(wb -> {
            Sheet tables = wb.createSheet("tables");
            writeRow(tables, 0, "table_name", "business_name");
            writeRow(tables, 1, "", "缺少表名的行");      // missing required table_name
            writeRow(tables, 2, "orders", "订单表");       // valid

            Sheet columns = wb.createSheet("columns");
            writeRow(columns, 0, "table_name", "column_name", "is_sensitive", "sensitivity_policy");
            writeRow(columns, 1, "orders", "phone", "true", "WEIRD");  // invalid policy
        });

        Parsed parsed = parser.parse(bytes);
        assertTrue(parsed.hasErrors());
        assertEquals(1, parsed.tables().size(), "valid rows must still be parsed");

        var tableError = parsed.errors().stream().filter(e -> e.sheet().equals("tables")).findFirst().orElseThrow();
        assertEquals(2, tableError.row());
        assertEquals("table_name", tableError.column());
        assertFalse(tableError.reason().isBlank());

        var policyError = parsed.errors().stream().filter(e -> e.sheet().equals("columns")).findFirst().orElseThrow();
        assertEquals(2, policyError.row());
        assertEquals("sensitivity_policy", policyError.column());
    }

    @Test
    void garbageInputYieldsWorkbookErrorNotException() {
        Parsed parsed = parser.parse(new byte[] { 1, 2, 3, 4 });
        assertTrue(parsed.hasErrors());
        assertEquals("(workbook)", parsed.errors().get(0).sheet());
    }
}
