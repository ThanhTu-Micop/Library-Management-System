# Library Management System — Consolidated Implementation Plan

| Field | Value |
|---|---|
| Version | 1.2 |
| Date | 2026-10-26 |
| Status | Person 1 implementation complete and verified with standalone tools; Person 2 build, deployment, and HTTP integration remain blocked |
| Person 1 | Database, SQL, JDBC/DAO, UI/UX |
| Person 2 | Controller, Service/business rules, integration |
| Supersedes | Initial Phase 1 full-stack plan and the Person 1 scope revision |
| Related | `docs/java-concepts-mapping.md`, `docs/runtime-toolchain-audit.md` |

## 1. Purpose and Scope

Build a functional Java Library Management System for a university software engineering project using:

- Java and Maven.
- PostgreSQL with handwritten SQL through JDBC.
- A strict three-tier MVC architecture.
- JSP, HTML, CSS, and vanilla JavaScript.
- Java Servlets and Apache Tomcat.
- No ORM and no Spring Boot Controller/Database layers.

The system must implement all 12 required business functions end to end while keeping the presentation, controller, service, and DAO layers separate.

Authentication and role-based authorization are outside the current MVP scope. This limitation must be documented in the README; authentication must not be claimed.

## 2. Current State and Blockers

| Item | Status |
|---|---|
| Application source code | Person 1 source present: 37 main Java files, 8 test source files, 15 JSPs, CSS/JS, and SQL scripts |
| Repository contents | `docs/`, `src/main/java`, `src/main/resources/db`, `src/main/webapp`, and `src/test/java`; no Maven build or web deployment descriptors |
| Git repository | Not initialized |
| Java JDK | Temurin JDK `17.0.20.1+1` installed at `C:\Users\BINHND\dev\jdk-17`; `JAVA_HOME` and `PATH` configured |
| Maven | Maven `3.9.16` installed at `C:\Users\BINHND\dev\apache-maven-3.9.16`; uses JDK 17 |
| PostgreSQL | PostgreSQL `16.15` installed locally; cluster running on `127.0.0.1:5432`; `library_db` and `library_test` applied and seeded |
| Tomcat | Tomcat `10.1.61` installed at `C:\Users\BINHND\dev\apache-tomcat-10.1.61`; HTTP and stock JSP verified |
| PostgreSQL JDBC | `postgresql-42.7.14.jar` downloaded and used by the standalone DAO tests |
| JUnit | JUnit Platform Console Standalone `1.14.4` used outside the workspace; 39/39 DAO tests pass; Maven/Surefire integration is still missing |
| Docker | Not installed; not required |
| Runtime/Servlet namespace decision | **Resolved:** Tomcat 10.1 + Jakarta Servlet 6.0 + JSP 3.1 + JSTL 3.0.1 |
| Project build | **Not created:** no `pom.xml`, Maven Wrapper, `web.xml`, or `context.xml` |
| Person 1 implementation | **Complete for the agreed scope:** schema, seed, models, DAO interfaces/implementations, JSP/CSS/JS, DAO tests, and docs |
| Phase 2 | **BLOCKED** pending Person 2's Maven build, WAR deployment, and HTTP integration evidence |

### Blocking conditions

1. Person 2 must create the initial Maven project (`pom.xml`, Maven Wrapper, `web.xml`, `context.xml`) and freeze the Service/Controller contracts.
2. Person 2 must integrate the existing Person 1 source and JSPs into the Maven WAR and add Jakarta Servlet/JSP/JSTL/pgJDBC/JUnit dependencies.
3. A project WAR must build, deploy to Tomcat, and pass JSP/HTTP checks.
4. JUnit 5 and Surefire must be integrated into the project; the current 39/39 result is standalone console evidence, not Maven evidence.
5. The university has not supplied a requirement document confirming the selected runtime; the project owner approved the stable baseline, but course acceptance is not evidenced.

The runtime and toolchain are installed and verified. Person 1's database, DAO, and UI scope is implemented and verified with standalone tools. Phase 2 remains gated on the project build, deployment, and HTTP integration owned by Person 2.

`docs/java-concepts-mapping.md` now marks the concepts evidenced by Person 1 source as implemented and keeps Person 2 and Maven-dependent items as planned or partial.

## 3. Mandatory Technical Constraints

- Language: Java.
- Build tool: Maven.
- Database: PostgreSQL.
- Database access: JDBC with handwritten SQL and `PreparedStatement`.
- No Hibernate, JPA, MyBatis, or any ORM.
- Web UI: JSP, HTML, CSS, and vanilla JavaScript.
- HTTP handling: Java Servlets.
- Application server: Tomcat compatible with the chosen Servlet API.
- Jakarta Servlet APIs if using Tomcat 10.1; `javax.servlet` if using Tomcat 9.
- No SQL inside JSP or Servlet files.
- No database access from the UI.
- No business rules inside JSP pages.
- Use transactions for operations that change multiple related records.
- Do not introduce unnecessary frameworks or dependencies.
- Never commit credentials or secrets.
- Validate all input server-side.
- Use POST for state-changing operations and redirect after successful submissions.
- Do not expose stack traces, SQL, passwords, or credentials to users.

