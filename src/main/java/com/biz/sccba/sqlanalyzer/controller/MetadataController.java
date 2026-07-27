package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Index / sharding metadata API (development-guide §7.3). */
@RestController
@RequestMapping("/api/v1/metadata")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class MetadataController {

    private final MetadataService metadata;
    private final BearerClients bearer;

    public MetadataController(MetadataService metadata, BearerClients bearer) {
        this.metadata = metadata;
        this.bearer = bearer;
    }

    @GetMapping("/indexes")
    public List<IndexDef> indexes(@RequestHeader("Authorization") String authorization,
                                  @RequestParam String table) {
        return metadata.indexesForTable(bearer.clientId(authorization), table);
    }

    @PostMapping("/indexes")
    public IndexDef upsertIndex(@RequestHeader("Authorization") String authorization,
                                @Valid @RequestBody IndexRequest request) {
        return metadata.upsertIndex(bearer.clientId(authorization),
                new IndexDef(null, null, request.datasource(), request.schemaName(), request.tableName(),
                request.indexName(), request.indexType() == null ? "NORMAL" : request.indexType(),
                request.columnsJson() == null ? "[]" : request.columnsJson(), request.cardinality(), request.usageCount(),
                "MANUAL", request.confirmedBy(), null, 1, null, null, null));
    }

    @GetMapping("/shards")
    public List<ShardDef> shards(@RequestHeader("Authorization") String authorization) {
        return metadata.shards(bearer.clientId(authorization));
    }

    @PostMapping("/shards")
    public ShardDef upsertShard(@RequestHeader("Authorization") String authorization,
                                @Valid @RequestBody ShardRequest request) {
        return metadata.upsertShard(bearer.clientId(authorization),
                new ShardDef(null, null, request.datasource(), request.logicalTable(),
                request.physicalPattern(), request.shardKey(), request.secondaryShardKey(), request.algorithm(),
                request.routingExpr(), request.topologyJson() == null ? "{}" : request.topologyJson(),
                "MANUAL", request.confirmedBy(), null, 1, null, null));
    }

    @GetMapping("/conflicts")
    public Object conflicts(@RequestHeader("Authorization") String authorization) {
        return metadata.pendingConflicts(bearer.clientId(authorization));
    }

    @PostMapping("/conflicts/{conflictId}/resolve")
    public void resolve(@RequestHeader("Authorization") String authorization,
                        @PathVariable String conflictId,
                        @Valid @RequestBody ResolveRequest request) {
        metadata.resolveConflict(bearer.clientId(authorization), conflictId, request.acceptIncoming());
    }

    public record IndexRequest(String datasource, String schemaName, @NotBlank String tableName,
                               @NotBlank String indexName, String indexType, String columnsJson,
                               Long cardinality, Long usageCount, String confirmedBy) {}

    public record ShardRequest(String datasource, @NotBlank String logicalTable, String physicalPattern,
                               String shardKey, String secondaryShardKey, String algorithm,
                               String routingExpr, String topologyJson, String confirmedBy) {}

    public record ResolveRequest(boolean acceptIncoming) {}
}
