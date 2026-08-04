package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary adapter to keep production retrieval implementation unchanged while callers migrate
 * from {@link KnowledgeRetriever} to {@link KnowledgeSearchIndex}.
 */
public final class LegacyKnowledgeSearchIndex implements KnowledgeSearchIndex {

    private final KnowledgeRetriever delegate;

    public LegacyKnowledgeSearchIndex(KnowledgeRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean available() {
        return delegate != null && delegate.available();
    }

    @Override
    public void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks) {
        if (delegate == null) return;
        List<KnowledgeRetriever.Chunk> legacy = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) {
            legacy.add(new KnowledgeRetriever.Chunk(c.kind(), c.name(), c.locator(), c.text()));
        }
        delegate.index(clientId, sourceId, versionNo, legacy);
    }

    @Override
    public List<SearchHit> search(String clientId, String query, String sourceId, int limit) {
        if (delegate == null) return List.of();
        List<KnowledgeRetriever.RetrievedFact> hits = delegate.search(clientId, query, sourceId, limit);
        List<SearchHit> out = new ArrayList<>(hits.size());
        for (KnowledgeRetriever.RetrievedFact hit : hits) {
            out.add(new SearchHit(
                    hit.text(), hit.kind(), hit.name(), hit.sourceId(), hit.versionNo(),
                    hit.locator(), hit.confidence(), hit.score()));
        }
        return out;
    }
}
