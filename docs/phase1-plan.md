# Phase 1 — Implementation Plan and Code-Level Mapping

| Field | Value |
|---|---|
| Version | 2.0 |
| Date | 2026-10-31 |
| Status | Phase 1 plan extended with a verified code-level mapping. Person 1 layers are implemented; Person 2 controller, service, build, and HTTP layers are still missing. 0 of 12 functions are complete end to end. |
| Person 1 owner | PostgreSQL design, SQL/constraints, DAO/JDBC, models used by those layers, UI/UX |
| Person 2 owner | Controller/Servlet, Service/business logic, application integration, Maven/WAR/deployment |
| Technical baseline | Java 17, PostgreSQL 16.15, JDBC with handwritten SQL, MVC/3-tier, no ORM, no Spring Boot in controller/database layers |
| Source of truth | The current workspace; every path and method below was checked against it |
| Documentation scope | Documentation-only update. No application source code was modified. |

> **Document evolution note.** The original Phase 1 plan was written before implementation and described a Linux sandbox, JDK 21, PostgreSQL 17, and an unresolved Tomcat blocker. Those environment findings have been reconciled with the current Windows workspace. The architectural decisions, ERD shape, transaction rules, and the names/order of all 12 functions are preserved. Where the implemented schema uses a different label or stronger constraint than the original draft, the implemented source is recorded and the change is called out.

## 1. Workspace and Toolchain Findings

| Item | Verified current finding |
|---|---|
| Workspace root | `C:\Users\BINHND\Desktop\Library-Management-System-main\Library-Management-System-main\library-management-system` |
| OS | Windows; PowerShell is the primary command shell |
| Java | Temurin JDK 17.0.20.1+1; sources compile with `javac --release 17 -encoding UTF-8` |
| Maven | Apache Maven 3.9.16 is installed, but no `pom.xml` or Maven Wrapper exists in the repository |
| PostgreSQL | PostgreSQL 16.15, port `5432`; databases `library_db` and `library_test` are seeded |
| JDBC driver | `postgresql-42.7.14.jar` |
| Tomcat | Tomcat 10.1.61 installed; Jakarta Servlet 6.0/JSP 3.1/JSTL 3.0.1 |
| Test runner | JUnit Platform Console Standalone 1.14.4 via `scripts/run-tests.ps1` / `run-tests.sh`; no Surefire integration |
| Main Java source | 40 `.java` files under `src/main/java` |
| Test Java source | 14 `.java` files under `src/test/java` (12 test classes plus `TestDatabase` and `TestFixtures`) |
| UI source | 15 JSP files plus 1 shared tag under `src/main/webapp` |
| SQL source | 3 files: `schema.sql`, `seed.sql`, `manual-relation-test.sql` |
| Build/deployment descriptors | `pom.xml`, `web.xml`, and `context.xml` are **not present** |
| Controller/Service source | No `controller` or `service` package and no Servlet/Service class exists |
| DAO test result | 124/124 tests pass in the recorded 2026-10-31 standalone run, 15 containers, 0 failures |
| JSP compile result | Tomcat `JspC`: 16 generated Java/class files from 15 JSPs + 1 tag, 0 errors |
| Authentication/RBAC | Outside MVP scope; not implemented and not claimed |

The current repository is a Person 1 delivery: database, models, DAO/JDBC, tests, and JSP/CSS/JS are present. It is not yet a deployable web application because the Person 2 layers and build descriptors do not exist.

## 2. Confirmed Technical Decisions

The original Phase 1 decisions remain in force unless a status note says otherwise.

1. **Java: JDK 17 throughout development and deployment.** Maven compiler release is `17` when the Maven build is added.
2. **Build tool: Maven.** This is the agreed target. The build file is currently missing, so the existing evidence uses direct `javac` plus JUnit Console.
3. **Database: PostgreSQL.** The implemented schema targets PostgreSQL 16.
4. **Database access: plain JDBC with handwritten SQL through DAOs; no ORM.** No Hibernate, JPA, MyBatis, or MongoDB code exists.
5. **Architecture: strict three-tier MVC.** Presentation/JSP, Controller/Servlet, Service/business logic, DAO/JDBC, and PostgreSQL are separate responsibilities.
6. **Controller and database restrictions: no Spring Boot.** SQL lives only in DAO classes, never in JSPs or Servlets. No Spring Boot dependency or configuration exists.
7. **Default borrowing period: 14 calendar days** from the loan date. This is enforced by `loans_due_date_14_days CHECK (due_date = loan_date + 14)`.
8. **Transaction management: the Service layer owns the JDBC connection and transaction** for operations that change multiple related records. The DAO layer receives a caller-owned `Connection`. This rule is architectural: no Service implementation exists yet.
9. **Concurrency control: `SELECT ... FOR UPDATE` row locking** where appropriate. Locking DAO primitives exist; transaction orchestration is missing.
10. **Active-loan invariant: a PostgreSQL partial unique index on `loans(copy_id)` with predicate `WHERE return_date IS NULL`.** Implemented as `idx_loans_active_partial_unique`.
11. **Active-loan definition (project-wide):** a loan is active if and only if `return_date IS NULL`. Overdue is derived at query time from `due_date < today`; it is not stored. The implemented `loans.status` values are `ACTIVE` and `RETURNED`; the original draft's `BORROWED` label was superseded by `ACTIVE` in the schema. `BORROWED` remains a valid label only for `book_copies.status` (`CopyStatus`); it is never a loan status.
12. **Packaging: WAR via `maven-war-plugin`; PostgreSQL driver `org.postgresql:postgresql`.** The driver version verified in this workspace is `42.7.14`; the WAR/build configuration is planned but missing.
13. **Authentication is outside MVP scope.** It will be documented as a limitation, not claimed as implemented.
14. **Tomcat runtime decision: Tomcat 10.1.61 with `jakarta.servlet`, JSP 3.1, and JSTL 3.0.1.** No legacy `javax.servlet` import exists.

## 3. Tomcat Compatibility — Original Blocker Resolved

The original Phase 1 document correctly refused to guess between `javax.servlet` and `jakarta.servlet`. That blocker is now resolved by the verified runtime:

- Tomcat 10.1.61 is installed.
- All 15 JSPs and the shared `pagination.tag` compile with Tomcat `JspC` and JSTL 3.0.1.
- JSP taglib URIs are `jakarta.tags.core` and `jakarta.tags.functions`.
- No `java.sun.com` JSTL URI and no `javax.servlet` import remain.

This resolves the namespace question, but **does not** prove HTTP routing, request dispatch, or end-to-end behavior. There is still no `web.xml`, no Servlet class, no Service class, and no deployed WAR. Person 2's integration gate remains open.

## 4. Phase Gate Status

| Phase | Status | Evidence |
|---|---|---|
| 0. Runtime/toolchain | **IMPLEMENTED** | JDK 17, PostgreSQL 16.15, pgJDBC 42.7.14, Tomcat 10.1.61, JUnit 1.14.4 verified |
| 1. Inspection/plan | **IMPLEMENTED** | This document and the consolidated `docs/implementation-plan.md` |
| 2. Schema/contracts | **PARTIALLY IMPLEMENTED** | Schema, seed, models, DAO interfaces, and database docs exist; Person 2 service/endpoint contracts are missing |
| 3. DAO layer | **IMPLEMENTED** | JDBC DAOs and 124 passing DAO tests; no Maven/Surefire run |
| 4. UI + controllers | **PARTIALLY IMPLEMENTED** | 15 JSPs + tag compile; no Servlets/controllers wire them to DAOs |
| 5. Integration | **MISSING** | No deployed application, no HTTP flow, no end-to-end function verification |
| 6. Verification/docs | **PARTIALLY IMPLEMENTED** | Person 1 DB/DAO/UI evidence exists; Person 2 integration evidence is missing |

The original Phase 2 "do not write code" gate is obsolete because Person 1 implementation already occurred. The current gate is narrower: Person 2 must add the build, descriptors, controllers, services, and deployment before any function can be called complete.

## 5. ERD (Reconciled with `schema.sql`)

The original ERD shape is preserved: reference tables feed books, books have physical copies and authors, and loans connect copies to members.

```
publishers                       categories                       authors
+----------------------+         +----------------------+         +----------------------+
| publisher_id PK      |         | category_id PK       |         | author_id PK         |
| name NOT NULL        |         | name NOT NULL        |         | name NOT NULL        |
| created_at           |         | created_at           |         | created_at           |
+----------------------+         +----------------------+         +----------------------+
          1                              1                              1
          |                              |                              |
          N                              N                              N
       +---------------------------------------------------------------+
       |                            books                              |
       | book_id PK | isbn UNIQUE | title NOT NULL                      |
       | publisher_id FK -> publishers (RESTRICT)                      |
       | category_id  FK -> categories (RESTRICT)                      |
       | publication_year CHECK 1000..2100 | created_at                 |
       +---------------------------------------------------------------+
                 |                                      |
                 | 1                                    | 1
                 | N                                    | N
          book_authors                           book_copies
       +----------------------+               +----------------------+
       | book_id FK (CASCADE) |               | copy_id PK           |
       | author_id FK (RESTRICT)|              | book_id FK (RESTRICT)|
       | PK(book_id, author_id)|              | copy_code UNIQUE     |
       +----------------------+               | status CHECK         |
                                              | created_at           |
                                              +----------------------+
                                                         | 1
                                                         | N
                                                      loans
                                              +----------------------+
                                              | loan_id PK           |
                                              | copy_id FK (RESTRICT)|
                                              | member_id FK (RESTRICT)|
                                              | loan_date NOT NULL   |
                                              | due_date NOT NULL    |
                                              | return_date NULL     |
                                              | status CHECK         |
                                              | created_at           |
                                              +----------------------+
                                                         | N
                                                         | 1
                                                      members
                                              +----------------------+
                                              | member_id PK         |
                                              | member_code UNIQUE   |
                                              | full_name NOT NULL   |
                                              | email NULL           |
                                              | status CHECK         |
                                              | created_at           |
                                              +----------------------+
```

