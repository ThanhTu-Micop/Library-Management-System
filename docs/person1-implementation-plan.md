# Person 1 Implementation Plan — Library Management System

| Field | Value |
|---|---|
| Status | Implemented and verified for Person 1 scope; checkboxes retained as the original work breakdown |
| Owner | Person 1 |
| Scope | Database, SQL, models, JDBC DAO, DAO tests, JSP/UI, CSS/JS, DB/UI docs |
| Out of scope | `pom.xml`, Maven Wrapper, `web.xml`, `context.xml`, Controllers, Services, filters, transaction boundaries |
| Runtime baseline | JDK 17, Maven 3.9.16, Tomcat 10.1.61, PostgreSQL 16.15, pgJDBC 42.7.14, JSTL 3.0.1 |
| Last updated | 2026-10-30 |
| Evidence policy | Never claim build, test, JSP, or deployment success without actual command output |

> **Implementation note (2026-10-30):** This plan has been implemented for the Person 1 scope. Current evidence is recorded in [`docs/runtime-toolchain-audit.md`](runtime-toolchain-audit.md): 40 main Java files, 14 test files, 124/124 standalone DAO tests, 15 JSPs plus 1 shared tag compiled by `JspC` (16 generated files), schema/seed applied to both databases, and 214 EL property references checked. The unchecked boxes below preserve the original work-breakdown checklist; they are not a claim that the work is pending. Maven, WAR, deployment, Controllers, Services, and HTTP flows remain Person 2 blockers.

## 1. Locked Decisions

| Topic | Decision |
|---|---|
| Read projections | Enriched models: add display fields to `Book`, `BookCopy`, `Loan`, and `Member`; DAO read queries populate them |
| Book deletion | Reject deletion when the book has **any** loan history; `LoanDAO.existsByBookId` checks all loans |
| ISBN | Store as entered, trim, 13–20 characters, exact unique match; no ISBN-13 normalization or checksum |
| Language | UI labels, JSP text, Javadoc, comments, SQL comments, test comments, and docs in English |
| Seed book titles | Keep authentic language: Vietnamese titles stay Vietnamese, English titles stay English; do not translate titles |
| Error contract | `fieldErrors` (`Map<String, String>`) plus `globalErrors` (`List<String>`); keep `globalError` for fatal errors |
| Date source | Pass `LocalDate today` into date-sensitive DAO methods; do not use `CURRENT_DATE` in DAO SQL |
| Database reset | Reset both `library_db` and `library_test` with the updated schema and seed |
| Endpoints | Follow docs: `/books/new`, `/books/edit?id=`, `/members/new`, `/members/edit?id=` |

## 2. Current State and Evidence

### Implemented source

- 17 model, enum, and criteria files.
- 9 DAO interfaces and 9 JDBC DAO implementations, including `BookAuthorDAO` and `JdbcBookAuthorDAO`.
- Shared JDBC support: `JdbcSupport` and `AbstractNamedEntityDAO<T>`; shared pagination: `PageCriteria`.
- `DatabaseConnection`, `AppConfig`, `DataAccessException`.
- 12 DAO test classes plus `TestDatabase` and `TestFixtures`.
- 15 JSP views, the shared `pagination.tag`, `app.css`, and `app.js`.
- `schema.sql`, `seed.sql`, 6 docs, and 2 test-runner scripts.

### Verification evidence

- Main Java sources compiled with `javac --release 17 -encoding UTF-8`: 40 files, 41 class files (including `JdbcSupport$RowMapper`).
- Test sources compiled with `javac --release 17 -encoding UTF-8`: 14 files, 14 class files.
- Standalone JUnit Platform Console: 124 tests found, 124 successful, 0 failed, exit code 0 (39 original + 78 merged + 7 diacritic tests).
- Schema and seed applied to both `library_db` and `library_test`; 8 tables, 11 named indexes plus 3 inline unique-constraint indexes, and seed counts `3/4/4/4/8/3/3/4` verified.
- The `unaccent` extension is installed in both databases; `Chí Phèo` and `Chi Pheo` both return the same book through the DAO search.
- Seven rollback-safe constraint probes passed on both databases: category/email case-insensitive duplicates, blank email, ISBN 12/13/20, 15-day due date, and duplicate active loan.
- All 15 JSPs plus the shared `pagination.tag` compiled with Tomcat `JspC` and JSTL 3.0.1: 16 Java files, 16 class files, 0 errors, exit code 0.
- Static EL check: 320 expressions, 214 model property references, no missing property after replacing invalid `hasNext`/`hasPrevious` pagination properties with `first`/`last`.
- Workspace secret scan found no password assignment or secret-looking file.

