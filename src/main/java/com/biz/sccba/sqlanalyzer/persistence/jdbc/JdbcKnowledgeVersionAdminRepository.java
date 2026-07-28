package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.repository.KnowledgeVersionAdminRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcKnowledgeVersionAdminRepository implements KnowledgeVersionAdminRepository {

    private static final String SELECT = """
            SELECT v.id, v.source_id, v.version_no, v.status, v.artifact_id, v.content_hash,
                   v.file_name, v.media_type, v.file_size, v.chunk_count,
                   v.processing_error_code, v.created_at, v.updated_at
              FROM sql_analyzer.knowledge_version v
              JOIN sql_analyzer.knowledge_source s ON s.id = v.source_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcKnowledgeVersionAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AdminVersion> findForClient(String clientId, String versionId) {
        return jdbc.query(SELECT + " WHERE s.client_id = :clientId AND v.id = :versionId",
                new MapSqlParameterSource("clientId", clientId).addValue("versionId", versionId),
                JdbcKnowledgeVersionAdminRepository::map).stream().findFirst();
    }

    @Override
    public Optional<AdminVersion> findByContentHash(String clientId, String sourceId, String contentHash) {
        return jdbc.query(SELECT + """
                 WHERE s.client_id = :clientId AND v.source_id = :sourceId
                   AND v.content_hash = :contentHash
                 ORDER BY v.created_at DESC
                """, new MapSqlParameterSource("clientId", clientId)
                        .addValue("sourceId", sourceId).addValue("contentHash", contentHash),
                JdbcKnowledgeVersionAdminRepository::map).stream().findFirst();
    }

    @Override
    public void updateUploadMetadata(String clientId, String versionId, String contentHash,
                                     String fileName, String mediaType, long fileSize, String status) {
        int changed = jdbc.update("""
                UPDATE sql_analyzer.knowledge_version
                   SET content_hash = :contentHash, file_name = :fileName, media_type = :mediaType,
                       file_size = :fileSize, status = :status, updated_at = CURRENT_TIMESTAMP
                 WHERE id = :versionId AND EXISTS (
                       SELECT 1 FROM sql_analyzer.knowledge_source s
                        WHERE s.id = source_id AND s.client_id = :clientId)
                """, params(clientId, versionId).addValue("contentHash", contentHash)
                .addValue("fileName", fileName).addValue("mediaType", mediaType)
                .addValue("fileSize", fileSize).addValue("status", status));
        requireChanged(changed);
    }

    @Override
    public void markReady(String clientId, String versionId, String previewJson, int chunkCount) {
        int changed = jdbc.update("""
                UPDATE sql_analyzer.knowledge_version
                   SET preview_json = :previewJson, chunk_count = :chunkCount, status = 'READY',
                       processing_error_code = NULL, error_json = '[]', updated_at = CURRENT_TIMESTAMP
                 WHERE id = :versionId AND EXISTS (
                       SELECT 1 FROM sql_analyzer.knowledge_source s
                        WHERE s.id = source_id AND s.client_id = :clientId)
                """, params(clientId, versionId).addValue("previewJson", previewJson)
                .addValue("chunkCount", chunkCount));
        requireChanged(changed);
    }

    @Override
    public void updateStatus(String clientId, String versionId, String status, String errorCode) {
        int changed = jdbc.update("""
                UPDATE sql_analyzer.knowledge_version
                   SET status = :status, processing_error_code = :errorCode,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = :versionId AND EXISTS (
                       SELECT 1 FROM sql_analyzer.knowledge_source s
                        WHERE s.id = source_id AND s.client_id = :clientId)
                """, params(clientId, versionId).addValue("status", status).addValue("errorCode", errorCode));
        requireChanged(changed);
    }

    private static MapSqlParameterSource params(String clientId, String versionId) {
        return new MapSqlParameterSource("clientId", clientId).addValue("versionId", versionId);
    }

    private static void requireChanged(int changed) {
        if (changed != 1) throw new IllegalArgumentException("知识版本不存在");
    }

    private static AdminVersion map(ResultSet rs, int row) throws SQLException {
        return new AdminVersion(rs.getString("id"), rs.getString("source_id"),
                rs.getInt("version_no"), rs.getString("status"), rs.getString("artifact_id"),
                rs.getString("content_hash"), rs.getString("file_name"), rs.getString("media_type"),
                (Long) rs.getObject("file_size"), rs.getInt("chunk_count"),
                rs.getString("processing_error_code"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
