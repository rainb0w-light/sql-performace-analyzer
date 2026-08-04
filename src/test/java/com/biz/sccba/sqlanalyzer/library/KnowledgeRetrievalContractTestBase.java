package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Parsed;
import com.biz.sccba.sqlanalyzer.knowledge.ExcelKnowledgeParser;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeSearchIndex;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.MarkdownChunker;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.MarkdownKnowledgeNormalizer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knowledge search index contract (docs/cloud-code-next-goal.md §5.4), executed identically against
 * the H2 portable adapter (every build) and the PgVector adapter (Docker gate): markdown goes
 * through real chunking + embedding + retrieval; results carry kind/name/source/version/locator/
 * confidence; tenants are isolated; results are deterministic.
 */
public abstract class KnowledgeRetrievalContractTestBase {

    /** The retriever under test, wired with a deterministic fake embedding model. */
    abstract KnowledgeSearchIndex retriever();

    private static String libraryMarkdown() throws Exception {
        try (InputStream in = KnowledgeRetrievalContractTestBase.class
                .getResourceAsStream("/fixtures/library/knowledge/library-domain.md")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<KnowledgeSearchIndex.Chunk> libraryChunks(String sourceName) throws Exception {
        List<KnowledgeSearchIndex.Chunk> chunks = new ArrayList<>(new MarkdownChunker().chunk("library-domain.md", libraryMarkdown()));
        Parsed parsed = new ExcelKnowledgeParser().parse(LibraryWorkbookFixtures.libraryKnowledge());
        var facts = new com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService.Facts(
                parsed.tables(), parsed.columns(), parsed.rules(), parsed.enums(), parsed.aliases(), parsed.shards());
        String normalized = new MarkdownKnowledgeNormalizer().normalize(sourceName, facts);
        chunks.addAll(new MarkdownChunker().chunk("knowledge.md", normalized));
        for (var c : parsed.columns()) {
            chunks.add(new KnowledgeSearchIndex.Chunk("COLUMN", c.tableName() + "." + c.columnName(), c.sheetLocator(),
                    "字段 " + c.tableName() + "." + c.columnName() + "：" + c.businessMeaning()
                            + "，敏感策略 " + c.sensitivityPolicy()));
        }
        return chunks;
    }

    @Test
    void retrievalCarriesEvidenceAndIsolatesTenants() throws Exception {
        String clientA = "client_retrieval_a_" + System.nanoTime();
        String clientB = "client_retrieval_b_" + System.nanoTime();
        String sourceId = "ks_library";

        retriever().index(clientA, sourceId, 1, libraryChunks("图书业务知识"));

        // Semantic search returns attributed evidence, not bare text.
        List<KnowledgeSearchIndex.SearchHit> hits = retriever().search(clientA, "逾期定义 借阅 到期", sourceId, 5);
        assertFalse(hits.isEmpty(), "must retrieve relevant chunks");
        KnowledgeSearchIndex.SearchHit top = hits.get(0);
        assertNotNull(top.kind());
        assertNotNull(top.name());
        assertEquals(sourceId, top.sourceId());
        assertEquals(1, top.versionNo());
        assertNotNull(top.locator(), "locator (heading/line or Sheet!row) must travel with the result");
        assertTrue(top.confidence() >= 0 && top.confidence() <= 1);
        assertTrue(top.text().contains("逾期"), "top hit must be about overdue definition: " + top.text());

        // Markdown chunks carry heading+line locators; structured chunks carry Sheet!row locators.
        List<KnowledgeSearchIndex.SearchHit> memberHits = retriever().search(clientA, "读者证号 敏感 策略", sourceId, 8);
        assertTrue(memberHits.stream().anyMatch(f -> f.text().contains("member_no")),
                "member_no sensitivity fact must be retrievable");
        assertTrue(memberHits.stream().anyMatch(f -> f.locator().contains("columns!row")),
                "structured fact must carry its Excel Sheet/row locator: "
                        + memberHits.stream().map(KnowledgeSearchIndex.SearchHit::locator).toList());
        assertTrue(hits.stream().anyMatch(f -> f.locator().contains("#")),
                "markdown fact must carry its heading/line locator");

        // Tenant isolation: client B sees nothing of client A's knowledge.
        assertTrue(retriever().search(clientB, "逾期定义 借阅 到期", null, 5).isEmpty(),
                "retrieval must never cross tenants");

        // Determinism: same query, same ordering and scores.
        List<KnowledgeSearchIndex.SearchHit> again = retriever().search(clientA, "逾期定义 借阅 到期", sourceId, 5);
        assertEquals(hits.stream().map(KnowledgeSearchIndex.SearchHit::locator).toList(),
                again.stream().map(KnowledgeSearchIndex.SearchHit::locator).toList(),
                "retrieval must be deterministic (no drifting external model)");
    }

    @Test
    void normalizedMarkdownIsDeterministic() throws Exception {
        Parsed parsed = new ExcelKnowledgeParser().parse(LibraryWorkbookFixtures.libraryKnowledge());
        var facts = new com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService.Facts(
                parsed.tables(), parsed.columns(), parsed.rules(), parsed.enums(), parsed.aliases(), parsed.shards());
        var normalizer = new MarkdownKnowledgeNormalizer();
        assertEquals(normalizer.normalize("图书业务知识", facts), normalizer.normalize("图书业务知识", facts),
                "normalized markdown must be deterministic for the same facts");
        String md = normalizer.normalize("图书业务知识", facts);
        assertTrue(md.contains("member_no") && md.contains("HASHED"),
                "normalized markdown must carry the sensitivity facts");
        assertTrue(md.contains("主分片键 `member_id`") && md.contains("二级分片键 `borrowed_at`"),
                "normalized markdown must carry primary + secondary shard keys");
    }

    @Test
    void activeVersionFilterNeverReturnsStaleChunksAfterRollback() {
        String client = "client_active_version_" + System.nanoTime();
        String source = "ks_versioned";
        retriever().index(client, source, 1,
                List.of(new KnowledgeSearchIndex.Chunk("RULE", "overdue", "v1#line1", "旧版本：逾期为 30 天")));
        retriever().index(client, source, 2,
                List.of(new KnowledgeSearchIndex.Chunk("RULE", "overdue", "version-two#line1", "新版本：逾期为 60 天")));

        List<KnowledgeSearchIndex.SearchHit> rolledBack = retriever().search(
                client, "逾期 天数", source, 1, 10);

        assertFalse(rolledBack.isEmpty());
        assertTrue(rolledBack.stream().allMatch(fact -> fact.versionNo() == 1));
        assertTrue(rolledBack.stream().noneMatch(fact -> fact.text().contains("60 天")),
                "rollback to version 1 must hide version 2 embeddings");
    }
}
