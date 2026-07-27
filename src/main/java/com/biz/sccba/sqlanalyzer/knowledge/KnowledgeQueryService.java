package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured exact retrieval over published business knowledge. Every fact carries source,
 * version, locator and confidence (development-guide §2.4): reports must show evidence, not just
 * model conclusions. All queries are scoped to the authenticated client — a client can never read
 * another client's published facts, even for identically named tables
 * (docs/cloud-code-next-goal.md §5). Semantic (RAG) retrieval is layered on top by
 * {@link KnowledgeVectorIndexer}.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeQueryService {

    private static final double EXCEL_PUBLISHED_CONFIDENCE = 0.9;

    private final KnowledgeSourceRepository knowledge;

    public KnowledgeQueryService(KnowledgeSourceRepository knowledge) {
        this.knowledge = knowledge;
    }

    public record Evidence(String sourceType, String sourceId, int version, String locator,
                           Instant collectedAt, double confidence) {}

    public record Fact(String kind, String name, String text, Map<String, Object> structured, Evidence evidence) {}

    public List<Fact> tables(String clientId, String tableName) {
        Map<String, Version> versions = new HashMap<>();
        List<Fact> out = new ArrayList<>();
        for (var t : knowledge.activeTables(clientId, tableName)) {
            out.add(new Fact("TABLE", t.tableName(),
                    "表 " + t.tableName() + (isBlank(t.businessName()) ? "" : "（" + t.businessName() + "）")
                            + (isBlank(t.purpose()) ? "" : "：" + t.purpose()),
                    mapOf("datasource", t.datasource(), "schema", t.schemaName(), "owner", t.owner(), "dataDomain", t.dataDomain()),
                    evidence(clientId, t.sourceId(), t.versionId(), t.sheetLocator(), versions)));
        }
        return out;
    }

    public List<Fact> columns(String clientId, String tableName, String columnName) {
        Map<String, Version> versions = new HashMap<>();
        List<Fact> out = new ArrayList<>();
        for (var c : knowledge.activeColumns(clientId, tableName, columnName)) {
            out.add(new Fact("COLUMN", c.tableName() + "." + c.columnName(),
                    "字段 " + c.tableName() + "." + c.columnName()
                            + (isBlank(c.businessMeaning()) ? "" : "：" + c.businessMeaning()),
                    mapOf("dataType", c.dataType(), "enumDomain", c.enumDomain(),
                            "sensitive", c.sensitive(), "required", c.required(),
                            "sensitivityPolicy", c.sensitivityPolicy()),
                    evidence(clientId, c.sourceId(), c.versionId(), c.sheetLocator(), versions)));
        }
        return out;
    }

    public List<Fact> rules(String clientId, String target) {
        Map<String, Version> versions = new HashMap<>();
        List<Fact> out = new ArrayList<>();
        for (var r : knowledge.activeRules(clientId, target)) {
            out.add(new Fact("RULE", isBlank(r.ruleKey()) ? r.target() : r.ruleKey(),
                    r.description() + (isBlank(r.constraintExpr()) ? "" : "（约束：" + r.constraintExpr() + "）"),
                    mapOf("target", r.target(), "priority", r.priority()),
                    evidence(clientId, r.sourceId(), r.versionId(), r.sheetLocator(), versions)));
        }
        return out;
    }

    public List<Fact> enums(String clientId, String enumCode) {
        Map<String, Version> versions = new HashMap<>();
        List<Fact> out = new ArrayList<>();
        for (var e : knowledge.activeEnums(clientId, enumCode)) {
            out.add(new Fact("ENUM", e.enumCode(),
                    "枚举 " + e.enumCode() + (isBlank(e.displayName()) ? "" : "（" + e.displayName() + "）")
                            + (isBlank(e.meaning()) ? "" : "：" + e.meaning()),
                    mapOf("valid", e.valid()),
                    evidence(clientId, e.sourceId(), e.versionId(), e.sheetLocator(), versions)));
        }
        return out;
    }

    public List<Fact> aliases(String clientId, String aliasName) {
        Map<String, Version> versions = new HashMap<>();
        List<Fact> out = new ArrayList<>();
        for (var a : knowledge.activeAliases(clientId, aliasName)) {
            out.add(new Fact("ALIAS", a.aliasName(), a.aliasType() + " 别名 " + a.aliasName() + " -> " + a.targetName(),
                    mapOf("aliasType", a.aliasType(), "target", a.targetName()),
                    evidence(clientId, a.sourceId(), a.versionId(), a.sheetLocator(), versions)));
        }
        return out;
    }

    /** Resolves a name through aliases to known table facts (synonym support), scoped to the client. */
    public List<Fact> resolveTables(String clientId, String nameOrAlias) {
        List<Fact> direct = tables(clientId, nameOrAlias);
        if (!direct.isEmpty()) return direct;
        List<Fact> viaAlias = new ArrayList<>();
        for (var alias : aliases(clientId, nameOrAlias)) {
            viaAlias.addAll(tables(clientId, String.valueOf(alias.structured().get("target"))));
        }
        return viaAlias;
    }

    /** Sensitivity policy lookup used by the profiler (defaults to PLAINTEXT for unknown columns). */
    public String sensitivityPolicy(String clientId, String tableName, String columnName) {
        var cols = knowledge.activeColumns(clientId, tableName, columnName);
        if (cols.isEmpty()) return "PLAINTEXT";
        return cols.get(0).sensitivityPolicy();
    }

    private Evidence evidence(String clientId, String sourceId, String versionId, String locator, Map<String, Version> cache) {
        Version version = cache.computeIfAbsent(versionId, id -> knowledge.findVersionForClient(clientId, id).orElse(null));
        return new Evidence("EXCEL_PUBLISHED", sourceId, version == null ? 0 : version.versionNo(),
                locator, version == null ? null : version.publishedAt(), EXCEL_PUBLISHED_CONFIDENCE);
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object value = kv[i + 1];
            if (value != null && !(value instanceof String s && s.isEmpty())) {
                map.put(String.valueOf(kv[i]), value);
            }
        }
        return map;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
