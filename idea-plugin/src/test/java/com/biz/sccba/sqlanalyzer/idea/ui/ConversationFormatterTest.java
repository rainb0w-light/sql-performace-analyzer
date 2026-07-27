package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ConversationFormatterTest {
    @Test
    public void formatsUserAndAssistantMessages() {
        String result = ConversationFormatter.format("[{\"role\":\"USER\",\"content\":\"select 1\"},"
                + "{\"role\":\"ASSISTANT\",\"content\":\"建议添加索引\"}]");
        assertTrue(result.contains("[USER] select 1"));
        assertTrue(result.contains("[ASSISTANT] 建议添加索引"));
    }
}
