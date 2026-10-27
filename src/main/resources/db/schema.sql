-- Library Management System schema
-- Person 1: database / SQL
-- PostgreSQL 16
-- Borrowing period: exactly 14 calendar days
-- The script is applied atomically: a failure rolls back every statement.

BEGIN;

-- Accent-insensitive search for Vietnamese titles and names (E05).
-- The extension is trusted, so the application role can install it.
CREATE EXTENSION IF NOT EXISTS unaccent;

DROP TABLE IF EXISTS book_authors;
DROP TABLE IF EXISTS loans;
DROP TABLE IF EXISTS book_copies;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS authors;
DROP TABLE IF EXISTS publishers;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS members;

CREATE TABLE publishers (
    publisher_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    category_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE authors (
    author_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE books (
    book_id SERIAL PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    publisher_id INTEGER NOT NULL REFERENCES publishers(publisher_id) ON DELETE RESTRICT,
    category_id INTEGER NOT NULL REFERENCES categories(category_id) ON DELETE RESTRICT,
    publication_year INTEGER CHECK (publication_year >= 1000 AND publication_year <= 2100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- ISBN is stored as entered but must remain 13-20 characters after trimming.
    CONSTRAINT books_isbn_length CHECK (char_length(btrim(isbn)) BETWEEN 13 AND 20)
);

CREATE TABLE book_copies (
    copy_id SERIAL PRIMARY KEY,
    book_id INTEGER NOT NULL REFERENCES books(book_id) ON DELETE RESTRICT,
    copy_code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'BORROWED', 'MAINTENANCE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE members (
    member_id SERIAL PRIMARY KEY,
    member_code VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Blank email must be normalized to NULL by the DAO instead of stored as spaces.
    CONSTRAINT members_email_not_blank CHECK (email IS NULL OR btrim(email) <> '')
);

CREATE TABLE loans (
    loan_id SERIAL PRIMARY KEY,
    copy_id INTEGER NOT NULL REFERENCES book_copies(copy_id) ON DELETE RESTRICT,
    member_id INTEGER NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    loan_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETURNED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Borrowing period is exactly 14 calendar days.
    CONSTRAINT loans_due_date_14_days CHECK (due_date = loan_date + 14),
    CONSTRAINT loans_return_date_check CHECK (return_date IS NULL OR return_date >= loan_date),
    CONSTRAINT loans_status_return_date_consistency CHECK (
        (status = 'ACTIVE' AND return_date IS NULL) OR
        (status = 'RETURNED' AND return_date IS NOT NULL)
    )
);

CREATE TABLE book_authors (
    book_id INTEGER NOT NULL REFERENCES books(book_id) ON DELETE CASCADE,
    author_id INTEGER NOT NULL REFERENCES authors(author_id) ON DELETE RESTRICT,
    PRIMARY KEY (book_id, author_id)
);

-- Indexes for search, pagination, and constraints.
CREATE INDEX idx_books_title_lower ON books(LOWER(title));
CREATE INDEX idx_books_publisher_id ON books(publisher_id);
CREATE INDEX idx_books_category_id ON books(category_id);

CREATE INDEX idx_book_copies_book_id_status ON book_copies(book_id, status);

CREATE INDEX idx_members_full_name_lower ON members(LOWER(full_name));
CREATE UNIQUE INDEX ux_members_email_lower
    ON members (LOWER(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX ux_categories_name_lower ON categories (LOWER(name));

CREATE INDEX idx_loans_copy_id ON loans(copy_id);
CREATE INDEX idx_loans_member_id_loan_date ON loans(member_id, loan_date DESC);
CREATE UNIQUE INDEX idx_loans_active_partial_unique ON loans(copy_id) WHERE return_date IS NULL;
CREATE INDEX idx_loans_active_due_partial ON loans(due_date) WHERE return_date IS NULL;

COMMIT;