### 5.1 Implemented Database Invariants

- `books.isbn` is `NOT NULL UNIQUE`; `books_isbn_length` requires 13–20 characters after `btrim`.
- `books.publication_year` is nullable but must be between 1000 and 2100.
- `books.publisher_id` and `books.category_id` are required foreign keys with `ON DELETE RESTRICT`.
- `book_copies.copy_code` is `NOT NULL UNIQUE`; `status` is the **copy status** (distinct from `loans.status`) and is one of `AVAILABLE`, `BORROWED`, `MAINTENANCE`.
- `book_copies.book_id` is `ON DELETE RESTRICT`.
- `book_authors` has primary key `(book_id, author_id)`; deleting a book cascades its author links; deleting an author with links is restricted.
- `members.member_code` is `NOT NULL UNIQUE`; `members.status` is `ACTIVE` or `INACTIVE`.
- `members.email` is nullable; `ux_members_email_lower` is a partial unique index on `LOWER(email) WHERE email IS NOT NULL`; `members_email_not_blank` rejects blank strings.
- `loans.due_date = loan_date + 14` is enforced by `loans_due_date_14_days`.
- `loans.return_date` is either `NULL` or not earlier than `loan_date`.
- `loans.status` is `ACTIVE` or `RETURNED`; `loans_status_return_date_consistency` ties `ACTIVE` to `NULL` return date and `RETURNED` to a non-null return date.
- `idx_loans_active_partial_unique` is a partial unique index on `loans(copy_id) WHERE return_date IS NULL`; a copy cannot have two active loans.
- `idx_loans_active_due_partial` supports overdue scans for active loans.
- `categories` has a case-insensitive unique index `ux_categories_name_lower`. Authors and publishers currently have no unique-name constraint; the E09 uniqueness decision remains unresolved.

The original draft's `BORROWED` **loan** status and `due_date >= loan_date` check are superseded by the implemented `ACTIVE` status and exact `+14 days` rule (`BORROWED` remains valid only as a copy status). These are the source-of-truth constraints in `src/main/resources/db/schema.sql`.

## 6. Codebase Directory Reference

This is the actual current tree, not the original target tree. Files marked **MISSING** are required by the architecture but are absent.

```
library-management-system/
├── .gitignore
├── README.md
├── docs/
│   ├── database-design.md
│   ├── implementation-plan.md
│   ├── java-concepts-mapping.md
│   ├── person1-implementation-plan.md
│   ├── runtime-toolchain-audit.md
│   ├── test-suite-report.md
│   └── phase1-plan.md                         (this document)
├── scripts/
│   ├── run-tests.ps1
│   ├── run-tests.sh
│   └── with-postgres.ps1
├── src/main/java/com/library/
│   ├── config/
│   │   ├── AppConfig.java
│   │   └── DatabaseConnection.java
│   ├── dao/
│   │   ├── AuthorDAO.java
│   │   ├── BookAuthorDAO.java
│   │   ├── BookCopyDAO.java
│   │   ├── BookDAO.java
│   │   ├── CategoryDAO.java
│   │   ├── DashboardDAO.java
│   │   ├── LoanDAO.java
│   │   ├── MemberDAO.java
│   │   └── PublisherDAO.java
│   ├── dao/jdbc/
│   │   ├── AbstractNamedEntityDAO.java
│   │   ├── JdbcAuthorDAO.java
│   │   ├── JdbcBookAuthorDAO.java
│   │   ├── JdbcBookCopyDAO.java
│   │   ├── JdbcBookDAO.java
│   │   ├── JdbcCategoryDAO.java
│   │   ├── JdbcDashboardDAO.java
│   │   ├── JdbcLoanDAO.java
│   │   ├── JdbcMemberDAO.java
│   │   ├── JdbcPublisherDAO.java
│   │   └── JdbcSupport.java
│   ├── exception/
│   │   └── DataAccessException.java
│   ├── model/
│   │   ├── Author.java
│   │   ├── Book.java
│   │   ├── BookCopy.java
│   │   ├── Category.java
│   │   ├── DashboardStats.java
│   │   ├── Loan.java
│   │   ├── Member.java
│   │   ├── Page.java
│   │   ├── Publisher.java
│   │   ├── criteria/
│   │   │   ├── BookSearchCriteria.java
│   │   │   ├── LoanSearchCriteria.java
│   │   │   ├── MemberSearchCriteria.java
│   │   │   └── PageCriteria.java
│   │   └── enums/
│   │       ├── CopyStatus.java
│   │       ├── LoanState.java
│   │       ├── LoanStatus.java
│   │       └── MemberStatus.java
│   ├── controller/                            MISSING
│   └── service/                               MISSING
├── src/main/resources/db/
│   ├── schema.sql
│   └── seed.sql
├── src/main/webapp/
│   ├── assets/css/app.css
│   ├── assets/js/app.js
│   └── WEB-INF/
│       ├── tags/pagination.tag
│       └── views/
│           ├── common/head.jsp
│           ├── common/foot.jsp
│           ├── dashboard.jsp
│           ├── books/{form,list,view,delete}.jsp
│           ├── members/{form,list}.jsp
│           ├── loans/{borrow,return,history,overdue}.jsp
│           └── errors/{404,500}.jsp
├── src/test/java/com/library/dao/
│   ├── CrossTableSynchronizationTest.java
│   ├── DataConsistencyTest.java
│   ├── DiacriticSearchTest.java
│   ├── JdbcBookAuthorDAOTest.java
│   ├── JdbcBookCopyDAOTest.java
│   ├── JdbcBookDAOTest.java
│   ├── JdbcDashboardDAOTest.java
│   ├── JdbcLoanDAOTest.java
│   ├── JdbcMemberDAOTest.java
│   ├── JdbcReferenceDAOTest.java
│   ├── LoanBusinessLogicTest.java
│   ├── TransactionIsolationTest.java
│   └── support/
│       ├── TestDatabase.java
│       └── TestFixtures.java
├── src/test/resources/db/manual-relation-test.sql
├── pom.xml                                    MISSING
├── mvnw / mvnw.cmd                            MISSING
├── src/main/webapp/WEB-INF/web.xml            MISSING
└── src/main/webapp/META-INF/context.xml       MISSING
```

## 7. Transaction and Connection Design

The original Phase 1 transaction rules are preserved. Their implementation status must be read carefully.

### 7.1 Connection Factory

- `src/main/java/com/library/config/DatabaseConnection.java` contains the actual connection factory. There is **no class named `DBConnection`** in the source; the brief's shorthand `DBConnection` maps to `DatabaseConnection`.
- `DatabaseConnection.getConnection()` reads `DB_URL`, `DB_USER`, and `DB_PASSWORD`; it can read an external password from the file named by `DB_CONFIG`.
- Defaults are `jdbc:postgresql://127.0.0.1:5432/library_db` and user `library_app`; no password is stored in source.
- `DatabaseConnection.closeQuietly(Connection)` exists for cleanup. The caller owns the connection, commits, rollbacks, and close operations.

### 7.2 DAO Ownership Rules

- Every DAO method receives the caller's `Connection`; no DAO opens, commits, rolls back, or closes that connection.
- `JdbcSupport` centralizes `PreparedStatement`, `ResultSet`, parameter binding, generated-key retrieval, affected-row checks, and pagination. It does not own transactions.
- Locking primitives exist:
  - `JdbcBookCopyDAO.findByIdForUpdate(Connection, int)` issues `SELECT ... FROM book_copies WHERE copy_id = ? FOR UPDATE`.
  - `JdbcLoanDAO.findByIdForUpdate(Connection, int)` issues `SELECT ... FROM loans WHERE loan_id = ? FOR UPDATE`.
  - `JdbcLoanDAO.findActiveByCopyId(Connection, int)` issues `SELECT ... WHERE copy_id = ? AND return_date IS NULL FOR UPDATE`.
- No production code calls `setAutoCommit`, `commit`, or `rollback`; that responsibility belongs to the missing Service layer. Tests use these JDBC transaction APIs directly to verify rollback, isolation, and locking.

### 7.3 Planned Transaction Sequences

| Flow | Required Service sequence | DAO/DB support that exists |
|---|---|---|
| Add book | Open one connection; insert book; insert author links; insert initial copies; commit or roll back | `JdbcBookDAO.insert`, `JdbcBookAuthorDAO.replaceForBook`, `JdbcBookCopyDAO.insert`; schema/FKs |
| Update book | Open one connection; update book; replace author links; commit or roll back | `JdbcBookDAO.update`, `JdbcBookAuthorDAO.replaceForBook` |
| Delete book | Open one connection; reject if any loan history; delete author links, copies, book; commit or roll back | `JdbcLoanDAO.existsByBookId`, `JdbcBookAuthorDAO.deleteByBookId`, `JdbcBookCopyDAO.deleteByBookId`, `JdbcBookDAO.delete`; FK RESTRICT |
| Borrow | Begin; lock copy; validate member/status; verify no active loan; insert loan; set copy status `BORROWED`; commit/roll back | `JdbcBookCopyDAO.findByIdForUpdate`, `JdbcMemberDAO.findById`, `JdbcLoanDAO.findActiveByCopyId`, `JdbcLoanDAO.insert`, `JdbcBookCopyDAO.updateStatus`; partial unique index |
| Return | Begin; lock loan; reject returned/missing; lock copy; set return date and loan status `RETURNED`; set copy status `AVAILABLE`; commit/roll back | `JdbcLoanDAO.findByIdForUpdate`, `JdbcLoanDAO.markReturned`, `JdbcBookCopyDAO.findByIdForUpdate`, `JdbcBookCopyDAO.updateStatus`; CHECK constraints |

