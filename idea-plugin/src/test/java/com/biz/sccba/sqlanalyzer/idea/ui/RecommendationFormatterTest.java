package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecommendationFormatterTest {
    @Test
    public void formatsStructuredRecommendationAndSelectsFirstId() {
        var result = RecommendationFormatter.format("[{\"id\":\"rec-1\",\"title\":\"添加索引\","
                + "\"problem\":\"全表扫描\",\"impact\":\"降低延迟\",\"priority\":\"HIGH\","
                + "\"status\":\"PROPOSED\",\"suggestedSql\":\"CREATE INDEX idx_x ON t(x)\"}]");
        assertEquals("rec-1", result.firstRecommendationId());
        assertTrue(result.text().contains("添加索引"));
        assertTrue(result.text().contains("全表扫描"));
        assertTrue(result.text().contains("HIGH / PROPOSED"));
    }

    @Test
    public void keepsMalformedPayloadVisibleInsteadOfThrowing() {
        var result = RecommendationFormatter.format("not-json");
        assertEquals("not-json", result.text());
        assertTrue(result.firstRecommendationId().isBlank());
    }
}