### Not yet verified (Person 2)

- No Maven `test`, `package`, or WAR build.
- No JUnit/Surefire integration in a project build.
- No Tomcat WAR deployment or HTTP rendering.
- No Controller/Service/filter/transaction flow.
- Full Phase 2 gate remains **BLOCKED** until Person 2 provides the build and integration.

## 3. Work Breakdown

### Phase 1 — Schema and seed

#### 1.1 `src/main/resources/db/schema.sql`

- [ ] Remove the inline case-sensitive `UNIQUE` from `categories.name`.
- [ ] Add a case-insensitive unique index:
  ```sql
  CREATE UNIQUE INDEX ux_categories_name_lower ON categories (LOWER(name));
  ```
- [ ] Add an email blank guard:
  ```sql
  CONSTRAINT members_email_not_blank CHECK (email IS NULL OR btrim(email) <> '')
  ```
- [ ] Replace the non-unique email index with a partial unique index:
  ```sql
  CREATE UNIQUE INDEX ux_members_email_lower
      ON members (LOWER(email))
      WHERE email IS NOT NULL;
  ```
- [ ] Keep `books.isbn VARCHAR(20)` and add a length check:
  ```sql
  CONSTRAINT books_isbn_length CHECK (char_length(btrim(isbn)) BETWEEN 13 AND 20)
  ```
- [ ] Translate all SQL comments to English.
- [ ] Consider wrapping the script in `BEGIN; ... COMMIT;` for atomic application.

#### 1.2 `src/main/resources/db/seed.sql`

- [ ] Translate comments to English.
- [ ] Keep a mix of Vietnamese and English book titles; do not translate titles.
- [ ] Keep authentic author and publisher names where appropriate.
- [ ] Use relative loan dates so the demo always contains one overdue loan, one active loan, and one returned loan:
  ```sql
  -- loan_date = CURRENT_DATE - 24, due_date = CURRENT_DATE - 10
  -- loan_date = CURRENT_DATE - 3,  due_date = CURRENT_DATE + 11
  -- loan_date = CURRENT_DATE - 45, due_date = CURRENT_DATE - 31, return_date = CURRENT_DATE - 40
  ```
- [ ] Preserve seed counts: publishers 3, categories 4, authors 4, books 4, copies 8, members 3, loans 3, book_authors 4.
- [ ] Suggested mixed seed:
  - Vietnamese: `Tôi thấy hoa vàng trên cỏ xanh`, `Chí Phèo`
  - English: `Clean Code`, `Foundation`
  - Authors: `Nguyen Nhat Anh`, `Nam Cao`, `Robert C. Martin`, `Isaac Asimov`
  - Publishers: `Literature Publisher`, `Science and Technology Publisher`, `Technology Publisher`
  - Categories: `Novel`, `Science`, `History`, `Technology`
  - Members: `Alice Nguyen`, `Bob Tran`, `Charlie Le`
- [ ] Update `book_authors` mappings to match the new book and author order.

#### 1.3 Apply and verify

- [ ] Apply the updated `schema.sql` and `seed.sql` to `library_db`.
- [ ] Apply the updated `schema.sql` and `seed.sql` to `library_test`.
- [ ] Verify with read-only queries:
  ```sql
  SELECT indexname, indexdef
  FROM pg_indexes
  WHERE schemaname = 'public'
    AND tablename IN ('books', 'categories', 'members', 'loans')
  ORDER BY tablename, indexname;

  SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
  FROM pg_constraint
  WHERE connamespace = 'public'::regnamespace
  ORDER BY conrelid::regclass::text, conname;
  ```