## 8. Existing Function-to-Layer Mapping (Preserved and Extended)

The 12 function names and their original order are preserved. Status values use only the four definitions from the brief.

### 8.1 Twelve-Function Overview

| # | Function | UI page (actual) | Controller | Service | DAO methods (actual) | Model | Database | Overall status |
|---|---|---|---|---|---|---|---|---|
| 1 | Add Book | `books/form.jsp` (create) | **MISSING** | **MISSING** | `JdbcBookDAO.insert`, `JdbcBookAuthorDAO.replaceForBook`, `JdbcBookCopyDAO.insert`, `JdbcBookDAO.isbnExists` | `Book`, `BookCopy`, `Author` | `books`, `book_authors`, `book_copies` | PARTIALLY IMPLEMENTED |
| 2 | Update Book | `books/form.jsp` (edit) | **MISSING** | **MISSING** | `JdbcBookDAO.findById`, `update`, `isbnExists`, `JdbcBookAuthorDAO.findByBookId`, `replaceForBook`, `JdbcBookCopyDAO.findByBookId` | `Book`, `Author`, `BookCopy` | `books`, `book_authors`, `book_copies` | PARTIALLY IMPLEMENTED |
| 3 | Delete Book | `books/delete.jsp`, `books/list.jsp` | **MISSING** | **MISSING** | `JdbcBookDAO.findById`, `delete`, `JdbcLoanDAO.existsByBookId`, `JdbcBookAuthorDAO.deleteByBookId`, `JdbcBookCopyDAO.deleteByBookId` | `Book`, `BookCopy` | `books`, `book_authors`, `book_copies`, `loans` | PARTIALLY IMPLEMENTED |
| 4 | Search Books | `books/list.jsp` | **MISSING** | **MISSING** | `JdbcBookDAO.search` | `BookSearchCriteria`, `Page<Book>`, `Book` | `books`, `publishers`, `categories`, `book_authors`, `authors`, `book_copies` | PARTIALLY IMPLEMENTED |
| 5 | View Book Details | `books/view.jsp` | **MISSING** | **MISSING** | `JdbcBookDAO.findById`, `JdbcBookAuthorDAO.findByBookId`, `JdbcBookCopyDAO.findByBookId` | `Book`, `Author`, `BookCopy` | `books`, `book_authors`, `authors`, `book_copies` | PARTIALLY IMPLEMENTED |
| 6 | Add Member | `members/form.jsp` (create) | **MISSING** | **MISSING** | `JdbcMemberDAO.memberCodeExists`, `emailExists`, `insert` | `Member`, `MemberStatus` | `members` | PARTIALLY IMPLEMENTED |
| 7 | Update Member | `members/form.jsp` (edit) | **MISSING** | **MISSING** | `JdbcMemberDAO.findById`, `update`, `memberCodeExists`, `emailExists` | `Member`, `MemberStatus` | `members` | PARTIALLY IMPLEMENTED |
| 8 | Search Members | `members/list.jsp` | **MISSING** | **MISSING** | `JdbcMemberDAO.search` | `MemberSearchCriteria`, `Page<Member>`, `Member` | `members`, `loans` | PARTIALLY IMPLEMENTED |
| 9 | Borrow Book | `loans/borrow.jsp` | **MISSING** | **MISSING** | `JdbcBookCopyDAO.findByIdForUpdate`, `JdbcMemberDAO.findById`, `JdbcLoanDAO.findActiveByCopyId`, `insert`, `JdbcBookCopyDAO.updateStatus`; dropdowns use `JdbcMemberDAO.findAllActive`, `JdbcBookCopyDAO.findAvailableWithBook` | `Loan`, `BookCopy`, `Member`, `CopyStatus`, `LoanStatus` | `loans`, `book_copies`, `members` | PARTIALLY IMPLEMENTED |
| 10 | Return Book | `loans/return.jsp` | **MISSING** | **MISSING** | `JdbcLoanDAO.findActive`, `findByIdForUpdate`, `markReturned`; `JdbcBookCopyDAO.findByIdForUpdate`, `updateStatus` | `Loan`, `BookCopy`, `LoanStatus`, `CopyStatus` | `loans`, `book_copies` | PARTIALLY IMPLEMENTED |
| 11 | Borrowing History | `loans/history.jsp` | **MISSING** | **MISSING** | `JdbcLoanDAO.search`; member filter uses `JdbcMemberDAO.findAllActive` | `LoanSearchCriteria`, `Page<Loan>`, `Loan` | `loans`, `book_copies`, `books`, `members` | PARTIALLY IMPLEMENTED |
| 12 | Overdue Books | `loans/overdue.jsp` | **MISSING** | **MISSING** | `JdbcLoanDAO.search` with `overdueOnly=true`; `JdbcLoanDAO.countOverdue` exists for dashboard | `LoanSearchCriteria`, `Page<Loan>`, `Loan` | `loans`, `book_copies`, `books`, `members` | PARTIALLY IMPLEMENTED |

### 8.2 Detailed Function Mapping

The UI rows below are **IMPLEMENTED** as JSP markup, but none is wired to a Servlet. The Controller and Service rows are **MISSING**, so every function is **PARTIALLY IMPLEMENTED** overall even where the UI, DAO, model, and database layers are complete.

In every data-flow block, the top-to-bottom `->` path is **UI → Controller → Service → DAO → PostgreSQL**; `<-` lines show the return path from the database/DAO back to the view.

Path convention for the detailed tables: DAO implementation names resolve to `src/main/java/com/library/dao/jdbc/<ClassName>.java`; core models resolve to `src/main/java/com/library/model/<ClassName>.java`; criteria resolve to `src/main/java/com/library/model/criteria/<ClassName>.java`; enums resolve to `src/main/java/com/library/model/enums/<ClassName>.java`. Every UI row gives its full JSP path. Controller and Service classes have no path because no such file exists. Full class-level paths are listed in §9.

#### 8.2.1 1. Add Book

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/books/form.jsp`, create mode (`empty book.bookId`), form action `/books/new`, fields `isbn`, `title`, `publisherId`, `categoryId`, `publicationYear`, `authorIds`, `initialCopyCount`, `_csrf`; entry link in `src/main/webapp/WEB-INF/views/books/list.jsp` | IMPLEMENTED |
| Controller | No Servlet/controller class or package exists. Planned endpoint from `docs/implementation-plan.md` §9: `GET/POST /books/new`; planned method name `createBook` (not present in Java source). | MISSING |
| Service | No service class or package exists. Planned transaction: validate; insert book; replace author links; insert initial copies; commit or roll back. | MISSING |
| DAO | `src/main/java/com/library/dao/jdbc/JdbcBookDAO.java` — `insert(Connection, Book)` sets the generated `bookId`; `isbnExists(Connection, String, Integer)`; `src/main/java/com/library/dao/jdbc/JdbcBookAuthorDAO.java` — `replaceForBook(Connection, int, List<Integer>)`; `src/main/java/com/library/dao/jdbc/JdbcBookCopyDAO.java` — `insert(Connection, BookCopy)` | IMPLEMENTED |
| Model | `src/main/java/com/library/model/Book.java`, `Author.java`, `BookCopy.java`; `Book` carries the display list `authors` and generated `bookId`; `BookCopy` carries `bookId`, `copyCode`, `CopyStatus` | IMPLEMENTED |
| Database | `books` (`book_id`, `isbn`, `title`, `publisher_id`, `category_id`, `publication_year`, `created_at`); `book_authors` (`book_id`, `author_id`); `book_copies` (`copy_id`, `book_id`, `copy_code`, `status`, `created_at`). Constraints: ISBN unique/length 13–20, publication year 1000–2100, two RESTRICT FKs on `books`, unique copy code, status CHECK, `book_authors` PK. | IMPLEMENTED |

**Data flow**

```
books/form.jsp (POST /books/new)
  -> Controller: MISSING
  -> Service: MISSING (planned transaction)
  -> JdbcBookDAO.insert(Connection, Book)              -> books (generated book_id written to Book)
  -> JdbcBookAuthorDAO.replaceForBook(Connection, id, authorIds)
                                                       -> book_authors
  -> JdbcBookCopyDAO.insert(Connection, BookCopy)      -> book_copies (one per initial copy)
  <- int affected rows / generated IDs
  <- Service: MISSING (commit or rollback)
  <- Controller: MISSING (redirect to /books/view?id=...)
  <- books/view.jsp (target)
```

The DAO primitives are exercised directly by tests today. There is no request path from the JSP to these DAOs.

#### 8.2.2 2. Update Book

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/books/form.jsp`, edit mode (`book.bookId` present), form action `/books/edit?id=${book.bookId}`; same metadata fields except `initialCopyCount`; edit links in `books/list.jsp` and `books/view.jsp` | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET/POST /books/edit?id=`; planned method `updateBook` in `docs/implementation-plan.md` §9 (not present in source). | MISSING |
| Service | No service. Planned transaction: validate; update book; replace authors; commit/roll back. | MISSING |
| DAO | `JdbcBookDAO.findById(Connection, int)`, `update(Connection, Book)`, `isbnExists(...)`; `JdbcBookAuthorDAO.findByBookId(Connection, int)`, `replaceForBook(...)`; `JdbcBookCopyDAO.findByBookId(Connection, int)` | IMPLEMENTED |
| Model | `Book` (editable fields plus `publisherName`, `categoryName`, `availableCopies`), `Author`, `BookCopy` | IMPLEMENTED |
| Database | `books` update columns; `book_authors` replacement via delete + insert; `book_copies` read-only for this form. Constraints: ISBN unique unless excluding self, FKs RESTRICT, year CHECK. | IMPLEMENTED |

**Data flow**

```
GET /books/edit?id=
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcBookDAO.findById -> books + publisher/category/available-copy projections
  -> JdbcBookAuthorDAO.findByBookId -> authors
  -> JdbcBookCopyDAO.findByBookId -> copies
  <- Book / List<Author> / List<BookCopy>
  <- Controller: MISSING (request attributes book, authorIds, publishers, categories, authors)
  <- books/form.jsp

