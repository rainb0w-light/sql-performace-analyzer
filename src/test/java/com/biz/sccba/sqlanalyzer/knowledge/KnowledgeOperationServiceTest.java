package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.KnowledgeOperation;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeOperationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    @Test
    void summaryNeverPersistsQueryOrTokenAndStatsDeduplicateSourcesPerRetrieve() {
        FakeRepository repository = new FakeRepository();
        var service = new KnowledgeOperationService(
                repository, new ObjectMapper(), 10, Clock.fixed(NOW, ZoneOffset.UTC));

        service.record("trace_1", "client_a", "agent_a", "AGENT_CLIENT", "RETRIEVE",
                null, null, null, null,
                Map.of("query", "raw secret query", "token", "Bearer secret",
                        "queryLength", 16, "queryHash", "abc",
                        "hitSourceIds", List.of("source_a", "source_a", "source_b")),
                "SUCCESS", null, 10, 3, null);
        service.record("trace_2", "client_a", "agent_a", "AGENT_CLIENT", "RETRIEVE",
                null, null, null, null,
                KnowledgeOperationService.querySummary("another raw query", 5, null, List.of("source_a")),
                "FAILED", "RETRIEVAL_FAILED", 30, 0, null);

        String stored = repository.rows.getFirst().requestSummaryJson();
        assertFalse(stored.contains("raw secret query"));
        assertFalse(stored.contains("Bearer secret"));
        assertTrue(stored.contains("\"queryLength\":16"));

        var stats = service.stats("client_a");
        assertEquals(2, stats.agentRetrievals());
        assertEquals(0.5, stats.agentRetrievalSuccessRate());
        assertEquals("source_a", stats.popularSourcesTop5().getFirst().key());
        assertEquals(2, stats.popularSourcesTop5().getFirst().count());
        assertEquals("source_b", stats.popularSourcesTop5().get(1).key());
        assertEquals(1, stats.popularSourcesTop5().get(1).count());
    }

    @Test
    void csvUsesTenantRowsAndEscapesFormulaInjection() {
        FakeRepository repository = new FakeRepository();
        var service = new KnowledgeOperationService(
                repository, new ObjectMapper(), 10, Clock.fixed(NOW, ZoneOffset.UTC));
        service.record("trace_1", "client_a", "=HYPERLINK(\"x\")", "KNOWLEDGE_ADMIN",
                "UPLOAD", "source_a", "version_a", null, null, Map.of(),
                "SUCCESS", null, 2, 1, null);
        service.record("trace_2", "client_b", "other", "KNOWLEDGE_ADMIN",
                "UPLOAD", "source_b", "version_b", null, null, Map.of(),
                "SUCCESS", null, 2, 1, null);

        String csv = new String(service.exportCsv("client_a", null), StandardCharsets.UTF_8);
        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("source_a"));
        assertFalse(csv.contains("source_b"));
    }

    private static final class FakeRepository implements KnowledgeOperationRepository {
        private final List<KnowledgeOperation> rows = new ArrayList<>();

        @Override
        public KnowledgeOperation append(KnowledgeOperation operation) {
            rows.add(operation);
            return operation;
        }

        @Override
        public Page find(String clientId, Filter filter, int page, int size) {
            List<KnowledgeOperation> selected = selected(clientId);
            return new Page(selected.stream().skip((long) page * size).limit(size).toList(),
                    page, size, selected.size());
        }

        @Override
        public List<KnowledgeOperation> findForExport(String clientId, Filter filter, int limit) {
            return selected(clientId).stream().limit(limit).toList();
        }

        private List<KnowledgeOperation> selected(String clientId) {
            return rows.stream().filter(row -> row.clientId().equals(clientId)).toList();
        }
    }
}
