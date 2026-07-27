# SQL Performance Analyzer IDEA Plugin

最小可运行客户端，当前功能：

1. 配置后端地址（默认 `http://localhost:18881`）。
2. 申请并持久化 IDEA 客户端 Token。
3. 读取编辑器选中的 SQL/MyBatis 文本。
4. 创建云端 Session，提交 Agent Run。
5. 轮询 SSE 事件并展示原始分析结果。

## 本地运行

```bash
cd idea-plugin
../gradlew runIde
```

插件构建：

```bash
../gradlew buildPlugin
```

后端需要先开启持久化和 Worker（`SQL_ANALYZER_PERSISTENCE_ENABLED=true`、`SQL_ANALYZER_WORKER_ENABLED=true`），详见 [`../docs/core-agent.md`](../docs/core-agent.md)。
