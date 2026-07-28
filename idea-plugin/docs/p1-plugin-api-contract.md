# IDEA Plugin P1 API 契约冻结

状态：Plugin consumer contract，服务端 P1 契约已实现并由真实后端 consumer contract 验证。
基路径：`/api/v1`。所有上行请求携带 `X-Request-Id`；创建/变更请求携带可重放的 `Idempotency-Key`。

## 1. 默认参数建议

Java 注解 Mapper 在 PSI 能唯一解析时，Plugin 使用
`POST /artifacts/mybatis/annotation-index` 上传 `sessionId/javaContent/namespace/methodName`。
该端点只接受单个可静态求值的 `@Select/@Insert/@Update/@Delete` 方法；无法静态求值返回
`UNSUPPORTED`，Plugin 不在本地展开动态 Java 表达式。

`POST /mapper-statements/default-parameters/suggest`

请求：

```json
{
  "artifactId": "artifact_1",
  "statementId": "findOverdueLoans",
  "datasourceProfileId": "dsp_1",
  "projectId": "project_1",
  "moduleId": "library-dao",
  "contentHash": "sha256"
}
```

响应：

```json
{
  "suggestionSetId": "suggest_1",
  "contextVersion": "knowledge@3|profile@snap_1",
  "nodes": [{
    "nodeId": "findOverdueLoans#if[0]",
    "kind": "IF",
    "testExpression": "status != null",
    "parameterPath": "status",
    "parameterType": "java.lang.String",
    "parentNodeId": null,
    "chooseGroupId": null,
    "category": "FILTER",
    "categorySource": "SERVER_EXPLAINED",
    "assignable": true,
    "suggestedEnabled": true,
    "suggestedValue": {"type": "STRING", "value": "ACTIVE"},
    "source": "PROFILE_SNAPSHOT",
    "version": "snap_1",
    "locator": "loan.status/top-k/0",
    "confidence": 0.94,
    "reason": "最新画像 Top-K"
  }]
}
```

`kind ∈ IF | CHOOSE_WHEN | CHOOSE_OTHERWISE | FOREACH | STRUCTURE`。
`category ∈ ROUTING | FILTER | SORT_PAGE | JOIN | OTHER`。未知/缺失分类由客户端按结构确定性回退为 `OTHER`，不猜业务语义。

## 2. BoundSql 预览

`POST /mapper-statements/default-parameters/preview`

请求：

```json
{
  "suggestionSetId": "suggest_1",
  "selections": [{"nodeId": "findOverdueLoans#if[0]", "selected": true, "collectionMode": null}],
  "parameters": {"status": {"type": "STRING", "value": "ACTIVE"}}
}
```

响应：

```json
{
  "boundSql": "SELECT ... WHERE status = ?",
  "hitNodeIds": ["findOverdueLoans#if[0]"],
  "parameterMappings": [{"property": "status", "jdbcType": "VARCHAR"}],
  "validationErrors": [{"field": "branchIds", "nodeId": "foreach_1", "code": "TYPE_MISMATCH", "message": "必须是集合"}],
  "redacted": true
}
```

该端点不创建 Session/Run，不执行 SQL，不读取生产数据。Plugin 不生成或拼接 BoundSql。

## 3. Analyze 扩展

`POST /mapper-statements/analyze` 在既有字段上增加：

```json
{
  "executionMode": "AUTO",
  "mainScenario": {
    "suggestionSetId": "suggest_1",
    "selections": [],
    "parameters": {}
  },
  "transientRules": [{
    "ruleId": "tmp_1",
    "kind": "ALLOWED_VALUES",
    "target": "orderBy",
    "operator": "IN",
    "values": [{"type": "STRING", "value": "due_at"}]
  }],
  "maxScenarios": 20,
  "costThreshold": "MEDIUM"
}
```

`executionMode ∈ AUTO | REVIEW`。临时规则 `kind ∈ PARAMETER_FACT | ALLOWED_VALUES | RANGE | USER_SAMPLE`，只属于当前 Run。

## 4. 临时规则影响预览

`POST /mapper-statements/transient-rules/preview`

响应只返回确定性规划差异：

```json
{
  "addedScenarioIds": ["scn_dollar"],
  "removedScenarioIds": [],
  "addedCoverageGoals": ["DOLLAR_WHITELIST"],
  "removedCoverageGoals": [],
  "guardChanges": [{"guard": "DOLLAR_WHITELIST", "before": "BLOCKING", "after": "SATISFIED"}],
  "costBefore": "MEDIUM",
  "costAfter": "MEDIUM",
  "fieldErrors": []
}
```

## 5. 同 Run 确认

`POST /runs/{runId}/confirm`

```json
{
  "includedScenarioIds": ["scn_main", "scn_dollar"],
  "excludedScenarios": [{"scenarioId": "scn_p95", "reason": "超出本次成本预算"}]
}
```

服务端必须拒绝排除 main path、guard 或 `required=true` 的场景，并继续原 Run。

## 6. 恢复、历史与认证

- `GET /runs/{runId}` 返回结构化业务状态、`lastEventId`、`reportId` 和 `cancellable`。
- `GET /reports?projectId=&moduleId=&statement=&datasourceProfileId=&severity=&completedFrom=&completedTo=&stale=&page=&size=` 返回分页历史；Plugin 不调用报告 DELETE。
- `GET /client` 可选返回 `expiresAt`；缺失时 Plugin 不推断到期时间。
- 所有错误使用 RFC 9457 Problem Details：`code`、`retryable`、`errors[]`。401 进入重新认证；网络、429、`retryable=true` 的 5xx 才允许有界重试；解析、`UNSUPPORTED`、校验失败和非幂等冲突不自动重试。

## 7. 真实集成状态

`codex/idea-plugin-ui-ux` 已实现第 1–6 节契约：静态注解 Mapper index、默认参数建议、
MyBatis 官方 BoundSql 预览、临时规则影响预览、同 Run 确认、Run 恢复、服务端历史筛选和
真实 Client 投影。`PluginBackendConsumerContractTest` 使用启动后的真实 Spring 后端覆盖
建议/预览零 Run 副作用，以及 `REVIEW → AWAITING_CONFIRMATION → confirm → COMPLETED`
同 Run 生命周期；Fake Gateway 继续用于 Plugin 独立状态机和错误交互测试，不冒充生产响应。
