package com.biz.sccba.sqlanalyzer.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeCsvTest {

    @Test
    void escapesSpreadsheetFormulaPrefixesAndRfc4180Characters() {
        assertEquals("'=cmd()", KnowledgeCsv.safeCell("=cmd()"));
        assertEquals("'+1", KnowledgeCsv.safeCell("+1"));
        assertEquals("'-1", KnowledgeCsv.safeCell("-1"));
        assertEquals("'@SUM(A1)", KnowledgeCsv.safeCell("@SUM(A1)"));
        assertEquals("\"a,b\"", KnowledgeCsv.csvCell("a,b"));
        assertEquals("\"a\"\"b\"", KnowledgeCsv.csvCell("a\"b"));
    }
}
