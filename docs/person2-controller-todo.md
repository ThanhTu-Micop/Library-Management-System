# Person 2 TODO — Controller/Service must fix known gaps

| Field | Value |
|---|---|
| Status | OPEN |
| Owner | Person 2 (Controller, Service, transaction, build, HTTP integration) |
| Created | 2026-11-01 |
| Related | `docs/phase1-plan.md`, `docs/implementation-plan.md` |

> This checklist is a condition for Person 2's Controller/Service work. The DAO layer is
> implemented and tested; the gaps below are in the missing Controller/Service path or in
> display fields that the DAO deliberately does not populate.

## 1. Overdue display fields

- [ ] `LoanService.getBorrowingHistory(...)` must compute and set `Loan.overdue` and
  `Loan.daysOverdue` for every loan in the returned `Page<Loan>`.
- [ ] `LoanService.getOverdueLoans(...)` must do the same for every overdue loan.
- [ ] Rule: `overdue = returnDate == null && dueDate < today`;
  `daysOverdue = DAYS.between(dueDate, today)` when overdue, otherwise `0`.
- [ ] Do not assume `JdbcLoanDAO` sets these fields. It does not; it leaves them
  `false` / `0`.
- [ ] Verify `loans/history.jsp` shows the Overdue badge and
  `loans/overdue.jsp` shows the correct day count.

## 2. Book authors on the detail page

- [ ] `BookService.getBookById(int)` must call
  `JdbcBookAuthorDAO.findByBookId(Connection, int)` and
  `book.setAuthors(...)`.
- [ ] `books/view.jsp` reads `book.authors`; there is no separate `authors`
  request attribute on the detail page.
- [ ] Do not confuse this with `${authors}` on `books/form.jsp`, which is the
  full author list used by the multi-select.

## 3. Controller/Service for all 12 functions

- [ ] Implement Servlets and Services for the 12 functions in
  `docs/implementation-plan.md`.
- [ ] One transaction per write operation: `setAutoCommit(false)`, `commit()` or
  `rollback()`, close in `finally`.
- [ ] Apply POST/Redirect/GET and forward field/global errors back to the form.
- [ ] Map `DataAccessException` SQLState values to friendly
  `ValidationException` / `BusinessRuleException` messages.
- [ ] Provide 404 and 500 error handling.

## 4. `LoanSearchCriteria.today`

- [ ] The Service must call `criteria.setToday(LocalDate.now())` before
  `JdbcLoanDAO.search(...)` when `overdueOnly` is true.
- [ ] Do not rely on the current default `LocalDate.now()`; set it explicitly so
  the query is deterministic.
- [ ] Preferred fix (coordinate with Person 1): default `today = null` and make
  `JdbcLoanDAO` throw `IllegalStateException` when `overdueOnly == true` and
  `today == null`.

## 5. `equals`/`hashCode` with null id

- [ ] Do not put unsaved entities (`id == null`) into `Set`/`Map` until Person 1
  fixes `equals`/`hashCode`.
- [ ] Preferred fix: if the id is `null`, use identity (`this == o`); otherwise
  compare the id.
- [ ] Add a test proving two unsaved entities are not equal.

## 6. CSRF

- [ ] Add a CSRF filter that validates `_csrf` on `POST`/`PUT`/`DELETE`.
- [ ] Generate the token in the session and expose `csrfToken` as a request
  attribute.
- [ ] JSP forms already contain `_csrf`; no JSP change is required.
- [ ] Return HTTP 403 for a missing or invalid token.

## 7. Build and deployment

- [ ] Add `pom.xml`: Java 17, Jakarta Servlet 6.0, JSP 3.1, JSTL 3.0.1,
  PostgreSQL driver `42.7.14`, JUnit 5, `maven-war-plugin`.
- [ ] Add `src/main/webapp/WEB-INF/web.xml`: welcome file, error pages, filter
  mappings, session configuration.
- [ ] `mvn clean package` produces a WAR.
- [ ] Deploy on Tomcat 10.1.61 and smoke-test all 12 functions.

## 8. Integration tests

- [ ] Borrow: loan `ACTIVE`, copy `BORROWED`.
- [ ] Return: loan `RETURNED`, copy `AVAILABLE`.
- [ ] Overdue: `overdue = true`, `daysOverdue > 0`.
- [ ] Book detail: `book.authors` is populated.
- [ ] Member update: `INACTIVE` is rejected while active loans exist.
- [ ] CSRF: `POST` without a token returns 403.
- [ ] HTTP smoke test for dashboard, books, members, and loans.

## Acceptance criteria

- `mvn clean test` passes (existing 124 DAO tests + new service tests).
- The WAR deploys on Tomcat 10.1.61.
- All 12 functions work end to end through HTTP.
- No overdue badge/column shows `false` / `0` for an actually overdue loan.
- Book detail shows the linked authors.
- `POST` without a CSRF token is rejected.

## Coordination with Person 1

- Person 1 owns the database, DAO/JDBC, models, and UI. Request changes through an
  issue/PR instead of editing those files silently.
- Confirm with Person 1 that these shared constants/fields are available:
  `AppConfig.SQLSTATE_UNIQUE_VIOLATION`,
  `AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION`,
  `AppConfig.SQLSTATE_CHECK_VIOLATION`,
  `AppConfig.LOAN_PERIOD_DAYS`,
  `Loan.overdue`, `Loan.daysOverdue`.
- If Person 1 fixes the `today` default or `equals`/`hashCode`, update this
  checklist and remove the corresponding workaround.
