package com.biz.sccba.sqlanalyzer.agent;

import com.biz.sccba.sqlanalyzer.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;

/** Management-database-backed ContextBuilder (vendor-neutral Spring Data JDBC). */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public final class JdbcContextBuilder implements ContextBuilder {
    private static final int MAX_CHUNKS = 40;
    private static final int MAX_CONTEXT_CHARS = 120_000;
    private final DocumentRepository documents;

    public JdbcContextBuilder(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public String build(String clientId, String sessionId, String userContent, List<String> artifactIds) {
        StringBuilder context = new StringBuilder(userContent == null ? "" : userContent);
        var chunks = artifactIds == null || artifactIds.isEmpty()
                ? documents.listChunksForSession(clientId, sessionId, MAX_CHUNKS)
                : documents.listChunksForArtifacts(clientId, artifactIds, MAX_CHUNKS);
        if (chunks.isEmpty()) return context.toString();
        context.append("\n\n--- 已关联上下文 ---\n");
        for (var chunk : chunks) {
            if (context.length() >= MAX_CONTEXT_CHARS) break;
            String text = chunk.content() == null ? "" : chunk.content();
            int remaining = MAX_CONTEXT_CHARS - context.length();
            context.append("[artifact=").append(chunk.artifactId())
                    .append(",type=").append(chunk.documentType())
                    .append(",chunk=").append(chunk.sequenceNo()).append("]\n")
                    .append(text, 0, Math.min(text.length(), remaining)).append("\n");
        }
        return context.toString();
    }
}
