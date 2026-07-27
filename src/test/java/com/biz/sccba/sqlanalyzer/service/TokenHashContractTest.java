package com.biz.sccba.sqlanalyzer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 0 contract baseline.
 *
 * The SHA-256 lowercase-hex token hash is a persisted-secret contract: already-issued client
 * tokens (stored as hashes in the database) MUST keep working across the Phase 1 class rename
 * and forever after. This test pins the exact output.
 */
class TokenHashContractTest {

    @Test
    void tokenHashIsStableSha256LowercaseHex() {
        // sha256("spa_contract_baseline") — pinned constant, never change.
        assertEquals("27d0031a95da05c419ad330d78ab981b4c568a622c9b7c012bcf40ed368a020c",
                TokenService.hash("spa_contract_baseline"));
    }

    @Test
    void tokenHashIsDeterministic() {
        assertEquals(TokenService.hash("repeatable"), TokenService.hash("repeatable"));
    }
}
