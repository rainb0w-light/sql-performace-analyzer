package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import java.util.List;

/**
 * Semantic knowledge index abstraction used by application services.
 *
 * <p>The previous direct dependency on {@link KnowledgeRetriever} is kept only behind the
 * adapter in this package, so callers can migrate without tying business services to AgentScope
 * knowledge internals.</p>
 */
public interface KnowledgeSearchIndex {

    /** True when semantic retrieval is enabled and initialized for this process. */
    boolean available();

    /** Indexes chunks of one published knowledge version, scoped to the owning client. */
    void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks);

    /** Semantic search scoped to the client; optionally filtered by source. */
    List<SearchHit> search(String clientId, String query, String sourceId, int limit);

    /**
     * Semantic search pinned to an explicit active version.
     *
     * @implSpec default implementation performs filtering on top of the base search result set.
     */
    default List<SearchHit> search(String clientId, String query, String sourceId,
                                  int versionNo, int limit) {
        return search(clientId, query, sourceId, Math.max(limit, 20)).stream()
                .filter(hit -> hit.versionNo() == versionNo)
                .limit(Math.max(1, limit))
                .toList();
    }

    /** One indexable piece of knowledge with provenance fields retained in metadata. */
    record Chunk(String kind, String name, String locator, String text) {}

    /**
     * One retrieved fact with evidence (source/version/locator) + scores:
     * {@code confidence} from backend, and raw retrieval {@code score}.
     */
    record SearchHit(String text, String kind, String name, String sourceId, int versionNo,
                    String locator, double confidence, double score) {}
}
