-- Forward migration: converge product database objects to version-less names under the
-- sql_analyzer schema (development-guide §11.1/§11.2). Historical files (V2..V5) are immutable;
-- this file renames the objects they created. Rollback procedure: docs/migrations/V6-rollback.md.

CREATE SCHEMA IF NOT EXISTS sql_analyzer;

-- Move tables out of public into sql_analyzer (owned sequences and indexes move with them),
-- then drop the product version prefix from each table name.
ALTER TABLE public.v2_client SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_client RENAME TO client;

ALTER TABLE public.v2_client_token SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_client_token RENAME TO client_token;

ALTER TABLE public.v2_analysis_session SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_analysis_session RENAME TO analysis_session;

ALTER TABLE public.v2_conversation_message SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_conversation_message RENAME TO conversation_message;

ALTER TABLE public.v2_agent_run SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_agent_run RENAME TO agent_run;

ALTER TABLE public.v2_agent_job SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_agent_job RENAME TO agent_job;

ALTER TABLE public.v2_run_event SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_run_event RENAME TO run_event;

ALTER TABLE public.v2_artifact SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_artifact RENAME TO artifact;

ALTER TABLE public.v2_artifact_content SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_artifact_content RENAME TO artifact_content;

ALTER TABLE public.v2_document SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_document RENAME TO document;

ALTER TABLE public.v2_document_chunk SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_document_chunk RENAME TO document_chunk;

ALTER TABLE public.v2_recommendation SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_recommendation RENAME TO recommendation;

ALTER TABLE public.v2_recommendation_feedback SET SCHEMA sql_analyzer;
ALTER TABLE sql_analyzer.v2_recommendation_feedback RENAME TO recommendation_feedback;

-- Indexes: drop the product version prefix (constraints keep their existing names).
ALTER INDEX sql_analyzer.idx_v2_client_token_active RENAME TO idx_client_token_active;
ALTER INDEX sql_analyzer.idx_v2_session_client_updated RENAME TO idx_session_client_updated;
ALTER INDEX sql_analyzer.idx_v2_message_session_created RENAME TO idx_message_session_created;
ALTER INDEX sql_analyzer.idx_v2_job_poll RENAME TO idx_job_poll;
ALTER INDEX sql_analyzer.idx_v2_run_event_run_id RENAME TO idx_run_event_run_id;
ALTER INDEX sql_analyzer.idx_v2_artifact_client_created RENAME TO idx_artifact_client_created;

-- BIGSERIAL-owned sequence (moved with the table): rename for consistency.
ALTER SEQUENCE IF EXISTS sql_analyzer.v2_run_event_id_seq RENAME TO run_event_id_seq;
