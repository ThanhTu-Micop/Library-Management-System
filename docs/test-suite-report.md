# Test Suite Report — Library Management System

> **Scope:** Bộ test JUnit 5 kiểm tra **DAO/JDBC**, **ràng buộc schema**, **logic nghiệp vụ**, **đồng bộ cross-table**, và **transaction/đồng thời** cho dự án `Library-Management-System` (Person 1 scope: DAO/JDBC + PostgreSQL).
>
> **Generated:** 2026-10-27
> **Last re-run:** 2026-11-01 (sau khi tái tổ hợp bộ test)
> **Target codebase:** working tree based on `main` at `b499748` in [github.com/ThanhTu-Micop/Library-Management-System](https://github.com/ThanhTu-Micop/Library-Management-System) (test-suite recombination and documentation updates are uncommitted as of this report)
> **Test framework:** JUnit 5 via `junit-platform-console-standalone` (không Maven, theo README §3.2)

---

## 1. Tóm tắt

Bộ test hiện có **74 test method** trên **12 class test** (14 file source, gồm cả `TestDatabase` và `TestFixtures`). Đây là kết quả của đợt tái tổ hợp ngày **2026-11-01**: mỗi rule có đúng một class "sở hữu", các boundary cùng rule được gộp bằng `assertAll`, và không có assertion nào bị mất so với bộ 124 test trước đó.

| # | File | Test methods | Nhóm trọng tâm |
|---|------|--------------|----------------|
| 1 | `JdbcBookDAOTest.java` | 14 | CRUD, ISBN, năm xuất bản, search/filter/pagination, `availableCopies` |
| 2 | `JdbcMemberDAOTest.java` | 6 | CRUD, mã/email unique, chuẩn hoá email rỗng, search, `activeLoanCount` |
| 3 | `JdbcReferenceDAOTest.java` | 2 | Category/Author/Publisher, unique tên category không phân biệt hoa thường |
| 4 | `JdbcBookAuthorDAOTest.java` | 3 | Replace author set, thứ tự, chống trùng, no-op |
| 5 | `JdbcBookCopyDAOTest.java` | 5 | CRUD bản sao, `FOR UPDATE`, available copies, xoá và RESTRICT |
| 6 | `JdbcLoanDAOTest.java` | 4 | Borrow/return flow, partial unique index, lịch sử, projection |
| 7 | `JdbcDashboardDAOTest.java` | 2 | 6 metric dashboard khớp bảng gốc, cập nhật theo mutation |
| 8 | `CrossTableSynchronizationTest.java` | 5 | **Gap E01**, CASCADE/RESTRICT, orphan check |
| 9 | `DataConsistencyTest.java` | 5 | CHECK enum, loan consistency, natural-key unique trên raw UPDATE |
| 10 | `LoanBusinessLogicTest.java` | 16 | Quy tắc 14 ngày, return transition, overdue, search |
| 11 | `TransactionIsolationTest.java` | 8 | READ COMMITTED, rollback, `FOR UPDATE`, concurrent unique |
| 12 | `DiacriticSearchTest.java` | 4 | Tìm kiếm có dấu/không dấu, escape LIKE |
| | **Tổng** | **74** | |

Tất cả file test compile sạch với `javac --release 17 -encoding UTF-8` (JDK 17) trên classpath `postgresql-42.7.14.jar` + `junit-platform-console-standalone-1.14.4.jar`, sau đó chạy thực tế trên PostgreSQL 16.15 với database `library_test`:

```text
[       74 tests found           ]
[       74 tests successful      ]
[         0 tests failed          ]
[         0 tests skipped         ]
[        15 containers successful ]
```

Kết quả **74/74 PASS**, exit code 0, **15/15 container** (12 test class + 3 JUnit engine container). Đây là standalone JUnit evidence, không phải Maven/Surefire.

### 1.1 Chiến lược tái tổ hợp

Bộ 124 test cũ có nhiều assertion trùng rule nằm rải ở 3–4 class. Đợt refactor giữ nguyên toàn bộ assertion nhưng tổ chức lại theo quy tắc:

- **DAO behavior** thuộc về `Jdbc*DAOTest` tương ứng. Ví dụ ISBN unique/length/trim nằm ở `JdbcBookDAOTest`, không lặp ở `DataConsistencyTest`.
- **Cross-table behavior** chỉ nằm ở `CrossTableSynchronizationTest`: manual copy-status sync, CASCADE/RESTRICT, orphan rows.
- **Raw-SQL schema invariant** chỉ nằm ở `DataConsistencyTest`: các UPDATE mà DAO không có method tương ứng.
- **Boundary cùng một rule** được gộp bằng `assertAll`, nên từng case vẫn báo pass/fail riêng.
- Insert và update của cùng một natural key được kiểm trong cùng một test thay vì hai test gần giống nhau.

Thống kê trên 12 file test: **145 lambda expression** và **34 lần `assertAll`**. Việc gộp không làm giảm coverage; các test cũ chỉ được chuyển về đúng "nhà" hoặc gộp theo rule.

> **Lịch sử:** bộ test từng đạt **124/124 PASS** trước khi tái tổ hợp. Con số 124 trong các tài liệu cũ là baseline lịch sử, không phải trạng thái hiện tại.

---

## 2. Ma trận kiểm thử

### 2.1 DAO persistence — 36 tests

#### 2.1.1 `JdbcBookDAOTest` (14 tests)

| Test method | Mục đích |
|---|---|
| `insertAndFindById` | Insert sách, ID được gán, join publisher/category trả đúng tên, `availableCopies=0`, `createdAt` có giá trị |
| `findByIdNotFound` | ID không tồn tại → `Optional.empty()` |
| `updateBook` | Update title, năm, publisher, category; đọc lại thấy giá trị mới |
| `deleteBook` | Delete sách; `findById` rỗng sau đó |
| `isbnIsUniqueOnInsertAndUpdate` | Gộp 3 test cũ: trùng ISBN khi INSERT và UPDATE đều → 23505; `isbnExists` có/không `excludeId`; UPDATE fail không đổi dữ liệu |
| `isbnLengthMustBeBetween13And20` | 13 và 20 ký tự OK; 12 → 23514; 21 → 22001 (`VARCHAR(20)`) |
| `isbnIsTrimmedOnInsertAndUpdate` | `'  978-X  '` được trim khi INSERT và UPDATE; `isbnExists` vẫn khớp sau trim |
| `publicationYearAcceptsBoundariesAndPreservesNull` | `NULL` được giữ; 1000 và 2100 OK; 999 và 2101 → 23514 |
| `keywordFilterRejectsNonMatchingBook` | Keyword khớp 1 sách; keyword không tồn tại → 0 |
| `publisherAndCategoryFilters` | Filter publisher và category trả đúng tập con |
| `authorNameFilter` | Filter theo tên tác giả khớp/không khớp |
| `availableOnlyFilter` | `availableOnly=true` chỉ trả sách có bản sao AVAILABLE và `availableCopies` đúng |
| `paginationAppliesFiltersToCountAndData` | Filter áp dụng cho cả `totalElements` lẫn data; trang cuối `isLast()` |
| `availableCopiesTracksOnlyAvailableCopies` | `availableCopies` bằng raw `COUNT(status='AVAILABLE')`, thay đổi khi copy đổi trạng thái |

#### 2.1.2 `JdbcMemberDAOTest` (6 tests)

| Test method | Mục đích |
|---|---|
| `insertAndFindById` | Insert member, ID và `createdAt` được gán |
| `memberCodeIsUniqueAndExistsExcludesSelf` | Mã unique khi INSERT và UPDATE → 23505; `memberCodeExists` loại trừ chính row đang sửa |
| `emailIsUniqueCaseInsensitivelyAndExistsExcludesSelf` | `Two@Test.com` và `TWO@TEST.COM` xung đột khi INSERT/UPDATE → 23505; `emailExists` loại trừ self |
| `blankAndNullEmailAreNormalizedOnInsertAndUpdate` | Chuỗi rỗng/blank → `NULL` khi INSERT và UPDATE; `NULL` giữ nguyên |
| `searchByStatusAndKeyword` | Filter status và keyword trả đúng member |
| `activeLoanCountTracksOnlyOpenLoans` | `activeLoanCount` bằng raw count `return_date IS NULL`; giảm dần khi trả; lịch sử vẫn còn trong bảng |

#### 2.1.3 `JdbcReferenceDAOTest` (2 tests)

| Test method | Mục đích |
|---|---|
| `categoryNamesAreUniqueCaseInsensitively` | `Novel` và `NOVEL` xung đột → 23505; raw UPDATE `Drama` → `POETRY` cũng bị unique index chặn; `findById` unknown ID rỗng |
| `authorAndPublisherCreatedAtAreMapped` | Author/Publisher insert gán ID và `createdAt` |

#### 2.1.4 `JdbcBookAuthorDAOTest` (3 tests)

| Test method | Mục đích |
|---|---|
| `replaceForBookReplacesSetAndIsOrderIndependent` | Replace `[1,2]` → `[3]` không còn link cũ; `[1,2,3]` và `[3,2,1]` cho cùng tập author |
| `duplicateAuthorIdsAreDeduplicated` | `[1,1,1]` chỉ tạo 1 link |
| `emptyReplaceAndDeleteAreSafeNoOps` | Replace list rỗng xoá hết link; `deleteByBookId` không ném lỗi khi không còn link |

#### 2.1.5 `JdbcBookCopyDAOTest` (5 tests)

| Test method | Mục đích |
|---|---|
| `insertAndFindByBookId` | Insert copy, ID/createdAt được gán, đọc theo book trả đúng status |
| `findByIdForUpdateAndUpdateStatus` | `findByIdForUpdate` trả row; `updateStatus` đổi trạng thái và đọc lại thấy |
| `findAvailableWithBookReturnsOnlyAvailableCopies` | Chỉ trả copy AVAILABLE; projection có `bookTitle` |
| `deleteByBookIdHandlesZeroAndMultipleCopies` | Xoá sách không có copy và sách có nhiều copy đều an toàn |
| `deletingCopiesWithLoanHistoryIsRejected` | Copy có loan history bị RESTRICT → 23503 |

#### 2.1.6 `JdbcLoanDAOTest` (4 tests)

| Test method | Mục đích |
|---|---|
| `borrowAndReturnFlow` | Insert loan, `findActiveByCopyId` thấy loan; `markReturned` làm nó rỗng |
| `onlyOneActiveLoanPerCopy` | Partial unique index chặn loan ACTIVE thứ hai trên cùng copy → 23505 |
| `existsByBookIdIncludesAllHistory` | `false` khi chưa có loan; `true` sau khi mượn; vẫn `true` sau khi trả |
| `joinedDisplayFieldsArePopulated` | Search/findActive trả `bookTitle`, `copyCode`, `memberName`, `createdAt` |

#### 2.1.7 `JdbcDashboardDAOTest` (2 tests)

| Test method | Mục đích |
|---|---|
| `emptyDatabaseCountsMatchRawTables` | 6 metric dashboard bằng raw `COUNT(*)` trên database rỗng và đều bằng 0 |
| `countsTrackMutationsAndFixedToday` | Metric cập nhật sau từng bước thêm book/copy/member/loan/return; `due_date < today` mới overdue; `today` do caller truyền |

### 2.2 Cross-table synchronization — 5 tests

`CrossTableSynchronizationTest` chỉ giữ các invariant bắc qua nhiều bảng.

| Test method | Mục đích |
|---|---|
| `copyStatusSyncIsManualNotAutomatic` | **Gap E01**: copy.status không tự đổi khi insert loan hoặc `markReturned`; pattern sync thủ công giữ 2 bảng nhất quán |
| `cascadeDeleteBookRemovesLinksAndPreservesAuthors` | Xoá book → `book_authors` bị CASCADE xoá, row `authors` còn nguyên |
| `restrictDeleteReferenceRowsWithBooksFails` | Xoá publisher/category/author đang được book tham chiếu → 23503 |
| `restrictDeleteRowsWithDependentChildrenFails` | Book có copy, copy có loan, member có loan đều bị RESTRICT; lịch sử đã RETURNED vẫn bảo vệ |
| `noOrphanRowsAfterMixedOperations` | 7 phép LEFT JOIN kiểm tra orphan sau chuỗi insert/return/re-borrow; tổng orphan = 0 |

### 2.3 Schema invariants — 5 tests

`DataConsistencyTest` dùng raw SQL để kiểm ràng buộc mà DAO không có method tương ứng; không lặp lại hành vi DAO.

| Test method | Mục đích |
|---|---|
| `enumColumnsRejectUnknownValues` | Raw INSERT `copy_status='LOST'`, `loan_status='OVERDUE'`, `member_status='BLOCKED'` → 23514 |
| `loanStatusAndReturnDateMustBeConsistentOnUpdate` | Raw UPDATE RETURNED thiếu ngày, ACTIVE còn ngày, return_date < loan_date → 23514; UPDATE fail atomic |
| `loanDueDateMustBeLoanDatePlus14OnUpdate` | Raw UPDATE `due_date = loan_date + 13` → 23514 |
| `memberNaturalKeysRejectDuplicatesOnUpdate` | Raw UPDATE trùng `email` (khác case) hoặc `member_code` → 23505; dữ liệu cũ không đổi |
| `bookAndCategoryNaturalKeysRejectDuplicatesOnUpdate` | Raw UPDATE trùng `isbn` hoặc tên category (khác case) → 23505; dữ liệu cũ không đổi |

### 2.4 Loan business logic — 16 tests

`LoanBusinessLogicTest` sở hữu quy tắc 14 ngày, return transition, overdue và các truy vấn lịch sử.

| Test method | Mục đích |
|---|---|
| `dueDateAcrossMonthAndYearBoundariesIsExactlyFourteenDays` | Jan 20 + 14 = Feb 3; Dec 20, 2026 + 14 = Jan 3, 2027 |
| `dueDateHandlesLeapAndNonLeapFebruary` | Feb 16, 2024 + 14 = Mar 1 (có Feb 29); Feb 16, 2026 + 14 = Mar 2 |
| `dueDateMustBeExactlyFourteenDays` | 13 ngày và 15 ngày đều → 23514 |
| `markReturnedIsIdempotentAndPreservesFirstReturnDate` | Lần 1 trả 1 row; lần 2/3 trả 0 row; `return_date` lần đầu được giữ |
| `markReturnedWithDateBeforeLoanDateIsRejected` | `return_date < loan_date` → 23514; loan vẫn ACTIVE, `return_date=NULL` |
| `markReturnedAcceptsSameDayAndFutureDates` | Trả trong ngày OK; **Gap E11**: `2099-12-31` vẫn được chấp nhận |
| `markReturnedOnNonExistentLoanIdReturnsZero` | `loan_id=999999` → 0 row, không exception |
| `activeLoanQueriesExcludeReturnedLoans` | `findActive`, `findActiveByCopyId` chỉ trả loan `return_date IS NULL` |
| `countActiveExcludesReturnedLoans` | Đếm giảm từ 3 → 2 → 1 khi trả dần |
| `loanInsertedWithReturnedStatusRequiresReturnDate` | INSERT RETURNED có ngày OK; thiếu ngày → 23514 |
| `multipleLoanHistoryPerCopyAllowed` | Cùng copy qua 3 chu kỳ → 3 loan history, 1 active |
| `multipleActiveLoansForDifferentCopiesOfSameBookAllowed` | 2 copy cùng sách → 2 active loan |
| `multipleActiveLoansForSameMemberDifferentBooksAllowed` | 1 member mượn nhiều sách cùng lúc |
| `overdueCountingUsesCallerSuppliedToday` | `due_date < today`; due đúng hôm nay không overdue; count và search dùng cùng predicate |
| `searchWithDateRangeFilterIsInclusive` | `fromDate`/`toDate` bao gồm cả hai đầu |
| `searchByStatusReturnsOnlyMatchingLoans` | Filter ACTIVE và RETURNED tách biệt đúng |

### 2.5 Transaction isolation — 8 tests

| Test method | Mục đích |
|---|---|
| `twoConcurrentActiveLoansForSameCopySecondFailsWith23505` | 2 transaction cùng mượn 1 copy → 23505 hoặc 55P03 |
| `uncommittedInsertNotVisibleToOtherConnection` | READ COMMITTED: tx2 không thấy row chưa commit; thấy sau commit |
| `rollbackHidesChangesFromOtherConnection` | Sau rollback, row không tồn tại với tx2 |
| `forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout` | `findByIdForUpdate` giữ lock; tx2 `updateStatus` → 55P03; sau commit tx2 retry OK |
| `twoConcurrentBorrowsOnDifferentCopiesOfSameBookBothSucceed` | 2 copy khác nhau → 2 transaction commit thành công |
| `twoConcurrentInsertsWithSameIsbnSecondFailsWith23505` | Trùng ISBN → 23505 |
| `twoConcurrentInsertsWithSameEmailSecondFailsWith23505` | Trùng email khác case → 23505 |
| `autoCommitPersistsInsertAndExplicitTransactionRollsBackBatch` | autoCommit lưu ngay; transaction tường minh rollback xoá cả batch |

### 2.6 Vietnamese diacritic search — 4 tests

| Test method | Mục đích |
|---|---|
| `bookKeywordSearchIgnoresCaseDiacriticsAndPosition` | `Chí Phèo`, `Chi Pheo`, `CHÍ PHÈO`, `pheo` đều khớp (4 case trong `assertAll`) |
| `authorNameSearchIsAccentInsensitive` | `Nguyen Nhat Anh` và `Nguyễn Nhất Anh` cùng khớp |
| `memberNameSearchIsAccentInsensitive` | `Nguyen Van An` và `Nguyễn Văn An` cùng khớp |
| `likeWildcardsRemainEscaped` | `%` và `_` bị escape; `100%` khớp title chứa `%` |

---

## 3. Phát hiện gap & vấn đề

### 3.1 Gap E01 (quan trọng) — `book_copies.status` không tự đồng bộ với `loans`

**Mô tả:** `JdbcLoanDAO.insert()` không gọi `JdbcBookCopyDAO.updateStatus(BORROWED)`, và `markReturned()` không gọi `updateStatus(AVAILABLE)`. Schema cũng không có trigger.

**Hệ quả:** Copy có thể `AVAILABLE` trong khi đang có loan active, hoặc `BORROWED` sau khi đã trả. Dashboard `availableCopies` (đọc `book_copies.status`) sẽ lệch với `activeLoans` (đọc `loans.return_date`).

**Test xác nhận:** `CrossTableSynchronizationTest.copyStatusSyncIsManualNotAutomatic` — test pass với hành vi hiện tại (gap) và sẽ fail nếu một fix được áp dụng; khi đó cập nhật assertion theo hành vi mới.

**Khuyến nghị (Person 2):** Service layer phải gọi cả hai DAO trong cùng transaction:

```
tx.begin();
loanDao.insert(c, loan);
copyDao.updateStatus(c, copyId, BORROWED);
tx.commit();
```

### 3.2 Gap E11 — `return_date` trong tương lai được chấp nhận

**Mô tả:** `loans_return_date_check` chỉ yêu cầu `return_date >= loan_date`, không chặn `return_date > CURRENT_DATE`; DAO cũng không kiểm tra.

**Test xác nhận:** `LoanBusinessLogicTest.markReturnedAcceptsSameDayAndFutureDates` — pass với `return_date=2099-12-31`.

**Khuyến nghị:** Quyết định nghiệp vụ; nếu cần chặn, thêm CHECK constraint hoặc validate trong Service.

### 3.3 Vấn đề tiềm ẩn — DELETE copy khi có loan history

`JdbcBookCopyDAOTest.deletingCopiesWithLoanHistoryIsRejected` và `CrossTableSynchronizationTest.restrictDeleteRowsWithDependentChildrenFails` cho thấy copy **không thể bị xóa** ngay cả khi mọi loan đã RETURNED. Điều này bảo vệ lịch sử mượn nhưng gây khó khi cần dọn sách hỏng; cần lưu ý khi vận hành.

### 3.4 Vấn đề tiềm ẩn — `existsByBookId` bao gồm cả loan đã trả

`JdbcLoanDAOTest.existsByBookIdIncludesAllHistory` xác nhận `LoanDAO.existsByBookId` trả `true` cho bất kỳ loan nào, kể cả RETURNED. Đây là bảo vệ lịch sử (sách từng được mượn không thể xóa) nhưng cần cân nhắc nếu nghiệp vụ cho phép thanh lý sách cũ.

---

## 4. Cách chạy

### 4.1 Yêu cầu môi trường

- **JDK 17+** (project dùng `--release 17`)
- **PostgreSQL 16** với database `library_test` đã khởi tạo:
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

### 4.4 Kết quả chạy thực tế (2026-11-01)

Trên PostgreSQL 16.15 + JDK 17 + `library_test` đã khởi tạo:

| Class | Test method count | Kết quả |
|---|---|---|
| `JdbcBookDAOTest` | 14 | 14 PASS |
| `JdbcMemberDAOTest` | 6 | 6 PASS |
| `JdbcReferenceDAOTest` | 2 | 2 PASS |
| `JdbcBookAuthorDAOTest` | 3 | 3 PASS |
| `JdbcBookCopyDAOTest` | 5 | 5 PASS |
| `JdbcLoanDAOTest` | 4 | 4 PASS |
| `JdbcDashboardDAOTest` | 2 | 2 PASS |
| `CrossTableSynchronizationTest` | 5 | 5 PASS |
| `DataConsistencyTest` | 5 | 5 PASS |
| `LoanBusinessLogicTest` | 16 | 16 PASS |
| `TransactionIsolationTest` | 8 | 8 PASS |
| `DiacriticSearchTest` | 4 | 4 PASS |
| **Tổng** | **74** | **74 PASS** |

Kết quả container: **15/15 successful** (12 test class + 3 JUnit engine container), **0 failed**, **0 skipped**, exit code 0.

**Lưu ý `TransactionIsolationTest`:**
- `forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout` phụ thuộc `lock_timeout = '1s'`; nếu máy chậm, tăng lên `'3s'`.
- Sau `55P03`, test gọi `tx2.rollback()` trước khi tiếp tục; nếu không, transaction đã abort sẽ trả `25P02`.
- `twoConcurrentActiveLoansForSameCopySecondFailsWith23505` chấp nhận cả `23505` và `55P03` vì thứ tự commit có thể thay đổi; đây là intentional flexibility.

---

## 5. Lịch sử thay đổi bộ test

### Baseline 2026-10-27

- Thêm 4 class test mở rộng (`LoanBusinessLogicTest`, `DataConsistencyTest`, `CrossTableSynchronizationTest`, `TransactionIsolationTest`) và `DiacriticSearchTest`.
- Tổng thời điểm đó: **124 test method** trên 12 class test.

### Refactor 2026-10-30

- Gom fixture lặp lại vào `TestFixtures`; thêm `JdbcSupport`, `AbstractNamedEntityDAO`, `PageCriteria`, `pagination.tag`.
- Không đổi số test method; vẫn **124/124 PASS**.

### Dọn dẹp 2026-11-01

- Xóa `model/enums/LoanState.java` và `DatabaseConnection.closeQuietly`.
- Thay chuỗi SQLState hardcode bằng `AppConfig.SQLSTATE_*`; dùng `AppConfig.LOAN_PERIOD_DAYS`.
- Sửa BOM UTF-8 và mojibake ở 3 file test; BOM làm `javac` báo `illegal character: '\ufeff'`.

### Tái tổ hợp 2026-11-01

- Chuyển toàn bộ assertion về đúng class sở hữu rule; gộp insert/update và boundary cùng rule bằng `assertAll`.
- **124 → 74 test method**, không mất assertion; 12 class test, 14 file source.
- Chạy lại thực tế: **39/39 main + 14/14 test biên dịch, 74/74 test PASS, 0 skipped, 15/15 container OK**.
- Thống kê: **145 lambda**, **34 `assertAll`** trong test source.

### Build verification

```
$ javac --release 17 -encoding UTF-8 -d build/main -cp lib/postgresql-42.7.14.jar src/main/java/**/*.java
# 39/39 main files compiled, 0 errors

$ javac --release 17 -encoding UTF-8 -d build/test -cp build/main:lib/postgresql-42.7.14.jar:lib/junit-platform-console-standalone-1.14.4.jar src/test/java/**/*.java
# 14/14 test files compiled (12 test classes + TestDatabase + TestFixtures), 0 errors
```

---

## 6. Bao phủ vs khoảng trống

### Bao phủ tốt

- ✅ Quy tắc 14 ngày — cross-month, leap year, non-leap, cross-year, và boundary 13/15 ngày bị từ chối
- ✅ Partial unique index `idx_loans_active_partial_unique` — cả single-transaction lẫn concurrent 2-transaction
- ✅ UNIQUE natural keys — ISBN exact; email và category không phân biệt hoa thường — kiểm cả INSERT và UPDATE
- ✅ Enum CHECK (copy/loan/member status) — gộp trong một raw-SQL test
- ✅ `loans_status_return_date_consistency` — RETURNED thiếu ngày, ACTIVE còn ngày, ngày trả trước ngày mượn
- ✅ `markReturned` semantics — idempotent, giữ ngày trả đầu tiên, no-op cho ID không tồn tại
- ✅ CASCADE/RESTRICT — đủ 7 FK, kể cả lịch sử đã trả
- ✅ Dashboard synchronization — 6 metric khớp `SELECT COUNT(*)` ở mọi bước mutation
- ✅ `member.activeLoanCount` / `book.availableCopies` — projection khớp raw count
- ✅ READ COMMITTED isolation — uncommitted invisible, rollback hides
- ✅ `FOR UPDATE` row lock — xác nhận qua `lock_timeout` 55P03
- ✅ Tìm kiếm tiếng Việt — có dấu/không dấu/uppercase/partial và escape LIKE

### Khoảng trống còn lại (theo README §6, không thuộc phạm vi Person 1)

- ❌ `pom.xml` / `mvn test` — chờ Person 2 (script `run-tests.ps1`/`run-tests.sh` thay thế tạm)
- ❌ Service/Controller/HTTP integration — chờ Person 2
- ❌ E02 — chặn member `INACTIVE` mượn sách — chưa áp dụng quyết định nghiệp vụ
- ❌ E09 — unique cho author/publisher name — chờ nhóm chốt quy tắc
- ❌ E13/E14 — `created_at` dùng `TIMESTAMP` thay vì `TIMESTAMPTZ` — cần đổi schema
- ❌ E21 — soft-delete — chưa áp dụng
- ❌ Trigger E01 tự sync `book_copies.status` — đang test như gap; khi áp dụng cần update assertion

---

## 7. Bước tiếp theo gợi ý

1. **Đã chạy thực tế:** 74/74 test PASS trên PostgreSQL 16.15, `library_test`, JDK 17, JUnit Platform Console `1.14.4`, pgJDBC `42.7.14`.
2. **Quyết định gap E01:** nhóm chốt cơ chế (trigger vs service-layer). Nếu chọn service, khi Person 2 implement `LoanService.borrow()`, chạy lại `CrossTableSynchronizationTest` — test gap sẽ fail đúng như mong đợi và cần cập nhật assertion sang `CopyStatus.BORROWED`.
3. **Thêm test Service khi Person 2 hoàn tất:** cùng pattern (mở connection, gọi service, assert); có thể thêm `LoanServiceTest.java`.
4. **CI/CD:** khi có `pom.xml`, cấu hình Surefire để chạy các class test này; các file đã nằm trong `src/test/java/com/library/dao/` nên `mvn test` sẽ tự pick up.