POST /books/edit?id=
  -> Controller: MISSING -> Service: MISSING (transaction)
  -> JdbcBookDAO.update -> books
  -> JdbcBookAuthorDAO.replaceForBook -> book_authors
  <- affected rows
  <- Service: MISSING (commit/rollback) -> Controller: MISSING (redirect to detail)
```

#### 8.2.3 3. Delete Book

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/books/delete.jsp` confirmation page with hidden `bookId` and `_csrf`; delete links in `books/list.jsp` and `books/view.jsp` use `data-confirm`; copy count displayed from `copyCount` | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `POST /books/delete`; planned method `deleteBook` (from `docs/implementation-plan.md` §9; not present). | MISSING |
| Service | No service. Planned rule: reject when `JdbcLoanDAO.existsByBookId` is true; otherwise delete author links, copies, and book in one transaction. | MISSING |
| DAO | `JdbcBookDAO.findById(Connection, int)`, `delete(Connection, int)`; `JdbcLoanDAO.existsByBookId(Connection, int)` checks all history including returned loans; `JdbcBookAuthorDAO.deleteByBookId(Connection, int)`; `JdbcBookCopyDAO.deleteByBookId(Connection, int)` | IMPLEMENTED |
| Model | `Book`, `BookCopy`; `Loan` supplies the history existence rule through the DAO query | IMPLEMENTED |
| Database | `books`, `book_authors` (`ON DELETE CASCADE` from book), `book_copies` (book FK RESTRICT), `loans` (copy/member FKs RESTRICT). Any loan history blocks copy deletion; deletion order must remove author links and copies before the book. | IMPLEMENTED |

**Data flow**

```
GET /books/delete?id=
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcBookDAO.findById + JdbcBookCopyDAO.findByBookId
  <- Book + copy count -> books/delete.jsp

POST /books/delete (bookId, _csrf)
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcLoanDAO.existsByBookId -> loans (all history)
  -> if false: JdbcBookAuthorDAO.deleteByBookId -> book_authors
               JdbcBookCopyDAO.deleteByBookId -> book_copies
               JdbcBookDAO.delete -> books
  <- affected rows / DataAccessException(SQLState)
  <- Service: MISSING (commit/rollback) -> Controller: MISSING (redirect /books)
```

The FK RESTRICT constraints are the database backstop; the service-level rejection rule is not implemented.

#### 8.2.4 4. Search Books

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/books/list.jsp`: GET form with `keyword`, `publisherId`, `categoryId`, `authorName`, `availableOnly`; results table; `ui:pagination` with `bookPage` and base URL `/books` supplies the `page` parameter | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET /books`; planned method `searchBooks` (not present). | MISSING |
| Service | No service. Planned: build `BookSearchCriteria`, validate/clamp pagination, load filter reference data, call DAO. | MISSING |
| DAO | `src/main/java/com/library/dao/jdbc/JdbcBookDAO.java` — `search(Connection, BookSearchCriteria)` builds count and data queries, applies every non-null filter, orders by `book_id`, applies `LIMIT/OFFSET`, and returns `Page<Book>` via `JdbcSupport.search` | IMPLEMENTED |
| Model | `BookSearchCriteria` (keyword, publisherId, categoryId, authorName, availableOnly, inherited page/size), `Page<Book>`, `Book` | IMPLEMENTED |
| Database | `books`, `publishers`, `categories`, `book_authors`, `authors`, `book_copies`. Filters use `unaccent(...) ILIKE unaccent(?)`, ISBN ILIKE, publisher/category equality, author EXISTS, available-copy EXISTS. Indexes: `idx_books_title_lower`, `idx_books_publisher_id`, `idx_books_category_id`, `idx_book_copies_book_id_status`. | IMPLEMENTED |

**Data flow**

```
books/list.jsp (GET /books?keyword=&publisherId=&categoryId=&authorName=&availableOnly=&page=)
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcBookDAO.search(Connection, BookSearchCriteria)
       -> count query on books (+ filters)      -> total
       -> data query with LIMIT/OFFSET + joins  -> List<Book>
  <- Page<Book> (content, totalElements, totalPages, page, size)
  <- Controller: MISSING (request attribute bookPage, publishers, categories)
  <- books/list.jsp + pagination.tag
```

`JdbcSupport.likePattern` escapes `\`, `%`, and `_` before binding, so wildcard characters in user input are treated literally.

#### 8.2.5 5. View Book Details

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/books/view.jsp`: book metadata, authors, available copy count, and copies table; edit/delete/back links | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET /books/view?id=`; planned method `getBookById` (not present). | MISSING |
| Service | No service. Planned: load book or report not found; load authors and copies; attach authors to the `Book` and expose `book` plus `copies` as request attributes. | MISSING |
| DAO | `JdbcBookDAO.findById(Connection, int)`; `JdbcBookAuthorDAO.findByBookId(Connection, int)`; `JdbcBookCopyDAO.findByBookId(Connection, int)` | IMPLEMENTED |
| Model | `Book` (including `publisherName`, `categoryName`, `availableCopies`, `authors`), `Author`, `BookCopy` | IMPLEMENTED |
| Database | `books` joined to `publishers` and `categories`; available-copy subquery on `book_copies`; `book_authors` + `authors`; all copies by book. | IMPLEMENTED |

**Data flow**

```
books/view.jsp (GET /books/view?id=)
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcBookDAO.findById -> Optional<Book> (+ publisherName, categoryName, availableCopies)
  -> JdbcBookAuthorDAO.findByBookId -> List<Author>
  -> JdbcBookCopyDAO.findByBookId -> List<BookCopy>
  <- Optional<Book>, List<Author>, List<BookCopy>
  <- Service: MISSING (attaches authors to book.authors; no separate authors attribute)
  <- Controller: MISSING (attributes book, copies; 404 when empty)
  <- books/view.jsp (reads book.authors and copies)
```

**Label note.** `${authors}` on `books/form.jsp` is the full author list used to populate the multi-select; it is not detail-page data. `books/view.jsp` reads only `book.authors`, which the planned service must populate with `Book.setAuthors(...)`; there is no separate `authors` request attribute on the detail page.

#### 8.2.6 6. Add Member

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/members/form.jsp`, create mode (`empty member.memberId`), form action `/members/new`, fields `memberCode`, `fullName`, `email`, `status`, `_csrf`; entry link in `members/list.jsp` | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET/POST /members/new`; planned method `createMember` (not present). | MISSING |
| Service | No service. Planned registration validation, duplicate handling, and insert. | MISSING |
| DAO | `JdbcMemberDAO.memberCodeExists(Connection, String, Integer)`, `emailExists(Connection, String, Integer)`, `insert(Connection, Member)` (sets generated ID) | IMPLEMENTED |
| Model | `Member`, `MemberStatus`; `Member` carries generated `memberId` and read-only `activeLoanCount` | IMPLEMENTED |
| Database | `members`: `member_id` PK, `member_code` unique, `full_name` NOT NULL, nullable `email` with lower-case partial unique index, `status` CHECK, `created_at`; blank email rejected by CHECK. | IMPLEMENTED |

**Data flow**

```
members/form.jsp (POST /members/new)
  -> Controller: MISSING
  -> Service: MISSING
  -> JdbcMemberDAO.memberCodeExists / emailExists -> members
  -> JdbcMemberDAO.insert -> members (generated member_id written to Member)
  <- affected rows / generated ID / DataAccessException
  <- Service: MISSING -> Controller: MISSING (redirect /members)
  <- members/list.jsp
```

`JdbcMemberDAO` normalizes blank email to `NULL` before insert/update; the database partial unique index still prevents duplicate non-null emails case-insensitively.

#### 8.2.7 7. Update Member

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/members/form.jsp`, edit mode, form action `/members/edit?id=${member.memberId}`; edit link in `members/list.jsp` | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET/POST /members/edit?id=`; planned method `updateMember` (not present). | MISSING |
| Service | No service. Planned rule: validate uniqueness and prevent changing to `INACTIVE` while the member has active loans. | MISSING |
| DAO | `JdbcMemberDAO.findById(Connection, int)` (includes `active_loan_count`), `update(Connection, Member)`, `memberCodeExists(...)`, `emailExists(...)` | IMPLEMENTED |
| Model | `Member` (including `activeLoanCount`), `MemberStatus` | IMPLEMENTED |
| Database | `members` update columns; `loans.return_date IS NULL` is the active-loan predicate used by the `active_loan_count` subquery. Member uniqueness/status checks remain authoritative. | IMPLEMENTED |

**Data flow**

```
GET /members/edit?id=
  -> Controller: MISSING -> Service: MISSING
  -> JdbcMemberDAO.findById -> Optional<Member> (+ activeLoanCount)
  <- Member -> Controller: MISSING -> members/form.jsp

POST /members/edit?id=
  -> Controller: MISSING -> Service: MISSING
  -> JdbcMemberDAO.memberCodeExists / emailExists (exclude self)
  -> JdbcMemberDAO.update -> members
  <- affected rows / DataAccessException
  <- Service: MISSING (inactive-with-active-loans rule, commit/rollback)
  <- Controller: MISSING (redirect /members)
