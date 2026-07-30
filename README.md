# SQL Performance Analyzer

基于 AgentScope Java 的私有部署慢 SQL 治理 Agent。服务端负责多客户端、多 Session、长上下文和历史恢复；IDEA 插件负责动态采集 MyBatis Mapper、提交分析并展示建议。

## 架构

```text
IDEA Plugin -> HTTPS JSON/SSE -> Session/Run/Job Worker
             -> AgentScope HarnessAgent
             -> PostgreSQL DAO + Artifact/Document/Chunk
             -> 只读 MySQL/GoldenDB 证据工具
             -> Recommendation + Feedback
```

PostgreSQL 是服务端唯一业务持久化数据库，业务层只依赖 DAO Port。Agent 第一阶段只生成建议，不执行 DDL、不修改代码或目标数据库。

## 本地启动

需要 Docker 时，直接启动 PostgreSQL 和 Agent：

```bash
export DEEPSEEK_API_KEY='your-key'
docker compose up -d postgres agent
curl http://localhost:18881/healthz
```

只启动应用进行无持久化开发：

```bash
./gradlew bootRun
curl http://localhost:18881/healthz
```

架构说明见 [docs/architecture.md](docs/architecture.md)，部署与配置见 [docs/operations.md](docs/operations.md)；
接口契约见 [docs/contracts/](docs/contracts/)（REST、AG-UI 映射、报告 Schema）。

标准 statement 分析可显式开启普通只读 EXPLAIN 和 AgentScope 报告增强：

```bash
export SQL_ANALYZER_EXPLAIN_ENABLED=true
export SQL_ANALYZER_AGENT_ENHANCEMENT_ENABLED=true   # 可选
```

EXPLAIN 只接受 SELECT/WITH 和 PreparedStatement 参数；DML、EXPLAIN ANALYZE、未受信任 `${}` 均不会发送到目标库。

## IDEA 插件

```bash
cd idea-plugin
../gradlew buildPlugin
```

安装生成的 `build/distributions/*.zip`。插件支持 Token 申请、Mapper 自动监听上传、SQL 分析、Session 历史、Recommendation 查询、接受/拒绝反馈和 Run 取消。

## 验证

统一执行本地交付门禁：

```bash
scripts/acceptance.sh --local
```

具备 Docker 和可访问 macOS GUI 时执行全部门禁：

```bash
scripts/acceptance.sh --all
```

```bash
./gradlew clean test
cd idea-plugin
../gradlew clean check buildPlugin verifyPluginStructure
```

具备 Docker 的环境执行真实 PostgreSQL 门禁：

```bash
RUN_POSTGRES_INTEGRATION_TESTS=true ./gradlew clean test
```

没有 Docker 但已有私有 PostgreSQL 时，可复用同一门禁：

```bash
RUN_POSTGRES_INTEGRATION_TESTS=true \
SQL_ANALYZER_TEST_POSTGRES_JDBC_URL='jdbc:postgresql://localhost:5432/sql_analyzer_test' \
SQL_ANALYZER_TEST_POSTGRES_USERNAME='postgres' \
SQL_ANALYZER_TEST_POSTGRES_PASSWORD='postgres' \
./gradlew clean test
```

运行验收前可先执行环境预检：

```bash
bash scripts/preflight.sh --all       # Docker + GUI
bash scripts/preflight.sh --postgres  # PostgreSQL 门禁
bash scripts/preflight.sh --ui        # runIde UI 门禁
```

CI 会自动执行该门禁并上传后端 JAR、IDEA 插件 ZIP；工作流位于 [.github/workflows/verify.yml](.github/workflows/verify.yml)。GUI UI 冒烟测试需登录 macOS 桌面并执行 `cd idea-plugin && ../gradlew uiSmoke`。

## 文档

- [架构](docs/architecture.md)
- [部署与运维](docs/operations.md)
- [接口契约](docs/contracts/)（REST / AG-UI / 报告 Schema）
- [迁移与回滚说明](docs/migrations/)
