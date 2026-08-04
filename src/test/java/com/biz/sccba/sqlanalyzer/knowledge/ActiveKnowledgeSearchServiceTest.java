package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveKnowledgeSearchServiceTest {

    @Test
    void sampleAndAgentGatewayPinTheSameRetrieverToCurrentVersion() {
        KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
        KnowledgeSearchIndex retriever = mock(KnowledgeSearchIndex.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeSearchIndex> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(retriever);
        when(retriever.available()).thenReturn(true);

        Source source = new Source("source_a", "client_a", "policy", "DOCUMENT",
                "version_2", Instant.now(), Instant.now());
        Version active = new Version("version_2", "source_a", 2, "PUBLISHED", "artifact_2",
                "{}", "[]", "admin", Instant.now(), Instant.now());
        when(sources.listSources("client_a")).thenReturn(List.of(source));
        when(sources.findVersionForClient("client_a", "version_2")).thenReturn(Optional.of(active));
        when(retriever.search("client_a", "loan status", "source_a", 2, 5))
                .thenReturn(List.of(new KnowledgeSearchIndex.SearchHit(
                        "ACTIVE and CLOSED", "DOCUMENT", "policy", "source_a", 2,
                        "chunk:0", 0.9, 0.9)));

        var service = new ActiveKnowledgeSearchService(sources, provider);
        var result = service.sample("client_a", "loan status", null, 5);

        assertEquals(1, result.results().size());
        assertEquals(2, result.results().getFirst().versionNo());
        verify(retriever).search("client_a", "loan status", "source_a", 2, 5);
    }

    @Test
    void sourceScopeCannotCrossTenantAndTopKIsWhitelisted() {
        KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
        KnowledgeSearchIndex retriever = mock(KnowledgeSearchIndex.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeSearchIndex> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(retriever);
        when(retriever.available()).thenReturn(true);
        when(sources.findSourceForClient("client_a", "source_b")).thenReturn(Optional.empty());
        var service = new ActiveKnowledgeSearchService(sources, provider);

        assertThrows(IllegalArgumentException.class,
                () -> service.sample("client_a", "query", "source_b", 5));
        assertThrows(IllegalArgumentException.class,
                () -> service.sample("client_a", "query", null, 7));
    }
}