```

The inactive-with-active-loans rule is documented in the architecture but has no production implementation. `JdbcMemberDAO.findById` exposes the active-loan count that the missing service can use.

#### 8.2.8 8. Search Members

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/members/list.jsp`: GET form with `keyword` and `status`; table with member code, name, email, status, active-loan count, edit/history links; `ui:pagination` with `memberPage` supplies the `page` parameter | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET /members`; planned method `searchMembers` (not present). | MISSING |
| Service | No service. Planned: build `MemberSearchCriteria`, validate pagination, call DAO. | MISSING |
| DAO | `JdbcMemberDAO.search(Connection, MemberSearchCriteria)`: count + data queries; keyword matches `full_name` (unaccent/ILIKE) or `member_code` (ILIKE); status equality; page/size; returns `Page<Member>` with `activeLoanCount` | IMPLEMENTED |
| Model | `MemberSearchCriteria`, `Page<Member>`, `Member` | IMPLEMENTED |
| Database | `members` plus correlated `loans` subquery for `active_loan_count`; indexes `idx_members_full_name_lower` and unique member code. | IMPLEMENTED |

**Data flow**

```
members/list.jsp (GET /members?keyword=&status=&page=)
  -> Controller: MISSING -> Service: MISSING
  -> JdbcMemberDAO.search(Connection, MemberSearchCriteria)
       -> count query -> total
       -> data query + active-loan subquery + LIMIT/OFFSET -> List<Member>
  <- Page<Member>
  <- Controller: MISSING -> members/list.jsp + pagination.tag
```

#### 8.2.9 9. Borrow Book

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/loans/borrow.jsp`: POST form to `/loans/borrow`, fields `memberId`, `copyId`, `loanDate`, `_csrf`; dropdowns use `activeMembers` and `availableCopies`; due date is read-only display text | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `POST /loans/borrow` from `docs/implementation-plan.md` §9; planned method `borrowBook` (not present). | MISSING |
| Service | No service. Planned transaction: begin; lock copy; validate copy/member; verify no active loan; compute `loanDate + 14`; insert loan; set copy status `BORROWED` while `loans.status` stays `ACTIVE`; commit/roll back; map SQLState 23505. | MISSING |
| DAO | `JdbcBookCopyDAO.findByIdForUpdate(Connection, int)`; `JdbcMemberDAO.findById(Connection, int)`; `JdbcMemberDAO.findAllActive(Connection)`; `JdbcBookCopyDAO.findAvailableWithBook(Connection)`; `JdbcLoanDAO.findActiveByCopyId(Connection, int)`; `JdbcLoanDAO.insert(Connection, Loan)`; `JdbcBookCopyDAO.updateStatus(Connection, int, CopyStatus)` | IMPLEMENTED |
| Model | `Loan`, `BookCopy`, `Member`, `CopyStatus`, `LoanStatus`; `Loan.overdue`/`daysOverdue` are derived display fields with no database column — the service must set them before rendering | IMPLEMENTED |
| Database | `loans` (`copy_id`, `member_id`, `loan_date`, `due_date`, `return_date`, `status`); `book_copies` (`status`); `members` (`status`). Constraints: exact 14-day due date, status/return-date consistency, FKs RESTRICT, partial unique active loan per copy, active-due partial index. | IMPLEMENTED |

**Data flow**

```
loans/borrow.jsp (GET: activeMembers + availableCopies)
  -> Controller: MISSING -> Service: MISSING
  -> JdbcMemberDAO.findAllActive -> members WHERE status='ACTIVE'
  -> JdbcBookCopyDAO.findAvailableWithBook -> available copies + book title
  <- lists -> Controller: MISSING -> loans/borrow.jsp

loans/borrow.jsp (POST memberId, copyId, loanDate)
  -> Controller: MISSING
  -> Service: MISSING (transaction owner)
       -> JdbcBookCopyDAO.findByIdForUpdate(copyId)      -> lock book_copies row
       -> JdbcMemberDAO.findById(memberId)               -> validate ACTIVE member
       -> JdbcLoanDAO.findActiveByCopyId(copyId)         -> reject duplicate active loan
       -> compute dueDate = loanDate + 14
       -> JdbcLoanDAO.insert(loan)                       -> loans (generated loan_id)
       -> JdbcBookCopyDAO.updateStatus(copyId, BORROWED) -> book_copies.status only
                                                          (copy is BORROWED; loans.status stays ACTIVE)
       -> commit / rollback
  <- Loan / DataAccessException (23505, 23514, 23503)
  <- Controller: MISSING (PRG redirect, field/global errors)
```

No production transaction sequence exists yet. The locking and mutation primitives are individually tested, including concurrent duplicate-active-loan behavior.

#### 8.2.10 10. Return Book

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/loans/return.jsp`: POST to `/loans/return`, `loanId`, `returnDate`, `_csrf`; active-loan dropdown from `activeLoans`; empty state when none | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `POST /loans/return`; planned method `returnBook` (not present). | MISSING |
| Service | No service. Planned transaction: begin; lock loan; reject missing/already returned; lock copy; mark returned; set copy `AVAILABLE`; commit/roll back. | MISSING |
| DAO | `JdbcLoanDAO.findActive(Connection)`; `JdbcLoanDAO.findByIdForUpdate(Connection, int)`; `JdbcLoanDAO.markReturned(Connection, int, LocalDate)`; `JdbcBookCopyDAO.findByIdForUpdate(Connection, int)`; `JdbcBookCopyDAO.updateStatus(Connection, int, CopyStatus)` | IMPLEMENTED |
| Model | `Loan`, `BookCopy`, `LoanStatus`, `CopyStatus`; UI reads `loan.bookTitle`, `memberName`, `copyCode`, `dueDate`, `overdue` | IMPLEMENTED |
| Database | `loans` return fields and status; `book_copies.status`; constraints `loans_return_date_check`, `loans_status_return_date_consistency`, partial unique active-loan index. | IMPLEMENTED |

**Data flow**

```
loans/return.jsp (GET)
  -> Controller: MISSING -> Service: MISSING
  -> JdbcLoanDAO.findActive -> active loans with book/copy/member display fields
  <- List<Loan> -> Controller: MISSING -> loans/return.jsp

POST /loans/return (loanId, returnDate)
  -> Controller: MISSING
  -> Service: MISSING (transaction owner)
       -> JdbcLoanDAO.findByIdForUpdate(loanId)        -> lock loans row
       -> reject missing or already returned
       -> JdbcBookCopyDAO.findByIdForUpdate(copyId)    -> lock copy
       -> JdbcLoanDAO.markReturned(loanId, returnDate) -> loans (0 rows means already returned)
       -> JdbcBookCopyDAO.updateStatus(copyId, AVAILABLE)
       -> commit / rollback
  <- affected rows / DataAccessException
  <- Controller: MISSING (redirect /loans/history or /loans/return)
```

`JdbcLoanDAO.markReturned` only updates `WHERE return_date IS NULL`; a repeated return yields 0 affected rows. The missing service must translate that to a friendly rule violation.

#### 8.2.11 11. Borrowing History

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/loans/history.jsp`: GET form with `memberId`, `status`, `fromDate`, `toDate`; table shows book, copy, member, dates, returned/overdue/active badges; `ui:pagination` with `loanPage` supplies the `page` parameter | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET /loans/history`; planned method `getBorrowingHistory` (not present). | MISSING |
| Service | No service. Planned: build `LoanSearchCriteria`, set `today`, call DAO, compute `overdue`/`daysOverdue`, provide active members for the filter. | MISSING |
| DAO | `JdbcLoanDAO.search(Connection, LoanSearchCriteria)`: filters memberId, copyId, bookId, status, fromDate/toDate, overdueOnly; joins `book_copies`, `books`, `members`; orders by `loan_date DESC, loan_id`; applies pagination; returns `Page<Loan>` | IMPLEMENTED |
| Model | `LoanSearchCriteria`, `Page<Loan>`, `Loan`; `Loan.overdue` and `daysOverdue` are not populated by `JdbcLoanDAO` | IMPLEMENTED |
| Database | `loans` + `book_copies` + `books` + `members` joins; indexes `idx_loans_member_id_loan_date`, `idx_loans_copy_id`, active-due partial index. | IMPLEMENTED |

**Data flow**

```
loans/history.jsp (GET /loans/history?memberId=&status=&fromDate=&toDate=&page=)
  -> Controller: MISSING
  -> Service: MISSING
       -> build LoanSearchCriteria (including today)
       -> JdbcMemberDAO.findAllActive for filter options
       -> JdbcLoanDAO.search
            -> count query -> total
            -> joined data query + filters + LIMIT/OFFSET -> List<Loan>
  <- Page<Loan> (bookTitle, copyCode, memberName; overdue fields still unset)
  <- Controller: MISSING (attributes loanPage, activeMembers, criteria)
  <- loans/history.jsp + pagination.tag
```

The JSP reads `l.overdue` and `l.daysOverdue`; `JdbcLoanDAO.mapLoanWithDisplay` does not set them. The missing service must compute these values, otherwise the badges/columns remain false/zero.

#### 8.2.12 12. Overdue Books

| Layer | Implementation mapping | Status |
|---|---|---|
| UI/UX | `src/main/webapp/WEB-INF/views/loans/overdue.jsp`: GET page, overdue table (book, copy, member, dates, days overdue), `ui:pagination` with `overduePage`, return/dashboard links | IMPLEMENTED |
| Controller | No Servlet/controller. Planned `GET /loans/overdue`; planned method `getOverdueLoans` (not present). | MISSING |
| Service | No service. Planned: build `LoanSearchCriteria` with `overdueOnly=true`, set `today`, load page, compute days overdue. | MISSING |
| DAO | `JdbcLoanDAO.search(Connection, LoanSearchCriteria)` with `overdueOnly=true` produces `return_date IS NULL AND due_date < today`; `JdbcLoanDAO.countOverdue(Connection, LocalDate)` exists for dashboard counts | IMPLEMENTED |
| Model | `LoanSearchCriteria` (`overdueOnly`, `today`, page/size), `Page<Loan>`, `Loan` with derived `daysOverdue` | IMPLEMENTED |
| Database | `loans` + `book_copies` + `books` + `members`; partial index `idx_loans_active_due_partial`; active predicate `return_date IS NULL`. | IMPLEMENTED |

**Data flow**

```
loans/overdue.jsp (GET /loans/overdue?page=)
  -> Controller: MISSING
  -> Service: MISSING
       -> LoanSearchCriteria.overdueOnly = true; today = LocalDate.now()
       -> JdbcLoanDAO.search -> count + joined data with due_date < today
       -> compute daysOverdue = today - dueDate for each Loan
  <- Page<Loan>
  <- Controller: MISSING (attribute overduePage)
  <- loans/overdue.jsp + pagination.tag
