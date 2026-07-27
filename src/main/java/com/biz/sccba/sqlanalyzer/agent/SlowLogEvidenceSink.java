package com.biz.sccba.sqlanalyzer.agent;

import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;

/** Persists tool-fetched slow-log evidence into the common artifact pipeline. */
public interface SlowLogEvidenceSink {
    String persist(AgentExecutionContext context, SlowLogSource.SlowLogBatch batch);
}
