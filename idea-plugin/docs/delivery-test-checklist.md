# IDEA 插件交付测试清单

## 自动化验收

在 `idea-plugin` 目录执行：

```bash
../gradlew clean check buildPlugin --no-daemon --console=plain
```

验收点：

- `contractTest` / `BackendClientTest` 覆盖 Token 申请、Bearer 鉴权、请求体转义、Run 事件和非 2xx 错误。
- `PluginDescriptorTest` 校验插件 ID、Tool Window 和持久化设置服务声明。
- `buildPlugin` 成功并生成 `build/distributions/*.zip`。

## 手工验收

1. 启动后端 PostgreSQL，并开启 persistence 和 Worker（SQL_ANALYZER_PERSISTENCE_ENABLED=true、SQL_ANALYZER_WORKER_ENABLED=true）。
2. 执行 `../gradlew runIde`。
3. 打开 `View -> Tool Windows -> SQL Analyzer`。
4. 点击“申请 Token”，确认状态变为“Token 已保存”。
5. 在编辑器中选中 SQL 或 MyBatis XML，点击“分析编辑器选中内容”。
6. 确认出现 Session、Run，并能看到 `RUN_QUEUED`/后续 Agent 事件。
7. 重启 IDE，确认 Token 仍存在。
8. 修改一个 MyBatis Mapper XML，确认 VFS watcher 自动上传 Mapper Artifact。
9. 重启 IDE 后点击“加载会话历史”，确认用户消息和 Agent 回答仍可恢复。

## 自动化 runIde UI 验收

在已登录 macOS 桌面会话、并给 Terminal/IDE 开启 Accessibility 权限的机器上执行：

```bash
../gradlew uiSmoke --no-daemon --console=plain
```

该任务会启动隔离的 `runIde`，使用 AppleScript 自动打开 `View -> Tool Windows -> SQL Analyzer`，检查 Tool Window、后端地址、Token 按钮和分析按钮是否实际渲染，然后退出 IDE。

当前无图形会话时会明确失败并提示：`No accessible macOS GUI session is available`；这是环境前置条件，不是插件功能断言失败。

## 当前已知范围

- Recommendation 查询和接受/拒绝反馈已接入；结果展示已转换为结构化摘要，完整卡片式证据展示属于下一阶段 UI 工作。
- Mapper watcher 已实现自动上传；服务端解析结果进入 Artifact/Document/Chunk 管线。
- 后端运行时使用 AgentScope HarnessAgent，不保留 legacy orchestrator 兼容适配器。