```

The SQL predicate and index exist. `JdbcLoanDAO` returns the matching rows but leaves `overdue`/`daysOverdue` for the service/controller to compute.

### 8.3 Supporting Dashboard (Outside the 12 Functions)

Dashboard metrics are supporting scope, not a thirteenth function:

- UI: `src/main/webapp/WEB-INF/views/dashboard.jsp` (six metrics: books, members, copies, active loans, overdue, available copies).
- DAO: `src/main/java/com/library/dao/jdbc/JdbcDashboardDAO.java` — `getStats(Connection, LocalDate)`; delegates active/overdue counts to `JdbcLoanDAO` and counts the other four metrics directly.
- Model: `DashboardStats`.
- Controller/Service: **MISSING**; no request attribute is populated today.

## 9. Class and Method Reference

### 9.1 Configuration and Exception Classes

| File path | Package | Class | Main responsibility | Important methods / members | Dependencies | Status |
|---|---|---|---|---|---|---|
| `src/main/java/com/library/config/AppConfig.java` | `com.library.config` | `AppConfig` | Shared constants for SQLState mapping and pagination | `SQLSTATE_UNIQUE_VIOLATION`, `SQLSTATE_FOREIGN_KEY_VIOLATION`, `SQLSTATE_CHECK_VIOLATION`, `LOAN_PERIOD_DAYS=14`, `DEFAULT_PAGE_SIZE=10`, `MAX_PAGE_SIZE=100`, `DEFAULT_PAGE=0` | None | IMPLEMENTED |
| `src/main/java/com/library/config/DatabaseConnection.java` | `com.library.config` | `DatabaseConnection` (final utility) | PostgreSQL connection factory; reads environment/external secret; caller owns connection | `getConnection()`, `closeQuietly(Connection)`, private `loadExternalPassword()` | `DriverManager`, `Properties`, `Connection`, `SQLException` | IMPLEMENTED |
| `src/main/java/com/library/exception/DataAccessException.java` | `com.library.exception` | `DataAccessException` (runtime) | Wrap `SQLException` while preserving SQLState and cause | Constructors `(String, SQLException)`, `(String, String, Throwable)`, `(String, Throwable)`, `(String)`; `getSqlState()`, `getMessage()` | `SQLException` | IMPLEMENTED |

### 9.2 DAO Interfaces

| File path | Package | Interface | Main responsibility | Important methods | Status |
|---|---|---|---|---|---|
| `src/main/java/com/library/dao/BookDAO.java` | `com.library.dao` | `BookDAO` | Book reads/search and writes | `findById`, `search`, `insert`, `update`, `delete`, `isbnExists` | IMPLEMENTED |
| `src/main/java/com/library/dao/BookCopyDAO.java` | `com.library.dao` | `BookCopyDAO` | Physical copy reads, locking, status, delete, available list | `findByBookId`, `findByIdForUpdate`, `insert`, `updateStatus`, `deleteByBookId`, `findAvailableWithBook` | IMPLEMENTED |
| `src/main/java/com/library/dao/BookAuthorDAO.java` | `com.library.dao` | `BookAuthorDAO` | Many-to-many book/author links | `findByBookId`, `replaceForBook`, `deleteByBookId` | IMPLEMENTED |
| `src/main/java/com/library/dao/MemberDAO.java` | `com.library.dao` | `MemberDAO` | Member search/write and uniqueness checks | `findById`, `search`, `findAllActive`, `insert`, `update`, `memberCodeExists`, `emailExists` | IMPLEMENTED |
| `src/main/java/com/library/dao/LoanDAO.java` | `com.library.dao` | `LoanDAO` | Loan insert, locking, return, history/search, counts | `insert`, `findByIdForUpdate`, `findActiveByCopyId`, `markReturned`, `search`, `findActive`, `existsByBookId`, `countActive`, `countOverdue` | IMPLEMENTED |
| `src/main/java/com/library/dao/DashboardDAO.java` | `com.library.dao` | `DashboardDAO` | Dashboard aggregate metrics | `getStats(Connection, LocalDate)` | IMPLEMENTED |
| `src/main/java/com/library/dao/AuthorDAO.java` | `com.library.dao` | `AuthorDAO` | Reference author CRUD/search | `findById`, `search`, `insert`, `update`, `delete`, `nameExists` | IMPLEMENTED |
| `src/main/java/com/library/dao/CategoryDAO.java` | `com.library.dao` | `CategoryDAO` | Reference category CRUD/search | `findById`, `search`, `insert`, `update`, `delete`, `nameExists` | IMPLEMENTED |
| `src/main/java/com/library/dao/PublisherDAO.java` | `com.library.dao` | `PublisherDAO` | Reference publisher CRUD/search | `findById`, `search`, `insert`, `update`, `delete`, `nameExists` | IMPLEMENTED |

All DAO methods take a caller-owned `Connection`; the interface documentation explicitly says DAOs never commit, roll back, or close it.

### 9.3 JDBC Implementations

| File path | Package | Class | Main responsibility | Important methods | Dependencies / notes | Status |
|---|---|---|---|---|---|---|
| `src/main/java/com/library/dao/jdbc/JdbcSupport.java` | `com.library.dao.jdbc` | `JdbcSupport` (package-private final) | Shared JDBC mechanics: binding, LIKE escaping, reads, counts, generated keys, updates, count+data pagination | `likePattern`, `fillParams(PreparedStatement, Object...)`, `fillParams(PreparedStatement, List<?>)`, `queryOne`, `queryList`, `queryLong`, `exists`, `update`, `updateExpectingRow`, `insert`, `search`; nested `RowMapper<T>` | `PreparedStatement`, `ResultSet`, `Statement.RETURN_GENERATED_KEYS`, `getGeneratedKeys()`, `DataAccessException`, `Page<T>` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/AbstractNamedEntityDAO.java` | `com.library.dao.jdbc` | `AbstractNamedEntityDAO<T>` (package-private abstract) | Shared CRUD/search/name check for `(id, name, created_at)` reference tables | `findById`, `search(BookSearchCriteria)`, `insert`, `update`, `delete`, `nameExists`; abstract `mapRow`, `setId`, `getId`, `getName` | `JdbcSupport`, `BookSearchCriteria`, `Page<T>` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcAuthorDAO.java` | `com.library.dao.jdbc` | `JdbcAuthorDAO` | Author table metadata and mapping | Inherited CRUD/search/name check; `mapRow`, `setId`, `getId`, `getName` | `AbstractNamedEntityDAO<Author>`, `AuthorDAO`, `ResultSet` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcCategoryDAO.java` | `com.library.dao.jdbc` | `JdbcCategoryDAO` | Category table with case-insensitive name uniqueness | Inherited CRUD/search/name check; `mapRow`, `setId`, `getId`, `getName` | `AbstractNamedEntityDAO<Category>`, `CategoryDAO` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcPublisherDAO.java` | `com.library.dao.jdbc` | `JdbcPublisherDAO` | Publisher table metadata and mapping | Inherited CRUD/search/name check; `mapRow`, `setId`, `getId`, `getName` | `AbstractNamedEntityDAO<Publisher>`, `PublisherDAO` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcBookDAO.java` | `com.library.dao.jdbc` | `JdbcBookDAO` | Book CRUD/search; publisher/category/available-copy projections | `findById`, `search`, `insert`, `update`, `delete`, `isbnExists`; private `appendSearchFilters`, `trimIsbn`, `mapBook` | `BookDAO`, `JdbcSupport`, `Book`, `Page<Book>`, `BookSearchCriteria` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcBookCopyDAO.java` | `com.library.dao.jdbc` | `JdbcBookCopyDAO` | Copy reads, lock, insert/status/delete, available-with-book | `findByBookId`, `findByIdForUpdate`, `insert`, `updateStatus`, `deleteByBookId`, `findAvailableWithBook`; private `mapBookCopy`, `mapAvailableCopy` | `BookCopyDAO`, `JdbcSupport`, `BookCopy`, `CopyStatus` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcBookAuthorDAO.java` | `com.library.dao.jdbc` | `JdbcBookAuthorDAO` | Replace/find/delete book-author links | `findByBookId`, `replaceForBook`, `deleteByBookId`; private `mapAuthor`; batch `PreparedStatement.addBatch/executeBatch` | `BookAuthorDAO`, `JdbcSupport`, `Author` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcMemberDAO.java` | `com.library.dao.jdbc` | `JdbcMemberDAO` | Member CRUD/search; active-loan projection; email normalization | `findById`, `search`, `findAllActive`, `insert`, `update`, `memberCodeExists`, `emailExists`; private `appendFilters`, `normalizeEmail`, `statusName`, `mapMember` | `MemberDAO`, `JdbcSupport`, `Member`, `MemberSearchCriteria` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcLoanDAO.java` | `com.library.dao.jdbc` | `JdbcLoanDAO` | Loan insert/lock/return/search/active lists/counts; display joins | `insert`, `findByIdForUpdate`, `findActiveByCopyId`, `markReturned`, `search`, `findActive`, `existsByBookId`, `countActive`, `countOverdue`; private `appendFilters`, `statusName`, `mapLoan`, `mapLoanWithDisplay` | `LoanDAO`, `JdbcSupport`, `Loan`, `LoanSearchCriteria`, `LoanStatus` | IMPLEMENTED |
| `src/main/java/com/library/dao/jdbc/JdbcDashboardDAO.java` | `com.library.dao.jdbc` | `JdbcDashboardDAO` | Six dashboard counts; delegates loan counts | `getStats(Connection, LocalDate)`; package-private `JdbcDashboardDAO(LoanDAO)` for tests | `DashboardDAO`, `LoanDAO`, `JdbcLoanDAO`, `JdbcSupport`, `DashboardStats` | IMPLEMENTED |

