package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.Conflict;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.IndexDefEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.MetadataConflictEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ShardDefEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.IndexDefJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.MetadataConflictJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ShardDefJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Index/sharding metadata with tenant ownership. The same table name under different clients or
 * datasources never mixes: lookups and upserts are always keyed by (clientId, datasource, table).
 */
@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcMetadataRepository implements MetadataRepository {

    private final IndexDefJdbcRepository indexes;
    private final ShardDefJdbcRepository shards;
    private final MetadataConflictJdbcRepository conflicts;

    public JdbcMetadataRepository(IndexDefJdbcRepository indexes, ShardDefJdbcRepository shards,
                                  MetadataConflictJdbcRepository conflicts) {
        this.indexes = indexes;
        this.shards = shards;
        this.conflicts = conflicts;
    }

    @Override
    public Optional<IndexDef> findIndex(String clientId, String tableName, String indexName) {
        return indexes.findByClientTableIndex(clientId, tableName, indexName).map(JdbcMetadataRepository::toIndexDef);
    }

    @Override
    public List<IndexDef> indexesForTable(String clientId, String tableName) {
        return indexes.findByClientAndTable(clientId, tableName).stream().map(JdbcMetadataRepository::toIndexDef).toList();
    }

    @Override
    public List<IndexDef> indexesForTable(String clientId, String datasourceProfileId,
                                          String schemaName, String tableName) {
        return indexes.findScoped(clientId, datasourceProfileId, schemaName, tableName).stream()
                .map(JdbcMetadataRepository::toIndexDef).toList();
    }

    @Override
    @Transactional(transactionManager = "managementTransactionManager")
    public void upsertIndex(String clientId, IndexDef def) {
        Optional<IndexDefEntity> existing = indexes.findScopedIndex(clientId, def.datasource(),
                def.schemaName(), def.tableName(), def.indexName());
        IndexDefEntity entity = existing.orElseGet(() -> {
            IndexDefEntity e = new IndexDefEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(def.id());
            e.markNew();
            return e;
        });
        entity.setUpdatedAt(Instant.now());
        entity.setClientId(clientId);
        entity.setDatasource(def.datasource());
        entity.setSchemaName(def.schemaName());
        entity.setTableName(def.tableName());
        entity.setIndexName(def.indexName());
        entity.setIndexType(def.indexType());
        entity.setColumnsJson(def.columnsJson());
        entity.setCardinality(def.cardinality());
        entity.setUsageCount(def.usageCount());
        entity.setSource(def.source());
        entity.setConfirmedBy(def.confirmedBy());
        entity.setValidFrom(def.validFrom() == null ? Instant.now() : def.validFrom());
        entity.setVersion(def.version());
        entity.setChecksum(def.checksum());
        indexes.save(entity);
    }

    @Override
    public Optional<ShardDef> findShard(String clientId, String logicalTable) {
        return shards.findByClientAndLogicalTable(clientId, logicalTable).map(JdbcMetadataRepository::toShardDef);
    }

    @Override
    public Optional<ShardDef> findShard(String clientId, String datasourceProfileId,
                                        String schemaName, String logicalTable) {
        return shards.findScoped(clientId, datasourceProfileId, schemaName, logicalTable)
                .map(JdbcMetadataRepository::toShardDef);
    }

    @Override
    public List<ShardDef> shards(String clientId) {
        return shards.findAllByClient(clientId).stream().map(JdbcMetadataRepository::toShardDef).toList();
    }

    @Override
    @Transactional(transactionManager = "managementTransactionManager")
    public void upsertShard(String clientId, ShardDef def) {
        Optional<ShardDefEntity> existing = shards.findScoped(clientId, def.datasource(),
                def.schemaName(), def.logicalTable());
        ShardDefEntity entity = existing.orElseGet(() -> {
            ShardDefEntity e = new ShardDefEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(def.id());
            e.markNew();
            return e;
        });
        entity.setUpdatedAt(Instant.now());
        entity.setClientId(clientId);
        entity.setDatasource(def.datasource());
        entity.setSchemaName(def.schemaName());
        entity.setLogicalTable(def.logicalTable());
        entity.setPhysicalPattern(def.physicalPattern());
        entity.setShardKey(def.shardKey());
        entity.setSecondaryShardKey(def.secondaryShardKey());
        entity.setAlgorithm(def.algorithm());
        entity.setRoutingExpr(def.routingExpr());
        entity.setTopologyJson(def.topologyJson());
        entity.setSource(def.source());
        entity.setConfirmedBy(def.confirmedBy());
        entity.setValidFrom(def.validFrom() == null ? Instant.now() : def.validFrom());
        entity.setVersion(def.version());
        shards.save(entity);
    }

    @Override
    public void addConflict(String clientId, Conflict conflict) {
        MetadataConflictEntity entity = new MetadataConflictEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(conflict.id());
        entity.setClientId(clientId);
        entity.setEntityType(conflict.entityType());
        entity.setEntityKey(conflict.entityKey());
        entity.setExistingJson(conflict.existingJson());
        entity.setIncomingJson(conflict.incomingJson());
        entity.setSource(conflict.source());
        entity.setStatus("PENDING");
        entity.markNew();
        conflicts.save(entity);
    }

    @Override
    public List<Conflict> pendingConflicts(String clientId) {
        return conflicts.findPendingForClient(clientId).stream().map(e -> new Conflict(e.getId(), e.getClientId(),
                e.getEntityType(), e.getEntityKey(), e.getExistingJson(), e.getIncomingJson(),
                e.getSource(), e.getStatus(), e.getCreatedAt())).toList();
    }

    @Override
    public void resolveConflict(String clientId, String id, String status) {
        conflicts.resolveForClient(clientId, id, status);
    }

    private static IndexDef toIndexDef(IndexDefEntity e) {
        return new IndexDef(e.getId(), e.getClientId(), e.getDatasource(), e.getSchemaName(), e.getTableName(),
                e.getIndexName(), e.getIndexType(), e.getColumnsJson(), e.getCardinality(), e.getUsageCount(),
                e.getSource(), e.getConfirmedBy(), e.getValidFrom(), e.getVersion() == null ? 1 : e.getVersion(),
                e.getChecksum(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static ShardDef toShardDef(ShardDefEntity e) {
        return new ShardDef(e.getId(), e.getClientId(), e.getDatasource(), e.getSchemaName(),
                e.getLogicalTable(), e.getPhysicalPattern(),
                e.getShardKey(), e.getSecondaryShardKey(), e.getAlgorithm(), e.getRoutingExpr(), e.getTopologyJson(),
                e.getSource(), e.getConfirmedBy(), e.getValidFrom(), e.getVersion() == null ? 1 : e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
