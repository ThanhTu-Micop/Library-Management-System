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
| Git state | Initialized on `main`; see `git log` for the latest commit |
| Code evidence audit | 59 `file:line` references resolved; 43 semantic spot checks passed; 9/9 DAO interface/implementation pairs matched; 3 locking SQL queries confirmed |
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

## Code Evidence Index

This table is the short answer for an oral review: it points to the exact file and line that demonstrates each Java feature. Line numbers refer to the 2026-10-26 snapshot; if the code moves, cite the class or method name instead, because that is the stable reference.

Path prefixes used below:

- `main/` = `src/main/java/com/library/`
- `test/` = `src/test/java/com/library/`
- `web/` = `src/main/webapp/`

| Topic | Java feature | Exact code evidence | What to point out |
|---|---|---|---|
| 1. Overview | Package and layer separation | `main/model/Book.java:1`; `main/dao/BookDAO.java:1`; `main/dao/jdbc/JdbcBookDAO.java:1` | Three packages show the model, DAO contract, and JDBC implementation layers. |
| 1. Overview | Class, object, method | `main/model/Book.java:16`; `main/dao/jdbc/JdbcBookDAO.java:240` | `Book` is the class; `new Book()` creates an object; `getIsbn()`/`setIsbn()` are methods. |
| 1. Overview | Constructor overloading | `main/model/Book.java:31` and `:34`; `main/model/Loan.java:36` and `:39` | A no-argument constructor and a full-field constructor coexist in the same class. |
| 2. Basic I/O | Variables, primitive and reference types | `main/model/Book.java:18-29`; `main/model/Loan.java:24-34`; `main/model/Member.java:21` | `Integer`, `String`, `LocalDate`, `LocalDateTime`, `boolean`, and `long` are used for real fields. |
| 2. Basic I/O | Operators and arithmetic | `main/dao/jdbc/JdbcBookDAO.java:84`; `main/model/Page.java:28`; `main/dao/jdbc/JdbcBookDAO.java:236` | Offset uses multiplication; total pages use division and `Math.ceil`; ISBN uses a ternary and `trim()`. |
| 2. Basic I/O | File I/O | `main/config/DatabaseConnection.java:87-93` | `Files.isReadable`, `FileReader`, `BufferedReader`, `IOException`, and try-with-resources read an external password file. |
| 3. Control | `if` and ternary conditions | `main/dao/jdbc/JdbcBookDAO.java:196-219`; `main/dao/jdbc/JdbcLoanDAO.java:234-264` | Each nullable search filter is applied with an `if`; there is no `switch` in the project. |
| 3. Control | Loops | `main/dao/jdbc/JdbcBookCopyDAO.java:55`; `main/dao/jdbc/JdbcBookAuthorDAO.java:62` and `:76`; `main/dao/jdbc/JdbcDashboardDAO.java:48` | `while (rs.next())` maps rows; enhanced `for` iterates lists; indexed `for` reads the varargs array. |
| 3. Control | Array through varargs | `main/dao/jdbc/JdbcDashboardDAO.java:46-48`; `test/dao/support/TestDatabase.java:64` | `Object...` and `String...` are compiled as arrays; `params.length` and `params[i]` prove the array access. |
| 4. Object | Encapsulation | `main/model/Book.java:18-29`; `main/model/Book.java:43-56`; `main/model/Page.java:17` | Fields are private; access is through public getters/setters; `Page.content` is `private final`. |
| 4. Object | `equals`, `hashCode`, `toString` | `main/model/Book.java:132`, `:140`, `:145`; `main/model/Page.java:68`, `:80`, `:84` | Entity classes compare by ID; `Page` compares by value; all provide readable `toString()` output. |
| 5. Inheritance | `extends` | `main/exception/DataAccessException.java:15`, `:21-24`, `:33` | This is the only class inheritance in Person 1: a custom unchecked exception extends `RuntimeException`, calls `super(message, cause)`, and inspects `instanceof SQLException`. |
| 5. Interface | `interface` contract | `main/dao/BookDAO.java:22`; `main/dao/LoanDAO.java:23` | Nine DAO interfaces declare operations without JDBC details. |
| 5. Interface | `implements` and polymorphism | `main/dao/jdbc/JdbcBookDAO.java:25`; `test/dao/JdbcBookDAOTest.java:26` | `JdbcBookDAO` implements `BookDAO`; the test field is typed as `BookDAO`, so it depends on the interface, not the concrete class. |
| 6. Exception | Catch and wrap | `main/dao/jdbc/JdbcBookDAO.java:67-68`; `main/exception/DataAccessException.java:21-24` | A checked `SQLException` is caught and wrapped in `DataAccessException` with context and the original cause. |
| 6. Exception | Try-with-resources | `main/dao/jdbc/JdbcBookDAO.java:59-61`; `main/dao/jdbc/JdbcLoanDAO.java:80`; `main/config/DatabaseConnection.java:90` | JDBC statements, result sets, and the password reader are closed automatically. |
| 6. Enum | Enumeration | `main/model/enums/LoanStatus.java:8-10`; `main/model/enums/CopyStatus.java:9-12`; `main/model/enums/MemberStatus.java:6-8` | Status values are type-safe constants and mirror database `CHECK` constraints. |
| 7. Generic | Generic class and generic parameter | `main/model/Page.java:15`, `:17`, `:23`; `main/dao/BookDAO.java:26` | `Page<T>` and `List<T>` reuse one pagination type; `Page<Book>` binds `T` to `Book`. |
| 7. Collection | `ArrayList`, `List`, `Set` | `main/model/Page.java:24`; `main/model/Book.java:28`; `main/dao/jdbc/JdbcAuthorDAO.java:63` and `:82`; `main/dao/jdbc/JdbcBookAuthorDAO.java:60` | `ArrayList` makes a defensive copy and accumulates rows; `List` is the return abstraction; `LinkedHashSet` removes duplicate author IDs. |
| 7. Lambda | Lambda expression (tests only) | `test/dao/JdbcBookDAOTest.java:103-104`; `test/dao/JdbcBookCopyDAOTest.java:132-133`; `test/dao/JdbcLoanDAOTest.java:110-111` | Ten lambdas exist, all passed to JUnit `assertThrows`/`assertDoesNotThrow`; no production lambda was added. |
| 8. GUI | Java GUI is not applicable | `web/WEB-INF/views/books/list.jsp:2`, `:62`, `:64`; `web/assets/js/app.js:4-5` | The UI is JSP/JSTL/EL plus vanilla JavaScript; there is no Swing, AWT, or JavaFX. |
| 9. JDBC | `DriverManager` and `Connection` | `main/config/DatabaseConnection.java:73`; `test/dao/support/TestDatabase.java:52`; `main/dao/BookDAO.java:13-14` | `DriverManager.getConnection` creates the connection; the caller passes it in; DAOs never commit, roll back, or close it. |
| 9. JDBC | `PreparedStatement` and `ResultSet` | `main/dao/jdbc/JdbcBookDAO.java:59-61`; `main/dao/jdbc/JdbcMemberDAO.java:114` | SQL uses `?` placeholders, `setInt`/`setString` bind values, and `while (rs.next())` maps rows. |
| 9. JDBC | Generated keys | `main/dao/jdbc/JdbcBookDAO.java:110` and `:124`; `main/dao/jdbc/JdbcLoanDAO.java:80` and `:95` | `RETURN_GENERATED_KEYS` plus `getGeneratedKeys()` writes the database ID back into the model. |
| 9. JDBC | Row locking | `main/dao/jdbc/JdbcBookCopyDAO.java:31`; `main/dao/jdbc/JdbcLoanDAO.java:49` and `:53` | Three `SELECT ... FOR UPDATE` queries lock a copy or loan row inside the caller's transaction. |
| 9. JDBC | Batch execution | `main/dao/jdbc/JdbcBookAuthorDAO.java:78` and `:80` | `addBatch()` and `executeBatch()` insert all author links for a book. |
| 9. JDBC | Database constraints | `src/main/resources/db/schema.sql:38`, `:45`, `:77`, `:110` | Unique ISBN, trimmed length 13–20, due date exactly 14 days later, and one active loan per copy. |
| 9. JDBC | Date and time mapping | `main/model/Loan.java:24-26`; `main/dao/jdbc/JdbcLoanDAO.java:286-288`; `main/dao/jdbc/JdbcDashboardDAO.java:31` | `LocalDate`/`LocalDateTime` fields map through `setObject` and `rs.getObject(..., LocalDate.class)`; overdue uses caller-supplied `today`. |

