# V6__rename_to_sql_analyzer.sql 回滚说明

> 适用：`src/main/resources/db/migration/V6__rename_to_sql_analyzer.sql`
> 回滚性质：手工 forward-fix（Flyway 历史不可回退；如环境支持可配合 `flyway undo`，否则按下列脚本前滚恢复）。

## 前置条件

- 应用必须回退到仍引用 `sql_analyzer.*` 的版本之后，再改回引用 `public.v2_*` 的旧版本；
  建议顺序：先部署识别新对象名的应用版本验证无误，再在需要时执行本回滚。
- 回滚期间停止 Agent Worker，避免写入中途失败。

## 回滚脚本（逆序执行）

```sql
ALTER SEQUENCE IF EXISTS sql_analyzer.run_event_id_seq RENAME TO v2_run_event_id_seq;

ALTER INDEX sql_analyzer.idx_artifact_client_created RENAME TO idx_v2_artifact_client_created;
ALTER INDEX sql_analyzer.idx_run_event_run_id RENAME TO idx_v2_run_event_run_id;
ALTER INDEX sql_analyzer.idx_job_poll RENAME TO idx_v2_job_poll;
ALTER INDEX sql_analyzer.idx_message_session_created RENAME TO idx_v2_message_session_created;
ALTER INDEX sql_analyzer.idx_session_client_updated RENAME TO idx_v2_session_client_updated;
ALTER INDEX sql_analyzer.idx_client_token_active RENAME TO idx_v2_client_token_active;

ALTER TABLE sql_analyzer.recommendation_feedback RENAME TO v2_recommendation_feedback;
ALTER TABLE sql_analyzer.v2_recommendation_feedback SET SCHEMA public;
ALTER TABLE sql_analyzer.recommendation RENAME TO v2_recommendation;
ALTER TABLE sql_analyzer.v2_recommendation SET SCHEMA public;
ALTER TABLE sql_analyzer.document_chunk RENAME TO v2_document_chunk;
ALTER TABLE sql_analyzer.v2_document_chunk SET SCHEMA public;
ALTER TABLE sql_analyzer.document RENAME TO v2_document;
ALTER TABLE sql_analyzer.v2_document SET SCHEMA public;
ALTER TABLE sql_analyzer.artifact_content RENAME TO v2_artifact_content;
ALTER TABLE sql_analyzer.v2_artifact_content SET SCHEMA public;
ALTER TABLE sql_analyzer.artifact RENAME TO v2_artifact;
ALTER TABLE sql_analyzer.v2_artifact SET SCHEMA public;
ALTER TABLE sql_analyzer.run_event RENAME TO v2_run_event;
ALTER TABLE sql_analyzer.v2_run_event SET SCHEMA public;
ALTER TABLE sql_analyzer.agent_job RENAME TO v2_agent_job;
ALTER TABLE sql_analyzer.v2_agent_job SET SCHEMA public;
ALTER TABLE sql_analyzer.agent_run RENAME TO v2_agent_run;
ALTER TABLE sql_analyzer.v2_agent_run SET SCHEMA public;
ALTER TABLE sql_analyzer.conversation_message RENAME TO v2_conversation_message;
ALTER TABLE sql_analyzer.v2_conversation_message SET SCHEMA public;
ALTER TABLE sql_analyzer.analysis_session RENAME TO v2_analysis_session;
ALTER TABLE sql_analyzer.v2_analysis_session SET SCHEMA public;
ALTER TABLE sql_analyzer.client_token RENAME TO v2_client_token;
ALTER TABLE sql_analyzer.v2_client_token SET SCHEMA public;
ALTER TABLE sql_analyzer.client RENAME TO v2_client;
ALTER TABLE sql_analyzer.v2_client SET SCHEMA public;

-- 可选：确认无对象残留后删除空 schema
DROP SCHEMA IF EXISTS sql_analyzer;
```

## 验证

```sql
SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 'v2\_%' ORDER BY 1;  -- 期望 13 张
SELECT tablename FROM pg_tables WHERE schemaname = 'sql_analyzer';                                   -- 期望 0 张
```

注意：`flyway_schema_history` 中 V6 记录保留；如使用 `flyway repair` 不要手工删除历史行。