### 9.4 Models, Criteria, and Enums

| File path | Package | Class / enum | Main responsibility | Important members / methods | Status |
|---|---|---|---|---|---|
| `src/main/java/com/library/model/Book.java` | `com.library.model` | `Book` | Book row plus display projections | `bookId`, `isbn`, `title`, `publisherId`, `categoryId`, `publicationYear`, `createdAt`, `publisherName`, `categoryName`, `authors`, `availableCopies`; constructors; getters/setters; `equals`/`hashCode`/`toString` | IMPLEMENTED |
| `src/main/java/com/library/model/BookCopy.java` | `com.library.model` | `BookCopy` | Physical copy and book-title projection | `copyId`, `bookId`, `copyCode`, `status`, `createdAt`, `bookTitle`; constructors; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/Member.java` | `com.library.model` | `Member` | Member row plus active-loan projection | `memberId`, `memberCode`, `fullName`, `email`, `status`, `createdAt`, `activeLoanCount`; constructors; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/Loan.java` | `com.library.model` | `Loan` | Loan row plus display/derived fields | `loanId`, `copyId`, `memberId`, `loanDate`, `dueDate`, `returnDate`, `status`, `createdAt`, `bookTitle`, `copyCode`, `memberName`, `overdue`, `daysOverdue`; `isOverdue()` | IMPLEMENTED |
| `src/main/java/com/library/model/Author.java` | `com.library.model` | `Author` | Author row | `authorId`, `name`, `createdAt`; constructors; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/Category.java` | `com.library.model` | `Category` | Category row | `categoryId`, `name`, `createdAt`; constructors; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/Publisher.java` | `com.library.model` | `Publisher` | Publisher row | `publisherId`, `name`, `createdAt`; constructors; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/Page.java` | `com.library.model` | `Page<T>` | Immutable pagination wrapper | Constructor `(List<T>, long, int, int)`; `getContent`, `getTotalElements`, `getTotalPages`, `getPage`, `getSize`, `isFirst`, `isLast`, `hasNext`, `hasPrevious` | IMPLEMENTED |
| `src/main/java/com/library/model/DashboardStats.java` | `com.library.model` | `DashboardStats` | Six dashboard metrics | `totalBooks`, `totalMembers`, `totalCopies`, `activeLoans`, `overdueLoans`, `availableCopies`; constructors/getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/criteria/PageCriteria.java` | `com.library.model.criteria` | `PageCriteria` (abstract) | Shared zero-based page/size state with clamping | `getPage`, `setPage`, `getSize`, `setSize`; uses `AppConfig.DEFAULT_PAGE`, `DEFAULT_PAGE_SIZE`, `MAX_PAGE_SIZE` | IMPLEMENTED |
| `src/main/java/com/library/model/criteria/BookSearchCriteria.java` | `com.library.model.criteria` | `BookSearchCriteria` | Book search filters | `keyword`, `publisherId`, `categoryId`, `authorName`, `availableOnly`; getters/setters; inherits paging | IMPLEMENTED |
| `src/main/java/com/library/model/criteria/MemberSearchCriteria.java` | `com.library.model.criteria` | `MemberSearchCriteria` | Member search filters | `keyword`, `status`; getters/setters; inherits paging | IMPLEMENTED |
| `src/main/java/com/library/model/criteria/LoanSearchCriteria.java` | `com.library.model.criteria` | `LoanSearchCriteria` | Loan history/overdue filters and deterministic `today` | `memberId`, `copyId`, `bookId`, `status`, `fromDate`, `toDate`, `overdueOnly`, `today`; getters/setters | IMPLEMENTED |
| `src/main/java/com/library/model/enums/CopyStatus.java` | `com.library.model.enums` | `CopyStatus` | Copy status matching DB CHECK | `AVAILABLE`, `BORROWED`, `MAINTENANCE` | IMPLEMENTED |
| `src/main/java/com/library/model/enums/LoanStatus.java` | `com.library.model.enums` | `LoanStatus` | Stored loan status matching DB CHECK | `ACTIVE`, `RETURNED` | IMPLEMENTED |
| `src/main/java/com/library/model/enums/LoanState.java` | `com.library.model.enums` | `LoanState` | Derived UI state; not stored | `ACTIVE`, `OVERDUE`, `RETURNED` | IMPLEMENTED |
| `src/main/java/com/library/model/enums/MemberStatus.java` | `com.library.model.enums` | `MemberStatus` | Member status matching DB CHECK | `ACTIVE`, `INACTIVE` | IMPLEMENTED |

### 9.5 UI and Shared Web Files

All JSP screens and the pagination tag are implemented as markup and compile independently. None is wired to a Servlet; request attributes and endpoints are still provided only by the planned Controller/Service layers.

