package com.biz.sccba.sqlanalyzer.knowledge.retrieval;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleEmbedderTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void externalFailureIsVisibleWithoutIncludingSensitiveResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] body = "third-party-secret-response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var embedder = embedder(Duration.ofSeconds(1));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> embedder.embed("query"));
        assertTrue(failure.getMessage().contains("503"));
        assertTrue(!failure.getMessage().contains("third-party-secret-response"));
    }

    @Test
    void externalTimeoutFailsWithinConfiguredBoundary() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            try {
                Thread.sleep(200);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        var embedder = embedder(Duration.ofMillis(30));

        assertThrows(IllegalStateException.class, () -> embedder.embed("query"));
    }

    private OpenAiCompatibleEmbedder embedder(Duration requestTimeout) {
        return new OpenAiCompatibleEmbedder("not-logged", "http://localhost:" + server.getAddress().getPort(),
                "test", 3, Duration.ofSeconds(1), requestTimeout);
    }
}
