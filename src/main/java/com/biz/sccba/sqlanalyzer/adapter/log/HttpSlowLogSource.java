package com.biz.sccba.sqlanalyzer.adapter.log;

import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Generic HTTP adapter for a private slow-log platform endpoint. */
@Component
public class HttpSlowLogSource implements SlowLogSource {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public SlowLogBatch fetch(Query query) {
        String endpoint = query.options() == null ? null : query.options().get("endpoint");
        if (endpoint == null || !(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
            return new SlowLogBatch("HTTP", List.of(), "endpoint 必须是 HTTP(S) URL");
        }
        try {
            String separator = endpoint.contains("?") ? "&" : "?";
            StringBuilder target = new StringBuilder(endpoint).append(separator)
                    .append("limit=").append(Math.max(1, Math.min(query.limit(), 1000)));
            if (query.sqlFingerprint() != null && !query.sqlFingerprint().isBlank()) {
                target.append("&sqlFingerprint=").append(encode(query.sqlFingerprint()));
            }
            if (query.from() != null) target.append("&from=").append(encode(query.from().toString()));
            if (query.to() != null) target.append("&to=").append(encode(query.to().toString()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target.toString())).timeout(Duration.ofSeconds(30)).GET();
            String token = query.options().get("token");
            if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return new SlowLogBatch("HTTP", List.of(), "HTTP " + response.statusCode() + ": " + response.body());
            return new SlowLogBatch("HTTP", parseEntries(response.body()), response.body());
        } catch (Exception e) { return new SlowLogBatch("HTTP", List.of(), e.getMessage()); }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private List<Entry> parseEntries(String raw) {
        try {
            var node = mapper.readTree(raw);
            if (!node.isArray()) return List.of(new Entry(raw, 1, 0, 0, java.time.Instant.now(), Map.of()));
            return java.util.stream.StreamSupport.stream(node.spliterator(), false).map(item -> new Entry(
                    item.path("sql").asText(item.path("query").asText("")), item.path("count").asLong(1),
                    item.path("avgMs").asDouble(0), item.path("maxMs").asDouble(0), java.time.Instant.now(), Map.of())).toList();
        } catch (Exception ignored) { return List.of(new Entry(raw, 1, 0, 0, java.time.Instant.now(), Map.of())); }
    }
}
