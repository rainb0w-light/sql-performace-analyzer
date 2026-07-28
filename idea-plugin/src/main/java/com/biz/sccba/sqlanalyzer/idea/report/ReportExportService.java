package com.biz.sccba.sqlanalyzer.idea.report;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/** P1 exports only standard JSON and Markdown. No PDF/HTML/share URL is synthesized. */
public final class ReportExportService {
    private ReportExportService() {}

    public enum Format { MARKDOWN, STANDARD_JSON }

    public static String export(String reportJson, Format format) {
        if (format == Format.STANDARD_JSON) {
            return new GsonBuilder().setPrettyPrinting().create()
                    .toJson(JsonParser.parseString(reportJson));
        }
        ReportViewModel report = ReportViewModel.parse(reportJson);
        StringBuilder out = new StringBuilder("# SQL Performance Analysis Report\n\n")
                .append("- Report: `").append(report.reportId()).append("`\n")
                .append("- Severity: **").append(report.severity()).append("**\n")
                .append("- Confidence: ").append(String.format(java.util.Locale.ROOT, "%.2f", report.confidence())).append("\n\n")
                .append("## Summary\n\n").append(report.headline()).append("\n\n")
                .append("## Risks\n\n");
        if (report.risks().isEmpty()) out.append("- None\n");
        for (ReportViewModel.Risk risk : report.risks()) {
            out.append("- **").append(risk.type()).append("**: ").append(risk.title());
            if (!risk.scenarioIds().isEmpty()) out.append(" (scenarios: ").append(String.join(", ", risk.scenarioIds())).append(")");
            out.append("\n");
        }
        out.append("\n## Recommendations\n\n");
        if (report.recommendations().isEmpty()) out.append("- None\n");
        for (ReportViewModel.Recommendation recommendation : report.recommendations()) {
            out.append("- **").append(recommendation.priority()).append("** ")
                    .append(recommendation.title()).append(": ").append(recommendation.problem()).append("\n");
        }
        out.append("\n## Limits\n\n");
        if (report.limits().isEmpty()) out.append("- None\n");
        report.limits().forEach(limit -> out.append("- ").append(limit).append("\n"));
        return out.toString();
    }
}
