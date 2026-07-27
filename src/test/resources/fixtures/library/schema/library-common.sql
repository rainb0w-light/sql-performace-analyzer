-- Library management system fixture schema (docs/cloud-code-next-goal.md §4.2).
-- Applied to the TARGET database under test (MySQL in the Docker gate; H2 in Docker-free
-- tests) — NOT the management database. Portable SQL accepted by both engines.

CREATE TABLE library_branch (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    region_code VARCHAR(20) NOT NULL
);

CREATE TABLE book (
    id BIGINT PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE UNIQUE INDEX uk_book_isbn ON book(isbn);
CREATE INDEX idx_book_category_status ON book(category, status);

CREATE TABLE book_copy (
    id BIGINT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    shelf_code VARCHAR(50),
    status VARCHAR(20) NOT NULL
);

CREATE INDEX idx_book_copy_branch_status_book ON book_copy(branch_id, status, book_id);

CREATE TABLE member (
    id BIGINT PRIMARY KEY,
    member_no VARCHAR(40) NOT NULL,
    level VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    home_branch_id BIGINT NOT NULL
);

CREATE UNIQUE INDEX uk_member_no ON member(member_no);

-- Primary shard key: member_id; secondary shard key: borrowed_at (member bucket + month routing).
CREATE TABLE loan (
    id BIGINT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    copy_id BIGINT NOT NULL,
    borrowed_at TIMESTAMP NOT NULL,
    due_at TIMESTAMP NOT NULL,
    returned_at TIMESTAMP,
    status VARCHAR(20) NOT NULL
);

CREATE INDEX idx_loan_member_status_due ON loan(member_id, status, due_at);
CREATE INDEX idx_loan_copy_status ON loan(copy_id, status);

CREATE TABLE reservation (
    id BIGINT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    priority INT NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE INDEX idx_reservation_book_branch_status_priority ON reservation(book_id, branch_id, status, priority);
