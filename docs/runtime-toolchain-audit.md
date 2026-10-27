# Runtime and Toolchain Audit

| Field | Value |
|---|---|
| Project | Library Management System |
| Audit date | 2026-10-26 |
| Report version | 3.0 |
| Auditor scope | Person 1: database, SQL, JDBC/DAO, and UI/UX |
| Workspace | `C:\Users\BINHND\Desktop\Library-Management-System-main\Library-Management-System-main\library-management-system` |
| Audit type | Environment inspection plus Person 1 implementation verification |
| Changes made | Person 1 source, schema, seed, JSPs, CSS/JS, tests, and documentation were created; the runtime toolchain was previously installed |
| Person 1 gate | **PASS** for the agreed standalone scope |
| Full Phase 2 gate | **BLOCKED** — Maven build, WAR deployment, HTTP flows, and Surefire integration are not available |

> **Gate verdict: Person 1 scope is implemented and verified with standalone tools. Full Phase 2 remains BLOCKED.** The project still has no `pom.xml`, Maven Wrapper, `web.xml`, `context.xml`, Controllers, Services, filters, WAR, deployment, or HTTP evidence. Maven, WAR, and HTTP success must not be claimed until Person 2 supplies actual results.

## 1. Executive Summary

### Installed and verified

- **Temurin JDK `17.0.20.1+1`** at `C:\Users\BINHND\dev\jdk-17`; `java` and `javac` report `17.0.20.1`.
- **Apache Maven `3.9.16`** at `C:\Users\BINHND\dev\apache-maven-3.9.16`; `mvn -v` reports Java `17.0.20.1`. Maven was not used to build this project.
- **Apache Tomcat `10.1.61`** at `C:\Users\BINHND\dev\apache-tomcat-10.1.61`; the Jakarta namespace and stock JSP runtime were previously verified. Tomcat `JspC` was used to compile the project JSPs.
- **PostgreSQL `16.15`** at `C:\Users\BINHND\dev\pgsql-16.15`; the local cluster runs on `127.0.0.1:5432`.
- **Databases `library_db` and `library_test`**, both owned by the least-privilege application role `library_app`.
- **PostgreSQL JDBC `42.7.14`** at `C:\Users\BINHND\dev\lib\postgresql-42.7.14.jar`; used by the standalone DAO tests.
- **JUnit Platform Console Standalone `1.14.4`** downloaded outside the workspace; used to run the DAO tests.
- **JSTL 3.0.1 API and implementation JARs**, plus Apache Ant `1.10.15` and Tomcat `juli`, downloaded outside the workspace for `JspC`.

### Implemented in the workspace

- 37 main Java source files, compiled to 37 class files.
- 9 DAO interfaces and 9 JDBC implementations, including `BookAuthorDAO` and `JdbcBookAuthorDAO`.
- 9 model classes, 4 enums, and 3 search-criteria classes.
- 8 test source files with 7 DAO test classes; 39 tests pass under the standalone JUnit console.
- 15 JSP files, all compiled by Tomcat `JspC`.
- `schema.sql`, `seed.sql`, CSS, and vanilla JavaScript.
- Updated database, implementation, Java-concept, and audit documentation.

### Still missing or blocked

