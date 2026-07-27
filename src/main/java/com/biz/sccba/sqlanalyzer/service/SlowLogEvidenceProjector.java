package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.agent.AgentExecutionContext;
import com.biz.sccba.sqlanalyzer.agent.SlowLogEvidenceSink;
import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Projects tool-fetched slow-log samples into the run's durable context pipeline. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public final class SlowLogEvidenceProjector implements SlowLogEvidenceSink {
    private final ArtifactPipelineService pipeline;

    public SlowLogEvidenceProjector(ArtifactPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public String persist(AgentExecutionContext context, SlowLogSource.SlowLogBatch batch) {
        return pipeline.ingestSlowLog(context.clientId(), context.sessionId(), context.runId(), batch).artifactId();
    }
}
