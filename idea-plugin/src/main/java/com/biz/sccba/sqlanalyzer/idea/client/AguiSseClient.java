package com.biz.sccba.sqlanalyzer.idea.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous AG-UI SSE consumer (docs/contracts/ag-ui-mapping.md §5):
 *
 * <ul>
 *   <li>one live connection per run — no fixed-count polling of the full event body;</li>
 *   <li>records the last event id and reconnects with {@code Last-Event-ID} so no event is lost;</li>
 *   <li>exponential backoff with full jitter on transport failures;</li>
 *   <li>client-generated {@code runId} + {@code Idempotency-Key} make run creation replay-safe
 *       when the connection dies before the first event;</li>
 *   <li>terminal detection is by the standard {@code RUN_FINISHED} event type, never by
 *       substring matching on raw text.</li>
 * </ul>
 */
public final class AguiSseClient {

    /** Receives parsed events on the calling (background) thread. */
    public interface Listener {
        /** @return true when the terminal event has been handled and streaming should stop. */
        boolean onEvent(String id, String type, String json);

        default void onReconnect(int attempt, String lastEventId, String reason) {}
    }

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final AtomicBoolean aborted = new AtomicBoolean(false);
    private volatile InputStream currentStream;

    public AguiSseClient(String baseUrl, String token) {
        this(baseUrl, token, 500L, 30_000L);
    }

