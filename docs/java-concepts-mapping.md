# Java Concepts Mapping

> Related plan: [Consolidated Implementation Plan](implementation-plan.md)

## Purpose

This document maps the nine Java course topics to the Library Management System. Its purpose is to demonstrate the Java concepts actually used by the implementation without adding artificial code or claiming coverage that the source does not support.

The mapping is intentionally evidence-based:

- **Implemented** means the concept exists in the current source and has been inspected.
- **Partially implemented** means some source evidence exists, but part of the topic is owned by Person 2 or depends on a missing Maven build.
- **Planned** means the concept is a design intention only; no source evidence exists yet.
- **Not applicable** means the chosen architecture does not use the concept, with an explanation.

## Current Verification Status

| Item | Value |
|---|---|
| Last reviewed | 2026-10-26 |
| Repository state | Person 1 source, schema, seed, JSPs, CSS/JS, tests, and docs present; no Maven build or deployment descriptors |
| Git commit | None; repository is not initialized |
| Java source inspected | 37 main files, 8 test files |
| Source pattern inventory | 9 DAO interfaces, 9 JDBC implementations, 4 enums, 10 test-only lambdas, 0 `switch`, 2 varargs methods |
| JSP/CSS/JS source inspected | 15 JSPs, `app.css`, `app.js` |
| SQL source inspected | `schema.sql`, `seed.sql` |
| Compilation evidence | `javac --release 17`: 37/37 main and 8/8 test files compiled |
| Test evidence | Standalone JUnit Platform Console: 39/39 DAO tests pass |
| JSP evidence | Tomcat `JspC`: 15/15 JSPs compiled with 0 errors |
| Course material files in workspace | None |

Person 1 concepts are marked **Implemented** only where the source was inspected. Person 2 concepts, Maven packaging, servlet inheritance, and HTTP integration remain **Planned** or **Partially implemented**.

## Ownership

Person 1 owns:

- Database schema, SQL scripts, models, and the JDBC/DAO layer.
- JSP, HTML, CSS, and vanilla JavaScript UI implementation.
- Database/DAO/UI documentation and tests.

Person 2 owns:

- Controller/Servlet implementation.
- Service layer and business rules.
- Maven build, `web.xml`, `context.xml`, filters, and overall integration.

Rows referencing service, controller, filter, or `web.xml` code are documented here for completeness only. Person 1 did not edit those components; those rows remain **Planned** until Person 2's actual source is available and inspected.

## Mapping Table

