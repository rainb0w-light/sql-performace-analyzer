package com.biz.sccba.sqlanalyzer.repository;

import java.util.List;

/** Parsed documents and their chunks (context evidence for analysis). */
public interface DocumentRepository {
    String create(String id, String artifactId, String documentType, String parserName,
                  String parserVersion, String normalizedText, String structuredData);

    void addChunk(String id, String documentId, int sequenceNo, String chunkType,
                  String content, int tokenCount, String metadata);

    List<ContextChunk> listChunksForSession(String clientId, String sessionId, int limit);

    List<ContextChunk> listChunksForArtifacts(String clientId, List<String> artifactIds, int limit);

    record ContextChunk(String documentId, String artifactId, String documentType,
                        int sequenceNo, String chunkType, String content, String metadata) {}
}
