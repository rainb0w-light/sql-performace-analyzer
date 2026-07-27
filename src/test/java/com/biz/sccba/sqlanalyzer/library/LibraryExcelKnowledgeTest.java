package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.RowError;
import com.biz.sccba.sqlanalyzer.knowledge.ExcelKnowledgeParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library fixture workbook parsing (docs/cloud-code-next-goal.md §5.4): the full template parses
 * cleanly into structured facts (including loan primary + secondary shard keys), and every
 * row-level error class — missing required column, invalid sensitivity policy, DUPLICATE KEYS,
 * invalid alias type — surfaces as an explicit RowError(sheet,row,column,reason), never silently
 * dropped.
 */
class LibraryExcelKnowledgeTest {

    private final ExcelKnowledgeParser parser = new ExcelKnowledgeParser();

    @Test
    void fullLibraryTemplateParsesCleanly() throws Exception {
        Parsed parsed = parser.parse(LibraryWorkbookFixtures.libraryKnowledge());
        assertTrue(parsed.errors().isEmpty(), "library workbook must parse without errors: " + parsed.errors());

        assertEquals(5, parsed.tables().size());
        assertEquals(10, parsed.columns().size());
        assertEquals(4, parsed.rules().size());
        assertEquals(8, parsed.enums().size());
        assertEquals(5, parsed.aliases().size());

        // loan sharding: primary and secondary keys parsed separately.
        assertEquals(1, parsed.shards().size());
        var loan = parsed.shards().get(0);
        assertEquals("loan", loan.logicalTable());
        assertEquals("member_id", loan.shardKey(), "primary shard key");
        assertEquals("borrowed_at", loan.secondaryShardKey(), "secondary shard key");

        // sensitivity: member_no HASHED, isbn PLAINTEXT.
        var memberNo = parsed.columns().stream().filter(c -> c.columnName().equals("member_no")).findFirst().orElseThrow();
        assertTrue(memberNo.sensitive());
        assertEquals("HASHED", memberNo.sensitivityPolicy());
        var isbn = parsed.columns().stream().filter(c -> c.columnName().equals("isbn")).findFirst().orElseThrow();
        assertEquals("PLAINTEXT", isbn.sensitivityPolicy());

        // provenance locators on every fact.
        assertTrue(parsed.tables().stream().allMatch(t -> t.sheetLocator() != null && t.sheetLocator().startsWith("tables!row")));
        assertTrue(parsed.enums().stream().allMatch(e -> e.sheetLocator().startsWith("enums!row")));
    }

    @Test
    void everyRowErrorClassIsReportedExplicitly() throws Exception {
        Parsed parsed = parser.parse(LibraryWorkbookFixtures.libraryKnowledgeWithErrors());
        List<RowError> errors = parsed.errors();
        assertFalse(errors.isEmpty());

        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("tables")
                        && e.column().equals("table_name") && e.reason().contains("缺少必填")),
                "missing required column must be reported: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("tables")
                        && e.reason().contains("重复")),
                "duplicate table_name must be reported: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("columns")
                        && e.reason().contains("重复")),
                "duplicate (table,column) must be reported: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("columns")
                        && e.column().equals("sensitivity_policy")),
                "invalid sensitivity policy must be reported: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("enums")
                        && e.reason().contains("重复")),
                "duplicate enum_code must be reported: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.sheet().equals("aliases")
                        && e.column().equals("alias_type")),
                "invalid alias_type must be reported: " + errors);

        // errors carry sheet + row + column, every one of them.
        assertTrue(errors.stream().allMatch(e -> e.sheet() != null && e.row() > 0 && e.column() != null));
    }
}