    /** Test-friendly constructor with tunable backoff. */
    public AguiSseClient(String baseUrl, String token, long initialBackoffMs, long maxBackoffMs) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
    }

    /** Stops streaming and closes any live connection. */
    public void abort() {
        aborted.set(true);
        InputStream stream = currentStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // closing is best-effort
            }
        }
    }

    public boolean isAborted() {
        return aborted.get();
    }

    /**
     * Follows a run already created by the statement-analysis command. Reconnects with
     * Last-Event-ID and never replays the command POST.
     */
    public String streamExisting(String streamUrl, String runId, Listener listener) throws Exception {
        String path = streamPath(streamUrl);
        String lastEventId = null;
        long lastProcessed = -1L;
        int attempt = 0;
        String lastFailure = "";

        while (!aborted.get()) {
            boolean deliveredAny = false;
            try {
                if (attempt > 0) listener.onReconnect(attempt, lastEventId, lastFailure);
                HttpRequest.Builder builder = newRequest("GET", path, "req_" + UUID.randomUUID(), attempt).GET();
                if (lastEventId != null) builder.header("Last-Event-ID", lastEventId);
                HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    String body;
                    try (InputStream errorBody = response.body()) {
                        body = new String(errorBody.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    throw new IOException("后端返回 HTTP " + response.statusCode() + ": " + body);
                }

                try (InputStream stream = response.body()) {
                    currentStream = stream;
                    StreamResult result = consume(stream, lastProcessed, listener);
                    deliveredAny = result.deliveredAny();
                    lastProcessed = result.lastProcessed();
                    if (result.lastEventId() != null) lastEventId = result.lastEventId();
                    if (result.stopped()) return runId;
                }
                lastFailure = "stream ended before terminal event";
            } catch (IOException e) {
                if (aborted.get()) break;
                lastFailure = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                currentStream = null;
            }

            if (aborted.get()) break;
            attempt = deliveredAny ? 0 : attempt + 1;
            backoff(attempt);
        }
        return runId;
    }

    /**
     * Starts the AG-UI run (POST) and consumes the event stream until the listener stops at the
     * terminal event. On transport failure resumes via {@code GET .../stream} with the cursor
     * (or replays the idempotent POST while no event has been observed yet).
     *
     * @param runAgentInputJson full RunAgentInput JSON; must contain a client-generated runId
     * @return the runId observed on the stream (may be null when aborted before any event)
     */
    public String runAndStream(String runAgentInputJson, Listener listener) throws Exception {
        String idempotencyKey = "idea-run-" + UUID.randomUUID();
        String requestId = "req_" + UUID.randomUUID();
        String lastEventId = null;
        long lastProcessed = -1L;
        String runId = null;
        int attempt = 0;
        String lastFailure = "";

        while (!aborted.get()) {
            boolean deliveredAny = false;
            try {
                HttpRequest request;
                if (runId == null) {
                    // Not a single event observed yet: replay the idempotent creation POST.
                    request = newRequest("POST", "/api/v1/agui/runs", requestId, attempt)
                            .header("Idempotency-Key", idempotencyKey)
                            .POST(HttpRequest.BodyPublishers.ofString(runAgentInputJson))
                            .build();
                } else {
                    if (attempt > 0) listener.onReconnect(attempt, lastEventId, lastFailure);
                    HttpRequest.Builder builder = newRequest("GET", "/api/v1/agui/runs/" + runId + "/stream", requestId, attempt)
                            .GET();
                    if (lastEventId != null) builder.header("Last-Event-ID", lastEventId);
                    request = builder.build();
                }

                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    String body;
                    try (InputStream errorBody = response.body()) {
                        body = new String(errorBody.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    throw new IOException("后端返回 HTTP " + response.statusCode() + ": " + body);
                }

                try (InputStream stream = response.body()) {
                    currentStream = stream;
                    SseFrame frame = new SseFrame();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    String line;
                    boolean stopped = false;
                    while (!aborted.get() && (line = reader.readLine()) != null) {
                        if (line.startsWith(":")) continue; // heartbeat comment
                        if (!line.isEmpty()) {
                            frame.accept(line);
                            continue;
                        }
                        if (!frame.hasEvent()) continue;
                        String id = frame.id;
                        String type = frame.type;
                        String data = frame.data();
                        frame.reset();

                        if (id != null) {
                            long numericId = parseId(id);
                            if (numericId >= 0 && numericId <= lastProcessed) continue; // already delivered
                            lastProcessed = Math.max(lastProcessed, numericId);
                            lastEventId = id;
                        }
                        deliveredAny = true;
                        if (runId == null) runId = extractRunId(data);
                        if (listener.onEvent(id, type, data)) {
                            stopped = true;
                            break;
                        }
                    }
                    if (stopped) return runId;
                }
                lastFailure = "stream ended before terminal event";
            } catch (IOException e) {
                if (aborted.get()) break;
                lastFailure = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                currentStream = null;
            }

            if (aborted.get()) break;
            attempt = deliveredAny ? 0 : attempt + 1;
            long ceiling = Math.min(maxBackoffMs, initialBackoffMs * (1L << Math.min(attempt, 20)));
            long sleepMs = ThreadLocalRandom.current().nextLong(Math.max(1, ceiling));
            long slept = 0;
            while (slept < sleepMs && !aborted.get()) {
                Thread.sleep(Math.min(50, sleepMs - slept));
                slept += 50;
            }
        }
        return runId;
    }

    private HttpRequest.Builder newRequest(String method, String path, String requestId, int attempt) {
        URI uri = path.startsWith("http://") || path.startsWith("https://")
                ? URI.create(path) : URI.create(baseUrl + (path.startsWith("/") ? path : "/" + path));
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(6))
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", requestId + "-" + attempt)
                .method(method, HttpRequest.BodyPublishers.noBody());
    }

    private String streamPath(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            throw new IllegalArgumentException("后端未返回 streamUrl");
        }
        return streamUrl;
    }

    private StreamResult consume(InputStream stream, long priorLastProcessed, Listener listener) throws IOException {
        long lastProcessed = priorLastProcessed;
        String lastEventId = null;
        boolean deliveredAny = false;
        SseFrame frame = new SseFrame();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while (!aborted.get() && (line = reader.readLine()) != null) {
            if (line.startsWith(":")) continue;
            if (!line.isEmpty()) {
                frame.accept(line);
                continue;
            }
            if (!frame.hasEvent()) continue;
            String id = frame.id;
            String type = frame.type;
            String data = frame.data();
            frame.reset();
            if (id != null) {
                long numericId = parseId(id);
                if (numericId >= 0 && numericId <= lastProcessed) continue;
                lastProcessed = Math.max(lastProcessed, numericId);
                lastEventId = id;
            }
            deliveredAny = true;
            if (listener.onEvent(id, type, data)) {
                return new StreamResult(true, lastProcessed, lastEventId, true);
            }
        }
        return new StreamResult(deliveredAny, lastProcessed, lastEventId, false);
    }

    private void backoff(int attempt) throws InterruptedException {
        long ceiling = Math.min(maxBackoffMs, initialBackoffMs * (1L << Math.min(attempt, 20)));
        long sleepMs = ThreadLocalRandom.current().nextLong(Math.max(1, ceiling));
        long slept = 0;
        while (slept < sleepMs && !aborted.get()) {
            long step = Math.min(50, sleepMs - slept);
            Thread.sleep(step);
            slept += step;
        }
    }

    private record StreamResult(boolean deliveredAny, long lastProcessed,
                                String lastEventId, boolean stopped) {}

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Cheap runId extraction without a JSON dependency in the hot path. */
    private static String extractRunId(String json) {
        if (json == null) return null;
        int key = json.indexOf("\"runId\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key);
        if (colon < 0) return null;
        int start = json.indexOf('"', colon);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        return end < 0 ? null : json.substring(start + 1, end);
    }

    /** Accumulates one SSE frame (id / event / data lines until the blank line). */
    private static final class SseFrame {
        String id;
        String type;
        private final StringBuilder data = new StringBuilder();
        private boolean hasData;

        void accept(String line) {
            if (line.startsWith("id:")) {
                id = line.substring(3).trim();
            } else if (line.startsWith("event:")) {
                type = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (hasData) data.append('\n');
                data.append(line.substring(5).trim());
                hasData = true;
            }
        }

        boolean hasEvent() {
            return type != null || hasData;
        }

        String data() {
            return data.toString();
        }

        void reset() {
            id = null;
            type = null;
            data.setLength(0);
            hasData = false;
        }
    }
}
