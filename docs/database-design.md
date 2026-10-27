# Database Design — Library Management System

| Field | Value |
|---|---|
| Owner | Person 1 (database, SQL, JDBC/DAO) |
| Database | PostgreSQL 16.15 |
| Schema file | `src/main/resources/db/schema.sql` |
| Seed file | `src/main/resources/db/seed.sql` |
| Last updated | 2026-10-26 |
| Status | Implemented and applied to `library_db` and `library_test` |

## 1. Entity-relationship model

```text
publishers 1 ─────< books >───── 1 categories
                      │
                      ├──< book_authors >── 1 authors
                      │
                      └──< book_copies 1 ───< loans >─── 1 members
```

- A book has exactly one publisher and one category.
- A book has many authors through `book_authors`.
- A book has many physical copies.
- A member has many loans.
- A copy has many historical loans but at most one active loan at a time.

## 2. Tables and constraints

| Table | Primary key | Foreign keys | Key constraints |
|---|---|---|---|
| `publishers` | `publisher_id` | — | `name` NOT NULL |
| `categories` | `category_id` | — | `name` NOT NULL; unique case-insensitive index on `LOWER(name)` |
| `authors` | `author_id` | — | `name` NOT NULL |
| `books` | `book_id` | `publisher_id` → `publishers` RESTRICT; `category_id` → `categories` RESTRICT | `isbn` NOT NULL UNIQUE; `title` NOT NULL; ISBN length 13–20 after trimming; `publication_year` 1000–2100 |
| `book_authors` | (`book_id`, `author_id`) | `book_id` → `books` CASCADE; `author_id` → `authors` RESTRICT | Many-to-many association; composite primary key prevents duplicates |
| `book_copies` | `copy_id` | `book_id` → `books` RESTRICT | `copy_code` NOT NULL UNIQUE; `status` is `AVAILABLE`, `BORROWED`, or `MAINTENANCE` |
| `members` | `member_id` | — | `member_code` NOT NULL UNIQUE; `email` optional but non-blank when present; unique case-insensitive email when present; `status` is `ACTIVE` or `INACTIVE` |
| `loans` | `loan_id` | `copy_id` → `book_copies` RESTRICT; `member_id` → `members` RESTRICT | `due_date = loan_date + 14`; return date not before loan date; status/return-date consistency; one active loan per copy |

All tables have `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`, except `book_authors`, which only stores the association.

## 3. Critical invariants

1. **Borrowing period** — `CHECK (due_date = loan_date + 14)`. The service computes the due date; the client cannot choose an arbitrary period.
2. **Return date order** — `CHECK (return_date IS NULL OR return_date >= loan_date)`.
3. **Status consistency** — `ACTIVE` loans have `return_date IS NULL`; `RETURNED` loans have a non-null return date.
4. **One active loan per copy** — partial unique index `idx_loans_active_partial_unique ON loans(copy_id) WHERE return_date IS NULL`.
5. **Loan history protection** — loan foreign keys use `ON DELETE RESTRICT`. A book with any loan history cannot be deleted.
6. **Category uniqueness** — `ux_categories_name_lower` rejects names that differ only by case.
7. **Member email uniqueness** — `ux_members_email_lower` is a partial unique index on `LOWER(email) WHERE email IS NOT NULL`. Blank email is rejected by `members_email_not_blank`; the DAO normalizes blank input to `NULL`.
8. **ISBN** — stored as entered after trimming, 13–20 characters, exact unique match. There is no ISBN-13 normalization or checksum validation.
9. **Availability** — derived from `book_copies.status = 'AVAILABLE'`. There is no editable quantity column.
10. **Overdue state** — derived as an active loan with `due_date < today`. No `OVERDUE` value is stored in `loans.status`.

## 4. Delete behavior

- `books` → `book_authors` uses `ON DELETE CASCADE`; deleting a book removes its author links.
- `books` → `book_copies` uses `ON DELETE RESTRICT`; copies must be removed first.
- `book_copies` → `loans` and `members` → `loans` use `ON DELETE RESTRICT`; loan history is preserved.
- Application rule: `LoanDAO.existsByBookId` checks **all** loans, including returned loans. When any loan history exists, the service must reject deletion before attempting SQL.
- When deletion is allowed, the service deletes author links, then copies, then the book in one transaction.
- Deleting a book with zero copies succeeds; `BookCopyDAO.deleteByBookId` tolerates zero affected rows.

## 5. Indexes

The schema creates 11 named search indexes plus inline unique-constraint indexes (`books_isbn_key`, `book_copies_copy_code_key`, `members_member_code_key`). The three indexes that duplicated inline unique constraints (`idx_books_isbn`, `idx_book_copies_copy_code`, `idx_members_member_code`) were removed because they add no value beyond the unique constraints.