- No `pom.xml`, Maven Wrapper, `.mvn\`, or project dependency graph.
- No `web.xml`, `context.xml`, Controllers, Services, filters, or `AppStartupListener`.
- No project WAR, Tomcat project deployment, or HTTP flow.
- No Maven/Surefire integration or `mvn test` result.
- No university requirements document confirming the selected Tomcat/Jakarta stack.
- PostgreSQL is not a Windows service; the logon task has been test-run but an actual reboot has not been exercised.

### Selected runtime baseline

The project owner approved the stable baseline on 2026-10-24:

| Component | Selected version |
|---|---|
| Java | Temurin JDK 17.0.20.1+1 |
| Maven | 3.9.16 |
| Tomcat | 10.1.61 |
| Servlet API | Jakarta Servlet 6.0 |
| JSP/JSTL | JSP 3.1 / JSTL 3.0.1 (`jakarta.tags.*`) |
| PostgreSQL | 16.15 |
| PostgreSQL JDBC | 42.7.14 |
| JUnit | 5.14.4 console standalone for interim evidence |
| Compilation target | Java 17 (`javac --release 17`) |

This is a project-owner decision. It is not evidence that the university requires or accepts this stack.

## 2. Scope, Method, and Limits

### Method

Person 1 verification used actual command execution:

- `java -version`, `javac -version`, `mvn -v`, `psql --version`, and Tomcat `version.bat`.
- `javac --release 17` for main and test sources.
- JUnit Platform Console against `library_test`.
- Tomcat `JspC` with JSTL 3.0.1 JARs for all JSPs.
- PostgreSQL DDL application and read-only metadata/count queries on both databases.
- Constraint probes inside rolled-back transactions.
- A static EL property checker against the JSPs and model classes.
- Source and workspace scans for secrets and stale `javax`/legacy JSTL URIs.

### Limits

- No Maven build exists, so imports, `pom.xml`, `web.xml`, WAR contents, and Surefire behavior could not be inspected.
- No project WAR has been built or deployed.
- No JUnit test has run inside Maven.
- No Controller, Service, filter, or HTTP flow exists, so no 12-function end-to-end check was possible.
- The machine has not been rebooted; the PostgreSQL logon task was registered and test-run successfully.
- No university assignment, grading rubric, Tomcat instruction, or operating-system mandate was found.
- The PostgreSQL archive checksum is recorded locally but was not compared with an official publisher checksum.

## 3. Installed Toolchain

### 3.1 Versions and locations

| Component | Installed version | Location | Verification |
|---|---|---|---|
| JDK | Temurin `17.0.20.1+1` | `C:\Users\BINHND\dev\jdk-17` | `java -version`; `javac -version`; `javac --release 17` build |
| Maven | `3.9.16` | `C:\Users\BINHND\dev\apache-maven-3.9.16` | `mvn -v`; not used for the project build |
| Tomcat | `10.1.61` | `C:\Users\BINHND\dev\apache-tomcat-10.1.61` | `version.bat`; `JspC` compilation |
| PostgreSQL | `16.15` | `C:\Users\BINHND\dev\pgsql-16.15` | `psql --version`; schema/seed and constraint checks |
| pgJDBC | `42.7.14` | `C:\Users\BINHND\dev\lib\postgresql-42.7.14.jar` | Compiled and executed DAO tests |
| JUnit | `5.14.4` console standalone | Outside the workspace | 39/39 DAO tests passed |
| JSTL | `3.0.1` API + implementation | Outside the workspace | Used by Tomcat `JspC` |
| Ant | `1.10.15` | Outside the workspace | Required by `JspC` at build time |

### 3.2 Environment variables

The following values are persisted at **User** scope:

| Variable | Value |
|---|---|
| `JAVA_HOME` | `C:\Users\BINHND\dev\jdk-17` |
| `MAVEN_HOME` | `C:\Users\BINHND\dev\apache-maven-3.9.16` |
| `CATALINA_HOME` | `C:\Users\BINHND\dev\apache-tomcat-10.1.61` |
| `PGDATA` | `C:\Users\BINHND\dev\pgdata-library` |

The User `PATH` contains the JDK, Maven, Tomcat, and PostgreSQL `bin` directories. A fresh environment previously verified `java`, `javac`, `mvn`, `psql`, and Tomcat `version.bat`.

Maven reports platform encoding `Cp1252`. Person 2's `pom.xml` must set UTF-8 explicitly (`project.build.sourceEncoding=UTF-8`); Person 1's standalone compilation used `-encoding UTF-8`.

### 3.3 Local operations scripts

| Script | Purpose |
|---|---|
| `C:\Users\BINHND\dev\scripts\start-postgres.ps1` | Starts PostgreSQL if it is not already ready; exits `0` when ready |
| `C:\Users\BINHND\dev\scripts\stop-postgres.ps1` | Fast-stops the local cluster |
| `C:\Users\BINHND\dev\scripts\start-tomcat.cmd` | Starts Tomcat with the installed JDK and `CATALINA_HOME` |
| `C:\Users\BINHND\dev\scripts\stop-tomcat.cmd` | Gracefully shuts down Tomcat |

## 4. Verification Evidence

### 4.1 Java and Maven

```text
openjdk version "17.0.20.1" 2026-08-18
OpenJDK Runtime Environment Temurin-17.0.20.1+1 (build 17.0.20.1+1)
OpenJDK 64-Bit Server VM Temurin-17.0.20.1+1 (build 17.0.20.1+1, mixed mode, sharing)

