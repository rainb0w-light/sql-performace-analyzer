package com.biz.sccba.sqlanalyzer.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Minimal unauthenticated probe for private deployment load balancers. */
@RestController
public final class HealthController {
    private final boolean persistenceEnabled;
    private final boolean workerEnabled;

    public HealthController(@Value("${sql-analyzer.persistence.enabled:false}") boolean persistenceEnabled,
                            @Value("${sql-analyzer.worker.enabled:false}") boolean workerEnabled) {
        this.persistenceEnabled = persistenceEnabled;
        this.workerEnabled = workerEnabled;
    }

    @GetMapping("/healthz")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "persistenceEnabled", persistenceEnabled,
                "workerEnabled", workerEnabled, "timestamp", Instant.now().toString());
    }
}
