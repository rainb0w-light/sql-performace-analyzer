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
        assertTrue("Java annotation Mapper PSI support must be declared",
                xml.contains("<depends>com.intellij.modules.java</depends>"));
        assertTrue(xml.contains("factoryClass=\"com.biz.sccba.sqlanalyzer.idea.ui.SqlAnalyzerToolWindowFactory\""));
        assertTrue("token secret must live in a PasswordSafe-backed application service",
                xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.settings.TokenStore\""));
        assertTrue("endpoint/session must be project-scoped settings",
                xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings\""));
        assertTrue(xml.contains("serviceImplementation=\"com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisMapperWatcher\""));
        assertTrue("statement analysis must be a registered action, not a manual flow",
                xml.contains("class=\"com.biz.sccba.sqlanalyzer.idea.actions.AnalyzeStatementAction\""));
        assertTrue(xml.contains("<add-to-group group-id=\"EditorPopupMenu\""));
        assertTrue("statement analysis must also be exposed in the gutter",
                xml.contains("MyBatisStatementLineMarkerProvider"));
        assertTrue("Alt/Option+Enter must expose an Intention",
                xml.contains("AnalyzeStatementIntention"));
        assertTrue("IntelliJ intentionAction requires its implementation as the className element",
                xml.contains("<className>com.biz.sccba.sqlanalyzer.idea.actions.AnalyzeStatementIntention</className>"));
        assertFalse("implementationClass is ignored by IntelliJ's intentionAction extension bean",
                xml.contains("<intentionAction implementationClass="));
        assertTrue("project settings must be searchable under Tools",
                xml.contains("<projectConfigurable id=\"tools.sql.analyzer\""));
        assertFalse("the plugin must not occupy IntelliJ's Ctrl/Cmd+Shift+A shortcut",
                xml.contains("first-keystroke=\"control shift A\"")
                        || xml.contains("first-keystroke=\"meta shift A\""));
    }
}