### 60-second answer order

If the teacher asks "which part of the code demonstrates the Java concept?", open the files in this order:

1. `main/model/Book.java` — class, private fields, two constructors, getters/setters, `equals`/`hashCode`/`toString`.
2. `main/dao/BookDAO.java` — interface contract.
3. `main/dao/jdbc/JdbcBookDAO.java` — `implements`, JDBC, try-with-resources, generated keys, exception wrapping.
4. `main/model/Page.java` — generics, `ArrayList`, pagination properties `first`/`last`.
5. `main/exception/DataAccessException.java` — inheritance, constructor chaining, SQLState.
6. `main/dao/jdbc/JdbcBookAuthorDAO.java` — `List`/`Set`, enhanced `for`, batch.
7. `main/dao/jdbc/JdbcDashboardDAO.java` — varargs and indexed array loop.
8. `test/dao/JdbcBookDAOTest.java` — JUnit, `@Test`, lambda, try-with-resources.
9. `main/config/DatabaseConnection.java` — file I/O and `DriverManager`.
10. `web/WEB-INF/views/books/list.jsp` and `web/assets/js/app.js` — web UI; explicitly say Java GUI is not applicable.

### Claims to avoid

- Do not say Java GUI is implemented; there is no Swing/AWT/JavaFX code.
- Do not say lambdas are used in production; all ten are in JUnit tests.
- Do not say standalone arrays are used; arrays appear only through varargs.
- Do not say there is a large inheritance hierarchy; only `DataAccessException extends RuntimeException` exists.
- Do not say DAOs manage transactions; they receive a caller-owned `Connection` and never commit or roll back. The service layer that owns transaction boundaries belongs to Person 2.
- Do not say Maven, Servlet, WAR, Tomcat, or HTTP integration is verified; those remain Person 2 work.

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

- **Code evidence:** exact `file:line` pointers are listed in the Code Evidence Index above; use that table when the instructor asks which code demonstrates a Java feature.
- **Implemented:** variables/types/operators, configuration and file I/O, loops, varargs arrays, debugging process, classes/objects/methods, encapsulation, packages, interfaces/polymorphism, exception handling, enumerations, generics, `ArrayList`, test lambdas, and JDBC.
- **Partially implemented:** Maven web-application packaging (build missing), conditions/`switch` (conditions present, no switch needed), and inheritance (runtime-exception parent present; service/servlet hierarchy pending Person 2).
- **Planned:** Maven build, Controller/Service/filter code, Servlet inheritance, and HTTP integration.
- **Not applicable:** Java GUI.

This mapping now reflects inspected Person 1 source. It deliberately leaves Person 2 and Maven-dependent rows incomplete rather than claiming coverage that does not exist.