- [ ] Verify duplicate category names differing only by case are rejected.
- [ ] Verify duplicate member emails differing only by case are rejected.
- [ ] Verify blank email is rejected or normalized before insert.
- [ ] Verify ISBN length 12 is rejected and 13/20 are accepted.
- [ ] Verify the 14-day check still rejects invalid due dates.

### Phase 2 — Models

- [ ] `Book.java`
  - Add `publisherName`, `categoryName`, `List<Author> authors`, and `Integer availableCopies`.
  - Add getters and setters.
  - Keep write operations independent of display fields.
  - Translate Javadoc to English.
- [ ] `BookCopy.java`
  - Add `bookTitle`.
  - Add getter and setter.
  - Map `createdAt` in DAO reads.
- [ ] `Loan.java`
  - Add `bookTitle`, `copyCode`, `memberName`, `boolean overdue`, and `long daysOverdue`.
  - Document that `overdue` and `daysOverdue` are computed by the Service layer.
- [ ] `Member.java`
  - Add `long activeLoanCount` for the member list.
  - Add getter and setter.
- [ ] Translate all model, enum, and criteria Javadoc/comments to English.
- [ ] Fix `LoanSearchCriteria` duplicate `java.time.LocalDate` import.
- [ ] Add `LocalDate today` to `LoanSearchCriteria`, default `LocalDate.now()`, with getter and setter.

### Phase 3 — DAO Interfaces

- [ ] `BookDAO`
  - Document that `findById` and `search` populate display fields.
  - Document that `insert` returns affected rows and sets the generated ID on the model.
  - Document that `search` applies every non-null criterion.
- [ ] `BookCopyDAO`
  - Change `updateStatus(Connection c, int copyId, String status)` to:
    ```java
    int updateStatus(Connection c, int copyId, CopyStatus status);
    ```
  - Add:
    ```java
    List<BookCopy> findAvailableWithBook(Connection c);
    ```
  - Document that `deleteByBookId` tolerates zero rows.
- [ ] `LoanDAO`
  - Document that `existsByBookId` checks any loan history.
  - Document that `search` and `findActive` populate display fields.
  - Document that `search` uses `criteria.getToday()` for `overdueOnly`.
- [ ] `DashboardDAO`
  - Change to:
    ```java
    DashboardStats getStats(Connection c, LocalDate today);
    ```
- [ ] New `BookAuthorDAO`
  ```java
  public interface BookAuthorDAO {
      List<Author> findByBookId(Connection c, int bookId);
      void replaceForBook(Connection c, int bookId, List<Integer> authorIds);
      void deleteByBookId(Connection c, int bookId);
  }
  ```
- [ ] `MemberDAO`
  - Document that `search` and `findAllActive` populate `activeLoanCount`.
- [ ] Translate all interface Javadoc to English.

### Phase 4 — JDBC DAOs

#### 4.1 `JdbcBookDAO`

- [ ] In `search`, call `appendSearchFilters(countSb, dataSb, criteria, params)` **before** `int filterParamCount = params.size()`.
- [ ] Ensure count query uses only filter parameters; data query uses filter parameters plus `LIMIT`/`OFFSET`.
- [ ] `findById` and `search` SQL: join publishers and categories, and add an available-copy subquery:
  ```sql
  SELECT b.book_id, b.isbn, b.title, b.publisher_id, b.category_id,
         b.publication_year, b.created_at,
         p.name AS publisher_name,
         c.name AS category_name,
         (SELECT COUNT(*) FROM book_copies bc
          WHERE bc.book_id = b.book_id AND bc.status = 'AVAILABLE') AS available_copies
  FROM books b
  JOIN publishers p ON p.publisher_id = b.publisher_id
  JOIN categories c ON c.category_id = b.category_id
  WHERE 1 = 1
  ```
- [ ] Fix `mapBook` null year:
  ```java
  int year = rs.getInt("publication_year");
  book.setPublicationYear(rs.wasNull() ? null : year);
  ```
