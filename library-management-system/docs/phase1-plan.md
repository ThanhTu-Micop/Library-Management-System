# Phase 1 — Implementation Plan (for approval)

Status: plan only. No application code written yet.

## 1. Workspace and toolchain findings

| Item | Finding | Verified by |
|---|---|---|
| Workspace contents | Empty — no existing files to preserve | directory listing |
| OS | Ubuntu 24.04, no root access; `docker` installed but daemon not running | shell check |
| Internet | Available | HTTPS check |
| Java | Installed **OpenJDK 21.0.13 (Temurin)** into `~/tools/jdk` | `java -version` output |
| Maven | Installed **Apache Maven 3.9.16** into `~/tools/maven` | `mvn -version` output |
| PostgreSQL | **PostgreSQL 17.11.0** portable binaries in `~/tools/pg17`, cluster at `~/tools/pgdata`, running on port 5432, database `library_db` created | `pg_ctl status`, JDBC roundtrip test |
| JDBC driver | PostgreSQL JDBC **42.7.6** verified (JDBC is the only DB access path, matching the project constraint) | smoke test: create table, insert, select, drop |
| Tomcat | **Not installed in the workspace, and no Tomcat/Jakarta evidence exists anywhere in the repository** — see §3 (UNRESOLVED blocker) | workspace inspection, §3 |

All verification above is from actual executed commands (2026-10-24). A repeatable setup script lives at
`scripts/setup-dev-env.sh`. Note: the sandbox currently has OpenJDK 21.0.13 installed, but the confirmed
build/deployment target for this project is **JDK 17 throughout** (see §2); a JDK 17 installation will be
used for the build toolchain in Phases 2–6.

## 2. Confirmed technical decisions

1. **Java: JDK 17 throughout development and deployment.**
2. **Maven compiler:** `maven.compiler.release=17`.
3. **Database:** PostgreSQL.
4. **Database access:** plain JDBC and handwritten SQL through the DAO layer; **no ORM**
   (no Hibernate, JPA, MyBatis), no MongoDB.
5. **Architecture:** strict three-tier MVC (Presentation/Controller/Service/DAO/Database).
6. **Controller and database restrictions:** no Spring Boot; SQL lives only in DAO classes,
   never in JSPs or Servlets.
7. **Default borrowing period: 14 calendar days** from the loan date.
8. **Transaction management:** the **Service layer** owns the JDBC connection and transaction
   for any operation that modifies multiple related records (borrow, return, book create,
   book delete); see §7.
9. **Concurrency control:** `SELECT ... FOR UPDATE` row locking, where appropriate — used inside
   the borrow and return transactions before validating and mutating copy/loan rows.
10. **Active-loan invariant:** a PostgreSQL **partial unique index on `loans(copy_id)` with the
    predicate `WHERE return_date IS NULL`**, so a physical copy can never hold more than one
    active loan — even under a race — together with the row locking above.
11. **Active-loan definition (project-wide):** a loan is **active if and only if
    `return_date IS NULL`**. There is no manually maintained `OVERDUE` loan status — an active loan
    with `due_date < CURRENT_DATE` is overdue, computed from dates at query time. The `loans.status`
    column carries the state machine (`BORROWED`/`RETURNED`) for reporting and joins, but
    `return_date IS NULL` is the authoritative predicate used everywhere.
12. Packaging: `war` via `maven-war-plugin`; PostgreSQL driver `org.postgresql:postgresql:42.7.6`.
13. Authentication: **outside MVP scope**; will be documented as such, not claimed.

## 3. Tomcat compatibility — UNRESOLVED BLOCKER

**Status: UNRESOLVED. The target Tomcat runtime is not verified. Do not pick
`jakarta.servlet` or `javax.servlet` on a guess.**

### Evidence gathered (2026-10-24)

Per the pre-Phase-2 checklist, the existing workspace was inspected for deployment evidence:

| Check | Result |
|---|---|
| `pom.xml` with a Servlet API dependency | **Not found** — workspace contains no `pom.xml` |
| `web.xml` or any Servlet configuration | **Not found** |
| Servlet API imports/dependencies (`javax.servlet` / `jakarta.servlet`) | **Not found** in any file |
| Deployment configuration (Tomcat `setenv`, `conf/Catalina`, Docker files, WAR names, CI/CD) | **Not found** |
| University deployment instructions / environment configuration / provided docs | **Not found** in the workspace (the workspace contained only this plan document, `scripts/setup-dev-env.sh`, and `.git`) |
| Tomcat references in existing files | Only inside `docs/phase1-plan.md` itself, which is our own **proposal** from earlier drafts, not evidence — Proposal ≠ Evidence |

### What this means

- Whether the target runtime is Tomcat 10.1.x with **Jakarta Servlet 6.0 (`jakarta.servlet`)**
  or Tomcat 9.x with **Servlet 4.0 (`javax.servlet`)** is unknown. These namespaces are NOT
  mixable; choosing wrong later would force a rewrite of every controller import, the Maven
  dependency, `web.xml`, and deployment conventions.
- All Servlet-related decisions are therefore deferred: Maven Servlet dependency, controller
  package imports, `web.xml` version, and the Phase 6 deployment procedure (Tomcat version) are
  **placeholders** until the runtime is verified.
- Everything non-Servlet (schema, models, DAOs, services, transactions) is unaffected and can be
  designed per §2, but the gated items in §4 still prevent Phase 2 start.

### Exact information needed to resolve

1. The **Tomcat version** used for deployment by the university/teaching team
   (e.g., "Tomcat 10.1.x" or "Tomcat 9.x"), from an official assignment hand-out,
   lab-environment guide, or the provided hosting environment.
2. If none exists, a **written team decision** recorded here confirming the version seen in the
   actual lab environment: `Tomcat version: _______ (Jakarta 6.0 / javax 4.0 / other)`. Once either
   artifact is available, update this section, record the evidence, and lift the gate in §4.

## 4. Phase gate: Phase 2 may NOT start until the Servlet namespace is verified

- Phase 1 documentation updates: **may proceed** (this document was updated under that allowance).
- Phase 2 implementation: **MUST NOT start** until the Servlet namespace and target Tomcat runtime
  are verified per §3 — do not create `schema.sql`, `seed.sql`, `pom.xml`, model classes, or
  `api-contract.md` until then.
- If Tomcat compatibility remains unresolved, work stops after this plan update and the blocker is
  reported.
- No application code has been generated to date. Runtime compatibility has **not** been verified —
  no document or status should claim otherwise.

## 5. ERD

```
publishers                categories                authors
+--------------+          +---------------+         +--------------+
| publisher_id |          | category_id   |         | author_id    |
| name (nn)    |          | name (nn,uq)  |         | name (nn)    |
| contact_email|          | description   |         | biography    |
| phone        |          +---------------+         +--------------+
+--------------+

        books
        +------------------+
        | book_id          |
        | isbn (nn,uq)     |
        | title (nn)       |
        | publisher_id FK->publishers
        | category_id  FK->categories
        | publication_year |  CHECK (year between 1000 and current year)
        | description      |
        | created_at       |
        +------------------+
           |                |
           | 1            1 |
           | N            N |
        book_authors   book_copies
        +--------------+   +---------------+
        | book_id FK   |   | copy_id       |
        | author_id FK |   | book_id FK    |
        | PK(book_id,  |   | copy_code(uq) |
        |  author_id)  |   | status CHECK  |  status IN ('AVAILABLE','BORROWED','MAINTENANCE')
        +--------------+   +---------------+
                              | 1
                              | N
                           loans
                           +--------------+
                           | loan_id      |
                           | copy_id FK   |  partial UNIQUE index: no two open loans per copy
                           | member_id FK |  (unique(copy_id) WHERE return_date IS NULL)
                           | loan_date (nn)
                           | due_date (nn)| CHECK (due_date >= loan_date)
                           | return_date  | CHECK (return_date >= loan_date OR is null handled in service)
                           | status CHECK |  status IN ('BORROWED','RETURNED')
                           +--------------+
                              N
                              |
                              1
                           members
                           +---------------+
                           | member_id     |
                           | member_code   |  (unique)
                           | full_name (nn)|
                           | email (unique when provided)
                           | phone         |
                           | address       |
                           | status CHECK  |  status IN ('ACTIVE','INACTIVE')
                           | created_at    |
                           +---------------+
```