| Course Topic | Java Concept | File/Class/Method | How It Is Applied | Status |
|---|---|---|---|---|
| 1. Java Overview | Maven web application and layered package organization | `src/main/java/com/library/{model,dao,dao.jdbc,config,exception}`; Person 2: `service`, `controller`, `filter` | Source is organized into presentation, DAO, configuration, and exception layers; Maven build is still missing | Partially implemented |
| 2. Basic I/O, Variables, Data Types, Operators | Primitive and reference types, fields, operators, comparisons | `model/Book.java`, `Member.java`, `Loan.java`, `BookCopy.java`, `Page.java`; all JDBC DAOs | Identifiers use `int`; counts use `long`; text uses `String`; dates use `LocalDate`/`LocalDateTime`; flags use `boolean`; pagination uses arithmetic and comparisons | Implemented |
| 2. Basic I/O, Variables, Data Types, Operators | Configuration reading | `config/DatabaseConnection.java` | Reads `DB_URL`, `DB_USER`, `DB_PASSWORD`, and optional `DB_CONFIG` from the environment with defaults; holds no password constant | Implemented |
| 2. Basic I/O, Variables, Data Types, Operators | File I/O | `config/DatabaseConnection.loadExternalPassword()` | Uses `FileReader`, `BufferedReader`, `Path`, and `Files.isReadable` to read an external password file whose path is outside the repository | Implemented |
| 3. Control Statements, Array, Debug | Conditions and `switch` statements | `dao/jdbc/Jdbc*DAO.java`; criteria classes | Applies nullable search filters with `if` conditions, ternary operators, enum checks, and result-set null checks. No `switch` is required by the current code, so none was added artificially | Partially implemented |
| 3. Control Statements, Array, Debug | Loops and iteration | `dao/jdbc/Jdbc*DAO.java`, `JdbcBookAuthorDAO.replaceForBook` | Iterates `ResultSet` rows into model objects, loops over filter parameters, and batch-inserts author links | Implemented |
| 3. Control Statements, Array, Debug | Arrays | `JdbcDashboardDAO.count(Connection, String, Object...)`; `TestDatabase.firstNonBlank(String...)` | Varargs are compiled as arrays; `JdbcDashboardDAO` binds them with an indexed loop. No standalone array was added artificially. `HttpServletRequest.getParameterValues(...)` may add `String[]` in Person 2's controller | Implemented (varargs) |
| 3. Control Statements, Array, Debug | Debugging techniques | DAO tests, constraint probes, standalone compile, `JspC`, EL checker | JUnit assertions, SQL transaction probes, compiler diagnostics, JSP compilation, and static EL property checking were used to find and fix defects | Implemented (process) |
| 4. Object, Class, Method | Classes, objects, constructors, methods | `model/*.java`, `dao/*.java`, `dao/jdbc/*.java` | Models represent domain records; DAO classes implement operations through interface methods; constructors and accessors create and read objects | Implemented |
| 4. Object, Class, Method | Encapsulation | `model/Book.java`, `BookCopy.java`, `Member.java`, `Loan.java`, `Author.java`, `Publisher.java`, `Category.java` | Fields are private and exposed through getters/setters; DAO mapping uses those accessors, and display projections are documented as read-only | Implemented |
| 5. Inheritance, Package, Interface | Packages and imports | `com.library.model`, `com.library.dao`, `com.library.dao.jdbc`, `com.library.config`, `com.library.exception` | Enforces layer boundaries and makes dependencies explicit | Implemented |
| 5. Inheritance, Package, Interface | Interfaces and polymorphism | `dao/BookDAO.java` + `dao/jdbc/JdbcBookDAO.java`; the other 8 DAO interface/JDBC pairs; tests typed to DAO interfaces | DAO contracts are separate from JDBC implementations, so callers and tests depend on interfaces rather than concrete classes | Implemented |
| 5. Inheritance, Package, Interface | Inheritance | `exception/DataAccessException.java extends RuntimeException`; Person 2: Servlets extend `HttpServlet` | `DataAccessException` reuses runtime-exception behavior and adds SQLState/cause context. The shared `LibraryException` hierarchy and Servlet inheritance remain Person 2 work | Partially implemented |
| 6. Exception Handling, Enumerations, IO-Files | Exception handling and resource management | `dao/jdbc/Jdbc*DAO.java`, `config/DatabaseConnection.java`, `exception/DataAccessException.java` | Wraps `SQLException` with context and cause, preserves SQLState, uses try-with-resources for JDBC resources, and leaves commit/rollback to the caller | Implemented |
| 6. Exception Handling, Enumerations, IO-Files | Enumerations | `model/enums/CopyStatus.java`, `MemberStatus.java`, `LoanStatus.java`, `LoanState.java` | Type-safe status values mirror database CHECK constraints; `LoanState` documents the derived UI state without storing an `OVERDUE` row value | Implemented |
| 7. Generic, ArrayList, Lambda Expression | Generics | `model/Page.java` | One generic pagination wrapper serves book, member, loan, and overdue results without duplicating result classes | Implemented |
| 7. Generic, ArrayList, Lambda Expression | `ArrayList` / `List` collections | `dao/jdbc/Jdbc*DAO.java` | DAO result mapping accumulates domain objects in `ArrayList` and returns `List`/`Page` abstractions | Implemented |
| 7. Generic, ArrayList, Lambda Expression | Lambda expressions | `src/test/java/com/library/dao/*Test.java` | Tests use lambdas with `assertThrows`, `assertDoesNotThrow`, and similar JUnit APIs. No production lambda was added artificially | Implemented (tests only) |
| 8. GUI | Java Swing/AWT/JavaFX and Java event handling | None | The project uses server-rendered JSP/HTML/CSS/vanilla JavaScript. JavaScript event handling is web UI behavior, not Java GUI programming | Not applicable |
| 9. JDBC | `Connection`, `PreparedStatement`, `ResultSet`, `SQLException`, `DriverManager`, generated keys | `config/DatabaseConnection.java`; all `dao/jdbc/Jdbc*DAO.java` | Executes handwritten parameterized SQL, maps `ResultSet` rows, retrieves generated keys, wraps `SQLException`, and uses caller-supplied connections for transactions | Implemented |

## Source Pattern Inventory

The tables below record the concrete patterns found in the 37 main and 8 test Java files during the 2026-10-26 inspection. They provide a file-level index; they do not claim that every file in a grouped row uses every listed pattern.

### Inventory summary