## 4. Team Ownership

### Person 1 — Database/DAO and UI/UX

- PostgreSQL schema, relationships, constraints, indexes, and seed data.
- `schema.sql` and `seed.sql`.
- Java model classes (initial author; Person 2 reviews).
- DAO interfaces and JDBC implementations using handwritten SQL.
- Database connection configuration.
- Database/DAO tests and documentation.
- JSP, HTML, CSS, and vanilla JavaScript.
- Client-side validation, responsive layout, confirmation dialogs, loading/empty states, and user feedback.
- Wiring the UI to Person 2’s agreed Controller endpoints.
- DB/DAO/UI sections of shared documentation.
- Maintaining `docs/java-concepts-mapping.md`.

### Person 2 — Controller, Service, and Integration

- Service interfaces, implementations, business rules, and transaction boundaries.
- Controller/Servlet endpoints and request validation.
- Filters, CSRF handling, encoding, `web.xml`, and `context.xml`.
- Application startup/integration configuration.
- Service/controller tests.
- Overall application integration.
- `docs/architecture.md` and final README ownership.
- Service/Controller sections of `docs/api-contract.md` and `docs/testing.md`.

### Shared responsibilities

- Freeze model fields, enums, DAO signatures, Service signatures, endpoint paths, request parameters, view attributes, flash keys, and error conventions.
- Agree on validation rules and SQLState-to-user-message mapping.
- Review database constraints against service requirements.
- Agree on transaction lock order and affected-row checks.
- Avoid simultaneous edits to shared files.

### File ownership

```text
src/main/resources/db/schema.sql                         [P1]
src/main/resources/db/seed.sql                           [P1]

src/main/java/com/library/model/**                       [P1 authors, P2 approves]
src/main/java/com/library/dao/**                         [P1]
src/main/java/com/library/config/DatabaseConnection.java [P1]
src/main/java/com/library/config/AppConfig.java          [P1]
src/main/java/com/library/exception/DataAccessException.java [P1]

src/main/java/com/library/service/**                     [P2]
src/main/java/com/library/controller/**                  [P2]
src/main/java/com/library/filter/**                      [P2]
src/main/java/com/library/config/AppStartupListener.java [P2]
src/main/java/com/library/exception/{LibraryException,ValidationException,BusinessRuleException,NotFoundException}.java [P2]
src/main/java/com/library/util/**                        [P2]

src/main/webapp/assets/**                                [P1]
src/main/webapp/WEB-INF/views/**                         [P1]
src/main/webapp/WEB-INF/web.xml                          [P2]
src/main/webapp/META-INF/context.xml                     [P2]

pom.xml                                                   [P2 owner, P1 review]
src/test/java/com/library/dao/**                         [P1]
src/test/java/com/library/dao/support/TestDatabase.java  [P1]
src/test/java/com/library/service/**                     [P2]
src/test/java/com/library/controller/**                  [P2]

docs/database-design.md                                  [P1]
docs/architecture.md                                     [P2, P1 reviews DB/UI]
docs/api-contract.md                                     [split sections]
docs/testing.md                                          [split sections]
docs/java-concepts-mapping.md                            [P1]
README.md                                                 [split sections; P2 final owner]
```

## 5. Runtime Decision and Toolchain

### Selected target and installed baseline

The project owner approved the stable baseline on 2026-10-24. No university requirement document was found; this is a project decision, not evidence of course approval.

| Component | Selected version | Installed path / status |
|---|---|---|
| Java | Temurin JDK `17.0.20.1+1` (Java 17 LTS) | `C:\Users\BINHND\dev\jdk-17`; verified |
| Maven | `3.9.16` | `C:\Users\BINHND\dev\apache-maven-3.9.16`; verified |
| PostgreSQL | `16.15` | `C:\Users\BINHND\dev\pgsql-16.15`; local cluster verified |
| Tomcat | `10.1.61` | `C:\Users\BINHND\dev\apache-tomcat-10.1.61`; HTTP/JSP verified |
| Servlet API | Jakarta Servlet `6.0` | Tomcat runtime contains `jakarta/servlet/http/HttpServlet.class` |
| JSP/JSTL | JSP `3.1` / JSTL `3.0.1` (`jakarta.tags.*`) | All 15 JSPs compiled with Tomcat `JspC` and temporary JSTL 3.0.1 JARs; WAR dependency pending in project |
| PostgreSQL JDBC | `42.7.14` | `C:\Users\BINHND\dev\lib\postgresql-42.7.14.jar`; local `SELECT 1` passed and DAO tests executed |
| Tests | JUnit `5.14.4` console standalone | 39/39 DAO tests pass outside Maven; Surefire integration pending |
| Compilation target | `maven.compiler.release=17` | Main and test sources compiled with `javac --release 17`; POM still pending |

### Verification checklist before Phase 2

