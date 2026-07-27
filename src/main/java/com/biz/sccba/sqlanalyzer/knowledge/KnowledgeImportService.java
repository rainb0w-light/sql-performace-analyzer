package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
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
    private final ObjectProvider<KnowledgeRetriever> retrieverProvider;
    private final MarkdownKnowledgeNormalizer normalizer = new MarkdownKnowledgeNormalizer();
    private final MarkdownChunker chunker = new MarkdownChunker();

    public KnowledgeImportService(ArtifactService artifacts, ExcelKnowledgeParser parser,
                                  KnowledgeSourceRepository knowledge,
                                  MetadataService metadata, ObjectMapper objectMapper,
                                  ObjectProvider<KnowledgeRetriever> retrieverProvider) {
        this.artifacts = artifacts;
        this.parser = parser;
        this.knowledge = knowledge;
        this.metadata = metadata;
        this.objectMapper = objectMapper;
        this.retrieverProvider = retrieverProvider;
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
        if (!"DRAFT".equals(version.status())) {
            throw new IllegalStateException("只有 DRAFT 版本可以发布，当前状态：" + version.status());
        }
        Facts facts = factsOf(version);
        knowledge.insertTables(version.sourceId(), version.id(), withIds(facts.tables()));
        knowledge.insertColumns(version.sourceId(), version.id(), withColumnIds(facts.columns()));
        knowledge.insertRules(version.sourceId(), version.id(), withRuleIds(facts.rules()));
        knowledge.insertEnums(version.sourceId(), version.id(), withEnumIds(facts.enums()));
        knowledge.insertAliases(version.sourceId(), version.id(), withAliasIds(facts.aliases()));
        knowledge.publishVersion(clientId, version.sourceId(), version.id(), publishedBy);
        metadata.ingestShardsFromExcel(clientId, facts.shards());
        Version published = knowledge.findVersionForClient(clientId, versionId).orElseThrow();
        indexSafely(clientId, published, facts);
        return published;
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
        // Reactivate the target version's already-persisted facts (publish deactivates all others).
        knowledge.publishVersion(clientId, source.id(), target.id(), "rollback:" + clientId);
        Version rolled = knowledge.findVersionForClient(clientId, targetVersionId).orElseThrow();
        indexSafely(clientId, rolled, factsOf(rolled));
        return rolled;
    }

    public List<Version> listVersions(String clientId, String sourceId) {
        return knowledge.listVersions(clientId, sourceId);
    }

    public Version preview(String clientId, String versionId) {
        return requireOwnedVersion(clientId, versionId);
    }

    /**
     * Syncs a published version into the semantic retrieval layer through the vendor-neutral
     * KnowledgeRetriever port: the canonical Markdown is derived from the published facts (Excel
     * and Markdown never diverge), chunked with stable locators, and indexed together with the
     * structured fact chunks (each carrying its Excel Sheet/row locator). Best-effort: structured
     * facts remain the source of truth.
     */
    private void indexSafely(String clientId, Version version, Facts facts) {
        var retriever = retrieverProvider.getIfAvailable();
        if (retriever == null || !retriever.available()) return;
        try {
            String sourceName = knowledge.findSourceForClient(clientId, version.sourceId())
                    .map(s -> s.name()).orElse("业务知识");
            List<KnowledgeRetriever.Chunk> chunks = new ArrayList<>();
            String markdown = normalizer.normalize(sourceName, facts);
            chunks.addAll(chunker.chunk("knowledge.md", markdown));
            for (var t : facts.tables()) {
                chunks.add(new KnowledgeRetriever.Chunk("TABLE", t.tableName(), t.sheetLocator(),
                        "表 " + t.tableName() + (isBlank(t.businessName()) ? "" : "（" + t.businessName() + "）")
                                + (isBlank(t.purpose()) ? "" : "：" + t.purpose())));
            }
            for (var c : facts.columns()) {
                chunks.add(new KnowledgeRetriever.Chunk("COLUMN", c.tableName() + "." + c.columnName(),
                        c.sheetLocator(), "字段 " + c.tableName() + "." + c.columnName()
                        + (isBlank(c.businessMeaning()) ? "" : "：" + c.businessMeaning())
                        + "，敏感策略 " + c.sensitivityPolicy()));
            }
            for (var r : facts.rules()) {
                chunks.add(new KnowledgeRetriever.Chunk("RULE", isBlank(r.ruleKey()) ? r.target() : r.ruleKey(),
                        r.sheetLocator(), "规则：" + r.description()));
            }
            for (var e : facts.enums()) {
                chunks.add(new KnowledgeRetriever.Chunk("ENUM", e.enumCode(), e.sheetLocator(),
                        "枚举 " + e.enumCode() + (isBlank(e.displayName()) ? "" : "（" + e.displayName() + "）")));
            }
            for (var s : facts.shards()) {
                chunks.add(new KnowledgeRetriever.Chunk("SHARD", s.logicalTable(), s.sheetLocator(),
                        s.logicalTable() + " 主分片键 " + s.shardKey() + "，二级分片键 " + s.secondaryShardKey()));
            }
            retriever.index(clientId, version.sourceId(), version.versionNo(), chunks);
        } catch (RuntimeException e) {
            // Retrieval sync is best-effort; structured facts remain the source of truth.
        }
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
