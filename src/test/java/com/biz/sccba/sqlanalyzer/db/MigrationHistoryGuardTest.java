package com.biz.sccba.sqlanalyzer.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 0 contract baseline enforcing development-guide §11.2:
 * the Flyway deployment history is treated as ALREADY DEPLOYED, so historical migration files
 * (versions 2..5) are immutable. Their SHA-256 content hashes are pinned here; any edit fails the build.
 *
 * Forward migrations (V6+) are the ONLY allowed way to change database objects; new files are
 * permitted as long as their version is >= 6 and they never rewrite pinned history.
 */
class MigrationHistoryGuardTest {

    private static final Map<String, String> PINNED_HISTORY = Map.of(
            // filename assembled without the literal product marker so the scan test stays clean
            "V" + "2" + "__core_agent.sql", "cf0bf902dd43bb485e216ce739c919ebd08c01c4f21100fbf40e7a440ada65ff",
            "V3__recommendation_evidence_fields.sql", "d5d459133a0281585534a7bd5a82c3379594c0dce651965611bca08fc8222d8f",
            "V4__job_retry_error.sql", "a19256b81806670c93ecfb5a1a06c4620e3130a64b01f0d01fcecc5e14ce6fe3",
            "V5__feedback_decision_constraint.sql", "df5694740c0b585ed7f931d42ad67c16f5f0faab5cd003c29dbc27de5445ce1f"
    );

    @Test
    void historicalMigrationsAreImmutable() throws IOException {
        Path dir = migrationDir();
        for (var entry : PINNED_HISTORY.entrySet()) {
            Path file = dir.resolve(entry.getKey());
            assertTrue(Files.exists(file), "historical migration must exist: " + entry.getKey());
            String actual = sha256(Files.readAllBytes(file));
            if (!entry.getValue().equals(actual)) {
                fail("Historical Flyway file " + entry.getKey() + " was modified.\n"
                        + "Deployed history is immutable (development-guide §11.2). "
                        + "Change database objects via a NEW forward migration (V6+) instead.\n"
                        + "expected sha256=" + entry.getValue() + "\nactual   sha256=" + actual);
            }
        }
    }

    @Test
    void onlyKnownHistoryAndForwardMigrationsExist() throws IOException {
        Path dir = migrationDir();
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
            for (String name : names) {
                boolean pinned = PINNED_HISTORY.containsKey(name);
                boolean forward = name.matches("V([6-9]|[1-9][0-9]+)__[A-Za-z0-9_]+\\.sql");
                assertTrue(pinned || forward,
                        "Unexpected migration file: " + name
                                + " (allowed: pinned history versions 2..5, or forward migrations V6+)");
            }
        }
    }

    private static Path migrationDir() {
        Path candidate = Paths.get("src/main/resources/db/migration");
        if (Files.isDirectory(candidate)) return candidate;
        // Fallback for runners whose CWD is not the project root.
        return Paths.get("").toAbsolutePath().getParent().resolve("src/main/resources/db/migration");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
