package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.Artifact;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactServiceTest {

    @Test
    void blankOptionalSessionIdIsPersistedAsNull() {
        ArtifactRepository repository = repositoryWithoutExistingArtifact();
        ArtifactService service = new ArtifactService(repository);

        service.ingest("client_1", "  ", "MYBATIS_MAPPER_XML", null,
                "application/xml", "<mapper/>".getBytes(StandardCharsets.UTF_8), "{}");

        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        verify(repository).create(artifact.capture());
        assertNull(artifact.getValue().sessionId());
    }

    @Test
    void nonBlankSessionIdIsPreserved() {
        ArtifactRepository repository = repositoryWithoutExistingArtifact();
        ArtifactService service = new ArtifactService(repository);

        service.ingest("client_1", "session_1", "MYBATIS_MAPPER_XML", null,
                "application/xml", "<mapper/>".getBytes(StandardCharsets.UTF_8), "{}");

        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        verify(repository).create(artifact.capture());
        assertEquals("session_1", artifact.getValue().sessionId());
    }

    @Test
    void missingSessionIdFallsBackToNullSession() {
        ArtifactRepository repository = repositoryWithoutExistingArtifact();
        SessionRepository sessions = mock(SessionRepository.class);
        ArtifactService service = new ArtifactService(repository, sessions);
        when(sessions.belongsToClient("session_missing", "client_1")).thenReturn(false);

        service.ingest("client_1", "session_missing", "MYBATIS_MAPPER_XML", null,
                "application/xml", "<mapper/>".getBytes(StandardCharsets.UTF_8), "{}");

        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        verify(repository).create(artifact.capture());
        assertNull(artifact.getValue().sessionId());
    }

    private static ArtifactRepository repositoryWithoutExistingArtifact() {
        ArtifactRepository repository = mock(ArtifactRepository.class);
        when(repository.findBySha256ForClient(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(repository).create(any(Artifact.class));
        return repository;
    }
}
