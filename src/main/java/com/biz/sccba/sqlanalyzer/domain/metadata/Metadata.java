package com.biz.sccba.sqlanalyzer.domain.metadata;

import java.time.Instant;

/** Index and sharding metadata with source/version/validation provenance (development-guide §7.3). */
public final class Metadata {

    private Metadata() {}

    public record IndexDef(String id, String clientId, String datasource, String schemaName, String tableName,
                           String indexName, String indexType, String columnsJson, Long cardinality, Long usageCount,
                           String source, String confirmedBy, Instant validFrom, int version, String checksum,
                           Instant createdAt, Instant updatedAt) {}

    public record ShardDef(String id, String clientId, String datasource, String schemaName,
                           String logicalTable, String physicalPattern,
                           String shardKey, String secondaryShardKey, String algorithm, String routingExpr,
                           String topologyJson, String source, String confirmedBy, Instant validFrom, int version,
                           Instant createdAt, Instant updatedAt) {
        /** Backward-compatible constructor for definitions created before schema became explicit. */
        public ShardDef(String id, String clientId, String datasource, String logicalTable,
                        String physicalPattern, String shardKey, String secondaryShardKey,
                        String algorithm, String routingExpr, String topologyJson, String source,
                        String confirmedBy, Instant validFrom, int version,
                        Instant createdAt, Instant updatedAt) {
            this(id, clientId, datasource, "public", logicalTable, physicalPattern, shardKey,
                    secondaryShardKey, algorithm, routingExpr, topologyJson, source, confirmedBy,
                    validFrom, version, createdAt, updatedAt);
        }
    }

    /** Auto-collected metadata never overwrites manual records; differences are queued here. */
    public record Conflict(String id, String clientId, String entityType, String entityKey, String existingJson,
                           String incomingJson, String source, String status, Instant createdAt) {}
}
