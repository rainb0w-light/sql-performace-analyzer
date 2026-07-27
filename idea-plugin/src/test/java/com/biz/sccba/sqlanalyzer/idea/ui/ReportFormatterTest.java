package com.biz.sccba.sqlanalyzer.idea.ui;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ReportFormatterTest {
    @Test
    public void rendersSummaryRisksDistributionAndEvidenceCount() {
        String report = """
                {
                  "reportId":"report_1",
                  "subject":{"namespace":"library.LoanMapper","statementId":"findOverdueLoans"},
                  "summary":{"severity":"HIGH","headline":"跨分片扫描","impactScope":"10 个场景"},
                  "risks":[{"type":"CROSS_SHARD","title":"缺少主分片键"}],
                  "dataDistribution":[{
                    "schema":"library","table":"loan","column":"status",
                    "nullRatio":0.0,"approxDistinct":2,"sensitivityPolicy":"PLAINTEXT"
                  }],
                  "evidenceCatalog":[{"evidenceId":"ev_1"},{"evidenceId":"ev_2"}]
                }
                """;

        String rendered = ReportFormatter.format(report);

        assertTrue(rendered.contains("library.LoanMapper.findOverdueLoans"));
        assertTrue(rendered.contains("严重度：HIGH"));
        assertTrue(rendered.contains("[CROSS_SHARD] 缺少主分片键"));
        assertTrue(rendered.contains("library.loan.status"));
        assertTrue(rendered.contains("policy=PLAINTEXT"));
        assertTrue(rendered.contains("证据：2 条"));
    }
}
