package com.biz.sccba.sqlanalyzer.idea.mybatis;

import org.junit.Test;

import static org.junit.Assert.*;

public class GutterAnalysisStateTest {
    @Test
    public void contentOrDatasourceChangeMakesCompletionStale() {
        GutterAnalysisState state = new GutterAnalysisState();
        state.mark("n#id", GutterAnalysisState.Status.COMPLETED,
                "hash_1", "dsp_1", "HIGH", "完成");
        assertEquals(GutterAnalysisState.Status.COMPLETED,
                state.get("n#id", "hash_1", "dsp_1").status());
        assertEquals(GutterAnalysisState.Status.STALE,
                state.get("n#id", "hash_2", "dsp_1").status());
        assertEquals(GutterAnalysisState.Status.STALE,
                state.get("n#id", "hash_1", "dsp_2").status());
        assertTrue(state.get("n#id", "hash_2", "dsp_1").message().contains("过期"));
    }
}
