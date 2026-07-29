package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.DatasourceProfile;
import com.biz.sccba.sqlanalyzer.service.LocalH2DatasourceBootstrapService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** datasource-profiles / profiling-jobs / snapshots API (development-guide §7.2, §9.1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ProfilingController {

    private final ProfilingRepository dao;
    private final ObjectMapper objectMapper;
    private final BearerClients bearer;
    private final LocalH2DatasourceBootstrapService localH2Bootstrap;

    public ProfilingController(ProfilingRepository dao, ObjectMapper objectMapper, BearerClients bearer,
                               LocalH2DatasourceBootstrapService localH2Bootstrap) {
        this.dao = dao;
        this.objectMapper = objectMapper;
        this.bearer = bearer;
        this.localH2Bootstrap = localH2Bootstrap;
    }

    @GetMapping("/datasource-profiles")
    public Object profiles(@RequestHeader("Authorization") String authorization) {
        return localH2Bootstrap.listOrBootstrap(bearer.clientId(authorization));
    }

    @PostMapping("/datasource-profiles")
    public DatasourceProfile createProfile(@RequestHeader("Authorization") String authorization,
                                           @Valid @RequestBody ProfileRequest request) {
        String clientId = bearer.clientId(authorization);
        // Passwords are never accepted or stored; credentials resolve from a named env/property.
        return dao.createProfile(new DatasourceProfile("dsp_" + UUID.randomUUID(), clientId, request.name(),
                request.dialect() == null ? "MYSQL" : request.dialect().toUpperCase(java.util.Locale.ROOT),
                request.jdbcUrl(), request.username(), request.credentialEnv(), true, null));
    }

    @PostMapping("/profiling-jobs")
    public Map<String, Object> enqueue(@RequestHeader("Authorization") String authorization,
                                       @Valid @RequestBody JobRequest request) {
        String clientId = bearer.clientId(authorization);
        dao.findProfile(request.datasourceProfileId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));
        String jobId = "pjob_" + UUID.randomUUID();
        String config;
        try {
            Map<String, Object> cfg = new LinkedHashMap<>();
            if (request.schema() != null) cfg.put("schema", request.schema());
            if (request.tables() != null) cfg.put("tables", request.tables());
            config = objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("画像配置无效", e);
        }
        dao.enqueueJob(jobId, clientId, request.datasourceProfileId(), config);
        return Map.of("jobId", jobId, "status", "QUEUED");
    }

    @GetMapping("/profiling-jobs/{jobId}")
    public Object job(@RequestHeader("Authorization") String authorization, @PathVariable String jobId) {
        return dao.findJob(jobId, bearer.clientId(authorization))
                .orElseThrow(() -> new IllegalArgumentException("画像任务不存在"));
    }

    @GetMapping("/profiling-jobs")
    public Object jobs(@RequestHeader("Authorization") String authorization) {
        return dao.listJobs(bearer.clientId(authorization));
    }

    @GetMapping("/datasource-profiles/{profileId}/snapshots")
    public Object snapshots(@RequestHeader("Authorization") String authorization, @PathVariable String profileId) {
        String clientId = bearer.clientId(authorization);
        return dao.listSnapshots(clientId, profileId);
    }

    @GetMapping("/profile-snapshots/{snapshotId}/stats")
    public Object stats(@RequestHeader("Authorization") String authorization, @PathVariable String snapshotId) {
        return dao.snapshotStats(bearer.clientId(authorization), snapshotId);
    }

    public record ProfileRequest(@NotBlank String name, String dialect, @NotBlank String jdbcUrl,
                                 String username, String credentialEnv) {}

    public record JobRequest(@NotBlank String datasourceProfileId, String schema, java.util.List<String> tables) {}
}
