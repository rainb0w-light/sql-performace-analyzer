# IDEA Plugin UI/UX P1 runIde 验收记录

日期：待执行  
分支/提交：`codex/idea-plugin-ui-ux` / 待填写  
IDE：IntelliJ IDEA Community 2025.1.3（runIde sandbox）  
环境：macOS，当前用户图形会话

## 自动 UI 冒烟

- 命令：`../gradlew uiSmoke --no-daemon --console=plain`
- 结果：待执行
- 证据：待填写日志路径与屏幕检查结果

## 人工检查

| 检查项 | 结果 | 记录 |
|---|---|---|
| Tool Window 打开与四 Tab | 待执行 | — |
| 上下文栏、状态栏、Token 不明文 | 待执行 | — |
| Light/Darcula 与缩放 | 待执行 | — |
| 键盘 Tab/方向键/Enter/Esc | 待执行 | — |
| XML Gutter/右键/Intention | 待执行 | — |
| Java 注解唯一解析/安全降级 | 待执行 | — |
| 多动态条件控件与 BoundSql preview | BLOCKED-EXTERNAL | 基线后端尚未部署 P1 suggest/preview 契约；由 Fake Gateway 自动测试覆盖 |
| 数据源/`${}`/成本/UNSUPPORTED 守卫 | BLOCKED-EXTERNAL | 基线后端尚未返回 P1 结构化守卫；reducer/model 自动测试覆盖 |
| SSE 续传/取消/终态 | BLOCKED-EXTERNAL | 需要可运行 Worker；Fake Gateway 自动测试覆盖 |
| 报告卡片/深链/过期/导出 | 待执行 | 可用 fixture 或真实报告检查 |
| DML 固定只读横幅 | 待执行 | — |

## 外部集成阻塞

`origin/master@ea30131` 不提供以下真实端点：default parameter suggest、BoundSql preview、
transient rule impact preview、Run confirm、Run status、服务端报告筛选、Java 注解 Mapper index。
Plugin 对 404/Problem Details 显示“服务端 P1 能力尚未部署”，不使用本地硬编码生产结果。

