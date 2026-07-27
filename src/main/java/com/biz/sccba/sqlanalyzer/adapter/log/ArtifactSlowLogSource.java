package com.biz.sccba.sqlanalyzer.adapter.log;

import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reads an already stored slow log artifact without touching the target database.
 *
 * <p>Tenant safe: the artifact is only read when the query options carry the authenticated
 * {@code clientId} (injected server-side by {@code ReadOnlySqlTools} from the Agent execution
 * context) and the artifact belongs to that client. A client-supplied artifactId alone is never
 * trusted (docs/cloud-code-next-goal.md §5, cross-tenant negative tests).
 */
@Component
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ArtifactSlowLogSource implements SlowLogSource {
    private final ArtifactRepository artifacts;

    public ArtifactSlowLogSource(ArtifactRepository artifacts) { this.artifacts = artifacts; }

    @Override
    public SlowLogBatch fetch(Query query) {
        String artifactId = query.options() == null ? null : query.options().get("artifactId");
        String clientId = query.options() == null ? null : query.options().get("clientId");
        if (artifactId == null || artifactId.isBlank() || clientId == null || clientId.isBlank()) {
            return new SlowLogBatch("ARTIFACT", List.of(), "");
        }
        String raw = artifacts.readAll(clientId, artifactId)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8)).orElse("");
        String fingerprint = query.sqlFingerprint();
        int limit = Math.max(1, Math.min(query.limit(), 1000));
        List<Entry> entries = raw.lines().filter(line -> !line.isBlank())
                .filter(line -> fingerprint == null || fingerprint.isBlank() || line.contains(fingerprint))
                .limit(limit)
                .map(line -> new Entry(line, 1, 0, 0, Instant.now(), Map.of()))
                .toList();
        return new SlowLogBatch("ARTIFACT", entries, raw);
    }
}
