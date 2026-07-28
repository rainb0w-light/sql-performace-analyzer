package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI-compatible embedding client for production wiring (POST {base}/embeddings). Tests never
 * use this class — they wire {@code DeterministicFakeEmbedder} so results cannot drift.
 */
public final class OpenAiCompatibleEmbedder implements Embedder {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleEmbedder(String apiKey, String baseUrl, String model, int dimensions) {
        this(apiKey, baseUrl, model, dimensions, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    OpenAiCompatibleEmbedder(String apiKey, String baseUrl, String model, int dimensions,
                              Duration connectTimeout, Duration requestTimeout) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public double[] embed(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model, "input", text == null ? "" : text, "dimensions", dimensions));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .timeout(requestTimeout)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("embedding 接口返回 " + response.statusCode());
            }
            JsonNode vector = mapper.readTree(response.body()).path("data").path(0).path("embedding");
            double[] out = new double[vector.size()];
            for (int i = 0; i < vector.size(); i++) out[i] = vector.get(i).asDouble();
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("embedding 调用失败", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return model;
    }
}
