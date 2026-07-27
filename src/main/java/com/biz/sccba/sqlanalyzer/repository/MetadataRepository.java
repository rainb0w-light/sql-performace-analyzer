package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.Conflict;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;

import java.util.List;
import java.util.Optional;

/**
 * Index/sharding metadata persistence (development-guide §7.3). Every operation is tenant
 * scoped: the same table name under different clients/datasources never mixes data
 * (docs/cloud-code-next-goal.md §5.2).
 */
public interface MetadataRepository {

    Optional<IndexDef> findIndex(String clientId, String tableName, String indexName);

    List<IndexDef> indexesForTable(String clientId, String tableName);

    default List<IndexDef> indexesForTable(String clientId, String datasourceProfileId,
                                           String schemaName, String tableName) {
        return indexesForTable(clientId, tableName).stream()
                .filter(index -> datasourceProfileId.equals(index.datasource()))
                .filter(index -> schemaName.equals(index.schemaName()))
                .toList();
    }

    void upsertIndex(String clientId, IndexDef def);

    Optional<ShardDef> findShard(String clientId, String logicalTable);

    default Optional<ShardDef> findShard(String clientId, String datasourceProfileId,
                                         String schemaName, String logicalTable) {
        return findShard(clientId, logicalTable)
                .filter(shard -> datasourceProfileId.equals(shard.datasource()))
                .filter(shard -> schemaName.equals(shard.schemaName()));
    }

    List<ShardDef> shards(String clientId);

    void upsertShard(String clientId, ShardDef def);

    void addConflict(String clientId, Conflict conflict);

    List<Conflict> pendingConflicts(String clientId);

    void resolveConflict(String clientId, String id, String status);
}
