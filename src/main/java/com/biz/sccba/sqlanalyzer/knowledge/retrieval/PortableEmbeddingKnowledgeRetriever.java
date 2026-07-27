package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * H2/dev KnowledgeRetriever (docs/cloud-code-next-goal.md §3.3): embeddings persist as portable
 * JSON float arrays in {@code sql_analyzer.kb_embedding_portable}; retrieval embeds the query
 * and computes cosine similarity in the JVM over the owning client's candidate set (scoped by
 * client, optionally by source). No vector extension, no external service at query time beyond
 * the configured {@link Embedder}.
 */
public final class PortableEmbeddingKnowledgeRetriever implements KnowledgeRetriever {

    private final NamedParameterJdbcTemplate jdbc;
    private final Embedder embedder;
    private final ObjectMapper mapper;

    public PortableEmbeddingKnowledgeRetriever(NamedParameterJdbcTemplate jdbc, Embedder embedder,
                                               ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.mapper = mapper;
    }

    @Override
    public boolean available() {
        return embedder != null;
    }

    @Override
    public void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks) {
        if (embedder == null) return;
        for (Chunk chunk : chunks) {
            jdbc.update("INSERT INTO sql_analyzer.kb_embedding_portable(id, client_id, source_id, version_no, "
                            + "kind, name, locator, content, embedding) "
                            + "VALUES (:id, :clientId, :sourceId, :versionNo, :kind, :name, :locator, :content, :embedding)",
                    new MapSqlParameterSource()
                            .addValue("id", "kbe_" + UUID.randomUUID())
                            .addValue("clientId", clientId)
                            .addValue("sourceId", sourceId)
                            .addValue("versionNo", versionNo)
                            .addValue("kind", chunk.kind())
                            .addValue("name", chunk.name())
                            .addValue("locator", chunk.locator())
                            .addValue("content", chunk.text())
                            .addValue("embedding", toJson(embedder.embed(chunk.text()))));
        }
    }

    @Override
    public List<RetrievedFact> search(String clientId, String query, String sourceId, int limit) {
        return searchInternal(clientId, query, sourceId, null, limit);
    }

    @Override
    public List<RetrievedFact> search(String clientId, String query, String sourceId,
                                      int versionNo, int limit) {
        return searchInternal(clientId, query, sourceId, versionNo, limit);
    }

    private List<RetrievedFact> searchInternal(String clientId, String query, String sourceId,
                                               Integer versionNo, int limit) {
        if (embedder == null) return List.of();
        String sql = "SELECT content, kind, name, source_id, version_no, locator, embedding "
                + "FROM sql_analyzer.kb_embedding_portable WHERE client_id = :clientId"
                + (sourceId == null || sourceId.isBlank() ? "" : " AND source_id = :sourceId")
                + (versionNo == null ? "" : " AND version_no = :versionNo");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("clientId", clientId);
        if (sourceId != null && !sourceId.isBlank()) params.addValue("sourceId", sourceId);
        if (versionNo != null) params.addValue("versionNo", versionNo);

        double[] queryVector = embedder.embed(query);
        List<RetrievedFact> scored = new ArrayList<>();
        jdbc.query(sql, params, rs -> {
            double score = cosine(queryVector, fromJson(rs.getString("embedding")));
            scored.add(new RetrievedFact(rs.getString("content"), rs.getString("kind"), rs.getString("name"),
                    rs.getString("source_id"), rs.getInt("version_no"), rs.getString("locator"),
                    score, score));
        });
        scored.sort(Comparator.comparingDouble(RetrievedFact::score).reversed());
        return scored.subList(0, Math.min(Math.max(1, limit), scored.size()));
    }

    private String toJson(double[] vector) {
        try {
            return mapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException("embedding 序列化失败", e);
        }
    }

    private double[] fromJson(String json) {
        try {
            return mapper.readValue(json, double[].class);
        } catch (Exception e) {
            throw new IllegalStateException("embedding 反序列化失败", e);
        }
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
