package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.LegacyKnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.PortableEmbeddingKnowledgeRetriever;
import contracttest.ContractTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

/**
 * Docker-free gate (every build): the retriever contract on the portable H2 adapter — embeddings
 * persist as JSON in the Flyway-managed table, similarity is computed in the JVM.
 */
class KnowledgeRetrievalH2Test extends KnowledgeRetrievalContractTestBase {

    static ConfigurableApplicationContext ctx;
    static KnowledgeSearchIndex retriever;

    @BeforeAll
    static void start() {
        MapPropertySource props = new MapPropertySource("contract", Map.of(
                "sql-analyzer.persistence.enabled", "true",
                "contract.jdbc-url", "jdbc:h2:mem:knowledge_retrieval_h2;DB_CLOSE_DELAY=-1",
                "contract.username", "sa",
                "contract.password", ""));
        ctx = new SpringApplicationBuilder(ContractTestConfig.class)
                .web(WebApplicationType.NONE)
                .initializers(c -> ((ConfigurableEnvironment) c.getEnvironment())
                        .getPropertySources().addFirst(props))
                .run();
        retriever = new LegacyKnowledgeSearchIndex(new PortableEmbeddingKnowledgeRetriever(
                ctx.getBean("managementNamedParameterJdbcTemplate", NamedParameterJdbcTemplate.class),
                new DeterministicFakeEmbedder(128), new ObjectMapper()));
    }

    @AfterAll
    static void stop() {
        if (ctx != null) ctx.close();
    }

    @Override
    KnowledgeSearchIndex retriever() {
        return retriever;
    }
}
