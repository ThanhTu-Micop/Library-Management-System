# Test Suite Report — Library Management System

> **Scope:** Tự tạo bộ test JUnit 5 kiểm tra **logic nghiệp vụ**, **thống nhất dữ liệu**, **đồng bộ cross-table**, và **transaction/đồng thời** cho dự án `Library-Management-System` (Person 1 scope: DAO/JDBC + PostgreSQL).
>
> **Generated:** 2026-10-27
> **Target codebase:** [github.com/ThanhTu-Micop/Library-Management-System](https://github.com/ThanhTu-Micop/Library-Management-System) (commit at HEAD of `main` as of report date)
> **Test framework:** JUnit 5 via `junit-platform-console-standalone` (không Maven, theo README §3.2)

---

## 1. Tóm tắt

Bốn class test mở rộng được thêm vào `src/test/java/com/library/dao/`, bổ sung **78 test method**; cùng với **7 test tìm kiếm tiếng Việt** trong `DiacriticSearchTest`, trên nền **39 test method** hiện có, tổng cộng là **124 test method** trên **12 class test** (14 file source, gồm cả `TestDatabase` và `TestFixtures`).

| # | File | Test methods | Nhóm trọng tâm |
|---|------|--------------|----------------|
| 1 | `LoanBusinessLogicTest.java` | 22 | 14-day rule (leap year, year boundary), `markReturned` idempotency, status transitions, multi-loan history |
| 2 | `DataConsistencyTest.java` | 26 | UNIQUE case-insensitive trên INSERT & UPDATE, blank email normalization, enum CHECK, status-return_date consistency trên UPDATE |
| 3 | `CrossTableSynchronizationTest.java` | 21 | Dashboard counts khớp bảng, member.activeLoanCount, book.availableCopies, CASCADE/RESTRICT, **gap E01** |
| 4 | `TransactionIsolationTest.java` | 9 | 2 connection cùng mượn 1 copy, uncommitted invisible, FOR UPDATE chặn concurrent update với `lock_timeout` 55P03 |
| | **Tổng cộng mới** | **78** | |

Ngoài bộ test mở rộng ở trên, PR hợp nhất còn thêm `DiacriticSearchTest` với **7 test** kiểm tra tìm kiếm có dấu/không dấu cho sách, tác giả, thành viên và việc escape ký tự LIKE.

Tất cả các file test đã được compile sạch với `javac --release 17 -encoding UTF-8` (JDK 17) cùng classpath gồm `postgresql-42.7.14.jar` và `junit-platform-console-standalone-1.14.4.jar`, sau đó chạy thực tế trên PostgreSQL 16.15 với database `library_test`: **124/124 test PASS**, 0 failed, exit code 0.

> **Cập nhật refactor 2026-10-30 (chạy lại 2026-10-31):** các fixture lặp lại (`prepare`, `insertBook`, `insertCopy`, `insertMember`, `insertActiveLoan`) đã được gom vào `src/test/java/com/library/dao/support/TestFixtures.java`. Không thêm/bớt test method; chạy lại toàn bộ suite vẫn **124/124 PASS**. Tổng số file test hiện tại là **14** (12 class test + `TestDatabase` + `TestFixtures`).

---

## 2. Ma trận kiểm thử

### 2.1 LoanBusinessLogicTest (22 tests) — *Logic nghiệp vụ*

| Test method | Mục đích |
|---|---|
| `dueDateExactlyFourteenDaysAcrossMonthBoundary` | Jan 20 + 14 = Feb 3 (chuyển tháng) |
| `dueDateExactlyFourteenDaysInLeapYear` | Feb 16, 2024 + 14 = Mar 1 (nhảy qua Feb 29) |
| `dueDateExactlyFourteenDaysInNonLeapYear` | Feb 16, 2026 + 14 = Mar 2 (không có Feb 29) |
| `dueDateExactlyFourteenDaysAcrossYearBoundary` | Dec 20, 2026 + 14 = Jan 3, 2027 |
| `dueDateFifteenDaysAfterLoanDateIsRejected` | 15 ngày → 23514 |
| `markReturnedIsIdempotent` | Gọi 2 lần: lần 1 ảnh hưởng 1 row, lần 2 ảnh hưởng 0 row, `return_date` lần đầu được giữ |
| `markReturnedWithDateBeforeLoanDateIsRejected` | `return_date < loan_date` → 23514, loan vẫn ACTIVE |
| `markReturnedOnSameDayAsLoanDateIsAllowed` | Trả trong ngày |
| `markReturnedWithFutureReturnDateIsAllowedByDatabase` | **Gap E11**: schema không chặn `return_date` trong tương lai |
| `markReturnedOnNonExistentLoanIdReturnsZero` | `loan_id=999999` → 0 row, không exception |
| `markReturnedCannotReactivateAnAlreadyReturnedLoan` | Gọi lại markReturned không thể "sống lại" loan đã RETURNED |
| `findActiveExcludesReturnedLoans` | Chỉ trả về loan có `return_date IS NULL` |
| `loanInsertedWithReturnedStatusRequiresReturnDate` | INSERT RETURNED phải có return_date, nếu không → 23514 |
| `multipleLoanHistoryPerCopyAllowed` | Borrow-return-borrow-return trên cùng copy → 3 loan trong history, 1 active |
| `multipleActiveLoansForDifferentCopiesOfSameBookAllowed` | 2 copy cùng sách → 2 active loan cùng lúc |
| `multipleActiveLoansForSameMemberDifferentBooksAllowed` | 1 member mượn nhiều sách cùng lúc |
| `countActiveExcludesReturnedLoans` | Đếm chỉ khi `return_date IS NULL` |
| `countOverdueDependsOnCallerSuppliedToday` | today=Oct 10/15/16/Nov 1 → overdue count thay đổi |
| `searchOverdueOnlyUsesCriteriaTodayNotSystemClock` | `criteria.today` quyết định overdue, không phải đồng hồ hệ thống |
| `searchWithDateRangeFilterIsInclusive` | `fromDate`/`toDate` include cả 2 đầu |
| `searchByStatusReturnsOnlyMatchingLoans` | Filter `status=ACTIVE` và `status=RETURNED` tách biệt |
| `findActiveByCopyIdReturnsEmptyAfterReturn` | Sau `markReturned`, `findActiveByCopyId` rỗng |

### 2.2 DataConsistencyTest (26 tests) — *Thống nhất dữ liệu*

| Test method | Mục đích |
|---|---|
| `emailUniqueCaseInsensitiveOnInsert` | `User@Example.com` và `USER@example.COM` → 23505 |
| `emailUniqueCaseInsensitiveOnUpdate` | UPDATE email trùng (khác case) → 23505 |
| `emailExistsExcludesSelf` | emailExists với `excludeId` chính xác trả về false cho email của chính mình |
| `memberCodeUniqueOnInsert` | Trùng code → 23505 |
| `memberCodeUniqueOnUpdate` | UPDATE code trùng → 23505 |
| `memberCodeExistsExcludesSelf` | Tự kiểm tra code của mình |
| `isbnUniqueOnInsert` | Trùng ISBN → 23505 |
| `isbnUniqueOnUpdate` | UPDATE ISBN trùng → 23505 |
| `isbnExistsExcludesSelf` | Tự kiểm tra ISBN của mình |
| `isbnTrimmedOnUpdate` | UPDATE với `'  978-X  '` → lưu `'978-X'` |
| `isbnLengthBoundaries` | 13 OK, 20 OK, 12 → 23514, 21 → 22001 |
| `categoryNameUniqueCaseInsensitiveOnInsert` | `Drama` và `DRAMA` → 23505 |
| `categoryNameUniqueCaseInsensitiveOnUpdateViaRawSql` | UPDATE `Drama` → `POETRY` qua raw SQL vì DAO không có method update |
| `blankEmailNormalizedToNullOnInsert` | `'   '` → NULL |
| `blankEmailNormalizedToNullOnUpdate` | UPDATE với `'   '` → NULL |
| `nullEmailIsPreservedOnUpdate` | UPDATE với `null` → NULL |
| `publicationYearBoundaries` | 1000 OK, 2100 OK, 999 → 23514, 2101 → 23514 |
| `copyStatusInvalidValueRejected` | `'LOST'` → 23514 (raw SQL) |
| `loanStatusInvalidValueRejected` | `'OVERDUE'` → 23514 (raw SQL) |
| `memberStatusInvalidValueRejected` | `'BLOCKED'` → 23514 (raw SQL) |
| `updateLoanToReturnedWithoutReturnDateIsRejected` | UPDATE status=RETURNED nhưng return_date=NULL → 23514 |
| `updateLoanToActiveWithReturnDateIsRejected` | UPDATE status=ACTIVE với return_date != NULL → 23514 |
| `updateLoanDueDateNotFourteenDaysIsRejected` | UPDATE due_date = loan_date + 13 → 23514 |
| `updateLoanReturnDateBeforeLoanDateIsRejected` | UPDATE return_date < loan_date → 23514 |
| `authorAndPublisherInsertAssignsIdAndCreatedAt` | Reference data: ID và createdAt được gán |
| `categoryFindByIdReturnsEmptyForUnknownId` | ID không tồn tại → Optional.empty |

### 2.3 CrossTableSynchronizationTest (21 tests) — *Đồng bộ cross-table*

| Test method | Mục đích |
|---|---|
| `bookCopyStatusIsNotAutomaticallySyncedOnLoanInsert` | **Gap E01**: copy.status vẫn AVAILABLE sau khi loan insert |
| `bookCopyStatusIsNotAutomaticallySyncedOnMarkReturned` | **Gap E01**: copy.status vẫn BORROWED sau khi markReturned |
| `manualSyncKeepsCopyStatusConsistentWithLoanState` | Pattern đúng: gọi loanDao + copyDao.updateStatus cùng lúc |
| `dashboardCountsMatchUnderlyingTablesOnEmptyDatabase` | Verify 6 metric khớp `SELECT COUNT(*)` |
| `dashboardCountsStayInSyncAfterEachMutation` | Insert book/copy/member/loan, dashboard phải theo kịp từng bước |
| `memberActiveLoanCountMatchesActualCount` | `member.activeLoanCount` == COUNT(loans WHERE member_id=X AND return_date IS NULL) |
| `memberActiveLoanCountExcludesReturnedHistory` | 2 loan đã trả → activeLoanCount=0 |
| `bookAvailableCopiesMatchesActualCount` | `book.availableCopies` == COUNT(book_copies WHERE book_id=X AND status='AVAILABLE') |
| `existsByBookIdTrueAfterAllLoansReturned` | History được giữ, existsByBookId vẫn true sau return |
| `existsByBookIdFalseForBookWithNoLoans` | Sách chưa từng có loan → false |
| `replaceForBookRemovesOldLinksAndInsertsNewSet` | Replace [1,2] → [3] không còn lại [1,2] |
| `replaceForBookIsOrderIndependent` | [1,2,3] và [3,2,1] cho cùng kết quả |
| `cascadeDeleteBookRemovesBookAuthorsLinks` | Delete book → book_authors rows bị CASCADE remove |
| `restrictDeletePublisherWithBooksFails` | 23503 |
| `restrictDeleteCategoryWithBooksFails` | 23503 |
| `restrictDeleteBookWithCopiesFails` | 23503 |
| `restrictDeleteBookCopyWithLoanHistoryFails` | 23503 (kể cả khi loan đã RETURNED) |
| `restrictDeleteMemberWithLoanHistoryFails` | 23503 |
| `restrictDeleteAuthorWithBookLinksFails` | 23503 |
| `cascadeDeleteBookAlsoRemovesCascadeLinkedAuthorsWhenNoOtherReferences` | CASCADE remove link, giữ author row |
| `noOrphanRowsAfterMixedOperations` | 7 LEFT JOIN check, orphans=0 sau mix ops |

### 2.4 TransactionIsolationTest (9 tests) — *Transaction/đồng thời*

| Test method | Mục đích |
|---|---|
| `twoConcurrentActiveLoansForSameCopySecondFailsWith23505` | 2 tx cùng insert loan cho 1 copy → 23505 hoặc 55P03 |
| `uncommittedInsertNotVisibleToOtherConnection` | READ COMMITTED: tx2 không thấy row uncommitted |
| `rollbackHidesChangesFromOtherConnection` | Sau rollback, row không tồn tại với tx2 |
| `forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout` | `findByIdForUpdate` lock → tx2 updateStatus chặn → 55P03 (lock_timeout 1s) |
| `twoConcurrentBorrowsOnDifferentCopiesOfSameBookBothSucceed` | 2 copy khác nhau của cùng book → 2 tx commit OK |
| `twoConcurrentInsertsWithSameIsbnSecondFailsWith23505` | Trùng ISBN → 23505 |
| `twoConcurrentInsertsWithSameEmailSecondFailsWith23505` | Trùng email (case-insensitive) → 23505 |
| `autoCommitConnectionPersistsInsertImmediately` | autoCommit=true mặc định → mỗi DAO call là 1 tx |
| `explicitTransactionCanRollbackEntireBatch` | autoCommit=false + rollback → batch không persist |

---

## 3. Phát hiện gap & vấn đề

### 3.1 Gap E01 (quan trọng) — `book_copies.status` không tự đồng bộ với `loans`

**Mô tả:** Khi `JdbcLoanDAO.insert()` tạo loan mới cho một copy, nó **không** gọi `JdbcBookCopyDAO.updateStatus(BORROWED)`. Tương tự, `markReturned()` không gọi `updateStatus(AVAILABLE)`. Schema cũng không có trigger nào làm việc này.

**Hệ quả:** Một copy có thể có `status='AVAILABLE'` trong khi đang có loan active, hoặc `status='BORROWED'` sau khi loan đã trả. Dashboard `availableCopies` đếm dựa trên `book_copies.status`, nên sẽ sai lệch so với `activeLoans` (đếm dựa trên `loans.return_date`).

**Test xác nhận gap:**
- `CrossTableSynchronizationTest.bookCopyStatusIsNotAutomaticallySyncedOnLoanInsert`
- `CrossTableSynchronizationTest.bookCopyStatusIsNotAutomaticallySyncedOnMarkReturned`

Hai test này **pass** với hành vi hiện tại (gap), và sẽ **fail** nếu một fix được áp dụng (trigger, service-layer orchestration, hoặc DAO tự sync). Khi đó, cập nhật assertion để phản ánh hành vi mới.

**Khuyến nghị (cho Person 2):** Service layer (chưa implement) phải gọi cả 2 DAO trong cùng một transaction:
```
tx.begin();
loanDao.insert(c, loan);
copyDao.updateStatus(c, copyId, BORROWED);
tx.commit();
```

### 3.2 Gap E11 — `return_date` trong tương lai được chấp nhận

**Mô tả:** CHECK constraint `loans_return_date_check` chỉ yêu cầu `return_date >= loan_date`, không yêu cầu `return_date <= CURRENT_DATE`. DAO cũng không kiểm tra.

**Test xác nhận:** `LoanBusinessLogicTest.markReturnedWithFutureReturnDateIsAllowedByDatabase` — pass với `return_date=2099-12-31`.

**Khuyến nghị:** Quyết định nghiệp vụ — nếu cần chặn, thêm CHECK constraint hoặc validate trong Service.

### 3.3 Vấn đề tiềm ẩn — DELETE copy khi có loan history

`CrossTableSynchronizationTest.restrictDeleteBookCopyWithLoanHistoryFails` cho thấy copy **không thể bị xóa** ngay cả khi tất cả loan đã RETURNED. Điều này bảo vệ lịch sử mượn, nhưng có thể gây khó khăn nếu cần dọn sách hỏng. Đây là quyết định thiết kế đúng cho thư viện, nhưng cần lưu ý khi vận hành.

### 3.4 Vấn đề tiềm ẩn — `existsByBookId` bao gồm cả loan đã trả

`LoanDAO.existsByBookId` trả về `true` cho bất kỳ loan nào (kể cả RETURNED). Điều này có thể được dùng để chặn xóa sách (RESTRICT) — sách đã từng được mượn không thể xóa. Tương tự như copy, đây là bảo vệ lịch sử.

---

## 4. Cách chạy

### 4.1 Yêu cầu môi trường

- **JDK 17+** (project dùng `--release 17`)
- **PostgreSQL 16** với database `library_test` đã được khởi tạo:
  ```powershell
  psql -h 127.0.0.1 -p 5432 -U library_app -d library_test `
       -v ON_ERROR_STOP=1 -f src/main/resources/db/schema.sql
  ```
- **Biến môi trường:**
  - `DB_URL` (mặc định `jdbc:postgresql://127.0.0.1:5432/library_test`)
  - `DB_USER` (mặc định `library_app`)
  - `DB_PASSWORD` (bắt buộc)

### 4.2 Chạy toàn bộ bộ test

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD = '<your-password>'
.\scripts\run-tests.ps1
```

**Linux/Mac (bash):**
```bash
export DB_PASSWORD='<your-password>'
./scripts/run-tests.sh
```

Script tự động:
1. Download `junit-platform-console-standalone-1.14.4.jar` và `postgresql-42.7.14.jar` vào `lib/` (chỉ lần đầu).
2. Compile `src/main/java` → `build/main/` với `-encoding UTF-8`.
3. Compile `src/test/java` → `build/test/` với `-encoding UTF-8`.
4. Chạy `java -jar junit-platform-console-standalone-1.14.4.jar execute --scan-classpath build/test`.

### 4.3 Chạy subset

```powershell
# PowerShell
.\scripts\run-tests.ps1 -TestClassFilter '*TransactionIsolation*'
```

```bash
# bash
./scripts/run-tests.sh '*TransactionIsolation*'
```

### 4.4 Kết quả chạy thực tế (2026-10-27)

Trên môi trường PostgreSQL 16.15 + JDK 17 + `library_test` đã khởi tạo:

| Class | Test method count | Kết quả |
|---|---|---|
| `JdbcBookAuthorDAOTest` | 4 | 4 PASS |
| `JdbcBookCopyDAOTest` | 6 | 6 PASS |
| `JdbcBookDAOTest` | 13 | 13 PASS |
| `JdbcDashboardDAOTest` | 1 | 1 PASS |
| `JdbcLoanDAOTest` | 7 | 7 PASS |
| `JdbcMemberDAOTest` | 6 | 6 PASS |
| `JdbcReferenceDAOTest` | 2 | 2 PASS |
| `LoanBusinessLogicTest` *(mới)* | 22 | 22 PASS |
| `DataConsistencyTest` *(mới)* | 26 | 26 PASS |
| `CrossTableSynchronizationTest` *(mới)* | 21 | 21 PASS |
| `TransactionIsolationTest` *(mới)* | 9 | 9 PASS |
| `DiacriticSearchTest` *(mới)* | 7 | 7 PASS |
| **Tổng** | **124** | **124 PASS** |

Lần chạy lại ngày **2026-10-31** sau refactor (dùng `TestFixtures`) cũng cho kết quả **124/124 PASS**, 0 failed; số test method không đổi.

**Lưu ý `TransactionIsolationTest`:**
- Test `forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout` phụ thuộc `lock_timeout = '1s'`. Nếu hệ thống chậm, có thể tăng lên `'3s'` trong file test.
- Sau khi `55P03` xảy ra, test gọi `tx2.rollback()` trước khi tiếp tục; nếu không, transaction đã abort sẽ trả `25P02` cho statement kế tiếp.
- Test `twoConcurrentActiveLoansForSameCopySecondFailsWith23505` chấp nhận cả 23505 và 55P03 vì thứ tự commit có thể thay đổi. Đây là intentional flexibility, không phải flakiness.

---

## 5. File đã thêm/sửa

### Thêm mới (7 file)

| Path | Mô tả |
|---|---|
| `src/test/java/com/library/dao/LoanBusinessLogicTest.java` | 22 test — logic nghiệp vụ loan |
| `src/test/java/com/library/dao/DataConsistencyTest.java` | 26 test — ràng buộc schema trên INSERT & UPDATE |
| `src/test/java/com/library/dao/CrossTableSynchronizationTest.java` | 21 test — đồng bộ cross-table, CASCADE/RESTRICT, gap E01 |
| `src/test/java/com/library/dao/TransactionIsolationTest.java` | 9 test — multi-connection, lock_timeout, READ COMMITTED |
| `src/test/java/com/library/dao/DiacriticSearchTest.java` | 7 test — tìm kiếm có dấu/không dấu, escape LIKE |
| `scripts/run-tests.ps1` | Runner PowerShell (download JAR, compile, run) |
| `scripts/run-tests.sh` | Runner bash (download JAR, compile, run) |

### Bổ sung trong refactor 2026-10-30

| Path | Mô tả |
|---|---|
| `src/test/java/com/library/dao/support/TestFixtures.java` | Fixture dùng chung cho toàn bộ test DAO; thay các helper `prepare`/`insert*` lặp lại trong từng class |
| `src/main/java/com/library/dao/jdbc/JdbcSupport.java` | Helper JDBC dùng chung: bind tham số, escape LIKE, query/update/insert, phân trang |
| `src/main/java/com/library/dao/jdbc/AbstractNamedEntityDAO.java` | Base class generic cho Author/Category/Publisher |
| `src/main/java/com/library/model/criteria/PageCriteria.java` | State phân trang dùng chung cho ba criteria |
| `src/main/webapp/WEB-INF/tags/pagination.tag` | Tag Previous/Next dùng chung cho bốn màn hình danh sách |

### File hiện có đã sửa trong quá trình hợp nhất

- 4 file test merge đã được sửa 8 lỗi phía test (không đổi schema/DAO để ép test pass):
  - `DataConsistencyTest`: dùng tên category không có trong seed (`Drama`/`Poetry`) thay vì `History`/`Science`.
  - `CrossTableSynchronizationTest`: 5 test raw SQL đổi từ `DataAccessException` sang `SQLException` và dùng `getSQLState()`.
  - `TransactionIsolationTest`: thêm `tx2.rollback()` sau lock timeout `55P03`.
- Production/UI: `schema.sql` thêm `CREATE EXTENSION unaccent`; 5 JDBC DAO dùng `unaccent(...) ILIKE unaccent(?)`; 8 JSP form thêm `accept-charset="UTF-8"`.
- Tài liệu: `README.md`, `docs/database-design.md`, `docs/implementation-plan.md`, `docs/java-concepts-mapping.md`, `docs/person1-implementation-plan.md`, `docs/runtime-toolchain-audit.md`.

### Build verification

```
$ javac --release 17 -encoding UTF-8 -d build/main -cp lib/postgresql-42.7.14.jar src/main/java/**/*.java
# 40/40 main files compiled, 0 errors

$ javac --release 17 -encoding UTF-8 -d build/test -cp build/main:lib/postgresql-42.7.14.jar:lib/junit-platform-console-standalone-1.14.4.jar src/test/java/**/*.java
# 14/14 test files compiled (12 test classes + TestDatabase + TestFixtures), 0 errors
```

---

## 6. Bao phủ vs khoảng trống

### Bao phủ tốt

- ✅ Quy tắc 14 ngày (CHECK constraint) — test trên 4 edge case (cross-month, leap year, non-leap, cross-year)
- ✅ Partial unique index `idx_loans_active_partial_unique` — test cả single-tx lẫn concurrent 2-tx
- ✅ UNIQUE case-insensitive (email, category) — test trên cả INSERT và UPDATE
- ✅ Enum CHECK (copy/loan/member status) — test trên cả 3 enum
- ✅ `loans_status_return_date_consistency` — test trên cả INSERT và UPDATE, cả 4 nhánh
- ✅ `markReturned` semantics — idempotent, ordering, no-op cho non-existent
- ✅ CASCADE/RESTRICT — test toàn bộ 7 FK
- ✅ Dashboard synchronization — 6 metric khớp `SELECT COUNT(*)` ở mọi bước mutation
- ✅ `member.activeLoanCount` / `book.availableCopies` — synchronization cross-table
- ✅ READ COMMITTED isolation — uncommitted invisible, rollback hides
- ✅ `FOR UPDATE` row lock — verified via `lock_timeout` 55P03

### Khoảng trống còn lại (theo README §6, không thuộc phạm vi Person 1)

- ❌ `pom.xml` / `mvn test` — chờ Person 2 (script `run-tests.ps1`/`run-tests.sh` đã thay thế)
- ❌ Service/Controller/HTTP integration — chờ Person 2
- ❌ E02 — chặn member INACTIVE mượn sách — chưa test vì là decision nghiệp vụ, chưa áp dụng
- ❌ E09 — unique cho author/publisher name — chờ nhóm chốt quy tắc
- ❌ E13/E14 — `created_at` dùng TIMESTAMP thay vì TIMESTAMPTZ — không test được vì cần thay đổi schema
- ❌ E21 — soft-delete — chưa áp dụng
- ❌ Trigger E01 tự sync `book_copies.status` — đang được test như gap, khi áp dụng cần update assertion

---

## 7. Bước tiếp theo gợi ý

1. **Đã chạy thực tế:** 124/124 test PASS trên PostgreSQL 16.15, `library_test`, JDK 17, JUnit Platform Console `1.14.4`, pgJDBC `42.7.14`.
2. **Quyết định gap E01**: nhóm chốt cơ chế (trigger vs service-layer). Nếu chọn service, khi Person 2 implement `LoanService.borrow()`, chạy lại `CrossTableSynchronizationTest` — 2 test gap sẽ fail (đúng như mong đợi) và cần update assertion sang `CopyStatus.BORROWED`.
3. **Thêm test Service khi Person 2 hoàn tất**: cùng pattern (mở connection, gọi service, assert). Có thể thêm file `LoanServiceTest.java` theo cách tương tự.
4. **CI/CD**: khi có `pom.xml`, cấu hình Surefire để chạy các class test này; các file đã nằm trong `src/test/java/com/library/dao/` nên `mvn test` sẽ tự pick up.
