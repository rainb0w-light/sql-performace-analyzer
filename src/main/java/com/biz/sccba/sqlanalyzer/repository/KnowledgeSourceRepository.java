package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Alias;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.ColumnDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.EnumValue;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Rule;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.TableDef;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;

import java.util.List;
import java.util.Optional;

/**
 * Structured business knowledge persistence (development-guide §7.1). All access is tenant
 * scoped: active-fact queries join the owning knowledge source so a client can never read
 * another client's published facts for the same table name (docs/cloud-code-next-goal.md §5).
 */
public interface KnowledgeSourceRepository {

    Source createSource(String id, String clientId, String name, String sourceType);

    Optional<Source> findSourceForClient(String clientId, String sourceId);

    List<Source> listSources(String clientId);

    Version createVersion(String clientId, String id, String sourceId, int versionNo, String artifactId,
                          String previewJson, String errorJson);

    Optional<Version> findVersionForClient(String clientId, String versionId);

    List<Version> listVersions(String clientId, String sourceId);

    int nextVersionNo(String clientId, String sourceId);

    void insertTables(String sourceId, String versionId, List<TableDef> rows);

    void insertColumns(String sourceId, String versionId, List<ColumnDef> rows);

    void insertRules(String sourceId, String versionId, List<Rule> rows);

    void insertEnums(String sourceId, String versionId, List<EnumValue> rows);

    void insertAliases(String sourceId, String versionId, List<Alias> rows);

    /** Activates one version's facts and deactivates all others of the source; marks statuses. */
    void publishVersion(String clientId, String sourceId, String versionId, String publishedBy);

    void markRolledBack(String clientId, String versionId);

    /** Active-fact queries (exact, structured retrieval), scoped to the client's sources. */
    List<TableDef> activeTables(String clientId, String tableName);

    List<ColumnDef> activeColumns(String clientId, String tableName, String columnName);

    List<Rule> activeRules(String clientId, String target);

    List<EnumValue> activeEnums(String clientId, String enumCode);

    List<Alias> activeAliases(String clientId, String aliasName);
}
