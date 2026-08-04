package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.LegacyKnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.PgVectorKnowledgeRetriever;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Docker gate (RUN_POSTGRES_INTEGRATION_TESTS=true, CI-enforced): the same retriever contract on
 * the PgVector adapter, driven by the deterministic fake embedding model — the vector store is
 * real, the embeddings never depend on an external service.
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class KnowledgeRetrievalPgTest extends KnowledgeRetrievalContractTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static {
        POSTGRES.start();
        // The image ships the extension binaries; enabling the extension and the product schema
        // is an operational step (mirrors production provisioning).
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute("CREATE SCHEMA IF NOT EXISTS sql_analyzer");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final KnowledgeSearchIndex RETRIEVER = new LegacyKnowledgeSearchIndex(new PgVectorKnowledgeRetriever(
            new DeterministicFakeEmbedder(128),
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(),
            "sql_analyzer", "kb_embedding", 128));

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Override
    KnowledgeSearchIndex retriever() {
        return RETRIEVER;
    }

}
