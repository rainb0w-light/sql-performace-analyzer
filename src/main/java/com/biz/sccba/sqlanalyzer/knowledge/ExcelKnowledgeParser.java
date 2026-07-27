package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.RowError;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ShardRow;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic parser for the controlled Excel business-knowledge template
 * (development-guide §7.1). Sheets: tables, columns, rules, enums, sharding, aliases.
 * Row-level problems are never silently dropped: each becomes a RowError(sheet,row,column,reason).
 *
 * <p>The parser is pure (bytes in, facts out) so preview/publish are rebuildable from the
 * original artifact, and unit tests need neither Spring nor a database.
 */
@Component
public class ExcelKnowledgeParser {

    private static final DataFormatter FORMATTER = new DataFormatter();

    public Parsed parse(byte[] workbookBytes) {
        List<TableDef> tables = new ArrayList<>();
        List<ColumnDef> columns = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        List<EnumValue> enums = new ArrayList<>();
        List<Alias> aliases = new ArrayList<>();
        List<ShardRow> shards = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            parseTables(workbook, tables, errors);
            parseColumns(workbook, columns, errors);
            parseRules(workbook, rules, errors);
            parseEnums(workbook, enums, errors);
            parseSharding(workbook, shards, errors);
            parseAliases(workbook, aliases, errors);
        } catch (Exception e) {
            errors.add(new RowError("(workbook)", 0, "", "无法解析 Excel 文件：" + e.getMessage()));
        }
        return new Parsed(tables, columns, rules, enums, aliases, shards, errors);
    }

    private void parseTables(Workbook wb, List<TableDef> out, List<RowError> errors) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        eachRow(wb, "tables", errors, (row, locator, values) -> {
            String tableName = require(values, "table_name", "tables", row, errors);
            if (tableName == null) return;
            if (!seen.add(tableName.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError("tables", row, "table_name", "重复的表名 " + tableName));
                return;
            }
            out.add(new TableDef(null, null, null,
                    values.getOrDefault("datasource", ""), emptyToNull(values.get("schema")),
                    tableName, values.getOrDefault("business_name", ""), values.getOrDefault("purpose", ""),
                    values.getOrDefault("owner", ""), values.getOrDefault("data_domain", ""),
                    locator, false, null));
        });
    }

    private void parseColumns(Workbook wb, List<ColumnDef> out, List<RowError> errors) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        eachRow(wb, "columns", errors, (row, locator, values) -> {
            String tableName = require(values, "table_name", "columns", row, errors);
            String columnName = require(values, "column_name", "columns", row, errors);
            if (tableName == null || columnName == null) return;
            String columnKey = tableName.toLowerCase(Locale.ROOT) + "." + columnName.toLowerCase(Locale.ROOT);
            if (!seen.add(columnKey)) {
                errors.add(new RowError("columns", row, "column_name",
                        "重复的字段 " + tableName + "." + columnName));
                return;
            }
            boolean sensitive = parseBool(values.get("is_sensitive"));
            String policy = sensitive
                    ? orDefault(values.get("sensitivity_policy"), "HASHED").toUpperCase(Locale.ROOT)
                    : "PLAINTEXT";
            if (sensitive && !List.of("PLAINTEXT", "HASHED", "OMITTED").contains(policy)) {
                errors.add(new RowError("columns", row, "sensitivity_policy",
                        "敏感字段策略必须是 PLAINTEXT/HASHED/OMITTED"));
                policy = "HASHED";
            }
            out.add(new ColumnDef(null, null, null, tableName, columnName,
                    values.getOrDefault("business_meaning", ""), values.getOrDefault("data_type", ""),
                    values.getOrDefault("enum_domain", ""), sensitive, parseBool(values.get("is_required")),
                    policy, locator, false, null));
        });
    }

    private void parseRules(Workbook wb, List<Rule> out, List<RowError> errors) {
        eachRow(wb, "rules", errors, (row, locator, values) -> {
            String description = require(values, "description", "rules", row, errors);
            if (description == null) return;
            int priority = parseInt(values.get("priority"), 100, "rules", row, errors);
            out.add(new Rule(null, null, null, values.getOrDefault("rule_key", ""), values.getOrDefault("target", ""),
                    description, values.getOrDefault("constraint_expr", ""), priority, null, locator, false, null));
        });
    }

    private void parseEnums(Workbook wb, List<EnumValue> out, List<RowError> errors) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        eachRow(wb, "enums", errors, (row, locator, values) -> {
            String code = require(values, "enum_code", "enums", row, errors);
            if (code == null) return;
            if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError("enums", row, "enum_code", "重复的枚举 " + code));
                return;
            }
            out.add(new EnumValue(null, null, null, code, values.getOrDefault("display_name", ""),
                    values.getOrDefault("meaning", ""), parseBoolDefaultTrue(values.get("is_valid")), locator, false, null));
        });
    }

    private void parseSharding(Workbook wb, List<ShardRow> out, List<RowError> errors) {
        eachRow(wb, "sharding", errors, (row, locator, values) -> {
            String logical = require(values, "logical_table", "sharding", row, errors);
            if (logical == null) return;
            out.add(new ShardRow(values.getOrDefault("datasource", ""), logical,
                    values.getOrDefault("physical_pattern", ""), values.getOrDefault("shard_key", ""),
                    values.getOrDefault("secondary_shard_key", ""), values.getOrDefault("algorithm", ""),
                    values.getOrDefault("routing_expr", ""), locator));
        });
    }

    private void parseAliases(Workbook wb, List<Alias> out, List<RowError> errors) {
        eachRow(wb, "aliases", errors, (row, locator, values) -> {
            String aliasName = require(values, "alias_name", "aliases", row, errors);
            String target = require(values, "target_name", "aliases", row, errors);
            if (aliasName == null || target == null) return;
            String type = orDefault(values.get("alias_type"), "TERM").toUpperCase(Locale.ROOT);
            if (!List.of("TABLE", "COLUMN", "TERM", "DOLLAR_WHITELIST").contains(type)) {
                errors.add(new RowError("aliases", row, "alias_type",
                        "alias_type 必须是 TABLE/COLUMN/TERM/DOLLAR_WHITELIST"));
                type = "TERM";
            }
            out.add(new Alias(null, null, null, type, aliasName, target, locator, false, null));
        });
    }

    // ---- helpers ----

    private interface RowHandler {
        void handle(int rowNumber, String locator, Map<String, String> values);
    }

    private void eachRow(Workbook wb, String sheetName, List<RowError> errors, RowHandler handler) {
        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) return; // sheet optional
        int headerRowNum = -1;
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            if (sheet.getRow(r) != null) {
                headerRowNum = r;
                break;
            }
        }
        if (headerRowNum < 0) return;
        Row headerRow = sheet.getRow(headerRowNum);
        Map<Integer, String> headerIndex = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String name = FORMATTER.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) headerIndex.put(cell.getColumnIndex(), name);
        }
        for (int r = headerRowNum + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> values = new LinkedHashMap<>();
            boolean any = false;
            for (var entry : headerIndex.entrySet()) {
                Cell cell = row.getCell(entry.getKey());
                String v = cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
                if (!v.isEmpty()) any = true;
                values.put(entry.getValue(), v);
            }
            if (!any) continue;
            handler.handle(r + 1, sheetName + "!row" + (r + 1), values);
        }
    }

    private static String require(Map<String, String> values, String key, String sheet, int row, List<RowError> errors) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            errors.add(new RowError(sheet, row, key, "缺少必填列 " + key));
            return null;
        }
        return v.trim();
    }

    private static boolean parseBool(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("是") || s.equals("y") || s.equals("t");
    }

    private static boolean parseBoolDefaultTrue(String v) {
        if (v == null || v.isBlank()) return true;
        return parseBool(v);
    }

    private static int parseInt(String v, int def, String sheet, int row, List<RowError> errors) {
        if (v == null || v.isBlank()) return def;
        try {
            return (int) Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            errors.add(new RowError(sheet, row, "priority", "priority 必须是整数"));
            return def;
        }
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
