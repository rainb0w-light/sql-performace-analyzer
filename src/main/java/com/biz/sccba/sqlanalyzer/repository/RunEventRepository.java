package com.biz.sccba.sqlanalyzer.repository;

import java.util.List;

/**
 * Persisted AG-UI events (persistence precedes delivery; event id is the resume cursor).
 * The id sequence is an auto-generated monotonic BIGINT on both management databases.
 */
public interface RunEventRepository {
    long append(String runId, String type, String payloadJson);

    /** Replays events of a run after the cursor, verifying run ownership by the client. */
    List<RunEvent> after(String clientId, String runId, long lastEventId);

    record RunEvent(long id, String runId, String type, String payloadJson) {}
}
