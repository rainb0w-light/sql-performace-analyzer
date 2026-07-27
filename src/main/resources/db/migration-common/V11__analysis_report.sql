-- Portable forward migration (docs/cloud-code-next-goal.md §完成定义/§5.6): the standard
-- analysis report, persisted after schema validation, with its Markdown projection.
-- Rollback: drop table sql_analyzer.analysis_report.

CREATE TABLE IF NOT EXISTS sql_analyzer.analysis_report (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.client(id),
    run_id VARCHAR(64),
    session_id VARCHAR(64),
    namespace VARCHAR(500),
    statement_id VARCHAR(300),
    schema_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    severity VARCHAR(20),
    report_json TEXT NOT NULL,
    markdown TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_report_client ON sql_analyzer.analysis_report(client_id, created_at);
CREATE INDEX IF NOT EXISTS idx_analysis_report_run ON sql_analyzer.analysis_report(run_id);
