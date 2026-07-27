package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.knowledge.retrieval.Embedder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Deterministic embedding for retrieval tests (docs/cloud-code-next-goal.md §3.3): token hash
 * bagging into a fixed-dimension vector, L2-normalized. Same text always yields the same vector;
 * texts sharing tokens have positive cosine similarity. No external model is ever contacted, so
 * results cannot drift.
 */
public final class DeterministicFakeEmbedder implements Embedder {

    private final int dimensions;

    public DeterministicFakeEmbedder(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^a-z0-9\\u4e00-\\u9fff]+")) {
            if (token.isBlank()) continue;
            int bucket = Math.floorMod(sha256Int(token), dimensions);
            vector[bucket] += 1.0;
        }
        double norm = 0;
        for (double v : vector) norm += v * v;
        if (norm > 0) {
            for (int i = 0; i < dimensions; i++) vector[i] /= Math.sqrt(norm);
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "deterministic-fake-embedding";
    }

    private static int sha256Int(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
        } catch (Exception e) {
            return token.hashCode();
        }
    }
}
