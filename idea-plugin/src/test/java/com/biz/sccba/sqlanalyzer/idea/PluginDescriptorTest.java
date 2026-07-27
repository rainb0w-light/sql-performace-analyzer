package com.biz.sccba.sqlanalyzer.idea;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class PluginDescriptorTest {
    @Test
    public void descriptorDeclaresToolWindowServicesAndStatementAction() throws Exception {
        Path descriptor = Path.of("src/main/resources/META-INF/plugin.xml");
        assertTrue(Files.exists(descriptor));
        String xml = Files.readString(descriptor);
        assertTrue(xml.contains("<id>com.biz.sccba.sqlanalyzer.idea</id>"));
        assertTrue("XML PSI support must be declared",
                xml.contains("<depends>com.intellij.modules.xml</depends>"));
        assertTrue(xml.contains("factoryClass=\"com.biz.sccba.sqlanalyzer.idea.ui.SqlAnalyzerToolWindowFactory\""));
        assertTrue("token secret must live in a PasswordSafe-backed application service",
                xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.settings.TokenStore\""));
        assertTrue("endpoint/session must be project-scoped settings",
                xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings\""));
        assertTrue(xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisMapperWatcher\""));
        assertTrue("statement analysis must be a registered action, not a manual flow",
                xml.contains("class=\"com.biz.sccba.sqlanalyzer.idea.actions.AnalyzeStatementAction\""));
        assertTrue(xml.contains("<add-to-group group-id=\"EditorPopupMenu\""));
    }
}