| File path | Type | Main responsibility | Key model/EL attributes | Status |
|---|---|---|---|---|
| `src/main/webapp/WEB-INF/views/books/list.jsp` | JSP | Book search/filter table, add/view/edit/delete links | `bookPage`, `criteria`, `publishers`, `categories` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/books/form.jsp` | JSP | Add/edit book form, authors, initial copies, field errors | `book`, `publishers`, `categories`, `authors`, `authorIds`, `initialCopyCount`, `fieldErrors`, `csrfToken` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/books/view.jsp` | JSP | Book details, authors, copies | `book`, `copies` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/books/delete.jsp` | JSP | Delete confirmation | `book`, `copyCount`, `csrfToken` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/members/list.jsp` | JSP | Member search/list and history links | `memberPage`, `criteria` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/members/form.jsp` | JSP | Add/edit member form | `member`, `fieldErrors`, `csrfToken` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/loans/borrow.jsp` | JSP | Borrow form | `activeMembers`, `availableCopies`, `loanDate`, `dueDate`, `fieldErrors`, `csrfToken` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/loans/return.jsp` | JSP | Return form | `activeLoans`, `returnDate`, `fieldErrors`, `csrfToken` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/loans/history.jsp` | JSP | Loan history filters/table/pagination | `loanPage`, `activeMembers`, `criteria` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/loans/overdue.jsp` | JSP | Overdue table/pagination | `overduePage` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/dashboard.jsp` | JSP | Six dashboard metrics and quick links | `dashboardStats` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/common/head.jsp` | JSP fragment | Shared head, navigation, flash/error messages | `pageTitle`, `currentPage`, `csrfToken`, `flashSuccess`, `flashError`, `globalError`, `globalErrors` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/common/foot.jsp` | JSP fragment | Shared footer and script include | None | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/errors/404.jsp` | JSP | Friendly not-found page | None | IMPLEMENTED |
| `src/main/webapp/WEB-INF/views/errors/500.jsp` | JSP | Friendly server-error page | `globalError` | IMPLEMENTED |
| `src/main/webapp/WEB-INF/tags/pagination.tag` | JSP tag | Shared Previous/Next control; preserves query parameters except `page` | `pageData` (`Page`), `baseUrl` | IMPLEMENTED |
| `src/main/webapp/assets/css/app.css` | CSS | Responsive layout, tables, forms, badges, flash, pagination | N/A | IMPLEMENTED |
| `src/main/webapp/assets/js/app.js` | Vanilla JS | Double-submit guard, delete confirmation, flash auto-dismiss, keyboard table rows | Functions `initFormGuard`, `initConfirmLinks`, `initAutoDismiss`, `initTableRows`; no framework | IMPLEMENTED |

### 9.6 SQL, Tests, Scripts, and Build

| File path | Type | Main responsibility | Status |
|---|---|---|---|
| `src/main/resources/db/schema.sql` | SQL | Atomic schema creation: extension, eight tables, FKs, CHECKs, unique/partial indexes; destroys/recreates application tables | IMPLEMENTED |
| `src/main/resources/db/seed.sql` | SQL | Demo publishers, categories, authors, books, copies, members, loans, book-author links; relative loan dates | IMPLEMENTED |
| `src/test/resources/db/manual-relation-test.sql` | SQL | 48 manual PK/FK/relation probes inside a rolled-back transaction | IMPLEMENTED |
| `src/test/java/com/library/dao/*.java` | Java tests | 12 JUnit classes covering CRUD, constraints, search, pagination, transactions, locking, diacritics, dashboard | IMPLEMENTED |
| `src/test/java/com/library/dao/support/TestDatabase.java` | Test utility | Test URL guard (`_test`), connection factory, reset/seed reference data | IMPLEMENTED |
| `src/test/java/com/library/dao/support/TestFixtures.java` | Test utility | Shared book/copy/member/loan fixtures | IMPLEMENTED |
| `scripts/run-tests.ps1` | PowerShell | Download JUnit/pgJDBC to `lib/`, compile main/test, run JUnit Console | IMPLEMENTED |
| `scripts/run-tests.sh` | Shell | Linux/macOS equivalent of the test runner | IMPLEMENTED |
| `scripts/with-postgres.ps1` | PowerShell | Start local PostgreSQL only for the wrapped command and stop it afterward if it started it | IMPLEMENTED |
| `pom.xml`, `mvnw`, `mvnw.cmd` | Maven build | Dependency management, WAR packaging, Surefire | MISSING |
| `src/main/webapp/WEB-INF/web.xml` | Deployment descriptor | Servlet mappings, filters, error pages, welcome file | MISSING |
| `src/main/webapp/META-INF/context.xml` | Tomcat context | Data source/context configuration if used | MISSING |
| `src/main/java/com/library/controller/` | Java package | Servlets/controllers | MISSING |
| `src/main/java/com/library/service/` | Java package | Business rules and transaction orchestration | MISSING |

## 10. Person Ownership Mapping

This section separates ownership from architectural mapping. Documenting a layer does not assign its implementation to Person 1.

| Layer / component | Person 1 responsibility | Person 2 responsibility | Current status |
|---|---|---|---|
| PostgreSQL database design | Owns ERD, schema, relationships, constraints, indexes | Reviews integration needs | IMPLEMENTED |
| SQL scripts and constraints | Owns `schema.sql`, `seed.sql`, manual probes | Runs/applies in deployment, adds migration needs | IMPLEMENTED |
| Models and criteria used by DAO/UI | Owns `Book`, `BookCopy`, `Member`, `Loan`, `Page`, `DashboardStats`, criteria, enums | Consumes models in service/controller | IMPLEMENTED |
| DAO interfaces | Owns contracts, caller-owned `Connection` rules | Consumes contracts from services | IMPLEMENTED |
| DAO/JDBC implementation | Owns all `Jdbc*` classes, `JdbcSupport`, `AbstractNamedEntityDAO`, SQLSTATE wrapping | Does not rewrite SQL; integrates transactions | IMPLEMENTED |
| DAO tests | Owns 12 test classes + `TestDatabase`/`TestFixtures` | Uses results for integration | IMPLEMENTED |
| UI/UX (JSP/CSS/JS) | Owns screens, forms, tables, pagination tag, client validation, accessibility markup | Wires JSPs to request attributes and endpoints | PARTIALLY IMPLEMENTED |
| Controller/Servlet | Not assigned | Owns routing, request validation, view selection, redirects, CSRF enforcement, error mapping | MISSING |
| Service/business logic | Not assigned | Owns rules, eligibility, 14-day computation, overdue projections, transaction boundaries | MISSING |
| Application integration | Provides DAO/UI contracts and data | Owns `pom.xml`, `web.xml`, `context.xml`, WAR, Tomcat deployment, HTTP end-to-end | MISSING |
| Authentication/RBAC | Out of scope | Out of scope | MISSING |

Person 1 must not be credited with controller, service, transaction orchestration, or deployment work. Person 2 must not be asked to rewrite the schema/DAO contracts; those are the frozen Person 1 boundary.

## 11. Implementation Status Summary

### 11.1 Component Status

| Component | Status | Evidence |
|---|---|---|
| Database schema, seed, constraints, indexes | IMPLEMENTED | `schema.sql`, `seed.sql`, manual relation probes |
| Models, criteria, enums | IMPLEMENTED | 17 model/criteria/enum source files |
| DAO interfaces | IMPLEMENTED | 9 interfaces under `com.library.dao` |
| JDBC DAOs and shared helpers | IMPLEMENTED | 11 implementation/support classes under `com.library.dao.jdbc` |
| DAO tests | IMPLEMENTED | 12 test classes; 124/124 pass in the recorded standalone run |
| JSP/CSS/JS | IMPLEMENTED | 15 JSPs + 1 tag, CSS/JS; JspC 16/16 with 0 errors |
| Controller/Servlet | MISSING | No `controller` package or Servlet class |
| Service/business logic | MISSING | No `service` package or Service class |
| Production transaction orchestration | MISSING | No production `setAutoCommit`, `commit`, or `rollback` call |
| Maven build | MISSING | No `pom.xml`, no Maven Wrapper |
| `web.xml` / `context.xml` | MISSING | No deployment descriptors |
| WAR/Tomcat deployment | MISSING | No WAR, no deployed application |
| HTTP end-to-end flow | MISSING | No routing or request/response evidence |
| CSRF enforcement | MISSING | JSP contains `_csrf` fields, but no filter/controller validates them |
| Reference-data management UI | MISSING | Optional support scope; DAOs exist for authors/categories/publishers but no screens |
| Copy management UI | MISSING | Optional support scope; copy DAOs exist but no dedicated screens |
| Author/publisher name uniqueness (E09) | PLANNED | Schema has no unique-name constraint for these tables |

### 11.2 Twelve-Function Status

| # | Function | UI | Controller | Service | DAO | DB | Overall |
|---|---|---|---|---|---|---|---|
| 1 | Add Book | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 2 | Update Book | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 3 | Delete Book | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 4 | Search Books | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 5 | View Book Details | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 6 | Add Member | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 7 | Update Member | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 8 | Search Members | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 9 | Borrow Book | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 10 | Return Book | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 11 | Borrowing History | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |
| 12 | Overdue Books | IMPLEMENTED | MISSING | MISSING | IMPLEMENTED | IMPLEMENTED | PARTIALLY IMPLEMENTED |

**Result: 0 of 12 functions are complete end to end; 12 of 12 are partially implemented because the UI, DAO, and database exist but the controller/service path does not.**

### 11.3 Missing or Incomplete Components

1. **Controller/Servlet layer:** no `src/main/java/com/library/controller/`, no Servlet class, no URL mappings. All endpoint paths in this document are planned contracts, not executable routes.
2. **Service layer:** no `src/main/java/com/library/service/`, no business-rule or transaction class. The 14-day due date, member eligibility, duplicate-return handling, inactive-member rule, delete-history rule, and overdue projections are not orchestrated in production code.
3. **Build and deployment:** no `pom.xml`, Maven Wrapper, `web.xml`, or `context.xml`; no WAR; no Tomcat deployment; no HTTP evidence.
4. **CSRF:** JSP forms contain `_csrf` and `head.jsp` can expose a meta tag, but no filter or controller validates the token.
5. **Exception contract:** only `DataAccessException` exists. `NotFoundException`, `ValidationException`, and `BusinessRuleException` are mentioned in planning documents but have no Java source.
6. **Overdue display fields:** `Loan.overdue` and `Loan.daysOverdue` are not set by `JdbcLoanDAO`; a missing service must compute and set them for `history.jsp` and `overdue.jsp`.
7. **Member update rule:** preventing `INACTIVE` while active loans exist has no implementation; only the `activeLoanCount` projection exists.
8. **Reference-data/copy management UI:** DAOs exist, but screens and endpoints are not implemented; these are optional support scope, not part of the 12 required functions.
9. **Author/publisher name uniqueness (E09):** the schema does not enforce unique names; the rule is still planned.
10. **Authentication/RBAC:** explicitly outside MVP scope and not implemented.

## 12. Final Validation

| Check | Result |
|---|---|
| All 12 functions included | 12/12, original names and order preserved |
| Every referenced file path exists | Verified against the current workspace; 75 application/UI/SQL files plus docs/scripts; missing build/controller/service paths are explicitly marked **MISSING** rather than shown as existing |
| Every referenced method exists | DAO, model, config, and UI references were matched to source declarations; planned controller/service method names are labeled as planned and are not claimed as Java methods |
| Missing/incomplete components identified | Controller, Service, Maven/WAR, descriptors, HTTP, CSRF validation, service-side rules, optional UI, E09 |
| Consistency with original Phase 1 architecture | MVC/3-tier, JDBC/handwritten SQL, no ORM, 14-day loan rule, active = `return_date IS NULL`, partial unique index, RESTRICT/CASCADE rules, and Person 1/Person 2 split retained |
| Person ownership separated | Person 1 = DB/SQL/DAO/JDBC/UI; Person 2 = Controller/Service/integration; no controller/service work assigned to Person 1 |
| Application source modified | **No.** This update changes only `docs/phase1-plan.md`. |

### 12.1 Path and Count Verification

- Main Java sources: **40** (23 classes/interfaces under `config`, `dao`, `dao/jdbc`, `exception`; 17 model/criteria/enum files).
- Test Java sources: **14** (12 test classes + `TestDatabase` + `TestFixtures`).
- JSP files: **15**; shared tags: **1**; CSS/JS assets: **2**.
- SQL files: **3** (`schema.sql`, `seed.sql`, `manual-relation-test.sql`).
- Application/UI/SQL total used for the mapping: **75 files**.
- The source tree contains no `pom.xml`, `mvnw`, `mvnw.cmd`, `web.xml`, `context.xml`, `controller` package, or `service` package.

## 13. Change Report

| Item | Detail |
|---|---|
| Sections added or updated | Workspace/toolchain findings reconciled; technical decisions preserved; Tomcat blocker marked resolved; phase gate updated; ERD reconciled with `schema.sql`; actual directory tree added; transaction/connection design retained and status-qualified; 12-function mapping extended with UI/Controller/Service/DAO/Model/Database details and data flows; class/method reference added; Person ownership mapping added; implementation status summary added; validation/change report added |
| Functions mapped | **12/12** — Add Book, Update Book, Delete Book, Search Books, View Book Details, Add Member, Update Member, Search Members, Borrow Book, Return Book, Borrowing History, Overdue Books |
| Verified source files | **75** application/UI/SQL files: 40 main Java + 14 test Java + 15 JSP + 1 tag + 2 assets + 3 SQL |
| Missing/incomplete components | Controller/Servlet, Service/business logic, production transactions, Maven build/WAR, `web.xml`/`context.xml`, HTTP integration, CSRF validation, service-side overdue/inactive-member rules, optional reference/copy screens, E09 uniqueness |
| Source code modified | **No application source code was modified.** Only `docs/phase1-plan.md` was created/updated. |
