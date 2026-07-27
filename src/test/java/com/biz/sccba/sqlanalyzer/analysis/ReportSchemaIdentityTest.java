package com.biz.sccba.sqlanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Single source of truth: the classpath schema used for runtime validation must be byte-identical
 * to the frozen contract in docs/contracts/report-schema.json.
 */
class ReportSchemaIdentityTest {

    @Test
    void classpathSchemaMatchesDocsContract() throws Exception {
        byte[] docs = Files.readAllBytes(Path.of("docs/contracts/report-schema.json"));
        byte[] classpath;
        try (InputStream in = getClass().getResourceAsStream("/contracts/report-schema.json")) {
            classpath = in.readAllBytes();
        }
        assertArrayEquals(docs, classpath,
                "src/main/resources/contracts/report-schema.json must stay byte-identical to docs/contracts/report-schema.json");
    }
}
