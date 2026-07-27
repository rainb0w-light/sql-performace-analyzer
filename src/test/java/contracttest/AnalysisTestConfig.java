package contracttest;

import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.RecommendationRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.biz.sccba.sqlanalyzer.service.RecommendationProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * End-to-end analysis slice for the library fixture (no Docker, no LLM): the persistence stack
 * (ContractTestConfig) plus the deterministic analysis chain (reference resolution, server-side
 * context resolution, scenario engine, report assembly/validation/rendering, report persistence,
 * recommendation projection).
 */
@Configuration
@Import(ContractTestConfig.class)
@ComponentScan(basePackages = {
        "com.biz.sccba.sqlanalyzer.analysis",
        "com.biz.sccba.sqlanalyzer.scenario",
        "com.biz.sccba.sqlanalyzer.mybatis",
        "com.biz.sccba.sqlanalyzer.knowledge",
        "com.biz.sccba.sqlanalyzer.metadata"
})
public class AnalysisTestConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper;
    }

    @Bean
    public ArtifactService artifactService(ArtifactRepository artifacts) {
        return new ArtifactService(artifacts);
    }

    @Bean
    public RecommendationProjector recommendationProjector(RecommendationRepository recommendations,
                                                           ObjectMapper objectMapper) {
        return new RecommendationProjector(recommendations, objectMapper);
    }
}
