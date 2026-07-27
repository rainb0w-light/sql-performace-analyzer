# 图书管理系统 Fixture（TDD 验收基准）

> 依据：`docs/cloud-code-next-goal.md` §4。受控样例域：证明架构能接受业务语义、画像、索引与分片证据，
> 并生成可解释的 SQL 场景。

## 目录

| 路径 | 内容 |
|---|---|
| `schema/library-common.sql` | 6 张业务表 + 业务索引（可移植 SQL：MySQL 与 H2 均可执行） |
| `schema/seed-data.sql` | 确定性种子数据：分类倾斜（FICTION 4/6）、热点分馆（branch 1 持 5/8 副本）、逾期/活跃/已还借阅、跨月份 `borrowed_at`（二级分片维度） |
| `mapper/BookMapper.xml` | `searchAvailableBooks`：可选 if 过滤 + `categories` foreach（空/单/多）+ choose 排序 + `${orderBy}` 白名单插值 |
| `mapper/LoanMapper.xml` | `findOverdueLoans`：主分片键 `memberId` 有无（单分片/跨分片）、`borrowedFrom/borrowedTo` 二级时间范围有无、`statuses` foreach |
| `mapper/ReservationMapper.xml` | `findQueue`：`bookId/branchId/status/maxPriority`，热点书目 + 高频分馆组合 |
| `knowledge/library-domain.md` | 业务语义 11 章节，每条知识带稳定 ID（`lib-…`）、来源版本与定位；测试中经过真实切块 + embedding + 检索 |
| `metadata/indexes.json` | 6 组索引（列顺序精确，如 `loan(member_id,status,due_at)`） |
| `metadata/shards.json` | `loan` 主分片 `member_id`（hash 16 桶）+ 二级分片 `borrowed_at`（月份） |
| `profiles/expected-profile.json` | 种子数据上的确定性画像期望（Top-K/null ratio/distinct/倾斜断言） |

## Excel 知识文件

`library-knowledge.xlsx` 不提交二进制，而由 `LibraryWorkbookFixtures`（测试代码）用 Apache POI
**确定性生成**——模板内容与行级错误用例在代码中可审查、可 diff：

- `tables` / `columns` / `rules` / `enums` / `aliases` / `sharding` 六个 sheet，与
  `library-domain.md` 同源（Excel 发布后生成/更新规范化 Markdown，两者不得分叉）。
- 错误用例工作簿：缺列、错误枚举、重复键、非法敏感策略 → 逐行 RowError（sheet/row/column/reason）。

## 场景期望（§4.6，Phase C 端到端断言依据）

`findOverdueLoans` 在发布图书知识 + 画像 + 索引/分片后，场景矩阵必须覆盖：

1. 典型逾期主路径（memberId + statuses=[ACTIVE] + asOf）；
2. 单主分片（memberId 指定）与跨分片（memberId 缺失）成对出现；
3. 带/不带 `borrowed_at` 范围的二级分片场景；
4. `statuses` foreach 空/单值/多值；
5. `due_at` 边界（min/分位/max/越界，来自画像）与高频/低频枚举值；
6. 总数 ≤ 20，按 BoundSql 指纹去重，每场景携带 `knowledgeVersion/profileSnapshotId/evidenceIds/reason`。

无语义基线（不发布知识/画像/元数据）下只产生结构覆盖与安全默认值，两者结果必须可区分。