- [ ] Map `createdAt`.
- [ ] Map `publisherName`, `categoryName`, `availableCopies`.
- [ ] Trim ISBN in `insert`, `update`, and `isbnExists`.
- [ ] Verify generated key after insert; throw `DataAccessException` if absent.
- [ ] Keep display fields ignored by `insert` and `update`.

#### 4.2 `JdbcBookCopyDAO`

- [ ] Change `updateStatus` to accept `CopyStatus` and bind `status.name()`.
- [ ] Remove the zero-row throw from `deleteByBookId`; deleting a book with no copies must succeed.
- [ ] Add `findAvailableWithBook`:
  ```sql
  SELECT bc.copy_id, bc.book_id, bc.copy_code, bc.status, bc.created_at,
         b.title AS book_title
  FROM book_copies bc
  JOIN books b ON b.book_id = bc.book_id
  WHERE bc.status = 'AVAILABLE'
  ORDER BY b.title, bc.copy_code
  ```
- [ ] Map `createdAt` and `bookTitle`.
- [ ] Verify generated key after insert.
- [ ] Remove unused imports.

#### 4.3 `JdbcLoanDAO`

- [ ] Change `EXISTS_BY_BOOK_ID` to check all loans:
  ```sql
  SELECT COUNT(*)
  FROM loans l
  JOIN book_copies bc ON bc.copy_id = l.copy_id
  WHERE bc.book_id = ?
  ```
- [ ] Update the error message from "active loan" to "loan history".
- [ ] Replace `CURRENT_DATE` in `appendFilters` with `criteria.getToday()`.
- [ ] Keep `countOverdue(Connection c, LocalDate today)` using the passed date.
- [ ] `findActive` and `search` data query: join `book_copies`, `books`, and `members` to populate `bookTitle`, `copyCode`, and `memberName`.
- [ ] Keep `findByIdForUpdate` and `findActiveByCopyId` on the base mapper; do not join tables in `FOR UPDATE` queries unless using `FOR UPDATE OF l`.
- [ ] Add `mapLoanWithDisplay` for joined reads.
- [ ] Map `createdAt`.
- [ ] Verify generated key after insert.
- [ ] Leave `overdue` and `daysOverdue` to the Service layer.

#### 4.4 `JdbcDashboardDAO`

- [ ] Change `getStats` to `getStats(Connection c, LocalDate today)`.
- [ ] Bind `today` to the overdue count:
  ```sql
  SELECT COUNT(*) FROM loans
  WHERE return_date IS NULL AND due_date < ?
  ```
- [ ] Keep the other five metrics unchanged.
- [ ] Translate Javadoc/comments to English.

#### 4.5 New `JdbcBookAuthorDAO`

- [ ] Implement `findByBookId`:
  ```sql
  SELECT a.author_id, a.name, a.created_at
  FROM authors a
  JOIN book_authors ba ON ba.author_id = a.author_id
  WHERE ba.book_id = ?
  ORDER BY a.name
  ```
- [ ] Implement `replaceForBook`:
  - Delete existing rows for the book.
  - Deduplicate `authorIds` with `LinkedHashSet`.
  - Batch insert with `PreparedStatement.addBatch`.
  - Do not commit, roll back, or close the caller's connection.
- [ ] Implement `deleteByBookId` and tolerate zero rows.
- [ ] Wrap `SQLException` in `DataAccessException`.
- [ ] Translate Javadoc/comments to English.

#### 4.6 `JdbcMemberDAO`

- [ ] Change `EMAIL_EXISTS` to:
  ```sql
  SELECT COUNT(*) FROM members
  WHERE LOWER(email) = LOWER(?)
    AND (? IS NULL OR member_id <> ?)
  ```
- [ ] Normalize blank email to `NULL` in `insert` and `update`.
- [ ] Make `emailExists` return `false` for null or blank input.
- [ ] Add active-loan count to `search` and `findAllActive`:
  ```sql
  (SELECT COUNT(*) FROM loans l
   WHERE l.member_id = m.member_id AND l.return_date IS NULL) AS active_loan_count
  ```
