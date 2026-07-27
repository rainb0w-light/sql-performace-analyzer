-- Deterministic seed data for the library fixture (docs/cloud-code-next-goal.md §4.2/§4.3).
-- Encodes the skew the profiling assertions rely on: category skew (fiction dominates),
-- a hotspot branch (branch 1), member levels, overdue vs active vs returned loans.

INSERT INTO library_branch (id, name, region_code) VALUES
    (1, '中心图书馆', 'EAST'),
    (2, '西城分馆', 'WEST'),
    (3, '南山分馆', 'SOUTH');

INSERT INTO book (id, isbn, title, category, status) VALUES
    (1, '978-0-1', '深入数据库', 'TECH', 'ACTIVE'),
    (2, '978-0-2', '小说：远航', 'FICTION', 'ACTIVE'),
    (3, '978-0-3', '小说：归途', 'FICTION', 'ACTIVE'),
    (4, '978-0-4', '小说：星海', 'FICTION', 'ACTIVE'),
    (5, '978-0-5', '历史简编', 'HISTORY', 'ACTIVE'),
    (6, '978-0-6', '下架旧书', 'FICTION', 'WITHDRAWN');

-- Copies: branch 1 is the hotspot (most copies there).
INSERT INTO book_copy (id, book_id, branch_id, shelf_code, status) VALUES
    (1, 1, 1, 'A-01', 'AVAILABLE'),
    (2, 1, 1, 'A-02', 'BORROWED'),
    (3, 2, 1, 'B-01', 'AVAILABLE'),
    (4, 2, 2, 'B-02', 'AVAILABLE'),
    (5, 3, 1, 'B-03', 'BORROWED'),
    (6, 4, 1, 'B-04', 'AVAILABLE'),
    (7, 5, 3, 'C-01', 'AVAILABLE'),
    (8, 6, 2, 'B-05', 'DAMAGED');

INSERT INTO member (id, member_no, level, status, home_branch_id) VALUES
    (1, 'M-1001', 'GOLD', 'ACTIVE', 1),
    (2, 'M-1002', 'SILVER', 'ACTIVE', 1),
    (3, 'M-1003', 'BRONZE', 'ACTIVE', 2),
    (4, 'M-1004', 'GOLD', 'SUSPENDED', 3);

-- Loans: overdue (ACTIVE + past due), active-not-due, returned. borrowed_at spans months for
-- the secondary (time) shard dimension.
INSERT INTO loan (id, member_id, copy_id, borrowed_at, due_at, returned_at, status) VALUES
    (1, 1, 2, TIMESTAMP '2026-05-01 10:00:00', TIMESTAMP '2026-05-15 10:00:00', NULL, 'ACTIVE'),
    (2, 2, 5, TIMESTAMP '2026-06-01 09:00:00', TIMESTAMP '2026-06-15 09:00:00', NULL, 'ACTIVE'),
    (3, 3, 1, TIMESTAMP '2026-07-01 09:00:00', TIMESTAMP '2026-08-01 09:00:00', NULL, 'ACTIVE'),
    (4, 1, 3, TIMESTAMP '2026-04-01 09:00:00', TIMESTAMP '2026-04-15 09:00:00', TIMESTAMP '2026-04-14 09:00:00', 'RETURNED'),
    (5, 2, 4, TIMESTAMP '2026-03-01 09:00:00', TIMESTAMP '2026-03-15 09:00:00', TIMESTAMP '2026-03-20 09:00:00', 'RETURNED'),
    (6, 1, 6, TIMESTAMP '2026-02-01 09:00:00', TIMESTAMP '2026-02-15 09:00:00', NULL, 'ACTIVE');

INSERT INTO reservation (id, member_id, book_id, branch_id, priority, status) VALUES
    (1, 3, 2, 1, 1, 'WAITING'),
    (2, 4, 2, 1, 2, 'WAITING'),
    (3, 1, 5, 3, 1, 'READY'),
    (4, 2, 3, 1, 1, 'FULFILLED');
