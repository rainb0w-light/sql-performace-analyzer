package com.biz.sccba.sqlanalyzer.idea;

import org.junit.Test;

import java.nio.file.*;

import static org.junit.Assert.*;

public class SecurityAndScopeGateTest {
    @Test
    public void tokenIsNeverRenderedPersistedLoggedOrCopied() throws Exception {
        String toolWindow = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/ui/SqlAnalyzerToolWindowFactory.java"));
        String settings = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/settings/ProjectAnalyzerSettings.java"));
        assertFalse(toolWindow.contains("JPasswordField"));
        assertFalse(toolWindow.contains("setText(token"));
        assertFalse(toolWindow.contains("getSystemClipboard().setContents(token"));
        assertFalse(settings.contains("public String token"));
        assertFalse(settings.contains("accessToken"));
    }

    @Test
    public void p1UiHasFourTabsDmlBannerNoPdfOrFakeShareAndNoServerDelete() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/ui/SqlAnalyzerToolWindowFactory.java"));
        assertTrue(source.contains("tabs.addTab(\"报告\""));
        assertTrue(source.contains("tabs.addTab(\"场景矩阵\""));
        assertTrue(source.contains("tabs.addTab(\"证据\""));
        assertTrue(source.contains("tabs.addTab(\"运行日志\""));
        assertTrue(source.contains("不会执行 DML、DDL 或 EXPLAIN ANALYZE"));
        assertFalse(source.contains("PDF"));
        assertFalse(source.contains("复制报告链接"));
        String client = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/client/BackendClient.java"));
        assertFalse(client.contains("DELETE\", \"/api/v1/reports"));
    }

    @Test
    public void transientRulePreviewIsExplicitNotDocumentDriven() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/ui/TransientRulesDialog.java"));
        assertTrue(source.contains("previewButton.addActionListener"));
        assertFalse(source.contains("DocumentListener"));
        assertFalse(source.contains("KeyListener"));
    }
}