- [ ] Alias the members table as `m` in search SQL so the subquery is unambiguous.
- [ ] Map `createdAt` and `activeLoanCount`.
- [ ] Verify generated key after insert.

#### 4.7 `JdbcCategoryDAO`

- [ ] Change `NAME_EXISTS` to `LOWER(name) = LOWER(?)`.
- [ ] Map `createdAt`.
- [ ] Verify generated key after insert.

#### 4.8 `JdbcAuthorDAO` and `JdbcPublisherDAO`

- [ ] Map `createdAt`.
- [ ] Verify generated key after insert.
- [ ] Translate Javadoc/comments to English.

#### 4.9 All mappers

- [ ] Map `created_at` with:
  ```java
  rs.getObject("created_at", LocalDateTime.class)
  ```
- [ ] Guard nullable columns with local variables and `rs.wasNull()`.
- [ ] Use `Enum.valueOf` only for values guaranteed by database checks.

### Phase 5 — Tests

#### 5.1 `TestDatabase`

- [ ] Prefer `TEST_DB_URL`; fall back to `DB_URL`.
- [ ] Require the resolved URL to contain `_test`; otherwise throw `IllegalStateException`.
- [ ] Add `resetAll(Connection c)`:
  ```sql
  TRUNCATE book_authors, loans, book_copies, books,
           authors, publishers, categories, members
  RESTART IDENTITY CASCADE;
  ```
- [ ] Add `seedReferenceData(Connection c)` for publishers, categories, and authors.
- [ ] Do not use `DELETE` alone; hard-coded IDs must be stable.
- [ ] Translate Javadoc/comments to English.

#### 5.2 Test matrix

| Test class | Required cases |
|---|---|
| `JdbcBookDAOTest` | import `Optional`; insert/find; update; delete; ISBN unique; 13/20 length; trim; null publication year; `createdAt`; keyword filter with non-matching book; publisher filter; category filter; author filter; available-only filter; pagination with filters |
| `JdbcMemberDAOTest` | import `Optional`; insert/find; member-code unique; email exact unique; email case-insensitive unique; blank email normalized to null; active/inactive search; active-loan count |
| `JdbcLoanDAOTest` | reference data; generated IDs; borrow/return flow; valid 14-day due date; invalid due date rejected with SQLState `23514`; active loan uniqueness; `countActive`; `countOverdue(today)`; `existsByBookId` true after returned loan; joined display fields |
| `JdbcDashboardDAOTest` | reference data; generated IDs; two active loans on two different copies; expected books/members/copies/active/overdue/available; fixed `today` |
| `JdbcBookCopyDAOTest` | insert/find; `updateStatus(CopyStatus)`; `findAvailableWithBook`; `deleteByBookId` with zero copies; `deleteByBookId` with multiple copies; FK rejection when loans exist |
| `JdbcBookAuthorDAOTest` | replace authors; find by book; replace with empty list; delete by book; duplicate author IDs deduplicated |
| Optional reference DAO test | category case-insensitive name check; publisher/author `createdAt` mapping |

#### 5.3 Test data rules

- [ ] Use `resetAll` at the start of each test.
- [ ] Use generated IDs for books, copies, members, and loans.
- [ ] Use reference IDs only after `seedReferenceData`.
- [ ] Use `LocalDate` relative to the `today` passed to the DAO.
- [ ] Do not insert two active loans for the same copy.
- [ ] Do not use a due date other than `loanDate.plusDays(14)` for valid cases.
- [ ] Assert `DataAccessException.getSqlState()` for constraint violations.

#### 5.4 Interim test execution

- [ ] Download a JUnit 5 console standalone JAR outside the workspace.
- [ ] Compile main and test sources with `javac --release 17`.
- [ ] Run the console launcher against `library_test`.
- [ ] Use environment variables for the DB URL, user, and password; never echo the password.
- [ ] Record the exact command and result in the audit.
- [ ] Label the result as standalone JUnit evidence, not Maven evidence.

### Phase 6 — JSP and UI

#### 6.1 Global JSP fixes

- [ ] Replace every JSTL URI:
  ```jsp
  <%@ taglib prefix="c" uri="jakarta.tags.core" %>
  <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
  <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
  ```
