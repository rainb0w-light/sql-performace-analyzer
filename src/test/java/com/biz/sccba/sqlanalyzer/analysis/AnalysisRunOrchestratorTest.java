package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.domain.AnalysisSession;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnalysisRunOrchestratorTest {

    private static final String CLIENT_ID = "client_1";
    private static final String PROFILE_ID = "profile_1";

    private SessionRepository sessions;
    private AgentRunRepository runs;
    private AgentJobRepository jobs;
    private RunEventRepository events;
    private ProfilingRepository profiling;
    private ArtifactService artifacts;
    private ObjectMapper objectMapper;

    private AnalysisRunOrchestrator orchestrator;

    @Before
    public void setUp() {
        sessions = mock(SessionRepository.class);
        runs = mock(AgentRunRepository.class);
        jobs = mock(AgentJobRepository.class);
        events = mock(RunEventRepository.class);
        profiling = mock(ProfilingRepository.class);
        artifacts = mock(ArtifactService.class);
        objectMapper = new ObjectMapper();

        when(profiling.findProfile(PROFILE_ID, CLIENT_ID)).thenReturn(Optional.of(
                new DatasourceProfile(PROFILE_ID, CLIENT_ID, "dsp", "H2",
                        "jdbc:h2:mem:test", "sa", "cred", false, Instant.now())));
        when(artifacts.read(CLIENT_ID, "artifact_1")).thenReturn("<mapper/>".getBytes());

        orchestrator = new AnalysisRunOrchestrator(sessions, runs, jobs, events, profiling, artifacts, objectMapper);
    }

    @Test
    public void shouldReuseExistingSessionIfProvided() {
        when(sessions.findByIdForClient("session_exists", CLIENT_ID))
                .thenReturn(newSession("session_exists"));

        AnalysisRunOrchestrator.Command command = command("session_exists");
        var handle = orchestrator.start(CLIENT_ID, command);

        assertNotNull(handle.sessionId());
        assertEquals("session_exists", handle.sessionId());
        verify(sessions, never()).create(any(), eq(CLIENT_ID), any());
        verify(events).append(eq(handle.runId()), eq("RUN_QUEUED"), contains("\"sessionId\":\"session_exists\""));
        verify(artifacts).read(CLIENT_ID, "artifact_1");
    }

    @Test
    public void shouldCreateSessionWhenProvidedSessionMissing() {
        when(sessions.findByIdForClient("session_missing", CLIENT_ID)).thenReturn(Optional.empty());
        when(sessions.create(any(), eq(CLIENT_ID), any())).thenReturn(createdSession("session_created"));

        AnalysisRunOrchestrator.Command command = command("session_missing");
        var handle = orchestrator.start(CLIENT_ID, command);

        assertEquals("session_created", handle.sessionId());
        verify(sessions).create(any(), eq(CLIENT_ID), any());
        verify(artifacts).read(CLIENT_ID, "artifact_1");
    }

    private static Optional<AnalysisSession> newSession(String id) {
        return Optional.of(new AnalysisSession(id, CLIENT_ID, "session", "ACTIVE", Instant.now(), Instant.now()));
    }

    private static AnalysisSession createdSession(String id) {
        return new AnalysisSession(id, CLIENT_ID, "session", "ACTIVE", Instant.now(), Instant.now());
    }

    private AnalysisRunOrchestrator.Command command(String sessionId) {
        return new AnalysisRunOrchestrator.Command("artifact_1", "selectLoans", PROFILE_ID,
                "project_1", "module_1", sessionId, null, null, null,
                20, List.of(), null, null, List.of(), null);
    }
}