| Check | Status | Evidence |
|---|---|---|
| `java -version` and `javac -version` report Java 17 | **PASS** | Both report `17.0.20.1` |
| `mvn -v` reports Maven `3.9.16` and Java 17 | **PASS** | Maven `3.9.16`, Java `17.0.20.1` |
| `psql --version` reports PostgreSQL `16.15` | **PASS** | `psql (PostgreSQL) 16.15` |
| Tomcat `version.bat` reports selected version | **PASS** | `Apache Tomcat/10.1.61`, JVM `17.0.20.1+1` |
| Tomcat `servlet-api.jar` contains Jakarta `HttpServlet` | **PASS** | `jakarta/servlet/http/HttpServlet.class` present |
| PostgreSQL databases and application role | **PASS** | `library_db`, `library_test`, and `library_app` connection verified |
| JDBC driver smoke test | **PASS** | Driver `42.7.14`, server `16.15`, `SELECT 1` returned `1` |
| Main and test source compilation | **PASS** | `javac --release 17`: 37/37 main files and 8/8 test files compiled |
| Schema, seed, and constraints | **PASS** | Applied to `library_db` and `library_test`; 8 tables, 14 named indexes, seed counts `3/4/4/4/8/3/3/4` |
| DAO tests | **PASS (standalone)** | JUnit Platform Console: 39 tests found, 39 successful, 0 failed |
| JSP compilation | **PASS (standalone)** | Tomcat `JspC`: 15 generated Java files, 15 class files, 0 errors |
| Static EL property check | **PASS** | 320 expressions scanned; 214 model property references checked; no missing property |
| Project build, WAR, and deployment | **BLOCKED** | No `pom.xml`, Maven Wrapper, `web.xml`, `context.xml`, or WAR |
| JUnit 5/Surefire integration | **BLOCKED** | No Maven test dependency, plugin, or Surefire execution |
| HTTP flows | **BLOCKED** | Controllers, Services, filters, and deployment descriptors are Person 2 scope |
| Runtime evidence recorded in project documentation | **PASS** | `docs/runtime-toolchain-audit.md` updated |

**If Tomcat 9 is later required by the course:** use `javax.servlet-api` 4.0.1, JSTL 1.2, and the old JSTL taglib URIs. Do not mix Jakarta and `javax` imports. The current project target is Tomcat 10.1/Jakarta.

### Database configuration contract

- Environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- Optional external password file via `DB_CONFIG`; the first line is the password and the file must not be committed.
- No project database-properties file is read. `DatabaseConnection` uses environment variables and the optional external password file.
- `DatabaseConnection` is Person 1-owned; Person 2 owns transaction boundaries.
- Local development target: `jdbc:postgresql://127.0.0.1:5432/library_db`, user `library_app`.
- Test target: `library_test` on the same local cluster.
- Local passwords are stored only under `C:\Users\BINHND\dev\secrets` with restricted ACLs, outside the workspace. They must never be copied into the repository, documentation, scripts, or chat.
- PostgreSQL is a portable, user-scope cluster, not a Windows service. It is started at user logon by the `LibraryManagementPostgres` scheduled task and can be started or stopped with `C:\Users\BINHND\dev\scripts\start-postgres.ps1` and `stop-postgres.ps1`.

## 6. Architecture

```text
Browser
  │ HTML forms / links
  ▼
JSP + CSS + vanilla JavaScript
  │ HTTP
  ▼
Servlet Controllers                  [Person 2]
  │ Java calls
  ▼
Service interfaces + implementations [Person 2]
  │ Java calls
  ▼
DAO interfaces + JDBC implementations [Person 1]
  │ JDBC
  ▼
PostgreSQL
```

### Layer rules

- **JSP:** rendering and navigation only; no SQL or business rules.
- **Controller:** request parsing, basic boundary validation, service calls, view selection, redirects; no SQL or business rules.
- **Service:** business rules and transaction boundaries; no Servlet/JSP types.
- **DAO:** handwritten SQL, `PreparedStatement`, `ResultSet` mapping; no UI or business rules.
- **Database:** constraints, indexes, relationships, and transactional consistency.

### Cross-cutting components

- `EncodingFilter` and `CsrfFilter` — Person 2 owns.
- `AppStartupListener` — Person 2 owns.
- `DatabaseConnection` / `AppConfig` — Person 1 owns.
- Exception hierarchy:
  - Person 2: `LibraryException`, `ValidationException`, `BusinessRuleException`, `NotFoundException`.
  - Person 1: `DataAccessException` wrapping `SQLException`, preserving SQLState and cause.
- Controllers map expected failures to friendly UI messages; unexpected failures become generic 500 responses and are logged server-side.
- Searches return empty collections/pages, never `null`. Get-by-id throws `NotFoundException`.

## 7. Database Design

### ERD

```text
publishers 1 ─────< books >───── 1 categories
                     │
                     ├──< book_authors >── 1 authors
                     │
                     └──< book_copies 1 ───< loans >─── 1 members
```

A book has one publisher and one category, many authors, and many physical copies. A member has many loans. A copy has many historical loans but at most one active loan.