- [ ] Add `pageEncoding="UTF-8"` to every JSP.
- [ ] Add missing taglibs where functions are used.
- [ ] Translate all JSP text, labels, buttons, placeholders, validation messages, and confirmation dialogs to English.
- [ ] Keep book titles in their original language.

#### 6.2 `common/head.jsp`

- [ ] Read `${param.pageTitle}` and `${param.currentPage}`.
- [ ] Set nav active state correctly:
  - `dashboard`
  - `books`
  - `members`
  - `loans`
  - `loans-history`
  - `loans-overdue`
- [ ] Display `globalErrors` with `fn:length`.
- [ ] Keep `flashSuccess`, `flashError`, and `globalError`.
- [ ] Escape the CSRF meta content with `c:out`.
- [ ] Translate all text to English.

#### 6.3 `common/foot.jsp`

- [ ] Add the core taglib for `c:url`.
- [ ] Translate footer text to English.

#### 6.4 `common/layout.jsp`

- [ ] Delete the file. It recursively imports `request.getRequestURI()` and is unused.
- [ ] Keep `head.jsp` and `foot.jsp` as the layout mechanism.

#### 6.5 Dashboard

- [ ] Translate all labels and descriptions to English.
- [ ] Keep the six metrics.

#### 6.6 Books

- [ ] `books/list.jsp`
  - Show `publisherName` and `categoryName`, not raw IDs.
  - Show `availableCopies`.
  - Preserve `keyword`, `publisherId`, `categoryId`, `authorName`, and `availableOnly` in pagination.
  - Build pagination URLs with `c:url` and `c:param` so values are encoded.
  - Hide pagination when `totalPages <= 1`.
  - Translate text to English.
- [ ] `books/form.jsp`
  - Create action: `/books/new`.
  - Update action: `/books/edit?id=${book.bookId}`.
  - Use `fieldErrors.isbn`, `fieldErrors.title`, etc.
  - Set ISBN `maxlength="20"` and a 13–20 character pattern.
  - Add author multi-select bound to `authorIds`.
  - Add `initialCopyCount` only for create.
  - Add hidden `_csrf`.
  - Translate text to English.
- [ ] `books/view.jsp`
  - Show `publisherName`, `categoryName`, and `authors`.
  - Show copies with lowercase status classes.
  - Do not add nonfunctional copy-action buttons. Add them only if Person 2 freezes copy endpoints.
  - Translate text to English.
- [ ] `books/delete.jsp`
  - Explain that a book with loan history cannot be deleted.
  - Keep the POST form and hidden `bookId`.
  - Add hidden `_csrf`.
  - Translate text to English.

#### 6.7 Members

- [ ] `members/list.jsp`
  - Show `activeLoanCount`.
  - Add a history link to `/loans/history?memberId=...`.
  - Use lowercase status classes.
  - Encode pagination parameters with `c:url` and `c:param`.
  - Translate text to English.
- [ ] `members/form.jsp`
  - Create action: `/members/new`.
  - Update action: `/members/edit?id=${member.memberId}`.
  - Use `fieldErrors.memberCode` and `fieldErrors.email`.
  - Add hidden `_csrf`.
  - Translate text to English.

#### 6.8 Loans

- [ ] `loans/borrow.jsp`
  - Show `availableCopies` with `bookTitle`.
  - Use `fieldErrors.memberId` and `fieldErrors.copyId`.
  - Add hidden `_csrf`.
  - Translate text to English.
- [ ] `loans/return.jsp`
  - Show `bookTitle`, `memberName`, and `copyCode` from `activeLoans`.
  - Replace `fmt:formatDate` on `LocalDate` with ISO `c:out` or a display getter.
  - Use `fieldErrors.returnDate`.
  - Add hidden `_csrf`.
  - Translate text to English.
- [ ] `loans/history.jsp`
  - Add filters for member, status, from date, and to date.
  - Use `fieldErrors` if validation is added.
  - Preserve filters in pagination.
  - Replace `fmt:formatDate` on `LocalDate`.
  - Use joined display fields and `overdue`.
  - Translate text to English.