Key invariants enforced by the database (not just Java):

- `loans`: `CREATE UNIQUE INDEX ... ON loans (copy_id) WHERE return_date IS NULL` — a copy can never
  have two open loans, even under a race (belt) — plus `SELECT ... FOR UPDATE` row locking during
  borrow/return transactions (suspenders).
- `loans`: CHECK `(due_date >= loan_date)`.
- `book_copies.status`: CHECK constraint; `loans.status` CHECK constraint.
- `books.publication_year`: CHECK `(publication_year IS NULL OR publication_year <= extract(year from current_date)::int)`.

## 6. Project tree

```
library-management-system/
├── pom.xml
├── README.md
├── .gitignore
├── docs/
│   ├── phase1-plan.md          (this file)
│   ├── architecture.md
│   ├── database-design.md
│   ├── api-contract.md
│   └── testing.md
├── scripts/
│   └── setup-dev-env.sh        (repeatable toolchain bootstrap, used in this VM)
├── src/
│   ├── main/
│   │   ├── java/com/library/
│   │   │   ├── config/DatabaseConnection.java
│   │   │   ├── model/{Book,Author,Publisher,Category,BookCopy,Member,Loan}.java
│   │   │   ├── dao/{BookDAO,AuthorDAO,PublisherDAO,CategoryDAO,BookCopyDAO,MemberDAO,LoanDAO}.java
│   │   │   ├── service/{BookService,MemberService,LoanService}.java
│   │   │   ├── service/impl/{BookServiceImpl,MemberServiceImpl,LoanServiceImpl}.java
│   │   │   ├── controller/{BookServlet,MemberServlet,LoanServlet,DashboardServlet}.java
│   │   │   └── exception/{ServiceException,ValidationException,NotFoundException,...}.java
│   │   ├── resources/db/{schema.sql,seed.sql}
│   │   └── webapp/
│   │       ├── assets/css/style.css
│   │       ├── assets/js/main.js
│   │       ├── WEB-INF/
│   │       │   ├── web.xml
│   │       │   └── views/{common/,dashboard.jsp,books/,members/,loans/}
│   └── test/java/com/library/   (unit tests for business rules; JDBC integration tests)
```

## 7. Transaction / connection design

- `DatabaseConnection.getConnection()` opens a JDBC connection from `DB_URL`/`DB_USER`/`DB_PASSWORD`
  environment variables (with a safe documented fallback for local dev).
- Data-access methods that can run standalone use their own connection.
- **Transactional flows (borrow, return, book create, book delete)** open ONE connection, set
  `autoCommit=false`, perform `SELECT ... FOR UPDATE` locking, do all writes, commit or roll back.
  To keep layers clean, DAO write/read methods have overloads accepting a `Connection`; the *Service*
  is the only class that manages the connection. A `Connection` never crosses the Service→Controller
  boundary, and no DAO has business rules. This implements decision §2-8 (Service-layer
  transactions) and decision §2-9 (`FOR UPDATE` locking).

## 8. Mapping of the 12 functions to layers

