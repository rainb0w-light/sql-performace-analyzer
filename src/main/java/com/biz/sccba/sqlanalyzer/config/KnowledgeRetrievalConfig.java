package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.knowledge.retrieval.Embedder;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.OpenAiCompatibleEmbedder;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.PgVectorKnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.PortableEmbeddingKnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.persistence.dialect.ManagementDatabaseDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

/**
 * Semantic retrieval wiring (docs/cloud-code-next-goal.md §3.3): one {@link KnowledgeRetriever}
 * bean selected by the management dialect — PgVector on PostgreSQL (when vector search is
 * enabled), portable embeddings + JVM cosine on H2. Both require an {@link Embedder}; without a
 * configured embedding endpoint the retriever reports unavailable (structured search still works)
 * — never an in-memory fake in production.
 */
@Configuration
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeRetrievalConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sql-analyzer.knowledge.embedding", name = "api-key")
    public Embedder knowledgeEmbedder(
            @Value("${sql-analyzer.knowledge.embedding.api-key:}") String apiKey,
            @Value("${sql-analyzer.knowledge.embedding.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${sql-analyzer.knowledge.embedding.model:text-embedding-3-small}") String model,
            @Value("${sql-analyzer.knowledge.vector.dimensions:1024}") int dimensions) {
        return new OpenAiCompatibleEmbedder(apiKey, baseUrl, model, dimensions);
    }

    @Bean
    public KnowledgeRetriever knowledgeRetriever(
            @Qualifier("managementDialect") ManagementDatabaseDialect dialect,
            ObjectProvider<Embedder> embedderProvider,
            @Qualifier("managementNamedParameterJdbcTemplate") NamedParameterJdbcTemplate namedJdbc,
            ObjectMapper objectMapper,
            @Value("${sql-analyzer.knowledge.vector.enabled:false}") boolean vectorEnabled,
            @Value("${sql-analyzer.knowledge.vector.jdbc-url:${sql-analyzer.persistence.jdbc-url:}}") String pgJdbcUrl,
            @Value("${sql-analyzer.knowledge.vector.username:${sql-analyzer.persistence.username:}}") String pgUsername,
            @Value("${sql-analyzer.knowledge.vector.password:${sql-analyzer.persistence.password:}}") String pgPassword,
            @Value("${sql-analyzer.knowledge.vector.schema:sql_analyzer}") String schema,
            @Value("${sql-analyzer.knowledge.vector.table:kb_embedding}") String table,
            @Value("${sql-analyzer.knowledge.vector.dimensions:1024}") int dimensions) {
        Embedder embedder = embedderProvider.getIfAvailable();
        if (embedder == null) {
            return new Unavailable();
        }
        return switch (dialect) {
            case H2 -> new PortableEmbeddingKnowledgeRetriever(namedJdbc, embedder, objectMapper);
            case POSTGRESQL -> vectorEnabled
                    ? new PgVectorKnowledgeRetriever(embedder, pgJdbcUrl, pgUsername, pgPassword, schema, table, dimensions)
                    : new Unavailable();
        };
    }

    /** Retrieval disabled (no embedding endpoint / vector search off): structured search remains. */
    static final class Unavailable implements KnowledgeRetriever {
        @Override public boolean available() { return false; }
        @Override public void index(String clientId, String sourceId, int versionNo, List<Chunk> chunks) { }
        @Override public List<RetrievedFact> search(String clientId, String query, String sourceId, int limit) {
            return List.of();
        }
    }
}
