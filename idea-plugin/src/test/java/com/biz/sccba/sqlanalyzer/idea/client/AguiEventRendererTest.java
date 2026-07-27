package com.biz.sccba.sqlanalyzer.idea.client;

import org.junit.Test;

import static org.junit.Assert.*;

/** AG-UI event rendering contract: incremental text, tool markers, type-based terminal detection. */
public class AguiEventRendererTest {

    @Test
    public void textDeltasRenderIncrementally() {
        assertEquals("hel", AguiEventRenderer.render("TEXT_MESSAGE_CONTENT", "{\"delta\":\"hel\"}"));
        assertEquals("lo", AguiEventRenderer.render("TEXT_MESSAGE_CONTENT", "{\"delta\":\"lo\"}"));
    }

    @Test
    public void toolCallsAndErrorsAreMarked() {
        assertTrue(AguiEventRenderer.render("TOOL_CALL_START", "{\"toolCallName\":\"explain\"}").contains("explain"));
        String error = AguiEventRenderer.render("RUN_ERROR", "{\"code\":\"INTERNAL\",\"message\":\"boom\"}");
        assertTrue(error.contains("INTERNAL"));
        assertTrue(error.contains("boom"));
    }

    @Test
    public void terminalIsDetectedByTypeNotContent() {
        assertTrue(AguiEventRenderer.isTerminal("RUN_FINISHED"));
        assertFalse(AguiEventRenderer.isTerminal("RUN_ERROR"));
        assertFalse(AguiEventRenderer.isTerminal("TEXT_MESSAGE_CONTENT"));
        // raw-text substring matches must NOT be treated as terminal events
        assertFalse(AguiEventRenderer.isTerminal("CUSTOM"));
    }

    @Test
    public void unknownAndMalformedEventsDoNotThrow() {
        assertNull(AguiEventRenderer.render("STATE_DELTA", "{\"whatever\":1}"));
        assertEquals("", AguiEventRenderer.render("TEXT_MESSAGE_CONTENT", "not-json"));
        assertNull(AguiEventRenderer.render(null, "{}"));
    }
}
