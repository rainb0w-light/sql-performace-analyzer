package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

/**
 * Text embedding port. Production wires an OpenAI-compatible endpoint; tests wire a
 * deterministic fake so retrieval results never drift (docs/cloud-code-next-goal.md §3.3).
 */
public interface Embedder {

    double[] embed(String text);

    int dimensions();

    String modelName();
}
