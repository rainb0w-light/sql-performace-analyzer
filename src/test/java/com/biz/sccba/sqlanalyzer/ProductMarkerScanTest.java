package com.biz.sccba.sqlanalyzer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final product-marker scan (definition of done): production sources, configs, env vars, Beans,
 * business tables, plugin text, scripts AND documentation must not contain the product version
 * marker. The marker literal is assembled reflectively so this test file itself stays clean.
 *
 * Explicit allowlist — nothing else:
 *  - the four pinned historical Flyway files (immutable versions and their historical SQL);
 *  - the forward RENAME migration, the sanctioned mechanism that moves legacy-named objects into
 *    the sql_analyzer schema (it must reference the old names exactly once, then never again);
 *  - the forward-rename rollback runbook, which must spell the legacy object names to revert to;
 *  - lines citing external AgentScope versioned-doc URLs.
 */
class ProductMarkerScanTest {

    /** The product-version marker, assembled so this source never contains it literally. */
    private static final String MARKER = new String(new char[] { 'v', '2' });

    private static final Pattern MARKER_PATTERN =
            Pattern.compile(Pattern.quote(MARKER), Pattern.CASE_INSENSITIVE);

    /** External AgentScope doc URL segment permitted on a hit line. */
    private static final String AGENTSCOPE_DOC_PATH = "java.agentscope.io/" + MARKER + "/";
    private static final String AGENTSCOPE_GITHUB_DOC_PATH =
            "github.com/agentscope-ai/agentscope-java/blob/" + MARKER;
    private static final String MERMAID_STATE_DIAGRAM = "stateDiagram-" + MARKER;

    private static final Set<String> ALLOWED_FILES = Set.of(
            "src/main/resources/db/migration/" + "V" + "2" + "__core_agent.sql",
            "src/main/resources/db/migration/V3__recommendation_evidence_fields.sql",
            "src/main/resources/db/migration/V4__job_retry_error.sql",
            "src/main/resources/db/migration/V5__feedback_decision_constraint.sql",
            "src/main/resources/db/migration/V6__rename_to_sql_analyzer.sql",
            "docs/migrations/V6-rollback.md"
    );

    @Test
    void noProductMarkerInProductionOrDocs() throws IOException {
        List<String> hits = new ArrayList<>();
        scanTree(Paths.get("src/main"), hits);
        scanTree(Paths.get("src/test"), hits);
        scanTree(Paths.get("docs"), hits);
        scanTree(Paths.get("idea-plugin/src"), hits);
        for (String f : List.of(
                "build.gradle", "settings.gradle", "Dockerfile",
                "docker-compose.yml", "docker-compose.targets.yml",
                "README.md", ".github/workflows/verify.yml",
                "scripts/preflight.sh", "scripts/acceptance.sh",
                "idea-plugin/build.gradle", "idea-plugin/settings.gradle",
                "idea-plugin/README.md", "idea-plugin/docs/delivery-test-checklist.md",
                "idea-plugin/scripts/run-ide-ui-smoke.sh")) {
            scanFile(Paths.get(f), hits);
        }

        assertTrue(hits.isEmpty(),
                "Product marker '" + MARKER + "' found:\n  " + String.join("\n  ", hits)
                        + "\nAllowed only: historical Flyway files, the rename-rollback runbook, "
                        + "and AgentScope versioned-doc URLs.");
    }

    @Test
    void markerConstantIsWhatWeThink() {
        if (!MARKER.equals(MARKER.toLowerCase(Locale.ROOT))) {
            throw new AssertionError("marker constant corrupted");
        }
    }

    private static void scanTree(Path root, List<String> hits) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path p : files) {
                scanFile(p, hits);
            }
        }
    }

    private static void scanFile(Path file, List<String> hits) throws IOException {
        if (!Files.isRegularFile(file)) return;
        String normalized = file.toString().replace('\\', '/');
        if (ALLOWED_FILES.contains(normalized)) return;
        String name = file.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".class") || name.endsWith(".png")
                || name.endsWith(".jpg") || name.endsWith(".zip")) return;
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean externalVersionedDoc = line.contains(AGENTSCOPE_DOC_PATH)
                    || line.contains(AGENTSCOPE_GITHUB_DOC_PATH);
            boolean mermaidSyntax = line.trim().equals(MERMAID_STATE_DIAGRAM);
            if (MARKER_PATTERN.matcher(line).find() && !externalVersionedDoc && !mermaidSyntax) {
                hits.add(normalized + ":" + (i + 1));
            }
        }
    }
}
