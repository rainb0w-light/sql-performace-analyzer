package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LocalH2DatasourceBootstrapServiceTest {

    @Test
    void createsOneDeterministicReadOnlyProfileForAnEmptyH2Client() {
        ProfilingRepository repository = mock(ProfilingRepository.class);
        String clientId = "client_1";
        DatasourceProfile created = profile(clientId);
        when(repository.listProfiles(clientId)).thenReturn(List.of(), List.of(created));
        when(repository.createProfile(org.mockito.ArgumentMatchers.any(DatasourceProfile.class)))
                .thenReturn(created);
        LocalH2DatasourceBootstrapService service = service(
                repository, "jdbc:h2:mem:test", true);

        List<DatasourceProfile> result = service.listOrBootstrap(clientId);

        ArgumentCaptor<DatasourceProfile> value = ArgumentCaptor.forClass(DatasourceProfile.class);
        verify(repository).createProfile(value.capture());
        assertEquals(LocalH2DatasourceBootstrapService.deterministicId(clientId), value.getValue().id());
        assertEquals("H2", value.getValue().dialect());
        assertTrue(value.getValue().readOnly());
        assertEquals(List.of(created), result);
    }

    @Test
    void preservesExplicitProfilesWithoutAddingAnAmbiguousDefault() {
        ProfilingRepository repository = mock(ProfilingRepository.class);
        DatasourceProfile explicit = new DatasourceProfile(
                "dsp_explicit", "client_1", "production", "MYSQL",
                "jdbc:mysql://db/app", "readonly", "APP_DB_PASSWORD", true, null);
        when(repository.listProfiles("client_1")).thenReturn(List.of(explicit));
        LocalH2DatasourceBootstrapService service = service(
                repository, "jdbc:h2:mem:test", true);

        assertEquals(List.of(explicit), service.listOrBootstrap("client_1"));
        verify(repository, never()).createProfile(
                org.mockito.ArgumentMatchers.any(DatasourceProfile.class));
    }

    @Test
    void doesNotBootstrapPostgresqlOrAnExplicitlyDisabledH2Runtime() {
        ProfilingRepository postgresql = mock(ProfilingRepository.class);
        when(postgresql.listProfiles("client_1")).thenReturn(List.of());
        assertTrue(service(postgresql, "jdbc:postgresql://localhost/test", true)
                .listOrBootstrap("client_1").isEmpty());
        verify(postgresql, never()).createProfile(
                org.mockito.ArgumentMatchers.any(DatasourceProfile.class));

        ProfilingRepository disabled = mock(ProfilingRepository.class);
        when(disabled.listProfiles("client_1")).thenReturn(List.of());
        assertTrue(service(disabled, "jdbc:h2:mem:test", false)
                .listOrBootstrap("client_1").isEmpty());
        verify(disabled, never()).createProfile(
                org.mockito.ArgumentMatchers.any(DatasourceProfile.class));
    }

    private static LocalH2DatasourceBootstrapService service(
            ProfilingRepository repository, String managementJdbcUrl, boolean enabled) {
        return new LocalH2DatasourceBootstrapService(repository, managementJdbcUrl, enabled,
                "jdbc:h2:file:/tmp/sql-analyzer-local-target", "sa");
    }

    private static DatasourceProfile profile(String clientId) {
        return new DatasourceProfile(
                LocalH2DatasourceBootstrapService.deterministicId(clientId),
                clientId,
                "Local H2 Static Analysis",
                "H2",
                "jdbc:h2:file:/tmp/sql-analyzer-local-target",
                "sa",
                null,
                true,
                null);
    }
}
