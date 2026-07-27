# 图书管理系统业务语义

> 知识源：`library-domain` · 版本：v1 · 来源：人工发布（MANUAL_RULE）
> 每条知识携带稳定 ID（`lib-…`）、来源版本与定位信息；嵌入检索结果必须回传这些证据字段。

## 表定义

- [lib-tab-book] `book`（书目）：一条书目记录，可有多个馆藏副本。定位：tables!row2
- [lib-tab-copy] `book_copy`（馆藏副本）：某分馆中某书目的一本实体副本。定位：tables!row3
- [lib-tab-member] `member`（读者）：持有读者证的注册用户。定位：tables!row4
- [lib-tab-loan] `loan`（借阅记录）：一次借阅生命周期（借出→到期→归还/逾期）。定位：tables!row5
- [lib-tab-reservation] `reservation`（预约记录）：读者对书目的预约排队。定位：tables!row6
- [lib-tab-branch] `library_branch`（分馆）：物理分馆，按 region_code 分区。定位：tables!row7

## 字段定义

- [lib-col-book-category] `book.category`：图书分类，业务含义「分类编码」，枚举域 BOOK_CATEGORY。定位：columns!row2
- [lib-col-book-status] `book.status`：书目状态，枚举域 BOOK_STATUS。定位：columns!row3
- [lib-col-copy-status] `book_copy.status`：副本状态，枚举域 COPY_STATUS；仅 AVAILABLE 可借。定位：columns!row4
- [lib-col-member-no] `member.member_no`：读者证号，敏感业务标识，敏感策略 HASHED。定位：columns!row5
- [lib-col-member-level] `member.level`：读者等级，枚举域 MEMBER_LEVEL，决定最大借阅数量。定位：columns!row6
- [lib-col-loan-status] `loan.status`：借阅状态，枚举域 LOAN_STATUS。定位：columns!row7
- [lib-col-loan-borrowed-at] `loan.borrowed_at`：借出时间，二级分片键（月份路由）。定位：columns!row8
- [lib-col-loan-due-at] `loan.due_at`：应还时间，逾期判定基准。定位：columns!row9
- [lib-col-reservation-status] `reservation.status`：预约状态，枚举域 RESERVATION_STATUS。定位：columns!row10

## 枚举

- [lib-enum-category] BOOK_CATEGORY：`TECH`（技术）、`FICTION`（小说，高频）、`HISTORY`（历史）。定位：enums!row2
- [lib-enum-book-status] BOOK_STATUS：`ACTIVE`（在架）、`WITHDRAWN`（下架）。定位：enums!row3
- [lib-enum-copy-status] COPY_STATUS：`AVAILABLE`（可借，高频）、`BORROWED`（借出）、`DAMAGED`（损毁，低频）。定位：enums!row4
- [lib-enum-loan-status] LOAN_STATUS：`ACTIVE`（借阅中）、`RETURNED`（已归还）、`OVERDUE_LOCKED`（逾期冻结，低频）。定位：enums!row5
- [lib-enum-member-level] MEMBER_LEVEL：`GOLD`（最大 10 本）、`SILVER`（最大 5 本）、`BRONZE`（最大 3 本）。定位：enums!row6
- [lib-enum-reservation-status] RESERVATION_STATUS：`WAITING`（排队中）、`READY`（可取）、`FULFILLED`（已履约）、`CANCELLED`（已取消，低频）。定位：enums!row7

## 业务规则

- [lib-rule-copy-borrowable] 仅 `book_copy.status=AVAILABLE` 的副本才能新建借阅。定位：rules!row2
- [lib-rule-active-null-returned] 活跃借阅（`loan.status=ACTIVE`）的 `returned_at` 必须为空。定位：rules!row3
- [lib-rule-overdue-def] 逾期定义：`loan.status='ACTIVE' AND loan.due_at < now`。定位：rules!row4
- [lib-rule-level-limit] 读者等级决定最大借阅数量（GOLD 10 / SILVER 5 / BRONZE 3）。定位：rules!row5
- [lib-rule-one-book-per-copy] 同一副本在同一时间只能有一条活跃借阅。定位：rules!row6

