package com.biz.sccba.sqlanalyzer.metadata;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ShardRow;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.Conflict;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Index/sharding metadata with conflict-safe merging (development-guide §7.3):
 * auto-collected records never overwrite MANUAL ones; differences are queued as PENDING conflicts.
 * Every operation is tenant scoped: the same table name under different clients never mixes
 * (docs/cloud-code-next-goal.md §5.2).
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class MetadataService {

    private static final String MANUAL = "MANUAL";

    private final MetadataRepository dao;
    private final ObjectMapper objectMapper;

    public MetadataService(MetadataRepository dao, ObjectMapper objectMapper) {
        this.dao = dao;
        this.objectMapper = objectMapper;
    }

    public List<IndexDef> indexesForTable(String clientId, String tableName) {
        return dao.indexesForTable(clientId, tableName);
    }

    public Optional<IndexDef> findIndex(String clientId, String tableName, String indexName) {
        return dao.findIndex(clientId, tableName, indexName);
    }

    public List<ShardDef> shards(String clientId) {
        return dao.shards(clientId);
    }

    public Optional<ShardDef> findShard(String clientId, String logicalTable) {
        return dao.findShard(clientId, logicalTable);
    }

    public List<Conflict> pendingConflicts(String clientId) {
        return dao.pendingConflicts(clientId);
    }

    /**
     * Resolves a conflict: accepting applies the incoming (auto-collected) definition over the
     * manual record with a version bump; dismissing leaves the manual record untouched.
     */
    public void resolveConflict(String clientId, String id, boolean acceptIncoming) {
        if (!acceptIncoming) {
            dao.resolveConflict(clientId, id, "DISMISSED");
            return;
        }
        Conflict conflict = dao.pendingConflicts(clientId).stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("冲突不存在或不属于当前客户端"));
        try {
            if ("INDEX".equals(conflict.entityType())) {
                IndexDef incoming = objectMapper.readValue(conflict.incomingJson(), IndexDef.class);
                IndexDef existing = objectMapper.readValue(conflict.existingJson(), IndexDef.class);
                dao.upsertIndex(clientId, new IndexDef(existing.id(), clientId, incoming.datasource(),
                        incoming.schemaName(), incoming.tableName(), incoming.indexName(), incoming.indexType(),
                        incoming.columnsJson(), incoming.cardinality(), incoming.usageCount(), incoming.source(),
                        incoming.confirmedBy(), incoming.validFrom(), existing.version() + 1,
                        incoming.checksum(), null, null));
            } else if ("SHARD".equals(conflict.entityType())) {
                ShardDef incoming = objectMapper.readValue(conflict.incomingJson(), ShardDef.class);
                ShardDef existing = objectMapper.readValue(conflict.existingJson(), ShardDef.class);
                dao.upsertShard(clientId, new ShardDef(existing.id(), clientId, incoming.datasource(),
                        incoming.logicalTable(), incoming.physicalPattern(), incoming.shardKey(),
                        incoming.secondaryShardKey(), incoming.algorithm(), incoming.routingExpr(),
                        incoming.topologyJson(), incoming.source(), incoming.confirmedBy(), incoming.validFrom(),
                        existing.version() + 1, null, null));
            } else {
                throw new IllegalArgumentException("未知冲突类型：" + conflict.entityType());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("冲突接受失败：" + e.getMessage(), e);
        }
        dao.resolveConflict(clientId, id, "RESOLVED");
    }

    public IndexDef upsertIndex(String clientId, IndexDef incoming) {
        var existing = dao.findIndex(clientId, incoming.tableName(), incoming.indexName());
        if (existing.isPresent() && MANUAL.equals(existing.get().source()) && !MANUAL.equals(incoming.source())) {
            addConflict(clientId, "INDEX", incoming.tableName() + "." + incoming.indexName(), existing.get(), incoming, incoming.source());
            return existing.get();
        }
        IndexDef merged = new IndexDef(existing.map(IndexDef::id).orElseGet(() -> "idx_" + UUID.randomUUID()),
                clientId, incoming.datasource(), incoming.schemaName(), incoming.tableName(), incoming.indexName(),
                incoming.indexType(), incoming.columnsJson(), incoming.cardinality(), incoming.usageCount(),
                incoming.source(), incoming.confirmedBy(), incoming.validFrom() == null ? Instant.now() : incoming.validFrom(),
                existing.map(e -> e.version() + 1).orElse(1), incoming.checksum(), null, null);
        dao.upsertIndex(clientId, merged);
        return merged;
    }

    public ShardDef upsertShard(String clientId, ShardDef incoming) {
        var existing = dao.findShard(clientId, incoming.logicalTable());
        if (existing.isPresent() && MANUAL.equals(existing.get().source()) && !MANUAL.equals(incoming.source())) {
            addConflict(clientId, "SHARD", incoming.logicalTable(), existing.get(), incoming, incoming.source());
            return existing.get();
        }
        ShardDef merged = new ShardDef(existing.map(ShardDef::id).orElseGet(() -> "shard_" + UUID.randomUUID()),
                clientId, incoming.datasource(), incoming.logicalTable(), incoming.physicalPattern(), incoming.shardKey(),
                incoming.secondaryShardKey(), incoming.algorithm(), incoming.routingExpr(), incoming.topologyJson(),
                incoming.source(), incoming.confirmedBy(), incoming.validFrom() == null ? Instant.now() : incoming.validFrom(),
                existing.map(e -> e.version() + 1).orElse(1), null, null);
        dao.upsertShard(clientId, merged);
        return merged;
    }

    /** Excel sharding sheet rows land as EXCEL-sourced shard definitions (conflict-safe). */
    public void ingestShardsFromExcel(String clientId, List<ShardRow> rows) {
        for (ShardRow row : rows) {
            upsertShard(clientId, new ShardDef(null, clientId, row.datasource(), row.logicalTable(), row.physicalPattern(),
                    row.shardKey(), row.secondaryShardKey(), row.algorithm(), row.routingExpr(),
                    "{\"locator\":\"" + row.sheetLocator() + "\"}", "EXCEL", null, Instant.now(), 1, null, null));
        }
    }

    private void addConflict(String clientId, String entityType, String entityKey, Object existing, Object incoming, String source) {
        try {
            dao.addConflict(clientId, new Conflict("cf_" + UUID.randomUUID(), clientId, entityType, entityKey,
                    objectMapper.writeValueAsString(existing), objectMapper.writeValueAsString(incoming),
                    source, "PENDING", Instant.now()));
        } catch (Exception ignored) {
            // conflict auditing is best-effort; the manual record stays untouched either way
        }
    }
}