javac 17.0.20.1

Apache Maven 3.9.16
Java version: 17.0.20.1, vendor: Eclipse Adoptium
Maven home: C:\Users\BINHND\dev\apache-maven-3.9.16
Default locale: en_US, platform encoding: Cp1252
```

### 4.2 Main and test compilation

```powershell
javac --release 17 -encoding UTF-8 -cp postgresql-42.7.14.jar -d <temp>\lms-main-classes (37 main .java files)
```

```text
MAIN_COMPILE_OK
MAIN_SOURCE_FILES=37
MAIN_CLASS_FILES=37
```

```powershell
javac --release 17 -encoding UTF-8 -cp "<main-classes>;postgresql-42.7.14.jar;junit-platform-console-standalone-1.14.4.jar" -d <temp>\lms-test-classes (8 test .java files)
```

```text
TEST_COMPILE_OK
TEST_SOURCE_FILES=8
TEST_CLASS_FILES=8
```

`--release 17` targets class-file major version 61 and prevents accidental use of newer JDK APIs.

### 4.3 Standalone DAO tests

The test database URL was supplied with `TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/library_test`; `DB_USER` and `DB_PASSWORD` were supplied through environment variables. The password was read from the external secrets file and was never printed.

```text
[         0 containers failed     ]
[        39 tests found           ]
[        39 tests successful      ]
[         0 tests failed          ]
EXIT_CODE=0
```

The 39 tests cover book CRUD/search/filters, member uniqueness/search/active-loan counts, loan borrow/return/overdue/history queries, dashboard metrics, book-copy lifecycle, book-author replacement, and reference data.

This is **standalone JUnit evidence**, not a Maven/Surefire result.

### 4.4 JSP compilation

Tomcat `JspC` was run with Tomcat 10.1.61 libraries, Apache Ant 1.10.15, `tomcat-juli.jar`, the compiled main classes, pgJDBC, and JSTL 3.0.1 API/implementation JARs.

```text
INFO: Generation completed with [0] errors in [2562] milliseconds
EXIT_CODE=0
JAVA_FILES=15
CLASS_FILES=15
```

All 15 JSP files under `WEB-INF/views` compiled, including shared `head.jsp`/`foot.jsp`, dashboard, books, members, loans, and error pages. JSTL taglib URIs use `jakarta.tags.core` and `jakarta.tags.functions`; no legacy `java.sun.com` URI or `javax.servlet` import remains.

A JSP compile does not prove runtime rendering, request routing, or HTTP behavior. Those remain Person 2 work.

### 4.5 PostgreSQL schema, seed, and constraints

```text
PostgreSQL 16.15
current_database | encoding
library_db       | UTF8
library_test     | UTF8
```

Both databases were rebuilt with `schema.sql` and `seed.sql` using `psql -v ON_ERROR_STOP=1`.

```text
metric            | library_db | library_test
tables            | 8          | 8
named_indexes     | 11         | 11
publishers        | 3          | 3
categories        | 4          | 4
authors           | 4          | 4
books             | 4          | 4
book_copies       | 8          | 8
members           | 3          | 3
loans             | 3          | 3
book_authors      | 4          | 4
```

`named_indexes` counts the `CREATE INDEX` statements (search indexes). In addition, inline unique constraints produce `books_isbn_key`, `book_copies_copy_code_key`, and `members_member_code_key`. Three indexes that duplicated those unique constraints (`idx_books_isbn`, `idx_book_copies_copy_code`, `idx_members_member_code`) were removed (see `E08`).

Constraint probes ran inside a transaction that was rolled back:

```text
category case-insensitive duplicate: rejected
case-insensitive duplicate email: rejected
blank email: rejected
ISBN length 12: rejected
ISBN length 13 and 20: accepted
due date loan_date + 15: rejected
second active loan for the same copy: rejected
```

The same seven checks passed on both `library_db` and `library_test`; no probe rows were committed.

### 4.5.1 Manual PK, FK, and relation matrix

`src/test/resources/db/manual-relation-test.sql` is a reproducible manual test script. It creates one valid relation chain, runs every probe inside a single transaction, records each outcome in a temporary result table, prints a summary and detail list, and ends with `ROLLBACK`. It does not depend on a particular identifier range because probe values are derived from `txid_current()`.

| Probe group | Probes | Expected behavior |
|---|---:|---|
| Primary keys | 10 | Duplicate IDs rejected with `23505`; null components rejected with `23502` |
| Unique constraints | 5 | Duplicate ISBN, copy code, member code, case-insensitive email, and case-insensitive category name rejected with `23505` |
| Foreign keys (insert) | 7 | Every child column rejects a missing parent with `23503` |
| Foreign keys (update) | 2 | Updating a child to a missing parent is rejected with `23503` |
| `ON DELETE RESTRICT` | 6 | Publisher, category, book, copy, member, and author deletion is blocked while referenced |
| `ON DELETE CASCADE` | 2 | Deleting a book succeeds and removes its `book_authors` rows |
| Active-loan partial unique index | 4 | A second active loan for one copy is rejected with `23505`; returning the first loan allows a new active loan |
| `CHECK` constraints | 11 | Short/long ISBN, invalid year, invalid statuses, wrong due date, invalid return dates, and blank email are rejected with `23514` (long ISBN with `22001`) |
| Orphan check | 1 | All seven relations contain zero orphan rows after the probes |

Actual result on `library_test` (2026-10-26):

```text
status | tests
PASS   |    48
```

```text
PSQL_EXIT=0
```

The seven foreign keys and their delete actions are:

| Child column | Parent | `ON DELETE` |
|---|---|---|
| `books.publisher_id` | `publishers.publisher_id` | `RESTRICT` |
| `books.category_id` | `categories.category_id` | `RESTRICT` |
| `book_copies.book_id` | `books.book_id` | `RESTRICT` |
| `loans.copy_id` | `book_copies.copy_id` | `RESTRICT` |
| `loans.member_id` | `members.member_id` | `RESTRICT` |
| `book_authors.book_id` | `books.book_id` | `CASCADE` |
| `book_authors.author_id` | `authors.author_id` | `RESTRICT` |

Rollback was verified: `library_test` returned to its pre-test state (`publishers=3`, `categories=4`, `authors=4`, `books=0`, `book_copies=0`, `members=2`, `loans=0`, `book_authors=0`), with zero leftover `MANUAL-*` rows and zero leftover temporary tables.

### 4.6 Static EL property check

A small checker scanned every JSP expression and resolved model property references against the compiled classes.

```text
JSP files scanned: 15
EL expressions scanned: 320
Model property references checked: 214
EL_PROPERTY_CHECK_OK
```

The first run found `${...Page.hasNext}` and `${...Page.hasPrevious}`. These are not JavaBeans properties because `Page` exposes `hasNext()`/`hasPrevious()` methods, not `getHasNext()`/`isHasNext()` getters. The affected book, member, history, and overdue pagination blocks were changed to the existing `first` and `last` bean properties. The re-run passed.

A direct Jakarta EL evaluation confirmed the corrected properties and enum comparisons:

```text
p.first=false
p.last=true
not p.last=false
m.status == 'ACTIVE'=true
m.status == 'INACTIVE'=false
```

### 4.7 Secret hygiene

- Real database passwords are stored only under `C:\Users\BINHND\dev\secrets`, outside the workspace.
- The directory and password files are restricted to `MY-PC\BINHND`; inherited access was removed.
- A workspace scan found no credential value, JDBC URL with an embedded password, `.env` file, `db.properties`, or secret-looking file. The only `DB_PASSWORD=` text is this audit's own prose, not a credential assignment.
- Password values were never printed in commands, logs, or this report.
- The workspace is not a Git repository, so no secret can have been committed yet. A `.gitignore` is still required before initialization.

## 5. Compatibility Matrix

Status values: **Verified**, **Partially verified**, **Missing**, **Unverified**, **Blocked**.

| Component | Current state | Required state | Status | Evidence / action |
|---|---|---|---|---|
| Operating system | Windows 11 Pro 64-bit, non-admin | Runs JDK 17, Maven, PostgreSQL, Tomcat | **Verified** | All tools run locally; no course OS mandate found |
| JDK 17 installation | Temurin `17.0.20.1+1`; `JAVA_HOME` and `PATH` set | Standalone JDK 17 used by Maven | **Verified** | `java`, `javac`, and `mvn -v` report 17 |
| Maven installation | `3.9.16` on User `PATH` | Maven 3.9.x | **Verified** | `mvn -v` |
| Maven project configuration | No `pom.xml` or wrapper | WAR packaging, release 17, UTF-8, Jakarta APIs, pgJDBC, JUnit/Surefire | **Missing** | Person 2 owns the build |
| Java compilation target | Standalone `javac --release 17` | `maven.compiler.release=17`; class-file major version 61 | **Partially verified** | 37 main and 8 test files compiled; POM still missing |
| PostgreSQL server | `16.15` local cluster on `127.0.0.1:5432` | PostgreSQL 16.x with application role and databases | **Verified** | Schema/seed applied; SQL metadata and constraint probes passed |
| PostgreSQL JDBC | `42.7.14` JAR | Driver on project runtime classpath | **Partially verified** | DAO tests executed; POM dependency still missing |
| Tomcat installation | `10.1.61` installed | Tomcat 10.1.x | **Verified** | `version.bat`; previous HTTP 200 on stock pages |
| Servlet namespace | Tomcat exposes `jakarta.servlet.*` | Project uses Jakarta Servlet 6.0 consistently | **Partially verified** | Runtime namespace confirmed; no Controller source or POM dependency yet |
| JSP runtime | 15 project JSPs compiled by `JspC` | Project JSPs compile and render | **Partially verified** | 15/15 compile with 0 errors; no deployment/render |
| JSTL | JSTL 3.0.1 JARs used for `JspC` | Correct Jakarta JSTL dependency and taglib URIs | **Partially verified** | `jakarta.tags.*` URIs compile; WAR dependency pending |
| WAR packaging | No `pom.xml`, no WAR | Maven WAR with JSPs, classes, assets, `WEB-INF` | **Missing** | Person 2 creates the build |
| Tomcat project deployment | Only stock Tomcat apps deployed | Project WAR deploys and serves routes | **Missing** | No project WAR |
| JUnit 5/Surefire | Console standalone only | JUnit 5 + Surefire in Maven build | **Partially verified** | 39/39 standalone tests pass; no Surefire execution |
| Project tests | 7 DAO test classes | Actual executed tests with reports | **Partially verified** | Standalone console evidence; no Maven reports |
| Database schema/seed | `schema.sql`, `seed.sql` applied | Constraints, indexes, demo data | **Verified** | 8 tables, 14 indexes, seed counts, seven constraint probes |
| 14-calendar-day rule | Schema check and DAO persistence implemented | Service computes due date; DB check enforces it; UI displays it | **Partially verified** | Constraint probe rejects +15; generated-date DAO test passes; Service not present |
| UI assets and JSPs | 15 JSPs, `app.css`, `app.js` | Screens compile and render against real endpoints | **Partially verified** | JSP compile passed; no HTTP render |
| Static EL properties | 214 references checked | No missing model property | **Verified** | Checker returned `EL_PROPERTY_CHECK_OK` after fixing pagination properties |
| Git repository | Not initialized | Repository with `.gitignore` | **Missing** | Non-blocking; initialize before sharing code |
| Secrets hygiene | Password files outside workspace; restricted ACLs | No secrets in tracked files | **Verified** | Workspace scan found no credential value or secret file; only prose mentions the variable name |
| Course runtime requirement | No university document found | Confirmed target or explicit approval | **Decision required** | Project-owner approval exists; course acceptance unverified |
| PostgreSQL binary checksum | Local SHA-256 recorded | Publisher checksum or equivalent integrity evidence | **Unverified** | No official checksum endpoint used |
| Reboot autostart | Logon task registered and test-run | PostgreSQL available after reboot | **Partially verified** | Actual reboot not exercised |
| Maven encoding | Platform `Cp1252` | UTF-8 build configuration | **Missing** | Set `project.build.sourceEncoding=UTF-8` in `pom.xml` |
| Browser/network | Edge and Chrome installed; Maven Central/PostgreSQL/Apache reachable | Browser UI checks and dependency downloads | **Verified** | Previous environment audit and successful downloads |

## 6. Gate Assessment

### 6.1 Person 1 scope

| Gate requirement | Status | Evidence |
|---|---|---|
| Schema, seed, constraints, and indexes exist | **PASS** | Applied to both databases; 8 tables, 14 named indexes |
| Constraint behavior is enforced | **PASS** | Seven rollback-safe probes passed on both databases |
| Main and test source compile on JDK 17 | **PASS** | `javac --release 17`: 37 main, 8 test files |
| DAO tests pass against `library_test` | **PASS (standalone)** | 39/39 tests, 0 failures, exit code 0 |
| JSPs compile with JSTL 3.0.1 | **PASS (standalone)** | Tomcat `JspC`: 15 Java files, 15 classes, 0 errors |
| JSP model properties exist | **PASS** | 214 references checked; no missing property |
| No secrets in the workspace | **PASS** | Scan found no credential value or secret file; only prose mentions the variable name |
| Documentation reflects actual state | **PASS** | `database-design.md`, `implementation-plan.md`, `java-concepts-mapping.md`, and this audit updated |

### 6.2 Full Phase 2 / integration

| Gate requirement | Status | Reason |
|---|---|---|
| 1. JDK 17 and Maven build configuration are consistent | **BLOCKED** | JDK and Maven are installed, but no `pom.xml`, wrapper, release 17 setting, or build exists |
| 2. PostgreSQL version and JDBC driver compatibility are established | **PASS** | PostgreSQL 16.15, pgJDBC 42.7.14, schema/seed, and DAO tests verified locally |
| 3. Target Tomcat version is confirmed or approved | **PASS (project owner)** | Tomcat 10.1.61 selected and installed; no university document confirms it |
| 4. Servlet namespace and API version match the target runtime | **PARTIAL** | Tomcat runtime is Jakarta and JSPs compile; no Controller source or POM dependency exists |
| 5. JSP, WAR packaging, and deployment configuration are compatible | **BLOCKED** | JSPs compile, but no `web.xml`, `context.xml`, WAR, or project deployment exists |
| 6. Required development and test tools are identified | **PARTIAL** | JDK, Maven, PostgreSQL, Tomcat, pgJDBC, JUnit, JSTL, and Ant are available; Maven/Surefire integration is missing |
| 7. All 12 functions work over HTTP | **BLOCKED** | No Controllers, Services, filters, or deployment |

### Verdict

**Person 1 scope: PASS.** The database, DAO, UI source, DAO tests, JSP compilation, EL property check, and secret hygiene are verified with actual command output.

**Full Phase 2: BLOCKED.** The project cannot be built, deployed, or exercised over HTTP yet. The remaining work is Person 2's Maven scaffold, Controller/Service/filter implementation, WAR deployment, and HTTP verification.

## 7. Remaining Blockers and Ownership

### Person 2 — Controller, Service, integration

- Create `pom.xml`, Maven Wrapper, `.mvn\`, and the Maven directory configuration around the existing source tree.
- Set `maven.compiler.release=17`, UTF-8 encoding, WAR packaging, and compatible plugin versions.
- Add Jakarta Servlet 6.0/JSP 3.1/JSTL 3.0.1 dependencies, pgJDBC `42.7.14`, JUnit `5.14.4`, and Surefire.
- Create `web.xml`, `context.xml`, filters, startup listener, Controllers, Services, and transaction boundaries.
- Run `mvn clean test` and `mvn clean package`; deploy the WAR to Tomcat 10.1.61.
- Verify the 12 business functions over HTTP and record the results.

### Person 1 — completed follow-up

- Keep the schema, seed, models, DAO implementations, JSPs, CSS/JS, DAO tests, and documentation aligned with any agreed contract change.
- Fix defects found during Person 2 integration without editing Person 2-owned files.
- Update this audit after integration evidence exists.

### Shared / external

- Confirm whether the university accepts Tomcat 10.1/Jakarta.
- Exercise an actual reboot to confirm the PostgreSQL logon task.
- Add `.gitignore` and initialize Git before code sharing.
- Re-run the full gate only after a successful Maven build, deployment, JSP render, and HTTP flow.

## 8. Next Steps, in Priority Order

1. **Person 2 creates the Maven project.**
   - `pom.xml`, Maven Wrapper, `src\main\webapp\WEB-INF\web.xml`, and `META-INF\context.xml`.
   - `maven.compiler.release=17`, `project.build.sourceEncoding=UTF-8`, WAR packaging.
   - Jakarta Servlet 6.0 (provided), JSP 3.1, JSTL 3.0.1, pgJDBC 42.7.14, JUnit 5.14.4, Surefire.
2. **Verify the project build.**
   - `mvn -v` uses JDK 17.
   - `mvn clean test` runs Surefire.
   - `mvn clean package` produces a WAR; inspect `WEB-INF\classes`, `WEB-INF\lib`, and JSP assets.
3. **Implement the integration layer.**
   - Controllers, Services, filters, CSRF, transaction boundaries, request attributes, and redirects.
4. **Deploy the WAR to Tomcat.**
   - Deploy, request the application root, render the JSPs, and verify no class/namespace errors.
5. **Verify all 12 functions over HTTP.**
   - Books, members, borrow, return, history, overdue, and dashboard.
6. **Re-run this audit.**
   - Replace **BLOCKED** and **PARTIAL** rows with actual Maven, deployment, and HTTP evidence.

## 9. Pre-Install Findings Retained for Context

- **SQL Developer JAR trap:** `jakarta.servlet-api-4.0.2.jar` contains `javax.servlet.*`, and `jakarta.servlet.jakarta.servlet-api.jar` is an empty placeholder. Use Maven coordinates, not IDE JARs.
- **No course document:** no university assignment, rubric, or Tomcat instruction was found. The selected stack is a project-owner decision.
- **Non-admin Windows:** the toolchain is installed per-user under `C:\Users\BINHND\dev`; PostgreSQL and Tomcat are not Windows services.
- **No Docker:** integration tests use the local PostgreSQL cluster, not Testcontainers.
- **No secrets printed:** password values were never displayed, logged, or written into the workspace.

## 10. Approval Statement

**Person 1 scope is approved for handoff.** The schema, seed, models, DAO layer, JSP/CSS/JS, DAO tests, JSP compilation, EL property check, and documentation have actual execution evidence.

**Full Phase 2 is not approved.** The project still cannot satisfy the integration gate because:

- No Maven build exists.
- No WAR has been deployed.
- No project JSP has rendered over HTTP.
- No Maven/Surefire test has run.
- No Controller/Service/HTTP flow exists.
- The university requirement for Tomcat/Jakarta is not documented.

Maven, WAR, deployment, and HTTP success must not be claimed until Person 2 produces the corresponding evidence.
