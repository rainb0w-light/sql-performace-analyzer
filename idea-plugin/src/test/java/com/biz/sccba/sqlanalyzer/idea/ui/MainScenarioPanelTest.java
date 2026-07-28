package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * UI structural gate. contractTest intentionally excludes the IDEA runtime; component behavior is
 * backed by MainScenarioModelTest and exercised in the runIde checklist.
 */
public class MainScenarioPanelTest {
    @Test
    public void rendersIfChooseAndForeachWithNativeKeyboardControls() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/ui/MainScenarioPanel.java"));
        assertTrue(source.contains("new JBCheckBox()"));
        assertTrue(source.contains("new JBRadioButton()"));
        assertTrue(source.contains("new ComboBox<>(CollectionMode.values())"));
        assertTrue(source.contains("new ButtonGroup()"));
        assertTrue(source.contains("刷新建议"));
        assertTrue(source.contains("分类："));
        assertTrue(source.contains("getAccessibleContext().setAccessibleName"));
        assertTrue(source.contains("model.enabled(node.nodeId())"));
        assertTrue(source.contains("confirmButton.setEnabled(model.valid())"));
    }
}