### Tables

| Table | Primary key | Foreign keys | Key constraints |
|---|---|---|---|
| `publishers` | `publisher_id` | — | `name` required |
| `categories` | `category_id` | — | `name` required; unique case-insensitive |
| `authors` | `author_id` | — | `name` required |
| `books` | `book_id` | `publisher_id`, `category_id` → RESTRICT | `isbn` unique; `title` required; publication-year check; `created_at` |
| `book_authors` | (`book_id`, `author_id`) | `book_id` → CASCADE; `author_id` → RESTRICT | Many-to-many association |
| `book_copies` | `copy_id` | `book_id` → RESTRICT | `copy_code` unique; status `AVAILABLE`, `BORROWED`, `MAINTENANCE` |
| `members` | `member_id` | — | `member_code` unique; case-insensitive unique email when present; status `ACTIVE`, `INACTIVE` |
| `loans` | `loan_id` | `copy_id`, `member_id` → RESTRICT | Date and status consistency; one active loan per copy |

### Loan period

- The borrowing period is **exactly 14 calendar days**.
- `due_date` is computed by the service as `loan_date.plusDays(14)`; the client must not be allowed to choose an arbitrary due date.
- The database enforces the same rule with `CHECK (due_date = loan_date + 14)`, so a bypassed service cannot create a different loan period.
- A loan is overdue when it is still active and `due_date < today`; no `OVERDUE` row status is stored.
- The 14-day rule is a business rule owned by Person 2; the database check, DAO persistence, and UI display are Person 1 responsibilities.

### Critical invariants

- `CHECK (due_date = loan_date + 14)`.
- `CHECK (return_date IS NULL OR return_date >= loan_date)`.
- `CHECK ((status = 'ACTIVE' AND return_date IS NULL) OR (status = 'RETURNED' AND return_date IS NOT NULL))`.
- Partial unique index: `UNIQUE (copy_id) WHERE return_date IS NULL`.
- Loan foreign keys use `ON DELETE RESTRICT` to preserve history.
- Book publisher/category are required.
- At least one author is required at the service level.
- Availability is derived from `book_copies.status = 'AVAILABLE'`; no editable quantity column.
- Overdue is derived: active loan with `due_date < today`; no stored `OVERDUE` status.

### Indexes

- Books: unique ISBN; `LOWER(title)`; publisher; category.
- Book copies: unique copy code; `(book_id, status)`.
- Loans: `(member_id, loan_date DESC)`, `(copy_id)`, partial active-copy unique index, partial active-due index.
- Members: unique member code; partial unique `LOWER(email)`; `LOWER(full_name)`.
- Reference tables: `LOWER(name)` indexes.

### Seed data

The implemented `seed.sql` includes:

- Publishers, categories, authors, books, and multiple copies.
- Active, returned, and overdue loans with dates relative to `CURRENT_DATE`.
- At least one inactive member.
- At least one book with no available copies.
- Consistent copy statuses matching loan records.

Verified counts are publishers 3, categories 4, authors 4, books 4, copies 8, members 3, loans 3, and book-author links 4. The seed script is destructive and documented as demo-only.

### SQLState mapping contract

| SQLState | Meaning | Person 2 handling |
|---|---|---|
| `23505` | Unique violation | Duplicate ISBN/code/email/active loan → business message |
| `23503` | Foreign-key violation | Invalid reference or history-protected delete → business message |
| `23514` | Check violation | Invalid date/status → validation/business message |

## 8. Project Structure

