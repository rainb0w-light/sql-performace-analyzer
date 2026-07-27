package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.Artifact;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.Conflict;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.IndexDef;
import com.biz.sccba.sqlanalyzer.domain.metadata.Metadata.ShardDef;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Import → preview → publish → rollback lifecycle semantics (development-guide §7.1), verified
 * without a database via in-memory port fakes: only published facts are active, publishing a new
 * version deactivates the previous one, rollback reactivates a prior version, and a second client
 * never sees the first client's facts (tenant isolation, docs/cloud-code-next-goal.md §5.4).
 */
class KnowledgeImportFlowTest {

    private InMemoryKnowledgeRepository knowledgeDao;
    private KnowledgeImportService service;
    private KnowledgeQueryService queryService;

    @BeforeEach
    void setUp() {
        knowledgeDao = new InMemoryKnowledgeRepository();
        InMemoryMetadataRepository metadataDao = new InMemoryMetadataRepository();
        service = new KnowledgeImportService(new ArtifactService(new InMemoryArtifactRepository()),
                new ExcelKnowledgeParser(), knowledgeDao, new MetadataService(metadataDao, new ObjectMapper()),
                new ObjectMapper(), provider(null));
        queryService = new KnowledgeQueryService(knowledgeDao);
    }

    private static byte[] twoVersionWorkbook(String purpose) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet tables = wb.createSheet("tables");
            row(tables, 0, "datasource", "schema", "table_name", "business_name", "purpose", "owner", "data_domain");
            row(tables, 1, "orders_db", "public", "orders", "订单表", purpose, "alice", "交易");
            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            row(columns, 1, "orders", "status", "订单状态", "varchar", "ORDER_STATUS", "false", "true", "");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, int idx, String... values) {
        var r = sheet.createRow(idx);
        for (int i = 0; i < values.length; i++) r.createCell(i).setCellValue(values[i]);
    }

    @Test
    void importCreatesDraftThenPublishActivatesFacts() throws Exception {
        var preview = service.importExcel("client_1", "业务知识", "kb.xlsx", twoVersionWorkbook("交易订单主表"));
        assertFalse(preview.parsed().hasErrors());
        assertEquals(1, preview.versionNo());

        // Before publish: nothing is active.
        assertTrue(queryService.tables("client_1", "orders").isEmpty());

        var published = service.publish("client_1", preview.versionId(), "alice");
        assertEquals("PUBLISHED", published.status());

        var facts = queryService.tables("client_1", "orders");
        assertEquals(1, facts.size());
        assertTrue(facts.get(0).text().contains("交易订单主表"), "fact text must carry the business meaning");
        assertTrue(facts.get(0).text().contains("交易订单主表"), "fact text must carry the business purpose");
        var evidence = facts.get(0).evidence();
        assertEquals("EXCEL_PUBLISHED", evidence.sourceType());
        assertEquals(1, evidence.version());
        assertEquals("tables!row2", evidence.locator());
        assertTrue(evidence.confidence() > 0 && evidence.confidence() <= 1);

        var columns = queryService.columns("client_1", "orders", "status");
        assertEquals(1, columns.size());
        assertEquals(0.9, columns.get(0).evidence().confidence(), 1e-9);

        // Tenant isolation: another client sees none of client_1's published facts.
        assertTrue(queryService.tables("client_2", "orders").isEmpty(),
                "published facts must not leak across clients");
    }

    @Test
    void publishingNewVersionDeactivatesPreviousAndRollbackRestores() throws Exception {
        var first = service.importExcel("client_1", "业务知识", "kb.xlsx", twoVersionWorkbook("旧版用途"));
        service.publish("client_1", first.versionId(), "alice");
        var second = service.importExcel("client_1", "业务知识", "kb.xlsx", twoVersionWorkbook("新版用途"));
        assertEquals(2, second.versionNo(), "same source name must bump the version");
        service.publish("client_1", second.versionId(), "alice");

        var active = knowledgeDao.activeTables("client_1", "orders");
        assertEquals(1, active.size(), "exactly one version's facts may be active");
        assertTrue(knowledgeDao.findVersionForClient("client_1", first.versionId()).orElseThrow().status().equals("PUBLISHED")
                || knowledgeDao.findVersionForClient("client_1", first.versionId()).orElseThrow().status().equals("ROLLED_BACK"));

        // Rollback to the first version reactivates its facts.
        service.rollback("client_1", first.sourceId(), first.versionId());
        active = knowledgeDao.activeTables("client_1", "orders");
        assertEquals(1, active.size());
        assertEquals(first.versionId(), active.get(0).versionId());
        assertEquals("PUBLISHED", knowledgeDao.findVersionForClient("client_1", first.versionId()).orElseThrow().status());
        assertEquals("ROLLED_BACK", knowledgeDao.findVersionForClient("client_1", second.versionId()).orElseThrow().status());
    }

    @Test
    void cannotPublishTwiceAndOwnershipIsEnforced() throws Exception {
        var preview = service.importExcel("client_1", "业务知识", "kb.xlsx", twoVersionWorkbook("用途"));
        service.publish("client_1", preview.versionId(), "alice");
        assertThrows(IllegalStateException.class, () -> service.publish("client_1", preview.versionId(), "alice"));
        assertThrows(IllegalArgumentException.class, () -> service.publish("client_2", preview.versionId(), "mallory"));
    }

    @Test
    void sensitivePolicyLookupDefaultsAndValues() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet columns = wb.createSheet("columns");
            row(columns, 0, "table_name", "column_name", "business_meaning", "data_type", "enum_domain",
                    "is_sensitive", "is_required", "sensitivity_policy");
            row(columns, 1, "orders", "phone", "电话", "varchar", "", "true", "false", "OMITTED");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            var preview = service.importExcel("client_1", "业务知识", "kb.xlsx", out.toByteArray());
            service.publish("client_1", preview.versionId(), "alice");
        }
        assertEquals("OMITTED", queryService.sensitivityPolicy("client_1", "orders", "phone"));
        assertEquals("PLAINTEXT", queryService.sensitivityPolicy("client_1", "orders", "unknown_column"));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getObject() { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }
        };
    }

    // ---- in-memory fakes of the vendor-neutral ports ----

    static final class InMemoryArtifactRepository implements ArtifactRepository {
        private final Map<String, Artifact> artifacts = new HashMap<>();
        private final Map<String, ByteArrayOutputStream> chunks = new HashMap<>();

        @Override public Artifact create(Artifact artifact) {
            artifacts.put(artifact.id(), artifact);
            return artifact;
        }

        @Override public void writeChunk(String artifactId, int sequence, byte[] content) {
            chunks.computeIfAbsent(artifactId, k -> new ByteArrayOutputStream()).writeBytes(content);
        }

        @Override public Optional<byte[]> readAll(String clientId, String artifactId) {
            return findByIdForClient(artifactId, clientId)
                    .flatMap(a -> Optional.ofNullable(chunks.get(artifactId)).map(ByteArrayOutputStream::toByteArray));
        }

        @Override public Optional<Artifact> findByIdForClient(String artifactId, String clientId) {
            return Optional.ofNullable(artifacts.get(artifactId)).filter(a -> a.clientId().equals(clientId));
        }
    }

    static final class InMemoryKnowledgeRepository implements KnowledgeSourceRepository {
        final Map<String, Source> sources = new HashMap<>();
        final Map<String, Version> versions = new HashMap<>();
        final List<TableDef> tables = new ArrayList<>();
        final List<ColumnDef> columns = new ArrayList<>();
        final List<Rule> rules = new ArrayList<>();
        final List<EnumValue> enums = new ArrayList<>();
        final List<Alias> aliases = new ArrayList<>();

        private boolean ownsSource(String clientId, String sourceId) {
            Source s = sources.get(sourceId);
            return s != null && s.clientId().equals(clientId);
        }

        @Override public Source createSource(String id, String clientId, String name, String sourceType) {
            Source s = new Source(id, clientId, name, sourceType, null, Instant.now(), Instant.now());
            sources.put(id, s);
            return s;
        }

        @Override public Optional<Source> findSourceForClient(String clientId, String sourceId) {
            return Optional.ofNullable(sources.get(sourceId)).filter(s -> s.clientId().equals(clientId));
        }

        @Override public List<Source> listSources(String clientId) {
            return sources.values().stream().filter(s -> s.clientId().equals(clientId)).toList();
        }

        @Override public Version createVersion(String clientId, String id, String sourceId, int versionNo,
                                               String artifactId, String previewJson, String errorJson) {
            if (!ownsSource(clientId, sourceId)) throw new IllegalArgumentException("知识源不存在");
            Version v = new Version(id, sourceId, versionNo, "DRAFT", artifactId, previewJson, errorJson, null, null, Instant.now());
            versions.put(id, v);
            return v;
        }

        @Override public Optional<Version> findVersionForClient(String clientId, String versionId) {
            Version v = versions.get(versionId);
            return v != null && ownsSource(clientId, v.sourceId()) ? Optional.of(v) : Optional.empty();
        }

        @Override public List<Version> listVersions(String clientId, String sourceId) {
            if (!ownsSource(clientId, sourceId)) throw new IllegalArgumentException("知识源不存在");
            return versions.values().stream().filter(v -> v.sourceId().equals(sourceId))
                    .sorted(Comparator.comparingInt(Version::versionNo).reversed()).toList();
        }

        @Override public int nextVersionNo(String clientId, String sourceId) {
            if (!ownsSource(clientId, sourceId)) throw new IllegalArgumentException("知识源不存在");
            return versions.values().stream().filter(v -> v.sourceId().equals(sourceId))
                    .mapToInt(Version::versionNo).max().orElse(0) + 1;
        }

        @Override public void insertTables(String sourceId, String versionId, List<TableDef> rows) {
            for (TableDef r : rows) {
                tables.add(new TableDef(r.id(), sourceId, versionId, r.datasource(), r.schemaName(), r.tableName(),
                        r.businessName(), r.purpose(), r.owner(), r.dataDomain(), r.sheetLocator(), false, Instant.now()));
            }
        }

        @Override public void insertColumns(String sourceId, String versionId, List<ColumnDef> rows) {
            for (ColumnDef r : rows) {
                columns.add(new ColumnDef(r.id(), sourceId, versionId, r.tableName(), r.columnName(), r.businessMeaning(),
                        r.dataType(), r.enumDomain(), r.sensitive(), r.required(), r.sensitivityPolicy(),
                        r.sheetLocator(), false, Instant.now()));
            }
        }

        @Override public void insertRules(String sourceId, String versionId, List<Rule> rows) {
            for (Rule r : rows) {
                this.rules.add(new Rule(r.id(), sourceId, versionId, r.ruleKey(), r.target(), r.description(),
                        r.constraintExpr(), r.priority(), r.effectiveFrom(), r.sheetLocator(), false, Instant.now()));
            }
        }

        @Override public void insertEnums(String sourceId, String versionId, List<EnumValue> rows) {
            for (EnumValue r : rows) {
                enums.add(new EnumValue(r.id(), sourceId, versionId, r.enumCode(), r.displayName(), r.meaning(),
                        r.valid(), r.sheetLocator(), false, Instant.now()));
            }
        }

        @Override public void insertAliases(String sourceId, String versionId, List<Alias> rows) {
            for (Alias r : rows) {
                aliases.add(new Alias(r.id(), sourceId, versionId, r.aliasType(), r.aliasName(), r.targetName(),
                        r.sheetLocator(), false, Instant.now()));
            }
        }

        @Override public void publishVersion(String clientId, String sourceId, String versionId, String publishedBy) {
            if (!ownsSource(clientId, sourceId)) throw new IllegalArgumentException("知识源不存在");
            replaceTables(sourceId, versionId);
            replaceColumns(sourceId, versionId);
            replaceRules(sourceId, versionId);
            replaceEnums(sourceId, versionId);
            replaceAliases(sourceId, versionId);
            Version v = versions.get(versionId);
            versions.put(versionId, new Version(v.id(), v.sourceId(), v.versionNo(), "PUBLISHED", v.artifactId(),
                    v.previewJson(), v.errorJson(), publishedBy, Instant.now(), v.createdAt()));
            Source s = sources.get(sourceId);
            sources.put(sourceId, new Source(s.id(), s.clientId(), s.name(), s.sourceType(), versionId,
                    s.createdAt(), Instant.now()));
        }

        private void replaceTables(String sourceId, String versionId) {
            for (int i = 0; i < tables.size(); i++) {
                TableDef t = tables.get(i);
                if (t.sourceId().equals(sourceId)) {
                    tables.set(i, withActive(t, t.versionId().equals(versionId)));
                }
            }
        }

        private static TableDef withActive(TableDef t, boolean active) {
            return new TableDef(t.id(), t.sourceId(), t.versionId(), t.datasource(), t.schemaName(), t.tableName(),
                    t.businessName(), t.purpose(), t.owner(), t.dataDomain(), t.sheetLocator(), active, t.createdAt());
        }

        private void replaceColumns(String sourceId, String versionId) {
            for (int i = 0; i < columns.size(); i++) {
                ColumnDef c = columns.get(i);
                if (c.sourceId().equals(sourceId)) {
                    columns.set(i, new ColumnDef(c.id(), c.sourceId(), c.versionId(), c.tableName(), c.columnName(),
                            c.businessMeaning(), c.dataType(), c.enumDomain(), c.sensitive(), c.required(),
                            c.sensitivityPolicy(), c.sheetLocator(), c.versionId().equals(versionId), c.createdAt()));
                }
            }
        }

        private void replaceRules(String sourceId, String versionId) {
            for (int i = 0; i < rules.size(); i++) {
                Rule r = rules.get(i);
                if (r.sourceId().equals(sourceId)) {
                    rules.set(i, new Rule(r.id(), r.sourceId(), r.versionId(), r.ruleKey(), r.target(), r.description(),
                            r.constraintExpr(), r.priority(), r.effectiveFrom(), r.sheetLocator(),
                            r.versionId().equals(versionId), r.createdAt()));
                }
            }
        }

        private void replaceEnums(String sourceId, String versionId) {
            for (int i = 0; i < enums.size(); i++) {
                EnumValue e = enums.get(i);
                if (e.sourceId().equals(sourceId)) {
                    enums.set(i, new EnumValue(e.id(), e.sourceId(), e.versionId(), e.enumCode(), e.displayName(),
                            e.meaning(), e.valid(), e.sheetLocator(), e.versionId().equals(versionId), e.createdAt()));
                }
            }
        }

        private void replaceAliases(String sourceId, String versionId) {
            for (int i = 0; i < aliases.size(); i++) {
                Alias a = aliases.get(i);
                if (a.sourceId().equals(sourceId)) {
                    aliases.set(i, new Alias(a.id(), a.sourceId(), a.versionId(), a.aliasType(), a.aliasName(),
                            a.targetName(), a.sheetLocator(), a.versionId().equals(versionId), a.createdAt()));
                }
            }
        }

        @Override public void markRolledBack(String clientId, String versionId) {
            Version v = versions.get(versionId);
            if (v != null && v.isPublished() && ownsSource(clientId, v.sourceId())) {
                versions.put(versionId, new Version(v.id(), v.sourceId(), v.versionNo(), "ROLLED_BACK", v.artifactId(),
                        v.previewJson(), v.errorJson(), v.publishedBy(), v.publishedAt(), v.createdAt()));
            }
        }

        @Override public List<TableDef> activeTables(String clientId, String tableName) {
            return tables.stream().filter(t -> t.active() && t.tableName().equals(tableName)
                    && ownsSource(clientId, t.sourceId())).toList();
        }

        @Override public List<ColumnDef> activeColumns(String clientId, String tableName, String columnName) {
            return columns.stream().filter(c -> c.active() && c.tableName().equals(tableName)
                    && (columnName == null || columnName.isBlank() || c.columnName().equals(columnName))
                    && ownsSource(clientId, c.sourceId())).toList();
        }

        @Override public List<Rule> activeRules(String clientId, String target) {
            return rules.stream().filter(r -> r.active() && r.target().equals(target)
                    && ownsSource(clientId, r.sourceId())).toList();
        }

        @Override public List<EnumValue> activeEnums(String clientId, String enumCode) {
            return enums.stream().filter(e -> e.active() && e.enumCode().equals(enumCode)
                    && ownsSource(clientId, e.sourceId())).toList();
        }

        @Override public List<Alias> activeAliases(String clientId, String aliasName) {
            return aliases.stream().filter(a -> a.active() && a.aliasName().equals(aliasName)
                    && ownsSource(clientId, a.sourceId())).toList();
        }
    }

    static final class InMemoryMetadataRepository implements MetadataRepository {
        final Map<String, IndexDef> indexes = new HashMap<>();
        final Map<String, ShardDef> shards = new HashMap<>();
        final List<Conflict> conflicts = new ArrayList<>();

        @Override public Optional<IndexDef> findIndex(String clientId, String tableName, String indexName) {
            return Optional.ofNullable(indexes.get(clientId + "|" + tableName + "." + indexName));
        }

        @Override public List<IndexDef> indexesForTable(String clientId, String tableName) {
            return indexes.values().stream().filter(i -> i.clientId().equals(clientId) && i.tableName().equals(tableName)).toList();
        }

        @Override public void upsertIndex(String clientId, IndexDef def) {
            indexes.put(clientId + "|" + def.tableName() + "." + def.indexName(), def);
        }

        @Override public Optional<ShardDef> findShard(String clientId, String logicalTable) {
            return Optional.ofNullable(shards.get(clientId + "|" + logicalTable));
        }

        @Override public List<ShardDef> shards(String clientId) {
            return shards.values().stream().filter(s -> s.clientId().equals(clientId)).toList();
        }

        @Override public void upsertShard(String clientId, ShardDef def) {
            shards.put(clientId + "|" + def.logicalTable(), def);
        }

        @Override public void addConflict(String clientId, Conflict conflict) { conflicts.add(conflict); }

        @Override public List<Conflict> pendingConflicts(String clientId) {
            return conflicts.stream().filter(c -> c.clientId().equals(clientId) && c.status().equals("PENDING")).toList();
        }

        @Override public void resolveConflict(String clientId, String id, String status) { }
    }
}
