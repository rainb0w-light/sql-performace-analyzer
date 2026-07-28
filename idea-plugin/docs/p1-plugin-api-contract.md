# IDEA Plugin P1 API 契约冻结

状态：Plugin consumer contract，等待服务端实现。  
基路径：`/api/v1`。所有上行请求携带 `X-Request-Id`；创建/变更请求携带可重放的 `Idempotency-Key`。

## 1. 默认参数建议

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

## 7. 尚未实现的真实集成

基线 `ea30131` 尚无第 1、2、4、5、6 节端点。P1 Plugin 使用 Fake Gateway 做 consumer contract 和状态交互验收；真实按钮收到 404/501 时显示结构化“服务端能力未部署”，不回退为本地伪造结果。

