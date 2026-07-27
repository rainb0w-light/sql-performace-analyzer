package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.PgVectorStore;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL KnowledgeRetriever backed by PgVector (docs/cloud-code-next-goal.md §3.3).
 * The embedding model is the product's {@link Embedder} port bridged into AgentScope, so tests
 * run against a deterministic fake — results never depend on an external embedding service.
 * Every stored document carries clientId/sourceId/version/locator payload and retrieval is
 * filtered to the owning client.
 */
public final class PgVectorKnowledgeRetriever implements KnowledgeRetriever {

    private final Embedder embedder;
    private final SimpleKnowledge knowledge;

    public PgVectorKnowledgeRetriever(Embedder embedder, String jdbcUrl, String username, String password,
                                      String schema, String table, int dimensions) {
        this.embedder = embedder;
        try {
            PgVectorStore store = PgVectorStore.builder()
                    .jdbcUrl(jdbcUrl).username(username).password(password)
                    .schema(schema).tableName(table).dimensions(dimensions)
                    .build();
            this.knowledge = SimpleKnowledge.builder()
                    .embeddingModel(new EmbedderBridge(embedder))
                    .embeddingStore(store)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("PgVector 知识库初始化失败（pgvector 扩展或连接不可用？）", e);
        }
    }

    @Override
    public boolean available() {
        return embedder != null;
    }

    @Override
    public void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks) {
        List<Document> docs = new ArrayList<>();
        for (Chunk chunk : chunks) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", clientId);
            payload.put("sourceId", sourceId);
            payload.put("versionNo", versionNo);
            payload.put("kind", chunk.kind());
            payload.put("name", chunk.name());
            payload.put("locator", chunk.locator());
            String id = "kb_" + UUID.randomUUID();
            docs.add(new Document(DocumentMetadata.builder()
                    .docId(id)
                    .chunkId(id)
                    .content(TextBlock.builder().text(chunk.text()).build())
                    .payload(payload)
                    .build()));
        }
        if (!docs.isEmpty()) {
            knowledge.addDocuments(docs).block(Duration.ofSeconds(60));
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
        // Threshold 0.0: rank by similarity, keep non-negative cosine scores; the caller
        // decides the cut (library contract requires stable ordering, not an opaque default).
        List<Document> found = knowledge.retrieve(query, RetrieveConfig.builder()
                .limit(Math.max(1, limit) * 3)
                .scoreThreshold(0.0)
                .build()).block(Duration.ofSeconds(30));
        List<RetrievedFact> out = new ArrayList<>();
        if (found == null) return out;
        for (Document d : found) {
            if (!clientId.equals(String.valueOf(d.getPayloadValue("clientId")))) continue;
            String docSource = String.valueOf(d.getPayloadValue("sourceId"));
            if (sourceId != null && !sourceId.isBlank() && !sourceId.equals(docSource)) continue;
            int docVersion = d.getPayloadValue("versionNo") instanceof Number n ? n.intValue() : 0;
            if (versionNo != null && versionNo != docVersion) continue;
            double score = d.getScore() == null ? 0.0 : d.getScore();
            out.add(new RetrievedFact(d.getMetadata().getContentText(),
                    String.valueOf(d.getPayloadValue("kind")),
                    String.valueOf(d.getPayloadValue("name")),
                    docSource,
                    docVersion,
                    String.valueOf(d.getPayloadValue("locator")),
                    Math.max(0.0, Math.min(1.0, score)), score));
            if (out.size() >= Math.max(1, limit)) break;
        }
        return out;
    }

    /** Bridges the product Embedder port into AgentScope's EmbeddingModel. */
    static final class EmbedderBridge implements EmbeddingModel {
        private final Embedder embedder;

        EmbedderBridge(Embedder embedder) {
            this.embedder = embedder;
        }

        @Override
        public Mono<double[]> embed(ContentBlock block) {
            String text = block instanceof TextBlock tb ? tb.getText() : String.valueOf(block);
            return Mono.fromCallable(() -> embedder.embed(text));
        }

        @Override
        public String getModelName() {
            return embedder.modelName();
        }

        @Override
        public int getDimensions() {
            return embedder.dimensions();
        }
    }
}
