package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Single active-version retrieval gateway used by both Agent search and the Admin sampling API.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ActiveKnowledgeSearchService {

    private static final Pattern EMAIL =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern SECRET =
            Pattern.compile("(?i)(bearer|api[_-]?key|token|password)\\s*[:=]\\s*\\S+");

    private final KnowledgeSourceRepository sources;
    private final ObjectProvider<KnowledgeRetriever> retrievers;

    public ActiveKnowledgeSearchService(KnowledgeSourceRepository sources,
                                        ObjectProvider<KnowledgeRetriever> retrievers) {
        this.sources = sources;
        this.retrievers = retrievers;
    }

    public SearchResponse search(String clientId, String query, String sourceId, int limit) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Query 不能为空");
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        KnowledgeRetriever retriever = retrievers.getIfAvailable();
        long started = System.nanoTime();
        if (retriever == null || !retriever.available()) {
            return new SearchResponse(false, List.of(), elapsedMs(started));
        }

        List<Source> scope;
        if (sourceId == null || sourceId.isBlank()) {
            scope = sources.listSources(clientId).stream()
                    .filter(source -> source.currentVersionId() != null)
                    .toList();
        } else {
            Source source = sources.findSourceForClient(clientId, sourceId)
                    .orElseThrow(() -> new IllegalArgumentException("知识源不存在"));
            scope = source.currentVersionId() == null ? List.of() : List.of(source);
        }

        List<KnowledgeRetriever.RetrievedFact> candidates = new ArrayList<>();
        for (Source source : scope) {
            var active = sources.findVersionForClient(clientId, source.currentVersionId()).orElse(null);
            if (active == null || !(active.isPublished() || "ACTIVE".equals(active.status()))) continue;
            candidates.addAll(retriever.search(
                    clientId, query, source.id(), active.versionNo(), boundedLimit));
        }
        candidates.sort(Comparator.comparingDouble(KnowledgeRetriever.RetrievedFact::score).reversed()
                .thenComparing(KnowledgeRetriever.RetrievedFact::sourceId)
                .thenComparing(KnowledgeRetriever.RetrievedFact::locator));
        List<SearchHit> hits = candidates.stream().limit(boundedLimit)
                .map(fact -> new SearchHit(redact(fact.text()), fact.kind(), fact.name(),
                        fact.sourceId(), fact.versionNo(), fact.locator(), fact.score()))
                .toList();
        return new SearchResponse(true, hits, elapsedMs(started));
    }

    public SearchResponse sample(String clientId, String query, String sourceId, int topK) {
        if (topK != 5 && topK != 10) throw new IllegalArgumentException("topK 只允许 5 或 10");
        return search(clientId, query, sourceId, topK);
    }

    private static String redact(String input) {
        if (input == null) return "";
        String redacted = EMAIL.matcher(input).replaceAll("[REDACTED_EMAIL]");
        redacted = SECRET.matcher(redacted).replaceAll("$1=[REDACTED]");
        return redacted.length() <= 1200 ? redacted : redacted.substring(0, 1200) + "…";
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record SearchHit(String text, String kind, String name, String sourceId, int versionNo,
                            String locator, double score) {}

    public record SearchResponse(boolean available, List<SearchHit> results, long durationMs) {}
}
