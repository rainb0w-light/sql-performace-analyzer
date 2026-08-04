package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.MarkdownChunker;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.MarkdownKnowledgeNormalizer;
import com.biz.sccba.sqlanalyzer.metadata.MetadataService;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Excel business-knowledge import lifecycle (development-guide §7.1):
 * original file stored as Artifact first → deterministic parse → preview (version DRAFT with
 * row-level errors) → publish (structured facts become active, prior version deactivated,
 * descriptions synced to the Knowledge/RAG layer) → rollback (reactivate a previous version).
 * All lookups are tenant scoped through the owning knowledge source.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeImportService {

    private final ArtifactService artifacts;
    private final ExcelKnowledgeParser parser;
    private final KnowledgeSourceRepository knowledge;
    private final MetadataService metadata;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<KnowledgeSearchIndex> searchIndexProvider;
    private final MarkdownKnowledgeNormalizer normalizer = new MarkdownKnowledgeNormalizer();
    private final MarkdownChunker chunker = new MarkdownChunker();

    public KnowledgeImportService(ArtifactService artifacts, ExcelKnowledgeParser parser,
                                  KnowledgeSourceRepository knowledge,
                                  MetadataService metadata, ObjectMapper objectMapper,
                                  ObjectProvider<KnowledgeSearchIndex> searchIndexProvider) {
        this.artifacts = artifacts;
        this.parser = parser;
        this.knowledge = knowledge;
        this.metadata = metadata;
        this.objectMapper = objectMapper;
        this.searchIndexProvider = searchIndexProvider;
    }

    /** Serializable parsed facts stored as the version preview (rebuildable from the artifact). */
    public record Facts(List<TableDef> tables, List<ColumnDef> columns, List<Rule> rules,
                        List<EnumValue> enums, List<Alias> aliases,
                        List<com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ShardRow> shards) {}

    public record ImportPreview(String sourceId, String versionId, int versionNo, Parsed parsed) {}

    public ImportPreview importExcel(String clientId, String sourceName, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Excel 内容不能为空");
        var artifact = artifacts.ingest(clientId, null, "KNOWLEDGE_EXCEL",
                fileName == null ? "knowledge.xlsx" : fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes, "{}");

        String name = sourceName == null || sourceName.isBlank() ? (fileName == null ? "业务知识" : fileName) : sourceName.trim();
        Source source = knowledge.listSources(clientId).stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseGet(() -> knowledge.createSource("ks_" + UUID.randomUUID(), clientId, name, "EXCEL"));

        Parsed parsed = parser.parse(bytes);
        try {
            Facts facts = new Facts(parsed.tables(), parsed.columns(), parsed.rules(), parsed.enums(),
                    parsed.aliases(), parsed.shards());
            String previewJson = objectMapper.writeValueAsString(facts);
            String errorJson = objectMapper.writeValueAsString(parsed.errors());
            int versionNo = knowledge.nextVersionNo(clientId, source.id());
            Version version = knowledge.createVersion(clientId, "kv_" + UUID.randomUUID(), source.id(), versionNo,
                    artifact.id(), previewJson, errorJson);
            return new ImportPreview(source.id(), version.id(), versionNo, parsed);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("知识解析结果无法保存", e);
        }
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public Version publish(String clientId, String versionId, String publishedBy) {
        Version version = requireOwnedVersion(clientId, versionId);
        if (version.isPublished() || "ACTIVE".equals(version.status())) {
            return version; // publish is intentionally idempotent
        }
        if (!"DRAFT".equals(version.status()) && !"READY".equals(version.status())
                && !"PUBLISHING".equals(version.status())) {
            throw new IllegalStateException("只有 READY 草稿可以发布，当前状态：" + version.status());
        }

        if (isDocumentDraft(version)) {
            indexDocumentOrThrow(clientId, version);
        } else {
            Facts facts = factsOf(version);
            knowledge.insertTables(version.sourceId(), version.id(), withIds(facts.tables()));
            knowledge.insertColumns(version.sourceId(), version.id(), withColumnIds(facts.columns()));
            knowledge.insertRules(version.sourceId(), version.id(), withRuleIds(facts.rules()));
            knowledge.insertEnums(version.sourceId(), version.id(), withEnumIds(facts.enums()));
            knowledge.insertAliases(version.sourceId(), version.id(), withAliasIds(facts.aliases()));
            indexOrThrow(clientId, version, chunksForStructuredFacts(clientId, version, facts), false);
            metadata.ingestShardsFromExcel(clientId, facts.shards());
        }

        // The externally visible switch is last. Any indexing exception above aborts this
        // transaction, so the old currentVersion remains effective.
        knowledge.publishVersion(clientId, version.sourceId(), version.id(), publishedBy);
        return knowledge.findVersionForClient(clientId, versionId).orElseThrow();
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public Version rollback(String clientId, String sourceId, String targetVersionId) {
        Source source = requireOwnedSource(clientId, sourceId);
        Version target = requireOwnedVersion(clientId, targetVersionId);
        if (!target.sourceId().equals(source.id())) throw new IllegalArgumentException("版本不属于该知识源");
        String current = source.currentVersionId();
        if (current != null && !current.equals(target.id())) {
            knowledge.markRolledBack(clientId, current);
        }
        if (isDocumentDraft(target)) {
            indexDocumentOrThrow(clientId, target);
        } else {
            indexOrThrow(clientId, target, chunksForStructuredFacts(clientId, target, factsOf(target)), false);
        }
        // Reactivate the target version's already-persisted facts (publish deactivates all others).
        knowledge.publishVersion(clientId, source.id(), target.id(), "rollback:" + clientId);
        return knowledge.findVersionForClient(clientId, targetVersionId).orElseThrow();
    }

    public List<Version> listVersions(String clientId, String sourceId) {
        return knowledge.listVersions(clientId, sourceId);
    }

    public Version preview(String clientId, String versionId) {
        return requireOwnedVersion(clientId, versionId);
    }

    private void indexDocumentOrThrow(String clientId, Version version) {
        try {
            KnowledgeAdminService.DocumentDraft draft =
                    objectMapper.readValue(version.previewJson(), KnowledgeAdminService.DocumentDraft.class);
            List<KnowledgeSearchIndex.Chunk> chunks = draft.chunks().stream()
                    .map(chunk -> new KnowledgeSearchIndex.Chunk(
                            chunk.kind(), chunk.name(), chunk.locator(), chunk.text()))
                    .toList();
            indexOrThrow(clientId, version, chunks, true);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("文档草稿无法读取", exception);
        }
    }

    private boolean isDocumentDraft(Version version) {
        try {
            return "UNSTRUCTURED".equals(objectMapper.readTree(version.previewJson()).path("format").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("版本预览无法读取", exception);
        }
    }

    /**
     * Builds deterministic chunks from the controlled Excel facts. This preserves the old
     * template path while the new Web upload path uses AgentScope Reader chunks directly.
     */
    private List<KnowledgeSearchIndex.Chunk> chunksForStructuredFacts(
            String clientId, Version version, Facts facts) {
        String sourceName = knowledge.findSourceForClient(clientId, version.sourceId())
                .map(s -> s.name()).orElse("业务知识");
        List<KnowledgeSearchIndex.Chunk> chunks = new ArrayList<>();
        String markdown = normalizer.normalize(sourceName, facts);
        chunks.addAll(chunker.chunk("knowledge.md", markdown));
        for (var t : facts.tables()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("TABLE", t.tableName(), t.sheetLocator(),
                    "表 " + t.tableName() + (isBlank(t.businessName()) ? "" : "（" + t.businessName() + "）")
                            + (isBlank(t.purpose()) ? "" : "：" + t.purpose())));
        }
        for (var c : facts.columns()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("COLUMN", c.tableName() + "." + c.columnName(),
                    c.sheetLocator(), "字段 " + c.tableName() + "." + c.columnName()
                    + (isBlank(c.businessMeaning()) ? "" : "：" + c.businessMeaning())
                    + "，敏感策略 " + c.sensitivityPolicy()));
        }
        for (var r : facts.rules()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("RULE", isBlank(r.ruleKey()) ? r.target() : r.ruleKey(),
                    r.sheetLocator(), "规则：" + r.description()));
        }
        for (var e : facts.enums()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("ENUM", e.enumCode(), e.sheetLocator(),
                    "枚举 " + e.enumCode() + (isBlank(e.displayName()) ? "" : "（" + e.displayName() + "）")));
        }
        for (var s : facts.shards()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("SHARD", s.logicalTable(), s.sheetLocator(),
                    s.logicalTable() + " 主分片键 " + s.shardKey() + "，二级分片键 " + s.secondaryShardKey()));
        }
        return chunks;
    }

    /**
     * Index failures are part of publish correctness and must never be swallowed. Structured
     * Excel remains publishable when semantic retrieval is deliberately disabled; unstructured
     * documents require an available retrieval backend because they have no alternate fact path.
     */
    private void indexOrThrow(String clientId, Version version,
                              List<KnowledgeSearchIndex.Chunk> chunks, boolean required) {
        var searchIndex = searchIndexProvider.getIfAvailable();
        if (searchIndex == null || !searchIndex.available()) {
            if (required) throw new IllegalStateException("知识检索后端未启用，文档不能发布");
            return;
        }
        searchIndex.index(clientId, version.sourceId(), version.versionNo(), chunks);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Facts factsOf(Version version) {
        try {
            return objectMapper.readValue(version.previewJson(), Facts.class);
        } catch (Exception e) {
            throw new IllegalStateException("版本预览无法读取", e);
        }
    }

    private Source requireOwnedSource(String clientId, String sourceId) {
        return knowledge.findSourceForClient(clientId, sourceId).orElseThrow(() -> new IllegalArgumentException("知识源不存在"));
    }

    private Version requireOwnedVersion(String clientId, String versionId) {
        return knowledge.findVersionForClient(clientId, versionId).orElseThrow(() -> new IllegalArgumentException("知识版本不存在"));
    }

    private List<TableDef> withIds(List<TableDef> rows) {
        List<TableDef> out = new ArrayList<>();
        for (TableDef r : rows) {
            out.add(new TableDef("kbt_" + UUID.randomUUID(), null, null, r.datasource(), r.schemaName(), r.tableName(),
                    r.businessName(), r.purpose(), r.owner(), r.dataDomain(), r.sheetLocator(), false, null));
        }
        return out;
    }

    private List<ColumnDef> withColumnIds(List<ColumnDef> rows) {
        List<ColumnDef> out = new ArrayList<>();
        for (ColumnDef r : rows) {
            out.add(new ColumnDef("kbc_" + UUID.randomUUID(), null, null, r.tableName(), r.columnName(),
                    r.businessMeaning(), r.dataType(), r.enumDomain(), r.sensitive(), r.required(),
                    r.sensitivityPolicy(), r.sheetLocator(), false, null));
        }
        return out;
    }

    private List<Rule> withRuleIds(List<Rule> rows) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : rows) {
            out.add(new Rule("kbr_" + UUID.randomUUID(), null, null, r.ruleKey(), r.target(), r.description(),
                    r.constraintExpr(), r.priority(), r.effectiveFrom(), r.sheetLocator(), false, null));
        }
        return out;
    }

    private List<EnumValue> withEnumIds(List<EnumValue> rows) {
        List<EnumValue> out = new ArrayList<>();
        for (EnumValue r : rows) {
            out.add(new EnumValue("kbe_" + UUID.randomUUID(), null, null, r.enumCode(), r.displayName(),
                    r.meaning(), r.valid(), r.sheetLocator(), false, null));
        }
        return out;
    }

    private List<Alias> withAliasIds(List<Alias> rows) {
        List<Alias> out = new ArrayList<>();
        for (Alias r : rows) {
            out.add(new Alias("kba_" + UUID.randomUUID(), null, null, r.aliasType(), r.aliasName(),
                    r.targetName(), r.sheetLocator(), false, null));
        }
        return out;
    }
}
