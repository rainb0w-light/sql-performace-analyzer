package com.biz.sccba.sqlanalyzer.idea.client;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisEvent;
import org.junit.Test;

import static org.junit.Assert.*;

public class AguiStateMapperTest {
    @Test
    public void mapsOnlyStructuredEventsAndKeepsCursor() {
        var started = AguiStateMapper.map("41", "RUN_STARTED", "{\"message\":\"失败 完成\"}");
        assertTrue(started.stream().anyMatch(event -> event instanceof AnalysisEvent.EventConsumed));
        assertTrue(started.stream().anyMatch(event -> event instanceof AnalysisEvent.RunStarted));

        var text = AguiStateMapper.map("42", "TEXT_MESSAGE_CONTENT",
                "{\"delta\":\"运行完成但这只是自然语言\"}");
        assertEquals(1, text.size());
        assertTrue(text.get(0) instanceof AnalysisEvent.EventConsumed);

        var report = AguiStateMapper.map("43", "CUSTOM",
                "{\"name\":\"spa.report_ready\",\"reportId\":\"report_1\"}");
        assertTrue(report.stream().anyMatch(event ->
                event instanceof AnalysisEvent.ReportReady ready && "report_1".equals(ready.reportId())));
    }

    @Test
    public void cancelledAndFailedAreDifferentStructuredFacts() {
        assertTrue(AguiStateMapper.map("1", "RUN_ERROR",
                        "{\"code\":\"CANCELLED\",\"message\":\"cancelled\"}").stream()
                .anyMatch(event -> event instanceof AnalysisEvent.Cancelled));
        assertTrue(AguiStateMapper.map("2", "RUN_ERROR",
                        "{\"code\":\"UNSUPPORTED\",\"message\":\"driver\",\"retryable\":false}").stream()
                .anyMatch(event -> event instanceof AnalysisEvent.Failed failed
                        && !failed.error().retryable()));
    }
}

