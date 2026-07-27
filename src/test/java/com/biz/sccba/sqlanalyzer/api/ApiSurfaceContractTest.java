package com.biz.sccba.sqlanalyzer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 contract baseline (no Docker required).
 *
 * Freezes the public surface in persistence-OFF mode:
 * - /healthz is unauthenticated and reports the persistence/worker flags (field names frozen).
 * - All /api/v1 resource endpoints are unavailable (404) when persistence is disabled,
 *   because their controllers are conditional on sql-analyzer.persistence.enabled=true.
 */
@SpringBootTest(properties = {
        "sql-analyzer.persistence.enabled=false",
        "sql-analyzer.worker.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSurfaceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthzReportsPersistenceAndWorkerFlags() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.persistenceEnabled").value(false))
                .andExpect(jsonPath("$.workerEnabled").value(false));
    }

    @Test
    void resourceApiUnavailableWhenPersistenceDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/client-tokens/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"contract\",\"clientType\":\"IDEA\",\"deviceId\":\"test\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessionCreationUnavailableWhenPersistenceDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .header("Authorization", "Bearer anything")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isNotFound());
    }
}