| Pattern | Observed evidence |
|---|---|
| DAO interface / implementation | 9 `public interface ...DAO` contracts and 9 `implements ...DAO` JDBC classes |
| Enumerations | 4: `CopyStatus`, `LoanState`, `LoanStatus`, `MemberStatus` |
| Generated keys | 7 `getGeneratedKeys()` call sites |
| Row-locking SQL | 3 `SELECT ... FOR UPDATE` queries: `JdbcBookCopyDAO.FIND_BY_ID_FOR_UPDATE`, `JdbcLoanDAO.FIND_BY_ID_FOR_UPDATE`, and `JdbcLoanDAO.FIND_ACTIVE_BY_COPY_ID` |
| Varargs / implicit arrays | 2 methods: `JdbcDashboardDAO.count(Connection, String, Object...)`, `TestDatabase.firstNonBlank(String...)` |
| Lambda expressions | 10, all in JUnit assertions; none in main source |
| `switch` statements | 0; conditions use `if` and ternary operators |
| Java GUI classes | 0 Swing/AWT/JavaFX classes; the UI is JSP/HTML/CSS/vanilla JavaScript |

### Configuration, exception, models, criteria, and enums

| File(s) | Patterns present |
|---|---|
| `config/AppConfig.java` | `final` class, private constructor, `public static final` constants for SQLState values, loan period, and pagination defaults; no credential fields |
| `config/DatabaseConnection.java` | Static connection factory; environment-variable precedence (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_CONFIG`); `Properties`; `DriverManager`; file I/O with `Path`, `Files.isReadable`, `FileReader`, and `BufferedReader`; null/blank checks; try-with-resources; `closeQuietly` helper; caller-owned transaction |
| `exception/DataAccessException.java` | Inheritance from `RuntimeException`; overloaded constructors; exception chaining with `super(message, cause)`; `instanceof SQLException`; final `sqlState` field; overridden `getMessage()` |
| `model/Book.java`, `Author.java`, `Publisher.java`, `Category.java`, `BookCopy.java`, `Member.java`, `Loan.java`, `DashboardStats.java` | Encapsulation with private fields and accessors; default and convenience constructors; `equals`/`hashCode` for identity-bearing models and `toString` for all; composition in `Book.authors`; enum and date fields; DAO-populated display projections |
| `model/Page.java` | Generic class `Page<T>` and generic `List<T>`; defensive copy with `ArrayList`; unmodifiable list; arithmetic for pages; bean properties `first` and `last`; additional `hasNext()`/`hasPrevious()` methods are not EL bean properties; `Page<?>` in `equals` |
| `model/criteria/BookSearchCriteria.java`, `MemberSearchCriteria.java`, `LoanSearchCriteria.java` | Encapsulated filter beans; nullable filters; default `page`/`size`; `LocalDate` ranges; caller-supplied `today` with a null-safe setter; `toString` |
| `model/enums/CopyStatus.java`, `LoanState.java`, `LoanStatus.java`, `MemberStatus.java` | Type-safe enum constants mirroring database CHECK constraints; `LoanState` documents a derived UI state that is not stored |

### DAO contracts

| File(s) | Patterns present |
|---|---|
| `dao/AuthorDAO.java`, `BookAuthorDAO.java`, `BookCopyDAO.java`, `BookDAO.java`, `CategoryDAO.java`, `DashboardDAO.java`, `LoanDAO.java`, `MemberDAO.java`, `PublisherDAO.java` | 9 interfaces; `Connection` as a caller-supplied parameter; `Optional`, `Page<T>`, `List<T>`, `LocalDate`, and enum parameters; interface-based polymorphism; Javadoc contracts for transactions, generated IDs, and display projections |

### JDBC implementations

| File(s) | Patterns present |
|---|---|
| `dao/jdbc/JdbcAuthorDAO.java`, `JdbcCategoryDAO.java`, `JdbcPublisherDAO.java` | `implements` DAO interfaces; static final SQL; `PreparedStatement` and `ResultSet`; try-with-resources; `Optional`; dynamic search with `StringBuilder` and `List<Object>`; `ILIKE`/`LOWER` filters; `LIMIT ? OFFSET ?` pagination; generated keys; `DataAccessException`; row mapping |
| `dao/jdbc/JdbcBookDAO.java` | CRUD; enriched joins for publisher/category; correlated subquery for available copies; `EXISTS` filters for author and availability; ISBN trimming; generated keys; dynamic search and count queries |
| `dao/jdbc/JdbcBookCopyDAO.java` | `CopyStatus` enum parameter and `valueOf` mapping; `SELECT ... FOR UPDATE`; generated keys; join with `books`; zero-row-tolerant delete; status update returning affected rows |
| `dao/jdbc/JdbcBookAuthorDAO.java` | Many-to-many join read; `LinkedHashSet` de-duplication; enhanced `for`; `addBatch()`/`executeBatch()`; transaction participation without commit/rollback |
| `dao/jdbc/JdbcDashboardDAO.java` | Six count queries; `LocalDate today`; private `count(Connection, String, Object...)`; indexed `for` loop over the implicit array; helper-method reuse; try-with-resources |
| `dao/jdbc/JdbcLoanDAO.java` | `LocalDate`/`LocalDateTime` mapping; `SELECT ... FOR UPDATE`; display joins to copies, books, and members; dynamic filters; `criteria.getToday()` for overdue queries; generated keys; return update with affected-row count |
| `dao/jdbc/JdbcMemberDAO.java` | Correlated active-loan count subquery; email normalization to `NULL`; `Types.VARCHAR`/`Types.INTEGER`; case-insensitive `LOWER(email)` checks; dynamic search; generated keys; enum mapping |

### Tests and test support

| File(s) | Patterns present |
|---|---|
| `JdbcBookDAOTest.java`, `JdbcBookCopyDAOTest.java`, `JdbcBookAuthorDAOTest.java`, `JdbcLoanDAOTest.java`, `JdbcMemberDAOTest.java`, `JdbcDashboardDAOTest.java`, `JdbcReferenceDAOTest.java` | JUnit 5 `@Test`; static assertion imports; `assertThrows`/`assertDoesNotThrow` with lambdas; try-with-resources; generated IDs; fixed `LocalDate` values; `List.of`/`Arrays.asList`; SQLState assertions; `resetAll`/`seedReferenceData` setup |
| `dao/support/TestDatabase.java` | Static utility class and private constructor; environment precedence; `_test` URL guard; `Properties`; `DriverManager`; `String...` varargs; `Statement`; `TRUNCATE ... RESTART IDENTITY CASCADE`; reference-data seeding |

### UI and SQL sources

| File(s) | Patterns present |
|---|---|
| `WEB-INF/views/**/*.jsp` | JSTL `jakarta.tags.core` and `jakarta.tags.functions`; EL expressions; `c:import`/`c:param`; `c:url`; `c:forEach`; `c:if`; `c:choose`; `c:out`; `fn:length`; no Java GUI or scriptlets |
| `assets/js/app.js`, `assets/css/app.css` | Vanilla JavaScript IIFE with `'use strict'`; DOM queries and event listeners; confirm dialog; timeout; CSS custom properties, flex/grid layout, responsive rules, and status classes; no framework |
| `db/schema.sql`, `db/seed.sql` | DDL, foreign keys, CHECK constraints, unique/partial indexes, `BEGIN`/`COMMIT`, `TRUNCATE ... RESTART IDENTITY CASCADE`; `CURRENT_DATE` appears in the schema default and seed data, not in DAO SQL |

### Patterns deliberately not present

- No `switch` statement was added; nullable filter logic is clearer with `if` and ternaries.
- No production lambda was added; lambdas appear only where JUnit's functional assertion APIs require them.
- No Java Swing/AWT/JavaFX code exists; the agreed UI is server-rendered JSP with browser JavaScript.
- Servlet, Service, filter, and Maven build patterns remain Person 2 scope and are not claimed here.

## Topic-Specific Notes

### Topic 1 — Java Overview

The layered package structure is implemented. The Maven `pom.xml`, Maven Wrapper, and WAR packaging are still missing and belong to Person 2. The Java overview is therefore only partially implemented as a build topic.

### Topic 2 — Basic I/O, Variables, Data Types, Operators

Variables, primitive/reference types, and operators appear naturally in models, criteria, and DAOs. `DatabaseConnection` provides real file I/O for an optional external password file; no artificial `db.properties` reader was added. `Properties` is used for JDBC connection properties rather than for a project configuration file.

### Topic 3 — Control Statements, Array, Debug

Conditions and loops are present in JDBC result mapping and search/filter logic. Arrays appear naturally as varargs in `JdbcDashboardDAO.count(Connection, String, Object...)` and `TestDatabase.firstNonBlank(String...)`; no standalone array was added. If Person 2 uses `getParameterValues` for multi-author selection, that usage can be documented later. Debugging is evidenced by actual tests, SQL probes, compiler output, and the EL property checker rather than by artificial source comments.

### Topic 4 — Object, Class, Method

Model classes and DAO classes provide the object-oriented structure. Encapsulation is implemented with private fields and accessors. Display projections such as `Book.publisherName`, `Book.availableCopies`, and `Loan.bookTitle` are documented as DAO-populated read-only fields.

### Topic 5 — Inheritance, Package, Interface

Interfaces are central to the DAO boundary: 9 DAO interfaces have JDBC implementations, and tests reference the interface types. Inheritance is limited to `DataAccessException extends RuntimeException`; the planned shared `LibraryException` parent, DAO/service exception subtypes, and Servlet inheritance require Person 2's source. No additional hierarchy was invented to satisfy the course topic.

### Topic 6 — Exception Handling, Enumerations, IO-Files

Exception handling and resource management are central to the JDBC layer: `DataAccessException` preserves SQLState and cause, and every DAO uses try-with-resources. Enumerations mirror database status constraints. File I/O is implemented for the optional external password file.

### Topic 7 — Generic, ArrayList, Lambda Expression

Generics and `ArrayList` have clear roles in pagination and DAO result mapping. Lambdas appear naturally in JUnit assertions; they were not added to production code artificially.

### Topic 8 — GUI

Java GUI is **Not applicable**. The agreed UI technology is JSP/HTML/CSS/vanilla JavaScript running in a web browser. The JavaScript confirmation and validation behavior is not Java GUI programming and is not presented as such.

### Topic 9 — JDBC

JDBC is the core of Person 1's implementation. Evidence includes `DriverManager`, `Connection`, `PreparedStatement`, `ResultSet`, `SQLException`, generated-key retrieval, caller-owned connections, batch inserts, `FOR UPDATE` queries, and parameterized search/count queries.

## Code Annotation Policy

Comments are concise English explanations of the Java concept and its role in the implementation. They do not restate obvious code, annotate every getter/setter, or repeat the same explanation in every class.

| Target file(s) | Concept to explain | Comment scope | Owner |
|---|---|---|---|
| `dao/jdbc/Jdbc*DAO.java` | JDBC, `PreparedStatement`, `ResultSet`, try-with-resources | Class-level JDBC explanation plus targeted comments at SQL execution, generated-key retrieval, and row mapping | Person 1 |
| `config/DatabaseConnection.java` | JDBC connection management and file I/O | Explains environment precedence, external password file, and caller-owned connections | Person 1 |
| `exception/DataAccessException.java` | Exception wrapping/chaining | Explains SQLState and cause preservation | Person 1 |
| `model/Page.java` | Generics | Explains why pagination is generic across entity types | Person 1 |
| `model/enums/*.java` | Enumerations | Explains that enum values mirror database CHECK-constrained statuses | Person 1 |
| `model/*.java` | Encapsulation/object modeling | One class-level comment where it adds value; no comments on obvious accessors | Person 1, reviewed by Person 2 |
| `service/**`, `controller/**` | Interfaces, inheritance, arrays, exceptions | Document only; comments require Person 2's approval | Person 2 |
| JSP/CSS/JS | Java GUI not applicable | No Java-concept comments; UI comments only for non-obvious browser behavior | Person 1 |
| DAO tests | JDBC verification and debugging | Comments only for non-obvious test setup, transaction participation, or constraint scenarios | Person 1 |

## Update Procedure

1. Update this document after each implementation phase.
2. Mark a row **Implemented** only after inspecting the actual source file, class, or method.
3. Cite the real file path and, where practical, the class/method.
4. For Person 2-owned code, inspect the source and coordinate with Person 2 before changing any comments.
5. Keep **Planned** and **Not applicable** rows explicit; do not upgrade a status without evidence.
6. Record the verification date and Git commit at the top of this document.
7. At final verification, cross-check all **Implemented** rows against the final source and remove stale claims.

## Current Conclusion

- **Implemented:** variables/types/operators, configuration and file I/O, loops, varargs arrays, debugging process, classes/objects/methods, encapsulation, packages, interfaces/polymorphism, exception handling, enumerations, generics, `ArrayList`, test lambdas, and JDBC.
- **Partially implemented:** Maven web-application packaging (build missing), conditions/`switch` (conditions present, no switch needed), and inheritance (runtime-exception parent present; service/servlet hierarchy pending Person 2).
- **Planned:** Maven build, Controller/Service/filter code, Servlet inheritance, and HTTP integration.
- **Not applicable:** Java GUI.

This mapping now reflects inspected Person 1 source. It deliberately leaves Person 2 and Maven-dependent rows incomplete rather than claiming coverage that does not exist.
