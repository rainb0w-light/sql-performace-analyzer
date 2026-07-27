package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.Conflict;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Index/sharding metadata service semantics (docs/cloud-code-next-goal.md §5.2): manual records
 * are never overwritten by auto-collection (conflict queued instead), accepting a conflict
 * applies the incoming data with a version bump, dismissing leaves the manual record untouched,
 * index column order is preserved verbatim, and primary/secondary shard keys are stored and
 * queried separately. Tenant scoping is proven at repository level by the contract suites; here
 * the service is exercised over an in-memory fake of the vendor-neutral port.
 */
class MetadataServiceTest {

    private static final String CLIENT = "client_library";
    private static final String COLUMNS = "[{\"column\":\"member_id\",\"direction\":\"ASC\"},"
            + "{\"column\":\"status\",\"direction\":\"ASC\"},{\"column\":\"due_at\",\"direction\":\"DESC\"}]";

    private InMemoryMetadataRepository repo;
    private MetadataService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryMetadataRepository();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        service = new MetadataService(repo, mapper);
    }

    private IndexDef manualIndex(String columnsJson, Long cardinality) {
        return new IndexDef(null, CLIENT, "library_db", "public", "loan", "idx_loan_member_status_due",
                "NORMAL", columnsJson, cardinality, 0L, "MANUAL", "alice", null, 1, null, null, null);
    }

    @Test
    void autoCollectionNeverOverwritesManualButQueuesConflict() {
        service.upsertIndex(CLIENT, manualIndex(COLUMNS, 1000L));

        var incoming = new IndexDef(null, CLIENT, "library_db", "public", "loan", "idx_loan_member_status_due",
                "NORMAL", "[{\"column\":\"other\"}]", 9999L, 0L, "SYSTEM_CATALOG", null, null, 1, null, null, null);
        IndexDef result = service.upsertIndex(CLIENT, incoming);

        assertEquals(COLUMNS, result.columnsJson(), "manual record must survive auto-collection");
        assertEquals(1, result.version());
        var conflicts = service.pendingConflicts(CLIENT);
        assertEquals(1, conflicts.size());
        assertEquals("INDEX", conflicts.get(0).entityType());
    }

    @Test
    void manualToManualUpsertBumpsVersion() {
        service.upsertIndex(CLIENT, manualIndex(COLUMNS, 1000L));
        IndexDef updated = service.upsertIndex(CLIENT, manualIndex(COLUMNS, 2000L));
        assertEquals(2, updated.version(), "same-source upsert must increment the version");
        assertEquals(2000L, updated.cardinality());
        assertEquals(1, service.indexesForTable(CLIENT, "loan").size());
    }

    @Test
    void acceptingConflictAppliesIncomingAndBumpsVersion() {
        service.upsertIndex(CLIENT, manualIndex(COLUMNS, 1000L));
        String newColumns = "[{\"column\":\"member_id\",\"direction\":\"DESC\"}]";
        service.upsertIndex(CLIENT, new IndexDef(null, CLIENT, "library_db", "public", "loan",
                "idx_loan_member_status_due", "NORMAL", newColumns, 5000L, 0L, "SYSTEM_CATALOG", null, null, 1, null, null, null));

        Conflict conflict = service.pendingConflicts(CLIENT).get(0);
        service.resolveConflict(CLIENT, conflict.id(), true);

        assertTrue(service.pendingConflicts(CLIENT).isEmpty(), "resolved conflict leaves the pending list");
        IndexDef current = service.indexesForTable(CLIENT, "loan").get(0);
        assertEquals(newColumns, current.columnsJson(), "accepting must apply the incoming data");
        assertEquals(2, current.version(), "accepting must bump the version");
    }

    @Test
    void dismissingConflictKeepsManualRecord() {
        service.upsertIndex(CLIENT, manualIndex(COLUMNS, 1000L));
        service.upsertIndex(CLIENT, new IndexDef(null, CLIENT, "library_db", "public", "loan",
                "idx_loan_member_status_due", "NORMAL", "[{\"column\":\"other\"}]", null, null,
                "SYSTEM_CATALOG", null, null, 1, null, null, null));
        Conflict conflict = service.pendingConflicts(CLIENT).get(0);

        service.resolveConflict(CLIENT, conflict.id(), false);

        assertTrue(service.pendingConflicts(CLIENT).isEmpty());
        assertEquals(COLUMNS, service.indexesForTable(CLIENT, "loan").get(0).columnsJson(),
                "dismissing must leave the manual record untouched");
        assertEquals(1, service.indexesForTable(CLIENT, "loan").get(0).version());
    }

    @Test
    void indexColumnOrderIsPreservedVerbatim() {
        service.upsertIndex(CLIENT, manualIndex(COLUMNS, 1000L));
        assertEquals(COLUMNS, service.indexesForTable(CLIENT, "loan").get(0).columnsJson(),
                "ordered column list must round-trip byte-identical");
    }

    @Test
    void primaryAndSecondaryShardKeysAreStoredAndQueriedSeparately() {
        service.upsertShard(CLIENT, new ShardDef(null, CLIENT, "library_db", "loan", "loan_{0..15}",
                "member_id", "borrowed_at", "hash", "member_id % 16", "{}", "MANUAL", "alice", null, 1, null, null));

        var shard = service.findShard(CLIENT, "loan").orElseThrow();
        assertEquals("member_id", shard.shardKey(), "primary shard key");
        assertEquals("borrowed_at", shard.secondaryShardKey(), "secondary shard key must be a separate field");

        // Updating only the secondary key keeps the primary key intact (version bumps).
        service.upsertShard(CLIENT, new ShardDef(null, CLIENT, "library_db", "loan", "loan_{0..15}",
                "member_id", "due_at", "hash", "member_id % 16", "{}", "MANUAL", "alice", null, 1, null, null));
        var updated = service.findShard(CLIENT, "loan").orElseThrow();
        assertEquals("member_id", updated.shardKey());
        assertEquals("due_at", updated.secondaryShardKey());
        assertEquals(2, updated.version());
    }

    @Test
    void shardConflictProtectionMirrorsIndexProtection() {
        service.upsertShard(CLIENT, new ShardDef(null, CLIENT, "library_db", "loan", "loan_{0..15}",
                "member_id", "borrowed_at", "hash", null, "{}", "MANUAL", "alice", null, 1, null, null));
        service.upsertShard(CLIENT, new ShardDef(null, CLIENT, "library_db", "loan", "loan_{0..31}",
                "copy_id", null, "hash", null, "{}", "SYSTEM_CATALOG", null, null, 1, null, null));

        assertEquals(1, service.pendingConflicts(CLIENT).size());
        assertEquals("SHARD", service.pendingConflicts(CLIENT).get(0).entityType());
        assertEquals("member_id", service.findShard(CLIENT, "loan").orElseThrow().shardKey(),
                "manual shard definition must survive auto-collection");
    }

    /** In-memory fake of the vendor-neutral port. */
    static final class InMemoryMetadataRepository implements MetadataRepository {
        final Map<String, IndexDef> indexes = new LinkedHashMap<>();
        final Map<String, ShardDef> shards = new LinkedHashMap<>();
        final List<Conflict> conflicts = new ArrayList<>();

        @Override
        public Optional<IndexDef> findIndex(String clientId, String tableName, String indexName) {
            return Optional.ofNullable(indexes.get(key(clientId, tableName, indexName)));
        }

        @Override
        public List<IndexDef> indexesForTable(String clientId, String tableName) {
            return indexes.values().stream()
                    .filter(i -> i.clientId().equals(clientId) && i.tableName().equals(tableName)).toList();
        }

        @Override
        public void upsertIndex(String clientId, IndexDef def) {
            indexes.put(key(clientId, def.tableName(), def.indexName()), def);
        }

        @Override
        public Optional<ShardDef> findShard(String clientId, String logicalTable) {
            return Optional.ofNullable(shards.get(clientId + "|" + logicalTable));
        }

        @Override
        public List<ShardDef> shards(String clientId) {
            return shards.values().stream().filter(s -> s.clientId().equals(clientId)).toList();
        }

        @Override
        public void upsertShard(String clientId, ShardDef def) {
            shards.put(clientId + "|" + def.logicalTable(), def);
        }

        @Override
        public void addConflict(String clientId, Conflict conflict) {
            conflicts.add(conflict);
        }

        @Override
        public List<Conflict> pendingConflicts(String clientId) {
            return conflicts.stream()
                    .filter(c -> c.clientId().equals(clientId) && "PENDING".equals(c.status())).toList();
        }

        @Override
        public void resolveConflict(String clientId, String id, String status) {
            for (int i = 0; i < conflicts.size(); i++) {
                Conflict c = conflicts.get(i);
                if (c.id().equals(id) && c.clientId().equals(clientId)) {
                    conflicts.set(i, new Conflict(c.id(), c.clientId(), c.entityType(), c.entityKey(),
                            c.existingJson(), c.incomingJson(), c.source(), status, c.createdAt()));
                }
            }
        }

        private static String key(String clientId, String table, String index) {
            return clientId + "|" + table + "." + index;
        }
    }

    static {
        // keep Instant import used for potential future time assertions
        Instant.now();
    }
}
