package com.biz.sccba.sqlanalyzer.idea.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Contract for the continuous AG-UI SSE client: a single stream per run, cursor resume after an
 * interruption (Last-Event-ID), no lost or duplicated events, terminal detection by event type.
 */
public class AguiSseClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastEventIdOnResume = new AtomicReference<>();
    private final AtomicReference<String> acceptHeader = new AtomicReference<>();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> existingRunAuthorization = new AtomicReference<>();
    private final AtomicInteger unauthorizedCalls = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // First connection: deliver events 1-2, then drop the connection WITHOUT a terminal event.
        server.createContext("/api/v1/agui/runs", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(sse("1", "RUN_STARTED",
                        "{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"type\":\"RUN_STARTED\"}"));
                out.write(sse("2", "TEXT_MESSAGE_CONTENT",
                        "{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"delta\":\"hel\"}"));
                out.flush();
            } // abrupt end of stream: no RUN_FINISHED
        });

        // Resume connection: must carry Last-Event-ID=2; deliver 3-4 including the terminal event.
        server.createContext("/api/v1/agui/runs/run_client/stream", exchange -> {
            lastEventIdOnResume.set(exchange.getRequestHeaders().getFirst("Last-Event-ID"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(sse("3", "TEXT_MESSAGE_CONTENT",
                        "{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"delta\":\"lo\"}"));
                out.write(sse("4", "RUN_FINISHED",
                        "{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"type\":\"RUN_FINISHED\"}"));
                out.flush();
            }
        });
        server.createContext("/api/v1/agui/runs/run_existing/stream", exchange -> {
            existingRunAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(sse("9", "RUN_STARTED",
                        "{\"runId\":\"run_existing\",\"type\":\"RUN_STARTED\"}"));
                out.write(sse("10", "RUN_FINISHED",
                        "{\"runId\":\"run_existing\",\"type\":\"RUN_FINISHED\"}"));
                out.flush();
            }
        });
        server.createContext("/api/v1/agui/runs/run_unauthorized/stream", exchange -> {
            unauthorizedCalls.incrementAndGet();
            byte[] body = "{\"code\":\"UNAUTHORIZED\",\"retryable\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void resumesFromCursorAfterInterruptionWithoutLossOrDuplication() throws Exception {
        AguiSseClient client = new AguiSseClient(baseUrl(), "spa_token", 50L, 200L);
        List<String[]> received = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> runId = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            try {
                runId.set(client.runAndStream(
                        "{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"messages\":[]}",
                        (id, type, json) -> {
                            received.add(new String[] {id, type, json});
                            return "RUN_FINISHED".equals(type);
                        }));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });
        consumer.start();
        assertTrue("streaming must reach the terminal event", done.await(20, TimeUnit.SECONDS));

        assertEquals("run_client", runId.get());
        assertEquals(4, received.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(String.valueOf(i + 1), received.get(i)[0]);
        }
        assertEquals("RUN_STARTED", received.get(0)[1]);
        assertEquals("TEXT_MESSAGE_CONTENT", received.get(1)[1]);
        assertEquals("TEXT_MESSAGE_CONTENT", received.get(2)[1]);
        assertEquals("RUN_FINISHED", received.get(3)[1]);
        assertTrue(received.get(1)[2].contains("hel"));
        assertTrue(received.get(2)[2].contains("lo"));

        assertEquals("text/event-stream", acceptHeader.get());
        assertNotNull(idempotencyKey.get());
        assertEquals("2", lastEventIdOnResume.get());
    }

    @Test
    public void abortStopsStreaming() throws Exception {
        AguiSseClient client = new AguiSseClient(baseUrl(), "spa_token", 50L, 200L);
        List<String[]> received = new CopyOnWriteArrayList<>();
        Thread consumer = new Thread(() -> {
            try {
                client.runAndStream("{\"threadId\":\"session_client\",\"runId\":\"run_client\",\"messages\":[]}",
                        (id, type, json) -> {
                            received.add(new String[] {id, type, json});
                            return false; // never terminal on its own
                        });
            } catch (Exception ignored) {
                // abort closes the stream
            }
        });
        consumer.start();
        long deadline = System.currentTimeMillis() + 5_000L;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20);
        client.abort();
        consumer.join(10_000L);
        assertFalse(consumer.isAlive());
        assertTrue(client.isAborted());
    }

    @Test
    public void followsServerCreatedRunWithoutPostingAnotherRun() throws Exception {
        AguiSseClient client = new AguiSseClient(baseUrl(), "spa_token", 10L, 20L);
        List<String> eventTypes = new CopyOnWriteArrayList<>();

        String runId = client.streamExisting(
                "/api/v1/agui/runs/run_existing/stream", "run_existing",
                (id, type, json) -> {
                    eventTypes.add(type);
                    return "RUN_FINISHED".equals(type);
                });

        assertEquals("run_existing", runId);
        assertEquals(List.of("RUN_STARTED", "RUN_FINISHED"), eventTypes);
        assertEquals("Bearer spa_token", existingRunAuthorization.get());
        assertNull("following a created run must not create/replay a run POST", idempotencyKey.get());
    }

    @Test
    public void unauthorizedStreamDoesNotBlindlyRetry() throws Exception {
        AguiSseClient client = new AguiSseClient(baseUrl(), "bad", 1L, 2L, 5);
        try {
            client.streamExisting("/api/v1/agui/runs/run_unauthorized/stream", "run_unauthorized",
                    (id, type, json) -> false);
            fail("expected unauthorized SSE failure");
        } catch (AguiSseClient.SseException expected) {
            assertEquals(401, expected.status());
            assertEquals("UNAUTHORIZED", expected.code());
            assertFalse(expected.retryable());
        }
        assertEquals(1, unauthorizedCalls.get());
    }

    private static byte[] sse(String id, String type, String data) {
        return ("id:" + id + "\nevent:" + type + "\ndata:" + data + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