```text
library-management-system/
├── pom.xml                                      [Person 2, planned]
├── README.md                                    [Person 2, planned]
├── .gitignore                                   [planned]
├── docs/
│   ├── architecture.md
│   ├── database-design.md
│   ├── api-contract.md
│   ├── testing.md
│   ├── implementation-plan.md
│   └── java-concepts-mapping.md
├── src/
│   ├── main/
│   │   ├── java/com/library/
│   │   │   ├── model/
│   │   │   │   ├── Author.java
│   │   │   │   ├── Book.java
│   │   │   │   ├── BookCopy.java
│   │   │   │   ├── Category.java
│   │   │   │   ├── DashboardStats.java
│   │   │   │   ├── Loan.java
│   │   │   │   ├── Member.java
│   │   │   │   ├── Page.java
│   │   │   │   ├── Publisher.java
│   │   │   │   ├── enums/
│   │   │   │   │   ├── CopyStatus.java
│   │   │   │   │   ├── LoanState.java
│   │   │   │   │   ├── LoanStatus.java
│   │   │   │   │   └── MemberStatus.java
│   │   │   │   └── criteria/
│   │   │   │       ├── BookSearchCriteria.java
│   │   │   │       ├── LoanSearchCriteria.java
│   │   │   │       └── MemberSearchCriteria.java
│   │   │   ├── dao/
│   │   │   │   ├── AuthorDAO.java
│   │   │   │   ├── BookAuthorDAO.java
│   │   │   │   ├── BookCopyDAO.java
│   │   │   │   ├── BookDAO.java
│   │   │   │   ├── CategoryDAO.java
│   │   │   │   ├── DashboardDAO.java
│   │   │   │   ├── LoanDAO.java
│   │   │   │   ├── MemberDAO.java
│   │   │   │   ├── PublisherDAO.java
│   │   │   │   └── jdbc/
│   │   │   │       ├── JdbcAuthorDAO.java
│   │   │   │       ├── JdbcBookAuthorDAO.java
│   │   │   │       ├── JdbcBookCopyDAO.java
│   │   │   │       ├── JdbcBookDAO.java
│   │   │   │       ├── JdbcCategoryDAO.java
│   │   │   │       ├── JdbcDashboardDAO.java
│   │   │   │       ├── JdbcLoanDAO.java
│   │   │   │       ├── JdbcMemberDAO.java
│   │   │   │       └── JdbcPublisherDAO.java
│   │   │   ├── service/
│   │   │   │   ├── BookService.java
│   │   │   │   ├── DashboardService.java
│   │   │   │   ├── LoanService.java
│   │   │   │   ├── MemberService.java
│   │   │   │   ├── impl/
│   │   │   │   └── policy/LoanPolicy.java
│   │   │   ├── controller/
│   │   │   ├── filter/
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java
│   │   │   │   ├── AppStartupListener.java
│   │   │   │   ├── DatabaseConnection.java
│   │   │   │   └── TransactionManager.java
│   │   │   ├── exception/
│   │   │   └── util/
│   │   ├── resources/db/
│   │   │   ├── schema.sql
│   │   │   └── seed.sql
│   │   └── webapp/
│   │       ├── assets/
│   │       │   ├── css/app.css
│   │       │   ├── js/app.js
│   │       │   └── images/
│   │       ├── META-INF/context.xml
│   │       └── WEB-INF/
│   │           ├── web.xml
│   │           └── views/
│   │               ├── common/
│   │               ├── dashboard.jsp
│   │               ├── errors/
│   │               ├── books/
│   │               ├── members/
│   │               └── loans/
│   └── test/java/com/library/
│       ├── dao/
│       │   ├── JdbcBookAuthorDAOTest.java
│       │   ├── JdbcBookCopyDAOTest.java
│       │   ├── JdbcBookDAOTest.java
│       │   ├── JdbcDashboardDAOTest.java
│       │   ├── JdbcLoanDAOTest.java
│       │   ├── JdbcMemberDAOTest.java
│       │   ├── JdbcReferenceDAOTest.java
│       │   └── support/TestDatabase.java
│       ├── service/
│       └── util/
```

The tree is the target layout, not a claim that every file exists. Person 1 source, JSPs, SQL, tests, and the five current docs are present; `pom.xml`, README, `.gitignore`, `web.xml`, `context.xml`, Service/Controller/filter files, and the three Person 2 docs remain planned. Ownership follows the matrix in section 4.

## 9. Business Function Ownership and Flow

| # | Function | Person 1 — DB/DAO + UI | Person 2 — Controller + Service | Main endpoint |
|---|---|---|---|---|
| 1 | Add Book | Schema/DAO inserts for book, authors, copies; form UI | `createBook` rules, Servlet, redirects, errors | `GET/POST /books/new` |
| 2 | Update Book | Update DAO, author replacement, edit UI | Update rules, Servlet | `GET/POST /books/edit?id=` |
| 3 | Delete Book | FK/check constraints, existence query, delete DAO, confirmation UI | Rejection rule, Servlet, error mapping | `POST /books/delete` |
| 4 | Search Books | Search/count/pagination SQL, list UI | Criteria validation, Servlet | `GET /books` |
| 5 | View Book Details | Detail/copy queries, detail JSP | Servlet, request attributes | `GET /books/view?id=` |
| 6 | Add Member | Unique constraints, insert DAO, form UI | Registration validation, Servlet | `GET/POST /members/new` |
| 7 | Update Member | Update DAO, uniqueness queries, edit UI | Update rules, Servlet | `GET/POST /members/edit?id=` |
| 8 | Search Members | Search/count/pagination SQL, list UI | Criteria validation, Servlet | `GET /members` |
| 9 | Borrow Book | Partial unique index, lock/query/insert/update DAOs, borrow UI | Transaction sequence, eligibility rules, Servlet | `POST /loans/borrow` |
| 10 | Return Book | Lock/update DAOs, return UI | Transaction sequence, repeated-return rule, Servlet | `POST /loans/return` |
| 11 | Borrowing History | Join/filter/pagination SQL, history JSP | Service filters, Servlet | `GET /loans/history` |
| 12 | Overdue Books | Overdue predicate/index, overdue JSP | Overdue rule/service, Servlet | `GET /loans/overdue` |

### Supporting screens

- Dashboard: Person 1 SQL + JSP; Person 2 service/controller.
- Active-loan console: Person 1 UI; Person 2 service/controller.
- Reference data management (optional): Person 1 schema/DAO/UI; Person 2 service/controller.
- Copy management (optional): Person 1 schema/DAO/UI; Person 2 service/controller.

