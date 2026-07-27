package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService.Facts;

/**
 * Generates the canonical Markdown rendering of a published Excel knowledge version
 * (docs/cloud-code-next-goal.md §4.1): Excel and Markdown never diverge because the Markdown is
 * derived from the published structured facts, not maintained separately. The output is
 * deterministic for the same facts, so it is regenerable and diffable.
 */
public final class MarkdownKnowledgeNormalizer {

    public String normalize(String sourceName, Facts facts) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(sourceName).append("（规范化业务知识）\n\n");
        md.append("> 本文档由已发布的结构化事实自动生成，与 Excel 知识源同源，不得手工分叉。\n\n");

        md.append("## 表定义\n\n");
        for (var t : facts.tables()) {
            md.append("- `").append(t.tableName()).append("`")
                    .append(isBlank(t.businessName()) ? "" : "（" + t.businessName() + "）")
                    .append(isBlank(t.purpose()) ? "" : "：" + t.purpose())
                    .append("。定位：").append(t.sheetLocator()).append("\n");
        }

        md.append("\n## 字段定义\n\n");
        for (var c : facts.columns()) {
            md.append("- `").append(c.tableName()).append('.').append(c.columnName()).append("`")
                    .append(isBlank(c.businessMeaning()) ? "" : "：" + c.businessMeaning())
                    .append(isBlank(c.enumDomain()) ? "" : "，枚举域 " + c.enumDomain())
                    .append("，敏感策略 ").append(c.sensitivityPolicy())
                    .append("。定位：").append(c.sheetLocator()).append("\n");
        }

        md.append("\n## 业务规则\n\n");
        for (var r : facts.rules()) {
            md.append("- ").append(isBlank(r.ruleKey()) ? r.target() : r.ruleKey())
                    .append("：").append(r.description())
                    .append(isBlank(r.constraintExpr()) ? "" : "（约束：")
                    .append(isBlank(r.constraintExpr()) ? "" : r.constraintExpr())
                    .append(isBlank(r.constraintExpr()) ? "" : "）")
                    .append("。定位：").append(r.sheetLocator()).append("\n");
        }

        md.append("\n## 枚举\n\n");
        for (var e : facts.enums()) {
            md.append("- `").append(e.enumCode()).append("`")
                    .append(isBlank(e.displayName()) ? "" : "（" + e.displayName() + "）")
                    .append(isBlank(e.meaning()) ? "" : "：" + e.meaning())
                    .append(e.valid() ? "" : "【已失效】")
                    .append("。定位：").append(e.sheetLocator()).append("\n");
        }

        md.append("\n## 别名\n\n");
        for (var a : facts.aliases()) {
            md.append("- ").append(a.aliasType()).append(" 别名 `").append(a.aliasName())
                    .append("` → `").append(a.targetName()).append("`")
                    .append("。定位：").append(a.sheetLocator()).append("\n");
        }

        md.append("\n## 分片与二级分片规则\n\n");
        for (var s : facts.shards()) {
            md.append("- `").append(s.logicalTable()).append("` 主分片键 `").append(s.shardKey())
                    .append("`，二级分片键 `").append(s.secondaryShardKey())
                    .append("`，算法 ").append(s.algorithm())
                    .append("。定位：").append(s.sheetLocator()).append("\n");
        }
        return md.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