- [ ] `loans/overdue.jsp`
  - Use `overduePage` or an equivalent paginated model.
  - Add pagination.
  - Show `bookTitle`, `copyCode`, `memberName`, `dueDate`, and `daysOverdue`.
  - Replace `fmt:formatDate` on `LocalDate`.
  - Translate text to English.

#### 6.9 Errors and assets

- [ ] `errors/404.jsp` and `errors/500.jsp`: English text and correct JSTL URIs.
- [ ] Add `.status-overdue` to `app.css`.
- [ ] Keep status classes lowercase; use `fn:toLowerCase` in JSPs.
- [ ] Translate CSS/JS comments to English if any remain.
- [ ] Keep vanilla JavaScript; no framework.
- [ ] Add CSRF hidden inputs in every POST form.

#### 6.10 JSP compilation

- [ ] Download JSTL 3.0.1 API and implementation JARs outside the workspace.
- [ ] Compile JSPs with Tomcat `JspC` and the JSTL JARs.
- [ ] Fix all compilation errors.
- [ ] Record the command and result.
- [ ] Do not claim HTTP or deployment success; Controllers are not present.

### Phase 7 — Language and Documentation

- [ ] Translate all Java Javadoc and comments to English.
- [ ] Translate all SQL comments to English.
- [ ] Translate all test comments to English.
- [ ] Translate all JSP comments and UI text to English.
- [ ] Fix `Ngoại ngữ` to `Data access exception`.
- [ ] Remove the Korean word `책임` from `DatabaseConnection`; use `responsibility`.
- [ ] Keep book titles in their original language.
- [ ] Update `docs/database-design.md`:
  - Case-insensitive unique category index.
  - Case-insensitive partial unique email index.
  - Email blank check.
  - ISBN length check.
  - Delete behavior.
- [ ] Update `docs/implementation-plan.md`:
  - Source tree includes `BookAuthorDAO` and `JdbcBookAuthorDAO`.
  - Enriched model fields.
  - `BookCopyDAO.updateStatus` uses `CopyStatus`.
  - Insert methods return affected rows and set generated IDs.
  - `DashboardDAO.getStats` takes `today`.
  - Error contract: `fieldErrors` plus `globalErrors`.
  - Endpoint paths.
  - Current state: source exists; build/deploy still blocked.
- [ ] Update `docs/runtime-toolchain-audit.md`:
  - Source, schema, seed, and tests exist.
  - Main `javac` evidence.
  - Standalone JUnit evidence.
  - JSP compile evidence.
  - Maven and deployment still not verified.
- [ ] Update `docs/java-concepts-mapping.md`:
  - Mark implemented concepts with source evidence.
  - Keep planned items clearly marked.
- [ ] Do not claim Phase 2 pass.

### Phase 8 — Verification and Handoff

#### 8.1 Person 1 verification

- [ ] Compile main sources with `javac --release 17`.
- [ ] Compile and run DAO tests with the standalone JUnit console.
- [ ] Compile JSPs with `JspC`.
- [ ] Verify schema indexes and constraints.
- [ ] Run a static EL property check: every `${book.*}`, `${copy.*}`, `${loan.*}`, and `${member.*}` property used in JSPs must exist on the model.
- [ ] Check that no password or secret appears in the workspace.
- [ ] Record exact evidence in the audit.

#### 8.2 Person 2 handoff

Person 2 must provide:

- [ ] `pom.xml` with JDK 17, Jakarta Servlet 6.0, JSP 3.1, JSTL 3.0.1, pgJDBC 42.7.14, JUnit 5, and Surefire.
- [ ] Maven Wrapper.
- [ ] `web.xml` and `context.xml`.
- [ ] Controllers and Services.
- [ ] Transaction boundaries for borrow, return, book create/update/delete, and member create/update.
- [ ] CSRF filter using `_csrf`.
- [ ] Request attributes:
  - `bookPage`, `memberPage`, `loanPage`, `overduePage`
  - `book`, `member`, `copies`
  - `publishers`, `categories`, `authors`, `activeMembers`, `availableCopies`
  - `criteria`
  - `fieldErrors`, `globalErrors`, `globalError`
  - `flashSuccess`, `flashError`
  - `csrfToken`
