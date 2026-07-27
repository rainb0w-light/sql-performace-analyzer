package com.biz.sccba.sqlanalyzer.library;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

/**
 * Deterministic Excel knowledge workbooks for the library fixture
 * (docs/cloud-code-next-goal.md §4.1). Generated with Apache POI in code instead of committing a
 * binary, so the template content and every row-level error case stays reviewable and diffable.
 * Content mirrors knowledge/library-domain.md (Excel and Markdown are one source of truth).
 */
public final class LibraryWorkbookFixtures {

    private LibraryWorkbookFixtures() {
    }

    /** Full library knowledge workbook: tables/columns/rules/enums/sharding/aliases. */
    public static byte[] libraryKnowledge() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet tables = wb.createSheet("tables");
            row(tables, 0, "datasource", "schema", "table_name", "business_name", "purpose", "owner", "data_domain");
            row(tables, 1, "library_db", "public", "book", "书目", "一条书目记录，可有多个馆藏副本", "alice", "图书");
            row(tables, 2, "library_db", "public", "book_copy", "馆藏副本", "某分馆中某书目的一本实体副本", "alice", "图书");
            row(tables, 3, "library_db", "public", "member", "读者", "持有读者证的注册用户", "alice", "读者");
            row(tables, 4, "library_db", "public", "loan", "借阅记录", "一次借阅生命周期", "alice", "借阅");
            row(tables, 5, "library_db", "public", "reservation", "预约记录", "读者对书目的预约排队", "alice", "借阅");

            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            row(columns, 1, "book", "category", "图书分类", "varchar", "BOOK_CATEGORY", "false", "true", "");
            row(columns, 2, "book", "status", "书目状态", "varchar", "BOOK_STATUS", "false", "true", "");
            row(columns, 3, "book", "isbn", "ISBN", "varchar", "", "false", "true", "PLAINTEXT");
            row(columns, 4, "book_copy", "status", "副本状态", "varchar", "COPY_STATUS", "false", "true", "");
            row(columns, 5, "member", "member_no", "读者证号", "varchar", "", "true", "true", "HASHED");
            row(columns, 6, "member", "level", "读者等级", "varchar", "MEMBER_LEVEL", "false", "true", "");
            row(columns, 7, "loan", "status", "借阅状态", "varchar", "LOAN_STATUS", "false", "true", "");
            row(columns, 8, "loan", "borrowed_at", "借出时间（二级分片键）", "timestamp", "", "false", "true", "");
            row(columns, 9, "loan", "due_at", "应还时间（逾期判定基准）", "timestamp", "", "false", "true", "");
            row(columns, 10, "reservation", "status", "预约状态", "varchar", "RESERVATION_STATUS", "false", "true", "");

            Sheet rules = wb.createSheet("rules");
            row(rules, 0, "rule_key", "target", "description", "constraint_expr", "priority");
            row(rules, 1, "lib-rule-copy-borrowable", "book_copy", "仅 AVAILABLE 副本才能新建借阅",
                    "book_copy.status = 'AVAILABLE'", "10");
            row(rules, 2, "lib-rule-active-null-returned", "loan", "活跃借阅的 returned_at 必须为空",
                    "loan.status = 'ACTIVE' => loan.returned_at IS NULL", "10");
            row(rules, 3, "lib-rule-overdue-def", "loan", "逾期定义：ACTIVE 且 due_at 早于当前时间",
                    "loan.status = 'ACTIVE' AND loan.due_at < now", "10");
            row(rules, 4, "lib-rule-level-limit", "member", "读者等级决定最大借阅数量",
                    "GOLD=10, SILVER=5, BRONZE=3", "20");

            Sheet enums = wb.createSheet("enums");
            row(enums, 0, "enum_code", "display_name", "meaning", "is_valid");
            row(enums, 1, "FICTION", "小说", "高频分类", "true");
            row(enums, 2, "TECH", "技术", "", "true");
            row(enums, 3, "HISTORY", "历史", "", "true");
            row(enums, 4, "ACTIVE", "借阅中", "LOAN_STATUS", "true");
            row(enums, 5, "RETURNED", "已归还", "LOAN_STATUS", "true");
            row(enums, 6, "OVERDUE_LOCKED", "逾期冻结", "低频 LOAN_STATUS", "true");
            row(enums, 7, "AVAILABLE", "可借", "高频 COPY_STATUS", "true");
            row(enums, 8, "DAMAGED", "损毁", "低频 COPY_STATUS", "true");

            Sheet sharding = wb.createSheet("sharding");
            row(sharding, 0, "datasource", "logical_table", "physical_pattern", "shard_key",
                    "secondary_shard_key", "algorithm", "routing_expr");
            row(sharding, 1, "library_db", "loan", "loan_{0..15}", "member_id", "borrowed_at", "hash",
                    "member_id % 16, monthly(borrowed_at)");

            Sheet aliases = wb.createSheet("aliases");
            row(aliases, 0, "alias_type", "alias_name", "target_name");
            row(aliases, 1, "TABLE", "books", "book");
            row(aliases, 2, "TABLE", "loans", "loan");
            row(aliases, 3, "TERM", "reader", "member");
            // ${orderBy} interpolation whitelist: only title/category may be injected.
            row(aliases, 4, "DOLLAR_WHITELIST", "orderBy", "title");
            row(aliases, 5, "DOLLAR_WHITELIST", "orderBy", "category");

            return bytes(wb);
        }
    }

    /**
     * Workbook exercising every row-level error class: missing required column, invalid
     * sensitivity policy, duplicate keys (table/column/enum), invalid alias type.
     */
    public static byte[] libraryKnowledgeWithErrors() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet tables = wb.createSheet("tables");
            row(tables, 0, "datasource", "schema", "table_name", "business_name");
            row(tables, 1, "library_db", "public", "book", "书目");
            row(tables, 2, "library_db", "public", "", "缺少表名的一行");          // missing table_name
            row(tables, 3, "library_db", "public", "book", "重复表名");            // duplicate table_name

            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "is_sensitive", "sensitivity_policy");
            row(columns, 1, "book", "category", "图书分类", "false", "");
            row(columns, 2, "book", "category", "重复字段", "false", "");           // duplicate column
            row(columns, 3, "member", "member_no", "非法策略", "true", "ENCRYPTED"); // invalid policy

            Sheet enums = wb.createSheet("enums");
            row(enums, 0, "enum_code", "display_name");
            row(enums, 1, "FICTION", "小说");
            row(enums, 2, "FICTION", "重复枚举");                                   // duplicate enum_code

            Sheet aliases = wb.createSheet("aliases");
            row(aliases, 0, "alias_type", "alias_name", "target_name");
            row(aliases, 1, "RELATION", "x", "y");                                  // invalid alias_type

            return bytes(wb);
        }
    }

    static void row(Sheet sheet, int idx, String... values) {
        var r = sheet.createRow(idx);
        for (int i = 0; i < values.length; i++) {
            r.createCell(i).setCellValue(values[i]);
        }
    }

    static byte[] bytes(XSSFWorkbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
