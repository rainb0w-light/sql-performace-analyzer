package com.biz.sccba.sqlanalyzer.persistence;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture guards (docs/cloud-code-next-goal.md §3.7), run on every build:
 * <ul>
 *   <li>business layers never depend on vendor adapters ({@code adapter.postgresql},
 *       {@code adapter.h2}) or on persistence internals — only on the vendor-neutral
 *       {@code repository} ports;</li>
 *   <li>no DDL (CREATE/ALTER/DROP/TRUNCATE TABLE) in Java production sources — schema changes
 *       live exclusively in Flyway migrations;</li>
 *   <li>domain repository ports carry vendor-neutral names;</li>
 *   <li>tests never silently skip the database gates via assumeTrue/assumingThat — the
 *       PostgreSQL gate is the sanctioned {@code @EnabledIfEnvironmentVariable} switch,
 *       enforced in CI.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.biz.sccba.sqlanalyzer")
class PersistenceArchitectureGuardTest {

    private static final String[] BUSINESS_LAYERS = {
            "..service..", "..controller..", "..domain..", "..agui..", "..api..",
            "..knowledge..", "..metadata..", "..profiling..", "..scenario..", "..mybatis..",
            "..agent.."
    };

    @ArchTest
    static final ArchRule business_layers_never_touch_vendor_adapters =
            noClasses().that().resideInAnyPackage(BUSINESS_LAYERS)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..adapter.postgresql..", "..adapter.h2..")
                    .because("management-database vendors are isolated behind dialect adapters "
                            + "and vendor-neutral ports (docs/cloud-code-next-goal.md §3.3/§3.7)");

    @ArchTest
    static final ArchRule business_layers_only_see_repository_ports =
            noClasses().that().resideInAnyPackage(BUSINESS_LAYERS)
                    .should().dependOnClassesThat().resideInAnyPackage("..persistence..")
                    .because("services talk to the persistence layer exclusively through the "
                            + "vendor-neutral repository ports (docs/cloud-code-next-goal.md §3.3)");

    @Test
    void noDdlInJavaProductionSources() throws IOException {
        Pattern ddl = Pattern.compile("(?i)\\b(create|alter|drop|truncate)\\s+table\\b");
        List<String> hits = new ArrayList<>();
        scan(Paths.get("src/main/java"), ddl, hits);
        assertTrue(hits.isEmpty(),
                "DDL is only allowed in Flyway migrations, never in Java production sources "
                        + "(docs/cloud-code-next-goal.md §3.7):\n  " + String.join("\n  ", hits));
    }

    @Test
    void repositoryPortsHaveVendorNeutralNames() throws IOException {
        Pattern vendor = Pattern.compile("(?i)(postgres|pgvector|oracle|mysql|goldendb|mariadb|h2)");
        List<String> hits = new ArrayList<>();
        Path ports = Paths.get("src/main/java/com/biz/sccba/sqlanalyzer/repository");
        if (Files.isDirectory(ports)) {
            try (Stream<Path> walk = Files.walk(ports)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".java") && vendor.matcher(name).find()) {
                        hits.add(name);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "repository port interfaces must be vendor-neutral:\n  " + String.join("\n  ", hits));
    }

    @Test
    void testsNeverSilentlySkipDatabaseGates() throws IOException {
        // Assembled so this guard's own source never self-matches.
        Pattern silentSkip = Pattern.compile("\\b(assume" + "True|assume" + "False|assuming"
                + "That|org\\.junit\\.jupiter\\.api\\.Assump" + "tions)\\b");
        List<String> hits = new ArrayList<>();
        scan(Paths.get("src/test/java"), silentSkip, hits);
        assertTrue(hits.isEmpty(),
                "PostgreSQL Testcontainers gates must not be silently skipped via assumptions; "
                        + "use @EnabledIfEnvironmentVariable(RUN_POSTGRES_INTEGRATION_TESTS) which "
                        + "CI enforces (docs/cloud-code-next-goal.md §完成定义):\n  "
                        + String.join("\n  ", hits));
    }

    private static void scan(Path root, Pattern pattern, List<String> hits) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (!p.getFileName().toString().endsWith(".java")) continue;
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
                    if (pattern.matcher(lines.get(i)).find()) {
                        hits.add(p + ":" + (i + 1));
                    }
                }
            }
        }
    }
}
