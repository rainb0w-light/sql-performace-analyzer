# SQL 性能分析报告

- 语句：`library.LoanMapper.findOverdueLoans`
- Mapper：fixtures/library/mapper/LoanMapper.xml（c379d4fbf228…）
- 知识版本：图书业务知识@1 · 画像快照：snap_e2e_8bc75e15-3c01-4c00-b6c6-129efbd882fd · 生成时间：2026-07-27T23:56:37.001161Z

## 结论摘要

**严重度 HIGH**（置信度 0.85）：缺少主分片键导致跨分片扫描（共 2 项风险，10 个场景）

- 缺少主分片键导致跨分片扫描
- 缺少二级分片时间范围，扩大时间分区扫描

## 风险

- CROSS_SHARD：缺少主分片键导致跨分片扫描
- CROSS_SHARD：缺少二级分片时间范围，扩大时间分区扫描

## 场景矩阵（10 个场景，BoundSql 均来自官方运行时）

| 场景 | 参数来源 | 指纹 | 风险 | 理由 |
|---|---|---|---|---|
| 业务主路径 | RULE_INFERRED | a3d64a221c5f0977 | — | 必填参数齐备的典型业务调用 |
| foreach(statuses) 空集合 | BOUNDARY_GENERATED | 2e3c161ea0957185 | — | foreach 集合规模场景：空集合（不复制真实大集合） |
| foreach(statuses) 典型多元素 | BOUNDARY_GENERATED | b324736f168f101c | — | foreach 集合规模场景：典型多元素（不复制真实大集合） |
| 跨分片扫描 | BOUNDARY_GENERATED | e54230da3451769c | — | 分片键 member_id 缺失，可能触发跨分片扫描 |
| foreach(statuses) 受控大列表 | BOUNDARY_GENERATED | 576026022bb7a351 | — | foreach 集合规模场景：受控大列表（不复制真实大集合） |
| 二级分片时间范围缺失 | BOUNDARY_GENERATED | bb4d60cdd14d9d03 | — | 二级分片键 borrowed_at 对应参数缺失，扩大时间分区扫描 |
| 条件不成立：branchId != null | BOUNDARY_GENERATED | b99039861799eccb | — | 动态条件 false 覆盖：branchId != null |
| 条件不成立：dueBefore != null | BOUNDARY_GENERATED | d6da1ef6981998de | — | 动态条件 false 覆盖：dueBefore != null |
| 条件不成立：borrowedFrom != null | BOUNDARY_GENERATED | 7960ea808b26550e | — | 动态条件 false 覆盖：borrowedFrom != null |
| 条件不成立：borrowedTo != null | BOUNDARY_GENERATED | c4169eead1e8eb90 | — | 动态条件 false 覆盖：borrowedTo != null |

## 索引与分片分析

- 索引 idx_loan_member_status_due(member_id,status,due_at)，来源 MANUAL
- 分片 loan：主分片键 member_id，二级分片键 borrowed_at，来源 MANUAL

## 数据分布

- library.loan.due_at 数据分布画像
- library.loan.returned_at 数据分布画像
- library.loan.status 数据分布画像

## 执行计划（普通只读 EXPLAIN）

（无可用执行计划）

## 优化建议

### 为查询补充主分片键 member_id 以路由单分片（HIGH，置信度 0.9）
- 问题：跨分片扫描：查询条件缺少主分片键
- 影响：缺失主分片键时查询必须扫描全部分片，延迟与资源消耗随分片数线性增长。
- 建议：—

### 为查询补充 borrowed_at 时间范围以裁剪月份分区（MEDIUM，置信度 0.85）
- 问题：二级分片裁剪缺失
- 影响：缺少 borrowed_at 范围将扫描全部历史月份分区。
- 建议：—

## AgentScope 增强

- 状态：SKIPPED

## 限制与缺失证据

- 普通只读 EXPLAIN 未启用；确定性报告仅使用 Mapper、画像、索引和分片证据。
- 未执行 EXPLAIN（只读建议边界）。
