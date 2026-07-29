package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Structural regression gate for the persistent context header in narrow Tool Windows. */
public class ToolWindowLayoutContractTest {
    @Test
    public void contextHeaderCannotBeSqueezedOutByTheActionToolbar() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/ui/SqlAnalyzerToolWindowFactory.java"));

        assertTrue("statement/datasource/knowledge/profile/run context must occupy its own row",
                source.contains("context.add(labels, BorderLayout.NORTH)"));
        assertTrue("the action toolbar must use the full row below the persistent context",
                source.contains("context.add(toolbar(), BorderLayout.CENTER)"));
        assertTrue("toolbar actions must start at the visible leading edge",
                source.contains("new FlowLayout(FlowLayout.LEFT)"));
    }
}
