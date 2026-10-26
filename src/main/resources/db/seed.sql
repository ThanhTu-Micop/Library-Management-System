-- Seed data for the Library Management System (demo only).
-- Person 1: database / SQL.
-- WARNING: run only against library_db / library_test. Never run against real or production data.
-- Book titles keep their original language; all other text is English.

BEGIN;

TRUNCATE book_authors, loans, book_copies, books, authors, publishers, categories, members RESTART IDENTITY CASCADE;

-- Publishers (ids 1-3).
INSERT INTO publishers (name) VALUES
('Literature Publisher'),
('Science and Technology Publisher'),
('Technology Publisher');

-- Categories (ids 1-4).
INSERT INTO categories (name) VALUES
('Novel'),
('Science'),
('History'),
('Technology');

-- Authors (ids 1-4).
INSERT INTO authors (name) VALUES
('Nguyen Nhat Anh'),
('Nam Cao'),
('Robert C. Martin'),
('Isaac Asimov');

-- Books (ids 1-4).
INSERT INTO books (isbn, title, publisher_id, category_id, publication_year) VALUES
('978-604-1-00001-1', 'Tôi thấy hoa vàng trên cỏ xanh', 1, 1, 2010),
('978-604-1-00002-2', 'Chí Phèo', 1, 1, 1941),
('978-0-13-235088-4', 'Clean Code', 3, 4, 2008),
('978-0-553-29335-7', 'Foundation', 2, 2, 1951);

-- Book copies (ids 1-8).
-- CP003 is under maintenance; CP004 and CP006 are currently borrowed; CP005 was returned.
INSERT INTO book_copies (book_id, copy_code, status) VALUES
(1, 'CP001', 'AVAILABLE'),
(1, 'CP002', 'AVAILABLE'),
(1, 'CP003', 'MAINTENANCE'),
(2, 'CP004', 'BORROWED'),
(2, 'CP005', 'AVAILABLE'),
(3, 'CP006', 'BORROWED'),
(4, 'CP007', 'AVAILABLE'),
(4, 'CP008', 'AVAILABLE');

-- Members (ids 1-3).
INSERT INTO members (member_code, full_name, email, status) VALUES
('M001', 'Alice Nguyen', 'alice@example.com', 'ACTIVE'),
('M002', 'Bob Tran', 'bob@example.com', 'ACTIVE'),
('M003', 'Charlie Le', 'charlie@example.com', 'INACTIVE');

-- Loans (ids 1-3). The 14-day rule requires due_date = loan_date + 14.
-- Dates are relative to CURRENT_DATE so the demo always contains one overdue loan,
-- one active loan that is not overdue, and one returned loan.
INSERT INTO loans (copy_id, member_id, loan_date, due_date, return_date, status) VALUES
(4, 1, CURRENT_DATE - 24, CURRENT_DATE - 10, NULL, 'ACTIVE'),
(6, 2, CURRENT_DATE - 3, CURRENT_DATE + 11, NULL, 'ACTIVE'),
(5, 1, CURRENT_DATE - 45, CURRENT_DATE - 31, CURRENT_DATE - 40, 'RETURNED');

-- Book authors (many-to-many).
INSERT INTO book_authors (book_id, author_id) VALUES
(1, 1),  -- Tôi thấy hoa vàng trên cỏ xanh -> Nguyen Nhat Anh
(2, 2),  -- Chí Phèo -> Nam Cao
(3, 3),  -- Clean Code -> Robert C. Martin
(4, 4);  -- Foundation -> Isaac Asimov

COMMIT;