| Table | Index | Purpose |
|---|---|---|
| `books` | `idx_books_title_lower` | Case-insensitive title search |
| `books` | `idx_books_publisher_id` | Publisher filter |
| `books` | `idx_books_category_id` | Category filter |
| `book_copies` | `idx_book_copies_book_id_status` | Copy lookup and availability counts |
| `members` | `idx_members_full_name_lower` | Case-insensitive name search |
| `members` | `ux_members_email_lower` | Partial unique email |
| `categories` | `ux_categories_name_lower` | Unique category name |
| `loans` | `idx_loans_copy_id` | Copy history |
| `loans` | `idx_loans_member_id_loan_date` | Member history ordered by date |
| `loans` | `idx_loans_active_partial_unique` | One active loan per copy |
| `loans` | `idx_loans_active_due_partial` | Overdue scans over active loans |

## 6. Seed data

`seed.sql` is destructive and demo-only. It wraps cleanup and inserts in one transaction, truncates all application tables with `RESTART IDENTITY CASCADE`, and uses dates relative to `CURRENT_DATE` so the demo never becomes stale.

Verified seed counts:

| Table | Rows |
|---|---|
| `publishers` | 3 |
| `categories` | 4 |
| `authors` | 4 |
| `books` | 4 |
| `book_copies` | 8 |
| `members` | 3 |
| `loans` | 3 |
| `book_authors` | 4 |

The seed includes:

- Vietnamese titles `Tôi thấy hoa vàng trên cỏ xanh` and `Chí Phèo`, and English titles `Clean Code` and `Foundation`.
- Available, borrowed, and maintenance copies.
- One overdue active loan, one active loan that is not overdue, and one returned loan.
- One inactive member and one book with no available copies.

## 7. Date handling

- DAO date-sensitive methods accept a caller-supplied `LocalDate today`.
- `LoanSearchCriteria.today` defaults to `LocalDate.now()` but tests set a fixed value.
- DAO SQL does not use `CURRENT_DATE`; only `seed.sql` uses it to build a stable demo.
- `DashboardDAO.getStats(Connection, LocalDate today)` binds `today` to the overdue predicate.
- `LoanDAO.countOverdue(Connection, LocalDate today)` also uses the supplied date.

## 8. SQLState mapping

| SQLState | Meaning | Expected handling |
|---|---|---|
| `23505` | Unique violation | Duplicate ISBN, member code, email, category name, or active loan → business message |
| `23503` | Foreign-key violation | Invalid reference or protected loan history → business message |
| `23514` | Check violation | Invalid loan date, ISBN length, year, status, or blank email → validation/business message |

`DataAccessException` preserves both the SQLState and the original `SQLException` cause.

## 9. Applying the schema

Run as the application role from the project root; keep the password outside the workspace:

```powershell
$env:PGPASSWORD = Get-Content '<secrets-path>\library_app.password' -Raw
$env:PGCLIENTENCODING = 'UTF8'
psql -h 127.0.0.1 -U library_app -d library_db -v ON_ERROR_STOP=1 -f src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U library_app -d library_db -v ON_ERROR_STOP=1 -f src/main/resources/db/seed.sql
```

Repeat with `-d library_test` for the test database. `schema.sql` and `seed.sql` both wrap their work in `BEGIN; ... COMMIT;`.

## 10. Verification evidence (2026-10-26)

- `schema.sql` and `seed.sql` applied successfully to both `library_db` and `library_test`.
- Both databases contain 8 application tables and 14 named indexes.
- Seed counts matched the expected `3 / 4 / 4 / 4 / 8 / 3 / 3 / 4` for publishers, categories, authors, books, copies, members, loans, and book-author links.
- Constraint checks passed on both databases:
  - Duplicate category name differing only by case: rejected.
  - Duplicate member email differing only by case: rejected.
  - Blank email: rejected.
  - ISBN length 12: rejected; lengths 13 and 20: accepted.
  - Due date `loan_date + 15`: rejected.
  - Second active loan for the same copy: rejected.
- The full read-only verification queries and command results are recorded in `docs/runtime-toolchain-audit.md`.

## 11. Related files

- `src/main/resources/db/schema.sql` — DDL, constraints, and indexes.
- `src/main/resources/db/seed.sql` — demo data.
- `src/main/java/com/library/dao/jdbc/*` — JDBC implementations.
- `src/test/java/com/library/dao/*` — DAO integration tests against `library_test`.
- `src/test/java/com/library/dao/support/TestDatabase.java` — test database guard, reset, and reference seed.
