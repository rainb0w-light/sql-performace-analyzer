package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import java.util.List;

/**
 * Vendor-neutral semantic knowledge retrieval port (docs/cloud-code-next-goal.md §3.3/§5.4).
 * PostgreSQL is backed by PgVector; H2/dev by a portable embedding table with JVM-side cosine
 * similarity over the client's candidate set. Both honor the same contract: every retrieved fact
 * carries its evidence — kind, name, source, version, locator and confidence — never an
 * unattributed model summary, and results never cross tenant boundaries.
 */
public interface KnowledgeRetriever {

    /** True when the retrieval backend is initialized (embedding model available). */
    boolean available();

    /** Indexes chunks of one published knowledge version, scoped to the owning client. */
    void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks);

    /** Semantic search scoped to the client; optionally filtered to one source. */
    List<RetrievedFact> search(String clientId, String query, String sourceId, int limit);

    /**
     * Semantic search pinned to the active version selected by the structured knowledge
     * lifecycle. Implementations must not return chunks from inactive versions.
     */
    default List<RetrievedFact> search(String clientId, String query, String sourceId,
                                       int versionNo, int limit) {
        return search(clientId, query, sourceId, Math.max(limit, 20)).stream()
                .filter(fact -> fact.versionNo() == versionNo)
                .limit(Math.max(1, limit))
                .toList();
    }

    /** One indexable piece of knowledge with provenance. */
    record Chunk(String kind, String name, String locator, String text) {}

    /** One retrieved fact with the evidence triple (source/version/locator) + confidence/score. */
    record RetrievedFact(String text, String kind, String name, String sourceId, int versionNo,
                         String locator, double confidence, double score) {}
}
