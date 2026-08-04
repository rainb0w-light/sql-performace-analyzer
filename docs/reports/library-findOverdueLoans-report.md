# SQL 性能分析报告

## 一页总结

- 语句：`library.LoanMapper.findOverdueLoans`
- 目标：对 MyBatis XML Mapper 的 `BoundSql` 做完整后台分析，并在真实 MySQL 目标库上补充只读 `EXPLAIN`
- 最终状态：已跑通完整流程，报告已包含 10 个场景、索引/分片分析、数据分布画像和执行计划
- 最终报告：`report_f4d109ec-95db-493a-b13b-361f041dd31b`
- 最终运行：`run_40b06738-0414-44c9-88a1-00fee76d5ca8`

## 执行结果

- Docker MySQL 已启动并作为测试目标库使用
- 目标库已导入 `loan`、`book`、`book_copy`、`member`、`reservation` 等表及种子数据
- 已创建只读账号 `ro`，用于后台只读分析与普通 `EXPLAIN`
- 知识库已发布为 `library-domain@1`
- 画像快照已生成并用于本次分析
- 解析链路已完成，`BoundSql` 来自 MyBatis 官方运行时
- 安全 `SELECT` 场景已执行普通只读 `EXPLAIN`
- 未执行 `EXPLAIN ANALYZE`

## 报告结论

**严重度：HIGH**

本次 `findOverdueLoans` 的核心风险已经明确：

- 查询场景存在跨分片扫描风险
- 查询场景存在二级时间分片裁剪不足风险
- 对应的优化方向已经落到可执行证据上，而不是纯静态猜测

## 证据概览

### 场景矩阵

本次共覆盖 10 个场景，均由 MyBatis 官方运行时生成 `BoundSql`：

| 场景 | 参数来源 | 指纹 | 说明 |
|---|---|---|---|
| 业务主路径 | RULE_INFERRED | `a3d64a221c5f0977` | 必填参数齐备的典型业务调用 |
| foreach(statuses) 空集合 | BOUNDARY_GENERATED | `2e3c161ea0957185` | 空集合边界 |
| foreach(statuses) 典型多元素 | BOUNDARY_GENERATED | `b324736f168f101c` | 常规多元素边界 |
| 跨分片扫描 | BOUNDARY_GENERATED | `e54230da3451769c` | 缺少主分片键 `member_id` |
| foreach(statuses) 受控大列表 | BOUNDARY_GENERATED | `576026022bb7a351` | 受控大列表边界 |
| 二级分片时间范围缺失 | BOUNDARY_GENERATED | `bb4d60cdd14d9d03` | 缺少 `borrowed_at` 范围 |
| 条件不成立：branchId != null | BOUNDARY_GENERATED | `b99039861799eccb` | 动态条件 false 覆盖 |
| 条件不成立：dueBefore != null | BOUNDARY_GENERATED | `d6da1ef6981998de` | 动态条件 false 覆盖 |
| 条件不成立：borrowedFrom != null | BOUNDARY_GENERATED | `7960ea808b26550e` | 动态条件 false 覆盖 |
| 条件不成立：borrowedTo != null | BOUNDARY_GENERATED | `c4169eead1e8eb90` | 动态条件 false 覆盖 |

### 索引与分片

- `idx_loan_member_status_due(member_id,status,due_at)`，来源 MANUAL
- `loan` 分片规则：主分片键 `member_id`，二级分片键 `borrowed_at`

### 数据分布

- `library.loan.due_at` 已有画像
- `library.loan.returned_at` 已有画像
- `library.loan.status` 已有画像

### 执行计划

- 已对 10 个安全 `SELECT` 场景执行普通只读 `EXPLAIN`
- 执行计划已经写入最终报告
- 关键场景已能命中索引 `idx_loan_member_status_due`
- 不再停留在“无可用执行计划”的状态

## 优化建议

1. 补充主分片键 `member_id`
   - 目的：路由到单分片，避免跨分片扫描
   - 证据：当前查询缺少主分片键时会扩散到全部分片

2. 补充 `borrowed_at` 时间范围
   - 目的：裁剪二级时间分片
   - 证据：当前时间范围缺失时会扩大历史分区扫描

## 结论

这份报告已经不是“静态推断版”，而是一次完整跑通后的后台分析结果：

- MyBatis `BoundSql` 已完成
- 目标 MySQL 已接入并可只读分析
- 数据、知识、画像、索引、分片、执行计划全部串起来了
- 报告结果可以作为 IDEA 插件验收的后端基线
