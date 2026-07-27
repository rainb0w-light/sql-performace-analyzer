package com.biz.sccba.sqlanalyzer.domain.knowledge;

import java.time.Instant;
import java.util.List;

/** Structured business knowledge facts (development-guide §7.1). All rows carry provenance. */
public final class Knowledge {

    private Knowledge() {}

    public record Source(String id, String clientId, String name, String sourceType,
                         String currentVersionId, Instant createdAt, Instant updatedAt) {}

    public record Version(String id, String sourceId, int versionNo, String status, String artifactId,
                          String previewJson, String errorJson, String publishedBy,
                          Instant publishedAt, Instant createdAt) {
        public boolean isPublished() { return "PUBLISHED".equals(status); }
    }

    public record TableDef(String id, String sourceId, String versionId, String datasource, String schemaName,
                           String tableName, String businessName, String purpose, String owner, String dataDomain,
                           String sheetLocator, boolean active, Instant createdAt) {}

    public record ColumnDef(String id, String sourceId, String versionId, String tableName, String columnName,
                            String businessMeaning, String dataType, String enumDomain, boolean sensitive,
                            boolean required, String sensitivityPolicy, String sheetLocator, boolean active,
                            Instant createdAt) {}

    public record Rule(String id, String sourceId, String versionId, String ruleKey, String target,
                       String description, String constraintExpr, int priority, Instant effectiveFrom,
                       String sheetLocator, boolean active, Instant createdAt) {}

    public record EnumValue(String id, String sourceId, String versionId, String enumCode, String displayName,
                            String meaning, boolean valid, String sheetLocator, boolean active, Instant createdAt) {}

    public record Alias(String id, String sourceId, String versionId, String aliasType, String aliasName,
                        String targetName, String sheetLocator, boolean active, Instant createdAt) {}

    /** Row-level import error: never silently dropped (sheet/row/column/reason). */
    public record RowError(String sheet, int row, String column, String reason) {}

    /** Deterministic parse result of one workbook; rebuildable from the original artifact. */
    public record Parsed(List<TableDef> tables, List<ColumnDef> columns, List<Rule> rules,
                         List<EnumValue> enums, List<Alias> aliases, List<ShardRow> shards,
                         List<RowError> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /** Sharding rows from the Excel template land in shard_def via the metadata layer. */
    public record ShardRow(String datasource, String logicalTable, String physicalPattern, String shardKey,
                           String secondaryShardKey, String algorithm, String routingExpr, String sheetLocator) {}
}