- [ ] Endpoint implementations:
  - `GET/POST /books/new`
  - `GET/POST /books/edit?id=`
  - `POST /books/delete`
  - `GET /books`
  - `GET /books/view?id=`
  - `GET/POST /members/new`
  - `GET/POST /members/edit?id=`
  - `GET /members`
  - `GET/POST /loans/borrow`
  - `GET/POST /loans/return`
  - `GET /loans/history`
  - `GET /loans/overdue`
  - `GET /dashboard`
- [ ] Run `mvn clean test`.
- [ ] Run `mvn clean package`.
- [ ] Deploy the WAR to Tomcat 10.1.61.
- [ ] Verify HTTP flows for all 12 functions.
- [ ] Update the Phase 2 gate only after the evidence exists.

## 4. Definition of Done

- [ ] Book search applies keyword, publisher, category, author, and available-only filters.
- [ ] Null `publicationYear` remains `null`; `createdAt` is populated.
- [ ] Deleting a book with zero copies succeeds.
- [ ] Deleting a book with any loan history is rejected with a clear message.
- [ ] ISBN is trimmed and accepted only at 13–20 characters.
- [ ] Category names and member emails are unique case-insensitively.
- [ ] Blank email is normalized to `NULL` or rejected by the check constraint.
- [ ] `BookAuthorDAO` persists and reads author links.
- [ ] Available copies and joined loan/member/book fields are populated for JSPs.
- [ ] All date-sensitive DAO methods use the caller-supplied `today`.
- [ ] DAO tests pass under the standalone JUnit runner.
- [ ] JSPs compile with JSTL 3.0.1.
- [ ] No JSP references a missing model property.
- [ ] UI, comments, SQL, tests, and docs are English.
- [ ] Seed book titles remain authentic Vietnamese/English.
- [ ] No Person 2 files are created.
- [ ] No secret is committed or printed.
- [ ] Maven, WAR, and HTTP claims remain blocked until Person 2 provides evidence.

## 5. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| JSTL 3.0.1 JARs are not in Tomcat `lib` | Person 2 must add JSTL dependencies to the WAR; Person 1 compiles JSPs with a temp JSTL classpath |
| No `pom.xml`, so Maven cannot run | Use standalone `javac` and JUnit console as interim evidence; do not claim Maven success |
| No Controllers/Services, so HTTP flows cannot run | Limit verification to DAO/JSP compile; hand off HTTP checks to Person 2 |
| Reset is destructive | User approved resetting both databases; back up any data that must be preserved |
| Test DB accidentally points to `library_db` | Require `_test` in the resolved JDBC URL |
| Fixed seed dates become stale | Use `CURRENT_DATE`-relative dates in seed |
| Enriched models blur entity/display concerns | Mark display fields as read-only projections; write SQL ignores them |
| Concurrent search/count mismatch | Person 2 may wrap search in a transaction if snapshot consistency is required |

## 6. Change Log

| Date | Change |
|---|---|
| 2026-10-25 | Created the standalone Person 1 implementation plan after the read-only review and decision freeze. |
| 2026-10-26 | Implemented the Person 1 scope and recorded standalone evidence: compilation, 39/39 DAO tests, `JspC`, EL checks, schema/seed, and constraint probes. Maven/WAR/HTTP remain Person 2 blockers. |
| 2026-10-27 | Merged the extended DAO suite, fixed eight test-side defects, added accent-insensitive search with `unaccent`, added 7 diacritic tests, and re-verified 124/124 tests plus `JspC` and 48 manual relation probes. |
| 2026-10-30 | Removed duplicated JDBC, criteria, test-fixture, JSP pagination, and dead JavaScript code; added `JdbcSupport`, `AbstractNamedEntityDAO`, `PageCriteria`, `TestFixtures`, and `pagination.tag`; re-verified 124/124 tests, 40 main/14 test files, and 16 `JspC` outputs. |