## 敏感级别

- [lib-sens-member-no] `member.member_no`：敏感业务标识 → **HASHED**（画像 Top-K 以 SHA-256 存储，任何环境不得明文落库/落日志/落报告）。定位：sensitivity!row2
- [lib-sens-isbn] `book.isbn`：可明文用于精确检索 → **PLAINTEXT**。定位：sensitivity!row3

## 索引事实

- [lib-idx-book-isbn] `book` 唯一索引 `uk_book_isbn(isbn)`：ISBN 精确检索主路径。定位：indexes!row2
- [lib-idx-book-cat-status] `book(category, status)` 联合索引：分类浏览主路径。定位：indexes!row3
- [lib-idx-copy-branch-status-book] `book_copy(branch_id, status, book_id)` 联合索引：分馆可借副本查询。定位：indexes!row4
- [lib-idx-loan-member-status-due] `loan(member_id, status, due_at)` 联合索引：单读者逾期查询主路径。定位：indexes!row5
- [lib-idx-loan-copy-status] `loan(copy_id, status)` 联合索引：副本活跃借阅检查。定位：indexes!row6
- [lib-idx-reservation-composite] `reservation(book_id, branch_id, status, priority)` 联合索引：预约队列查询。定位：indexes!row7

## 分片与二级分片规则

- [lib-shard-loan-primary] `loan` 主分片键 `member_id`：按会员桶路由（hash，16 桶）。缺少 member_id 的查询触发**跨分片扫描**。定位：shards!row2
- [lib-shard-loan-secondary] `loan` 二级分片键 `borrowed_at`：按月份路由时间分区。缺少 borrowed_at 范围将**扩大时间分区扫描**。定位：shards!row3

## 查询主路径

- [lib-path-overdue] 逾期借阅主路径：`findOverdueLoans` 携带 `memberId` + `statuses=[ACTIVE]` + `asOf=now`，命中 `idx_loan_member_status_due`，路由单分片。定位：paths!row2
- [lib-path-search] 可借书目检索主路径：`searchAvailableBooks` 携带 `branchId` + `category`，命中 `idx_book_copy_branch_status_book`。定位：paths!row3
- [lib-path-queue] 预约队列主路径：`findQueue` 携带 `bookId` + `branchId` + `status=WAITING`，命中 `idx_reservation_composite`。定位：paths!row4

## 边界与异常场景

- [lib-edge-no-member] `findOverdueLoans` 缺少 `memberId` → 跨分片扫描（风险：CROSS_SHARD）。定位：edges!row2
- [lib-edge-no-time-range] `findOverdueLoans` 缺少 `borrowed_at` 范围 → 扫描全部月份分区。定位：edges!row3
- [lib-edge-hot-branch] 分馆 1（中心馆）为热点分馆，副本与预约高度集中 → 数据倾斜（风险：HOTSPOT）。定位：edges!row4
- [lib-edge-empty-foreach] `categories`/`statuses` 为空集合 → `<if size()>0` 短路，IN 子句不生成。定位：edges!row5
- [lib-edge-orderby-whitelist] `searchAvailableBooks.orderBy` 是 `${}` 插值点，白名单仅允许 `title`、`category`；其他取值必须标记风险且不生成任意值。定位：edges!row6

## 别名

- [lib-alias-books] `books` → `book`（TABLE 别名）。定位：aliases!row2
- [lib-alias-loans] `loans` → `loan`（TABLE 别名）。定位：aliases!row3
- [lib-alias-reader] `reader` → `member`（TERM 别名）。定位：aliases!row4
- [lib-alias-orderby-title] `orderBy` → `title`（DOLLAR_WHITELIST：`${orderBy}` 插值白名单）。定位：aliases!row5
- [lib-alias-orderby-category] `orderBy` → `category`（DOLLAR_WHITELIST：`${orderBy}` 插值白名单）。定位：aliases!row6

## 证据版本

- [lib-evidence-version] 知识版本：library-domain v1；发布人 alice；结构化事实与本文档同源（Excel 发布后生成/更新本 Markdown，两者不得成为互不一致的两套事实）。
