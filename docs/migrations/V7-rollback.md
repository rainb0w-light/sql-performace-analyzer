# V7__knowledge_profile_metadata.sql 回滚说明

> 适用：`src/main/resources/db/migration/V7__knowledge_profile_metadata.sql`
> 回滚性质：手工 forward-fix（Flyway 历史不可回退）。仅在 Phase 3 功能下线时执行。

## 前置条件

- 已停写：禁用 knowledge/profiling 相关 API 与 ProfilingWorker（`SQL_ANALYZER_WORKER_ENABLED=false` 或部署回退）。
- 确认无下游引用这些表（报告、快照查询）。

## 回滚脚本（逆依赖顺序）

```sql
DROP TABLE IF EXISTS sql_analyzer.profile_column_stat;
DROP TABLE IF EXISTS sql_analyzer.profile_snapshot;
DROP TABLE IF EXISTS sql_analyzer.profiling_job;
DROP TABLE IF EXISTS sql_analyzer.datasource_profile;
DROP TABLE IF EXISTS sql_analyzer.metadata_conflict;
DROP TABLE IF EXISTS sql_analyzer.shard_def;
DROP TABLE IF EXISTS sql_analyzer.index_def;
DROP TABLE IF EXISTS sql_analyzer.kb_alias;
DROP TABLE IF EXISTS sql_analyzer.kb_enum_value;
DROP TABLE IF EXISTS sql_analyzer.kb_rule;
DROP TABLE IF EXISTS sql_analyzer.kb_column_def;
DROP TABLE IF EXISTS sql_analyzer.kb_table_def;
DROP TABLE IF EXISTS sql_analyzer.knowledge_version;
DROP TABLE IF EXISTS sql_analyzer.knowledge_source;
```

PgVector 检索层表（由 AgentScope PgVectorStore 自建，默认 `sql_analyzer.kb_embedding`）如需一并删除：

```sql
DROP TABLE IF EXISTS sql_analyzer.kb_embedding;
```

注意：`flyway_schema_history` 中 V7 记录保留，不要手工删除历史行。
