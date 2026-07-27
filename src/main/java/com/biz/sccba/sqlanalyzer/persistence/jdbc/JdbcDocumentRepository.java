package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DocumentChunkEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DocumentEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.DocumentChunkJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.DocumentJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcDocumentRepository implements DocumentRepository {

    private final DocumentJdbcRepository documents;
    private final DocumentChunkJdbcRepository chunks;
    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(DocumentJdbcRepository documents, DocumentChunkJdbcRepository chunks,
                                  @Qualifier("managementJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.documents = documents;
        this.chunks = chunks;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String create(String id, String artifactId, String documentType, String parserName,
                         String parserVersion, String normalizedText, String structuredData) {
        DocumentEntity entity = new DocumentEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setArtifactId(artifactId);
        entity.setDocumentType(documentType);
        entity.setParserName(parserName);
        entity.setParserVersion(parserVersion);
        entity.setNormalizedText(normalizedText);
        entity.setStructuredData(structuredData == null ? "{}" : structuredData);
        entity.setStatus("PARSED");
        entity.markNew();
        documents.save(entity);
        return id;
    }

    @Override
    public void addChunk(String id, String documentId, int sequenceNo, String chunkType,
                         String content, int tokenCount, String metadata) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(id);
        entity.setDocumentId(documentId);
        entity.setSequenceNo(sequenceNo);
        entity.setChunkType(chunkType);
        entity.setContent(content);
        entity.setTokenCount(tokenCount);
        entity.setMetadata(metadata == null ? "{}" : metadata);
        entity.markNew();
        chunks.save(entity);
    }

    @Override
    public List<ContextChunk> listChunksForSession(String clientId, String sessionId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("SELECT d.id, d.artifact_id, a.source_type, c.sequence_no, c.chunk_type, c.content, c.metadata "
                        + "FROM sql_analyzer.document_chunk c JOIN sql_analyzer.document d ON d.id = c.document_id "
                        + "JOIN sql_analyzer.artifact a ON a.id = d.artifact_id "
                        + "WHERE a.client_id = ? AND a.session_id = ? "
                        + "ORDER BY a.created_at, d.id, c.sequence_no LIMIT ?",
                (rs, n) -> new ContextChunk(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)),
                clientId, sessionId, boundedLimit);
    }

    @Override
    public List<ContextChunk> listChunksForArtifacts(String clientId, List<String> artifactIds, int limit) {
        if (artifactIds == null || artifactIds.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(artifactIds.size(), "?"));
        String sql = "SELECT d.id, d.artifact_id, a.source_type, c.sequence_no, c.chunk_type, c.content, c.metadata "
                + "FROM sql_analyzer.document_chunk c JOIN sql_analyzer.document d ON d.id = c.document_id "
                + "JOIN sql_analyzer.artifact a ON a.id = d.artifact_id WHERE a.client_id = ? AND a.id IN (" + placeholders + ") "
                + "ORDER BY a.created_at, d.id, c.sequence_no LIMIT ?";
        List<Object> args = new ArrayList<>();
        args.add(clientId);
        args.addAll(artifactIds);
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.query(sql, (rs, n) -> new ContextChunk(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7)), args.toArray());
    }
}