| # | Function | UI (page) | Controller (servlet) | Service (method) | DAO methods | Tables touched |
|---|---|---|---|---|---|---|
| 1 | Add Book | books/list.jsp + book-form.jsp | BookServlet POST `/books` | createBook | insertBook, insertBookAuthors, insertCopies | books, book_authors, book_copies |
| 2 | Update Book | book-form.jsp | BookServlet POST `/books/update` | updateBook | updateBook, replaceBookAuthors, listCopiesByBook | books, book_authors |
| 3 | Delete Book | books/list.jsp (confirm dialog) | BookServlet POST `/books/delete` | deleteBook | bookHasLoans, deleteCopies, deleteBookAuthors, deleteBook | book_copies, book_authors, books |
| 4 | Search Books | books/list.jsp | BookServlet GET `/books` | searchBooks | searchBooks (title/ISBN/author/category, LIMIT/OFFSET) | books (+ joins) |
| 5 | View Book Details | book-details.jsp | BookServlet GET `/books/view` | getBookById | getBookById, getAuthorsByBook, getPublisher, getCategory, listCopiesByBook | all book-related |
| 6 | Add Member | members/list + member-form.jsp | MemberServlet POST `/members` | createMember | insertMember | members |
| 7 | Update Member | member-form.jsp | MemberServlet POST `/members/update` | updateMember | getMemberById, updateMember, validate not inactivating with open loan | members |
| 8 | Search Members | members/list.jsp | MemberServlet GET `/members` | searchMembers | searchMembers (code/name/email/phone) | members |
| 9 | Borrow Book | loans/borrow.jsp | LoanServlet POST `/loans/borrow` | borrowBook (tx) | memberStatus, lockCopy, copyHasActiveLoan, insertLoan, setCopyStatus | loans, book_copies, members |
| 10 | Return Book | loans/active.jsp | LoanServlet POST `/loans/return` | returnBook (tx) | lockLoan, updateLoanReturned, setCopyStatus | loans, book_copies |
| 11 | Borrowing History | loans/history.jsp | LoanServlet GET `/loans/history` | getBorrowingHistory | getLoansByMember (join book/copy/member, filters) | loans + joins |
| 12 | Overdue Books | loans/overdue.jsp | LoanServlet GET `/loans/overdue` | getOverdueLoans | getOverdueLoans (return_date IS NULL AND due_date < current_date) | loans + joins |

Dashboard metrics (titles, copies, available, active loans, overdue, members) are six aggregate
queries executed by DashboardService/DashboardServlet — counted, not invented.

## 9. Error-handling contract (summary)

- Search methods return **empty lists**, never null.
- Single lookups (`getBookById`) throw `NotFoundException` when absent (no null returns).
- Create/update return the new id (`int`) or throw `ValidationException` on invalid input,
  `ServiceException` for rule violations (deleting a lent book, inactive-member borrow, double return).
- DAO layer throws checked `DAOException` wrapping `SQLException`; Service translates to typed
  exceptions; Controllers map them to friendly JSP error messages. Full signatures will be pinned in
  `docs/api-contract.md` in Phase 2 before either side implements.

## 10. Phase order (per specification §13)

1. **Phase 1 (this)** — inspect, verify toolchain, present plan. ✅ done (this document).
2. **Phase 2** — schema.sql + seed.sql, model classes, api-contract.md. Applies schema to
   `library_db` and verifies constraints. 🔒 **GATED** — blocked by the unresolved Servlet/Tomcat
   runtime in §3/§4; do not start until resolved.
3. **Phase 3** — DatabaseConnection + DAOs + DAO/JDBC integration tests against the running PostgreSQL.
4. **Phase 4** — Services + transaction flows + Servlets; compile with Maven. Servlet imports and
   dependency follow the runtime verified in §3.
5. **Phase 5** — JSP/CSS/JS UI, all 12 screens wired to real endpoints.
6. **Phase 6** — Tomcat download (version per §3) + deploy verification, full test matrix, docs
   updated with real results.

## 11. Open questions for the other team members / approver

1. **BLOCKER — resolved first.** The target Tomcat runtime is UNRESOLVED (§3). Provide the official
   Tomcat version evidence from the university/lab environment, or an explicit team decision
   (Tomcat 10.1.x / Jakarta 6.0 vs Tomcat 9.x / javax 4.0), recorded in §3 before Phase 2 starts.
2. ~~Confirm Java toolchain~~ — **resolved**: JDK 17 throughout with `maven.compiler.release=17`
   (§2). A JDK 17 installation will be used for builds going forward.
3. ~~Default loan period~~ — **resolved**: **14 calendar days** from the loan date (§2).
