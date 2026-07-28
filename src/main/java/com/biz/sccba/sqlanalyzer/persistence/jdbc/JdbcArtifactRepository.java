package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.Artifact;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ArtifactEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ArtifactJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcArtifactRepository implements ArtifactRepository {

    private final ArtifactJdbcRepository jdbc;
    private final JdbcTemplate jdbcTemplate;

    public JdbcArtifactRepository(ArtifactJdbcRepository jdbc,
                                  @Qualifier("managementJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Artifact create(Artifact artifact) {
        ArtifactEntity entity = new ArtifactEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(artifact.id());
        entity.setClientId(artifact.clientId());
        entity.setSessionId(artifact.sessionId());
        entity.setSourceType(artifact.sourceType());
        entity.setFileName(artifact.fileName());
        entity.setMediaType(artifact.mediaType());
        entity.setSha256(artifact.sha256());
        entity.setByteSize(artifact.byteSize());
        entity.setStatus(artifact.status());
        entity.setMetadata(artifact.metadataJson());
        entity.markNew();
        return toDomain(jdbc.save(entity));
    }

    @Override
    public void writeChunk(String artifactId, int sequence, byte[] content) {
        jdbcTemplate.update("INSERT INTO sql_analyzer.artifact_content(artifact_id, sequence_no, content) VALUES (?, ?, ?)",
                artifactId, sequence, content);
    }

    @Override
    public Optional<byte[]> readAll(String clientId, String artifactId) {
        if (jdbc.findByIdAndClientId(artifactId, clientId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jdbcTemplate.query(
                "SELECT content FROM sql_analyzer.artifact_content WHERE artifact_id = ? ORDER BY sequence_no",
                rs -> {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    while (rs.next()) output.writeBytes(rs.getBytes(1));
                    return output.toByteArray();
                }, artifactId));
    }

    @Override
    public Optional<Artifact> findByIdForClient(String artifactId, String clientId) {
        return jdbc.findByIdAndClientId(artifactId, clientId).map(JdbcArtifactRepository::toDomain);
    }

    @Override
    public Optional<Artifact> findBySha256ForClient(String clientId, String sourceType, String sha256) {
        return jdbc.findBySha256AndClientId(clientId, sourceType, sha256)
                .map(JdbcArtifactRepository::toDomain);
    }

    static Artifact toDomain(ArtifactEntity e) {
        return new Artifact(e.getId(), e.getClientId(), e.getSessionId(), e.getSourceType(), e.getFileName(),
                e.getMediaType(), e.getSha256(), e.getByteSize() == null ? 0 : e.getByteSize(),
                e.getStatus(), e.getMetadata(), e.getCreatedAt());
    }
}
