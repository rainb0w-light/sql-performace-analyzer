package com.biz.sccba.sqlanalyzer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sql-analyzer.persistence.enabled=true",
        "sql-analyzer.persistence.jdbc-url=jdbc:h2:mem:local_h2_bootstrap;DB_CLOSE_DELAY=-1",
        "sql-analyzer.persistence.username=sa",
        "sql-analyzer.persistence.password=",
        "sql-analyzer.worker.enabled=false",
        "sql-analyzer.local-h2.datasource-bootstrap.jdbc-url=jdbc:h2:mem:local_target"
})
class LocalH2DatasourceBootstrapApiTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void firstDatasourceReadCreatesOneStableLocalProfile() throws Exception {
        HttpResponse<String> apply = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/client-tokens/apply")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"clientName\":\"H2 Bootstrap\",\"clientType\":\"IDEA\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apply.statusCode(), apply.body());
        String token = json.readTree(apply.body()).path("accessToken").asText();

        JsonNode first = profiles(token);
        JsonNode second = profiles(token);

        assertEquals(1, first.size());
        assertEquals(first, second);
        JsonNode profile = first.get(0);
        assertTrue(profile.path("id").asText().startsWith("dsp_local_h2_"));
        assertEquals("Local H2 Static Analysis", profile.path("name").asText());
        assertEquals("H2", profile.path("dialect").asText());
        assertTrue(profile.path("readOnly").asBoolean());
    }

    private JsonNode profiles(String token) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create(url("/api/v1/datasource-profiles")))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
