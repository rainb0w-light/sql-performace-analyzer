package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits a business-semantics Markdown document into section-level chunks with stable locators
 * (docs/cloud-code-next-goal.md §4.5): each chunk keeps its heading path and line range so
 * retrieval results can point back to {@code library-domain.md#查询主路径@L120-L130}. Stable
 * knowledge IDs written as {@code [lib-…]} travel inside the chunk text.
 */
public final class MarkdownChunker {

    /** Chunks one Markdown document; locator = resourceName#anchor@L<start>-L<end>. */
    public List<KnowledgeSearchIndex.Chunk> chunk(String resourceName, String markdown) {
        List<KnowledgeSearchIndex.Chunk> chunks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        String heading = null;
        int start = -1;
        StringBuilder body = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("## ")) {
                flush(resourceName, chunks, heading, start, i, body);
                heading = line.substring(3).trim();
                start = i + 1; // 1-based line number
                body.setLength(0);
                body.append(line).append('\n');
            } else if (heading != null) {
                body.append(line).append('\n');
            }
        }
        flush(resourceName, chunks, heading, start, lines.length, body);
        return chunks;
    }

    private void flush(String resourceName, List<KnowledgeSearchIndex.Chunk> out, String heading,
                       int start, int endExclusive, StringBuilder body) {
        if (heading == null || body.length() == 0) return;
        String text = body.toString().trim();
        if (text.isEmpty()) return;
        String locator = resourceName + "#" + anchor(heading) + "@L" + start + "-L" + endExclusive;
        out.add(new KnowledgeSearchIndex.Chunk("MARKDOWN", heading, locator, text));
    }

    private static String anchor(String heading) {
        return heading.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }
}