## 10. Contracts

### 10.1 DAO contract (Person 1 provides)

All methods receive the caller’s `Connection`; DAOs never commit, roll back, or close it.

```java
public interface BookDAO {
    Optional<Book> findById(Connection c, int bookId);
    Page<Book> search(Connection c, BookSearchCriteria criteria);
    int insert(Connection c, Book book);                 // generated ID
    void update(Connection c, Book book);
    void delete(Connection c, int bookId);
    boolean isbnExists(Connection c, String isbn, Integer excludeBookId);
}

public interface BookCopyDAO {
    List<BookCopy> findByBookId(Connection c, int bookId);
    Optional<BookCopy> findByIdForUpdate(Connection c, int copyId);
    int insert(Connection c, BookCopy copy);              // generated ID
    int updateStatus(Connection c, int copyId, CopyStatus status); // affected rows
    void deleteByBookId(Connection c, int bookId);        // zero rows tolerated
    List<BookCopy> findAvailableWithBook(Connection c);   // display join
}

public interface LoanDAO {
    int insert(Connection c, Loan loan);                  // generated ID
    Optional<Loan> findByIdForUpdate(Connection c, int loanId);
    Optional<Loan> findActiveByCopyId(Connection c, int copyId);
    int markReturned(Connection c, int loanId, LocalDate returnDate); // affected rows
    Page<Loan> search(Connection c, LoanSearchCriteria criteria);
    List<Loan> findActive(Connection c);
    boolean existsByBookId(Connection c, int bookId);
    long countActive(Connection c);
    long countOverdue(Connection c, LocalDate today);
}

public interface MemberDAO {
    Optional<Member> findById(Connection c, int memberId);
    Page<Member> search(Connection c, MemberSearchCriteria criteria);
    List<Member> findAllActive(Connection c);
    int insert(Connection c, Member member);              // generated ID
    void update(Connection c, Member member);
    boolean memberCodeExists(Connection c, String code, Integer excludeMemberId);
    boolean emailExists(Connection c, String email, Integer excludeMemberId);
}

public interface BookAuthorDAO {
    List<Author> findByBookId(Connection c, int bookId);
    void replaceForBook(Connection c, int bookId, List<Integer> authorIds);
    void deleteByBookId(Connection c, int bookId);
}

public interface DashboardDAO {
    DashboardStats getStats(Connection c, LocalDate today);
}
```

Additional DAOs: `AuthorDAO`, `PublisherDAO`, `CategoryDAO`.

**DAO guarantees:**

- Search returns a non-null `Page` with total count; empty items when no results.
- Reads return `Optional.empty()` for missing rows.
- Inserts return affected rows and set the generated ID on the model.
- Lock methods execute `SELECT ... FOR UPDATE` inside the caller’s transaction.
- No business rules such as member eligibility or delete permission.
- `SQLException` is wrapped in `DataAccessException` with SQLState and cause.
- JDBC resources are managed with try-with-resources; no swallowed errors.
- Date-sensitive methods use the caller-supplied `LocalDate today`; DAO SQL does not use `CURRENT_DATE`.

### 10.2 UI/Controller contract (Person 2 provides)

Person 2 must freeze:

- Endpoint paths and HTTP methods.
- Request parameter names and validation rules.
- JSP view names under `/WEB-INF/views/`.
- Request attribute names and types:
  - `bookPage`, `memberPage`, `loanPage`, `overduePage`
  - `book`, `member`, `copies`, `activeLoans`, `dashboardStats`
  - `publishers`, `categories`, `authors`, `authorIds`, `initialCopyCount`
  - `activeMembers`, `availableCopies`
  - `criteria`
  - `fieldErrors` (`Map<String, String>`), `globalErrors` (`List<String>`), `globalError`
  - `flashSuccess`, `flashError`
  - `csrfToken`
- CSRF parameter name: `_csrf`.
- POST redirect targets and PRG behavior.
- Error mapping: validation → 400, not found → 404, business rule → 409, unexpected → 500.
- Date formats: ISO `yyyy-MM-dd` for inputs.
- Server-authoritative validation rules and error keys.
- Whether reference-data and copy-management screens are included.

### 10.3 Models, enums, and behavior

- `Book`, `Member`, `Loan`, `BookCopy`, `Author`, `Publisher`, `Category`, `Page<T>`, `DashboardStats`.
- Enums: `CopyStatus`, `MemberStatus`, `LoanStatus`, `LoanState`.
- Searches return empty pages/lists, never `null`.
- Get-by-id throws `NotFoundException`.
- Create operations return generated IDs.
- Expected failures throw `ValidationException` or `BusinessRuleException`.
- Database failures are `DataAccessException`.
- Person 2 owns overdue calculation and business eligibility; Person 1 provides SQL predicates and indexes.

## 11. Transaction Rules

Person 2 owns transaction boundaries. Person 1 provides DAO lock/update primitives and database constraints.

### Borrow

