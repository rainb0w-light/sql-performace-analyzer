-- Portable forward migration (docs/cloud-code-next-goal.md §3.4, docs/contracts/rest-api.md §4):
-- server-side Idempotency-Key store. All creating endpoints accept an Idempotency-Key header;
-- the server keeps (clientId, idempotencyKey) -> response for 24h, replays the stored response
-- for identical requests and answers 409 when the same key is reused with a different request
-- digest. Scoped to the authenticated client: the key space never crosses tenants.
-- Rollback: drop table sql_analyzer.idempotency_record.

CREATE TABLE sql_analyzer.idempotency_record (
    client_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    method VARCHAR(10) NOT NULL DEFAULT 'POST',
    path VARCHAR(400) NOT NULL DEFAULT '',
    response_status INTEGER NOT NULL DEFAULT 200,
    response_body TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (client_id, idempotency_key),
    CONSTRAINT fk_idempotency_client FOREIGN KEY (client_id) REFERENCES sql_analyzer.client(id)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON sql_analyzer.idempotency_record(expires_at);
