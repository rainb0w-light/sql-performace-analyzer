package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbAliasEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbColumnDefEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbEnumValueEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbRuleEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbTableDefEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KnowledgeSourceEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KnowledgeVersionEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KbAliasJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KbColumnDefJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KbEnumValueJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KbRuleJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KbTableDefJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KnowledgeSourceJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.KnowledgeVersionJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Structured knowledge persistence. Publishing is one transaction on both databases: deactivate
 * the source's active facts, activate the target version's facts, mark statuses. Active-fact
 * queries always join the owning source and filter by client.
 */
@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcKnowledgeSourceRepository implements KnowledgeSourceRepository {

    private final KnowledgeSourceJdbcRepository sources;
    private final KnowledgeVersionJdbcRepository versions;
    private final KbTableDefJdbcRepository tables;
    private final KbColumnDefJdbcRepository columns;
    private final KbRuleJdbcRepository rules;
    private final KbEnumValueJdbcRepository enums;
    private final KbAliasJdbcRepository aliases;

    public JdbcKnowledgeSourceRepository(KnowledgeSourceJdbcRepository sources, KnowledgeVersionJdbcRepository versions,
                                         KbTableDefJdbcRepository tables, KbColumnDefJdbcRepository columns,
                                         KbRuleJdbcRepository rules, KbEnumValueJdbcRepository enums,
                                         KbAliasJdbcRepository aliases) {
        this.sources = sources;
        this.versions = versions;
        this.tables = tables;
        this.columns = columns;
        this.rules = rules;
        this.enums = enums;
        this.aliases = aliases;
    }

    @Override
    public Source createSource(String id, String clientId, String name, String sourceType) {
        KnowledgeSourceEntity entity = new KnowledgeSourceEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setName(name);
        entity.setSourceType(sourceType);
        entity.markNew();
        return toSource(sources.save(entity));
    }

    @Override
    public Optional<Source> findSourceForClient(String clientId, String sourceId) {
        return sources.findByIdAndClientId(sourceId, clientId).map(JdbcKnowledgeSourceRepository::toSource);
    }

    @Override
    public List<Source> listSources(String clientId) {
        return sources.findAllByClientId(clientId).stream().map(JdbcKnowledgeSourceRepository::toSource).toList();
    }

    @Override
    public Version createVersion(String clientId, String id, String sourceId, int versionNo, String artifactId,
                                 String previewJson, String errorJson) {
        if (sources.findByIdAndClientId(sourceId, clientId).isEmpty()) {
            throw new IllegalArgumentException("知识源不存在");
        }
        KnowledgeVersionEntity entity = new KnowledgeVersionEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setSourceId(sourceId);
        entity.setVersionNo(versionNo);
        entity.setStatus("DRAFT");
        entity.setArtifactId(artifactId);
        entity.setPreviewJson(previewJson);
        entity.setErrorJson(errorJson);
        entity.markNew();
        return toVersion(versions.save(entity));
    }

    @Override
    public Optional<Version> findVersionForClient(String clientId, String versionId) {
        return versions.findByIdForClient(clientId, versionId).map(JdbcKnowledgeSourceRepository::toVersion);
    }

    @Override
    public List<Version> listVersions(String clientId, String sourceId) {
        if (sources.findByIdAndClientId(sourceId, clientId).isEmpty()) {
            throw new IllegalArgumentException("知识源不存在");
        }
        return versions.findBySourceOrderByVersionNoDesc(sourceId).stream()
                .map(JdbcKnowledgeSourceRepository::toVersion).toList();
    }

    @Override
    public int nextVersionNo(String clientId, String sourceId) {
        if (sources.findByIdAndClientId(sourceId, clientId).isEmpty()) {
            throw new IllegalArgumentException("知识源不存在");
        }
        return versions.maxVersionNo(sourceId) + 1;
    }

    @Override
    public void insertTables(String sourceId, String versionId, List<TableDef> rows) {
        List<KbTableDefEntity> entities = new ArrayList<>();
        for (TableDef r : rows) {
            KbTableDefEntity e = new KbTableDefEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(r.id());
            e.setSourceId(sourceId);
            e.setVersionId(versionId);
            e.setDatasource(r.datasource());
            e.setSchemaName(r.schemaName());
            e.setTableName(r.tableName());
            e.setBusinessName(r.businessName());
            e.setPurpose(r.purpose());
            e.setOwner(r.owner());
            e.setDataDomain(r.dataDomain());
            e.setSheetLocator(r.sheetLocator());
            e.setActive(false);
            e.markNew();
            entities.add(e);
        }
        tables.saveAll(entities);
    }

    @Override
    public void insertColumns(String sourceId, String versionId, List<ColumnDef> rows) {
        List<KbColumnDefEntity> entities = new ArrayList<>();
        for (ColumnDef r : rows) {
            KbColumnDefEntity e = new KbColumnDefEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(r.id());
            e.setSourceId(sourceId);
            e.setVersionId(versionId);
            e.setTableName(r.tableName());
            e.setColumnName(r.columnName());
            e.setBusinessMeaning(r.businessMeaning());
            e.setDataType(r.dataType());
            e.setEnumDomain(r.enumDomain());
            e.setIsSensitive(r.sensitive());
            e.setIsRequired(r.required());
            e.setSensitivityPolicy(r.sensitivityPolicy());
            e.setSheetLocator(r.sheetLocator());
            e.setActive(false);
            e.markNew();
            entities.add(e);
        }
        columns.saveAll(entities);
    }

    @Override
    public void insertRules(String sourceId, String versionId, List<Rule> rows) {
        List<KbRuleEntity> entities = new ArrayList<>();
        for (Rule r : rows) {
            KbRuleEntity e = new KbRuleEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(r.id());
            e.setSourceId(sourceId);
            e.setVersionId(versionId);
            e.setRuleKey(r.ruleKey());
            e.setTarget(r.target());
            e.setDescription(r.description());
            e.setConstraintExpr(r.constraintExpr());
            e.setPriority(r.priority());
            e.setEffectiveFrom(r.effectiveFrom());
            e.setSheetLocator(r.sheetLocator());
            e.setActive(false);
            e.markNew();
            entities.add(e);
        }
        rules.saveAll(entities);
    }

    @Override
    public void insertEnums(String sourceId, String versionId, List<EnumValue> rows) {
        List<KbEnumValueEntity> entities = new ArrayList<>();
        for (EnumValue r : rows) {
            KbEnumValueEntity e = new KbEnumValueEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(r.id());
            e.setSourceId(sourceId);
            e.setVersionId(versionId);
            e.setEnumCode(r.enumCode());
            e.setDisplayName(r.displayName());
            e.setMeaning(r.meaning());
            e.setIsValid(r.valid());
            e.setSheetLocator(r.sheetLocator());
            e.setActive(false);
            e.markNew();
            entities.add(e);
        }
        enums.saveAll(entities);
    }

    @Override
    public void insertAliases(String sourceId, String versionId, List<Alias> rows) {
        List<KbAliasEntity> entities = new ArrayList<>();
        for (Alias r : rows) {
            KbAliasEntity e = new KbAliasEntity();
            e.setCreatedAt(java.time.Instant.now());
            e.setId(r.id());
            e.setSourceId(sourceId);
            e.setVersionId(versionId);
            e.setAliasType(r.aliasType());
            e.setAliasName(r.aliasName());
            e.setTargetName(r.targetName());
            e.setSheetLocator(r.sheetLocator());
            e.setActive(false);
            e.markNew();
            entities.add(e);
        }
        aliases.saveAll(entities);
    }

    @Override
    @Transactional(transactionManager = "managementTransactionManager")
    public void publishVersion(String clientId, String sourceId, String versionId, String publishedBy) {
        if (sources.findByIdAndClientId(sourceId, clientId).isEmpty()) {
            throw new IllegalArgumentException("知识源不存在");
        }
        if (versions.findByIdForClient(clientId, versionId).isEmpty()) {
            throw new IllegalArgumentException("知识版本不存在");
        }
        tables.deactivateBySource(sourceId);
        columns.deactivateBySource(sourceId);
        rules.deactivateBySource(sourceId);
        enums.deactivateBySource(sourceId);
        aliases.deactivateBySource(sourceId);
        tables.activateByVersion(versionId);
        columns.activateByVersion(versionId);
        rules.activateByVersion(versionId);
        enums.activateByVersion(versionId);
        aliases.activateByVersion(versionId);
        versions.markPublished(versionId, publishedBy);
        sources.updateCurrentVersion(sourceId, versionId);
    }

    @Override
    public void markRolledBack(String clientId, String versionId) {
        if (versions.findByIdForClient(clientId, versionId).isEmpty()) {
            throw new IllegalArgumentException("知识版本不存在");
        }
        versions.markRolledBack(versionId);
    }

    @Override
    public List<TableDef> activeTables(String clientId, String tableName) {
        return tables.findActiveForClient(clientId, tableName).stream().map(e -> new TableDef(e.getId(),
                e.getSourceId(), e.getVersionId(), e.getDatasource(), e.getSchemaName(), e.getTableName(),
                e.getBusinessName(), e.getPurpose(), e.getOwner(), e.getDataDomain(), e.getSheetLocator(),
                Boolean.TRUE.equals(e.getActive()), e.getCreatedAt())).toList();
    }

    @Override
    public List<ColumnDef> activeColumns(String clientId, String tableName, String columnName) {
        var rows = columnName == null || columnName.isBlank()
                ? columns.findActiveForClient(clientId, tableName)
                : columns.findActiveColumnForClient(clientId, tableName, columnName);
        return rows.stream().map(e -> new ColumnDef(e.getId(), e.getSourceId(), e.getVersionId(), e.getTableName(),
                e.getColumnName(), e.getBusinessMeaning(), e.getDataType(), e.getEnumDomain(),
                Boolean.TRUE.equals(e.getIsSensitive()), Boolean.TRUE.equals(e.getIsRequired()),
                e.getSensitivityPolicy(), e.getSheetLocator(), Boolean.TRUE.equals(e.getActive()),
                e.getCreatedAt())).toList();
    }

    @Override
    public List<Rule> activeRules(String clientId, String target) {
        return rules.findActiveForClient(clientId, target).stream().map(e -> new Rule(e.getId(), e.getSourceId(),
                e.getVersionId(), e.getRuleKey(), e.getTarget(), e.getDescription(), e.getConstraintExpr(),
                e.getPriority(), e.getEffectiveFrom(), e.getSheetLocator(), Boolean.TRUE.equals(e.getActive()),
                e.getCreatedAt())).toList();
    }

    @Override
    public List<EnumValue> activeEnums(String clientId, String enumCode) {
        return enums.findActiveForClient(clientId, enumCode).stream().map(e -> new EnumValue(e.getId(),
                e.getSourceId(), e.getVersionId(), e.getEnumCode(), e.getDisplayName(), e.getMeaning(),
                Boolean.TRUE.equals(e.getIsValid()), e.getSheetLocator(), Boolean.TRUE.equals(e.getActive()),
                e.getCreatedAt())).toList();
    }

    @Override
    public List<Alias> activeAliases(String clientId, String aliasName) {
        return aliases.findActiveForClient(clientId, aliasName).stream().map(e -> new Alias(e.getId(),
                e.getSourceId(), e.getVersionId(), e.getAliasType(), e.getAliasName(), e.getTargetName(),
                e.getSheetLocator(), Boolean.TRUE.equals(e.getActive()), e.getCreatedAt())).toList();
    }

    private static Source toSource(KnowledgeSourceEntity e) {
        return new Source(e.getId(), e.getClientId(), e.getName(), e.getSourceType(),
                e.getCurrentVersionId(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static Version toVersion(KnowledgeVersionEntity e) {
        return new Version(e.getId(), e.getSourceId(), e.getVersionNo(), e.getStatus(), e.getArtifactId(),
                e.getPreviewJson(), e.getErrorJson(), e.getPublishedBy(), e.getPublishedAt(), e.getCreatedAt());
    }
}