1. Begin transaction.
2. Lock the selected copy row with `SELECT ... FOR UPDATE`.
3. Validate copy availability and book existence.
4. Validate member exists and is active.
5. Compute `due_date = loan_date + 14 calendar days`; reject any client-supplied due date and verify no active loan exists for the copy.
6. Insert the loan with the computed due date.
7. Update the copy status to `BORROWED`; require one affected row.
8. Commit; roll back on any failure.
9. Partial unique index catches concurrent double-borrow; SQLState `23505` maps to a friendly message.

### Return

1. Begin transaction.
2. Lock the loan row with `SELECT ... FOR UPDATE`.
3. Reject missing, already returned, or inconsistent loans.
4. Lock and validate the copy.
5. Set return date and status; require one affected row.
6. Set the copy to `AVAILABLE`; require one affected row.
7. Commit; roll back on any failure.

### Book deletion

- Reject when `LoanDAO.existsByBookId` is true.
- Otherwise delete author associations, copies, and the book in one transaction.
- FK RESTRICT is the final backstop against deleting loan history.

### Concurrency

- Borrow and return use row locks and affected-row checks.
- Lock ordering must be agreed to avoid deadlocks.
- Database constraints remain authoritative even if service checks are bypassed.

## 12. UI/UX Plan

### Navigation

- Dashboard
- Books
- Members
- Borrow / Return
- Borrowing History
- Overdue Books
- Optional Reference Data

### Screens

- **Dashboard:** six real metrics, quick links, overdue summary.
- **Books:** search/filter, pagination, availability, Add/View/Edit/Delete.
- **Book form:** metadata, publisher/category, authors, initial copies; server validation with preserved values.
- **Book details:** metadata, authors, copies, status badges, copy actions, delete confirmation.
- **Members:** search/filter, pagination, status, active-loan count, history link.
- **Member form:** required fields, status, uniqueness feedback.
- **Borrow/Return:** active members, available copies, a read-only due date computed as loan date + 14 calendar days, active loans, return actions, overdue badges.
- **History:** member/state/date filters, pagination, empty state.
- **Overdue:** member/book/copy/due date/days overdue, pagination, empty state.
- **Errors:** friendly 403/404/500 pages; no internal details.

### UX standards

- Responsive layout for desktop and mobile.
- Accessible labels, fieldsets, focus states, and keyboard navigation.
- `aria-live` feedback, inline validation, submit-button disabling.
- Confirmation before destructive actions.
- Empty and loading states.
- No fake data or nonfunctional buttons.
- Escape all user-controlled output with JSTL tags/functions.
- No SQL or business rules in JSPs.

### Client-side validation

Person 1 implements required-field, format, length, and basic date checks for UX. Person 2’s server-side rules are authoritative. Client validation must not be the only protection.

## 13. Testing Strategy

### Person 1

- Schema creation and FK/CHECK/unique constraints.
- DAO CRUD, search, pagination, and result mapping.
- Active-loan uniqueness and history-protection constraints.
- The 14-calendar-day rule: due date equals loan date + 14; invalid arbitrary due dates are rejected by the database check.
- Generated keys, locking queries, and SQLState wrapping.
- UI behavior: empty states, validation messages, confirmation dialogs, responsive layout, accessibility, and output escaping.
- Manual UI checks against real endpoints.

### Person 2

- Service business rules and exception behavior.
- Controller request validation and view/redirect selection.
- Transaction commit/rollback and concurrency.
- Service/controller integration tests.
- Overall end-to-end integration.

### Joint acceptance

All 12 functions, dashboard, empty/error states, and the documented manual checklist.

### Commands (to run after tools are installed)

```powershell
mvn clean test
mvn clean verify -Pdb-tests
mvn clean package
```

Integration tests use a separate `library_test` database and are gated by test environment variables. No Testcontainers/Docker dependency.

### Status

Person 1 DAO tests exist and pass under the standalone JUnit Platform Console: 39 tests found, 39 successful, 0 failed, exit code 0. Main sources compile with `javac --release 17` (37 files), test sources compile (8 files), and all 15 JSPs compile with Tomcat `JspC` and JSTL 3.0.1 (0 errors). These are standalone-tool results, not Maven/Surefire results. No `mvn test`, `mvn package`, WAR deployment, or HTTP flow has been executed because Person 2's build and integration files do not exist.

## 14. Documentation Plan

| Document | Owner | Status |
|---|---|---|
| `README.md` | Person 2 final; Person 1 DB/UI sections | Planned |
| `docs/architecture.md` | Person 2; Person 1 reviews DB/UI | Planned |
| `docs/database-design.md` | Person 1 | **Updated for the implemented schema** |
| `docs/api-contract.md` | Split: Person 1 DAO/DB; Person 2 Service/Controller | Planned |
| `docs/testing.md` | Split: Person 1 DB/DAO/UI; Person 2 Service/Controller | Planned |
| `docs/implementation-plan.md` | Consolidated planning document | **Updated for the current implementation** |
| `docs/java-concepts-mapping.md` | Person 1; Person 2 reviews own-code rows | **Updated with source evidence** |
| `docs/runtime-toolchain-audit.md` | Person 1 | **Updated with implementation evidence** |
| `docs/person1-implementation-plan.md` | Person 1 | **Implemented; retained as the original work breakdown** |

## 15. Phases and Gates

| Phase | Person 1 | Person 2 | Gate |
|---|---|---|---|
| 0. Runtime/toolchain | Verify DB/JDBC tools; support install | Confirm Tomcat/Servlet target; integration setup | **PASS:** runtime and namespace verified; tools installed. Project build/deployment/test gate remains open. |
| 1. Inspection/plan | Consolidated plan, ownership, Java concepts doc | Review contracts | Plan approved; no code claims |
| 2. Schema/contracts | Complete: schema, seed, models, DAO interfaces, DB docs | Service interfaces and endpoint/view contract still pending | Schema applies to both databases; Person 1 contracts are frozen |
| 3. DAO layer | Complete: JDBC DAOs and 39 passing DAO tests | Services against DAO contracts; transaction logic pending | DAO/constraint tests pass standalone; no Maven/Surefire run |
| 4. UI + controllers | Complete: JSP/CSS/JS and client validation; all 15 JSPs compile | Servlets, services, filters pending | Screens are not wired to real endpoints yet |
| 5. Integration | Pending end-to-end defects | Own end-to-end integration | All 12 functions still unverified over HTTP |
| 6. Verification/docs | DB/DAO/UI standalone evidence complete | Service/integration evidence pending | Person 1 scope verified; full Definition of Done not met |

### Reporting after each phase

1. Files created or changed.
2. Features completed.
3. Commands and tests actually executed.
4. Errors or blockers.
5. Remaining work.

## 16. Risks and Open Decisions

1. **Runtime decision:** resolved in favor of Tomcat 10.1.61/Jakarta. If the university later requires Tomcat 9, the namespace and JSTL choices must be revisited before source is written.
2. **Project build blocker:** `pom.xml`, Maven Wrapper, `web.xml`, `context.xml`, and WAR deployment do not exist yet. Person 2 owns the initial build and integration setup.
3. **Test integration blocker:** JUnit 5 and Surefire are not integrated. The 39/39 DAO result comes from the standalone JUnit Platform Console and must not be reported as a Maven test result.
4. **Shared model authorship:** recommended Person 1 authors initial models; Person 2 approves.
5. **Reference data and copy management:** support screens are optional scope, not part of the 12 functions.
6. **Shared file ownership:** `pom.xml`, README, API contract, testing doc, and `web.xml` need single writers.
7. **No authentication/RBAC:** documented limitation.
8. **No connection pooling:** acceptable for the demo; potential future improvement.
9. **Date/timezone consistency:** application and database should use the same local date; the 14-day rule is calendar-day based.
10. **Seed data is destructive:** must not be run against real data.
11. **No Docker:** integration tests rely on local PostgreSQL.
12. **No false claims:** build, deployment, tests, and Java concept coverage require actual evidence.
13. **Local PostgreSQL startup:** the cluster is user-scope and starts at logon through the `LibraryManagementPostgres` scheduled task; it is not a Windows service.

## 17. Definition of Done

- All 12 business functions are implemented end to end.
- The three-tier MVC architecture is preserved.
- PostgreSQL is the actual persistence layer.
- SQL is executed through JDBC DAO classes with `PreparedStatement`.
- No ORM or prohibited Spring Boot layers are used.
- The UI does not access the database directly.
- Borrow and return operations preserve data consistency.
- Every loan has a due date exactly 14 calendar days after its loan date.
- Validation and user-friendly error handling are implemented.
- The project builds and deploys using the documented process.
- All reported test results are based on actual execution.
- API contracts are sufficient for Person 2 to integrate.
- `docs/java-concepts-mapping.md` reflects actual source evidence, not claims.

## 18. Next Action

Person 1 implementation is complete for the agreed scope. The remaining work belongs to Person 2:

1. Create the initial Maven project: `pom.xml`, Maven Wrapper, `web.xml`, `context.xml`, dependencies, and Surefire/JUnit configuration around the existing source tree.
2. Confirm the DAO/service/endpoint/view contracts and add the Controller, Service, filter, and transaction-boundary code.
3. Integrate the JSPs, CSS, and JavaScript into the Maven WAR with Jakarta Servlet 6.0, JSP 3.1, JSTL 3.0.1, and pgJDBC 42.7.14.
4. Run `mvn clean test` and `mvn clean package`, deploy the WAR to Tomcat 10.1.61, and verify all 12 business functions over HTTP.
5. Re-run the Phase 2 gate in `docs/runtime-toolchain-audit.md` only after the Maven build, deployment, and HTTP evidence exists.

Person 1's schema, DAO, UI, and standalone verification are complete; the project build, deployment, HTTP, and Maven/Surefire gates remain open. Maven, WAR, and HTTP success must not be claimed until Person 2 produces actual evidence. This document consolidates the earlier planning text and now reflects the implemented Person 1 state.
