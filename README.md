# Library-Management-System

Library Management System — đồ án phân chia làm 2 người:

- **Person 1:** PostgreSQL database, SQL, DAO/JDBC, DAO tests, JSP/UI, CSS/JS, docs.
- **Person 2:** Service, business logic, Controller/Servlet, build (`pom.xml`), WAR, deployment, HTTP integration.

> Trạng thái hiện tại: Person 1 scope đã hoàn thiện và kiểm chứng bằng công cụ độc lập (không Maven/WAR/Tomcat deployment). Phần build, deployment và HTTP vẫn còn chờ Person 2 (xem mục [Known gaps](#known-gaps)).

## 1. Cấu trúc dự án

```
src/main/java/com/library/{model,dao,dao.jdbc,config,exception}
src/main/java/com/library/model/{criteria,enums}
src/main/resources/db/{schema.sql,seed.sql}
src/main/webapp/WEB-INF/views/{books,members,loans,common,errors}
src/main/webapp/WEB-INF/tags/pagination.tag
src/main/webapp/assets/{css,js}
src/test/java/com/library/{dao,dao/support}
scripts/{run-tests.ps1,run-tests.sh,with-postgres.ps1}
docs/{database-design,implementation-plan,java-concepts-mapping,runtime-toolchain-audit,person1-implementation-plan,test-suite-report}.md
```

## 2. Cơ sở dữ liệu (Person 1)

PostgreSQL 16.15, chạy trên `127.0.0.1:5432`, vai trò ứng dụng `library_app`.

### 2.1 Áp dụng schema và seed

```powershell
$env:PGPASSWORD = Get-Content '<secrets-path>\library_app.password' -Raw
$env:PGCLIENTENCODING = 'UTF8'

# library_db (dữ liệu mẫu đầy đủ)
psql -h 127.0.0.1 -p 5432 -U library_app -d library_db -v ON_ERROR_STOP=1 -f src/main/resources/db/schema.sql
psql -h 127.0.0.1 -p 5432 -U library_app -d library_db -v ON_ERROR_STOP=1 -f src/main/resources/db/seed.sql

# library_test (dùng cho kiểm thử DAO, có thể reset an toàn)
psql -h 127.0.0.1 -p 5432 -U library_app -d library_test -v ON_ERROR_STOP=1 -f src/main/resources/db/schema.sql
psql -h 127.0.0.1 -p 5432 -U library_app -d library_test -v ON_ERROR_STOP=1 -f src/main/resources/db/seed.sql
```

Mật khẩu thực không được lưu trong kho lưu chính, chỉ ở bên ngoài workspace (`C:\Users\BINHND\dev\secrets\library_app.password`).

`schema.sql` tự chạy `CREATE EXTENSION IF NOT EXISTS unaccent;`. Extension này đi kèm PostgreSQL (`postgresql-contrib`) và đã được kiểm tra là cài được bằng vai trò `library_app`. Nếu áp dụng lên database đã tồn tại mà không chạy lại toàn bộ schema, hãy chạy riêng:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
```

### 2.2 Biến môi trường mà DAO đọc

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://127.0.0.1:5432/library_db` |
| `DB_USER` | Tên tài khoản database | `library_app` |
| `DB_PASSWORD` | Mật khẩu (bắt buộc) | — |
| `DB_CONFIG` | Đường dẫn file chứa mật khẩu (dòng đầu tiên) | tùy chọn |

`DatabaseConnection` đọc từ biến môi trường và (tuỳ chọn) file mật khẩu bên ngoài; không giữ hằng mật khẩu trong mã nguồn.

Riêng test DAO dùng `TestDatabase`, đọc `TEST_DB_URL` trước rồi mới đến `DB_URL`; URL phải chứa `_test` để tránh xoá nhầm database phát triển.

### 2.3 Các ràng buộc quan trọng (database enforcement)

- `books.isbn`: unique, 13–20 ký tự sau khi cắt khoảng trắng (`CHECK char_length(btrim(isbn)) BETWEEN 13 AND 20`).
- `loans.due_date = loan_date + 14` (khoảng thời gian mượn cố định 14 ngày).
- Một bản sao chỉ có tối đa một phiếu mượn đang hoạt động (`idx_loans_active_partial_unique`).
- Trạng thái `loans.status` (`ACTIVE`/`RETURNED`) nhất quán với `return_date`.
- 7 khóa ngoại: `ON DELETE RESTRICT` cho người xuất bản, thể loại, bản sao, thành viên, tác giả; `ON DELETE CASCADE` từ sách sang liên kết tác giả.
- Tìm kiếm sách/thành viên không phân biệt dấu tiếng Việt: `unaccent(title) ILIKE unaccent(?)`, nên `Chí Phèo`, `Chi Pheo`, `CHÍ PHÈO` đều khớp.
- Không có dòng mồ côi sau các bài kiểm tra ràng buộc (xem mục kiểm tra bên dưới).

## 3. Xây dựng và kiểm thử (độc lập, không Maven)

### 3.1 Biên dịch

```powershell
javac --release 17 -encoding UTF-8 -d <main-out> -cp postgresql-42.7.14.jar (src/main/java/**/*.java)
javac --release 17 -encoding UTF-8 -d <test-out> -cp "<main-out>;postgresql-42.7.14.jar;junit-platform-console-standalone-1.14.4.jar" (src/test/java/**/*.java)
```

Kết quả lần kiểm chứng gần nhất: **40/40 main**, **14/14 test** file biên dịch thành công (12 class test + `TestDatabase` + `TestFixtures`).

### 3.2 Kiểm thử DAO (JUnit Platform Console, không Maven/Surefire)

```powershell
java -jar junit-platform-console-standalone-1.14.4.jar execute -cp "<main-out>;<test-out>;postgresql-42.7.14.jar" --scan-classpath <test-out> --disable-banner
```

Kết quả: **124/124 test thành công** (39 test DAO gốc + 78 test mở rộng + 7 test tìm kiếm tiếng Việt), 0 thất bại.

### 3.3 Kiểm tra thủ công PK/FK/quan hệ (48 probe)

Bộ kiểm tra thủ công tại `src/test/resources/db/manual-relation-test.sql` chạy trong một transaction và **rollback** sau khi xong. Nó bao gồm:

- 10 probe khóa chính (trùng ID, null thành phần)
- 5 probe ràng buộc duy nhất (ISBN, mã bản sao, mã thành viên, email không phân biệt hoa thường, tên thể loại)
- 7 probe khóa ngoại khi INSERT, 2 probe khi UPDATE
- 6 probe `ON DELETE RESTRICT`, 2 probe `ON DELETE CASCADE`
- 4 probe chỉ một phiếu mượn đang hoạt động cho mỗi bản sao
- 11 probe `CHECK` (ISBN độ dài, năm xuất bản, trạng thái, hạn trả, ngày trả, email trống)
- 1 probe không có dòng mồ khoá sau tất cả

```powershell
psql -h 127.0.0.1 -p 5432 -U library_app -d library_test -v ON_ERROR_STOP=1 -f src/test/resources/db/manual-relation-test.sql
```

Kết quả: **48/48 PASS**, `PSQL_EXIT=0`; `library_test` quay lại trạng thái trước kiểm tra (không còn dòng `MANUAL-*`, không còn bảng tạm).

### 3.4 Chạy toàn bộ kiểm thử (script tiện ích)

Trong repo có sẵn `scripts/run-tests.sh` (Linux/macOS) và `scripts/run-tests.ps1` (Windows) để chạy liên tiếp:
1. Tải JUnit Platform Console `1.14.4` và pgJDBC `42.7.14` vào `lib/` (chỉ lần đầu; `lib/` đã nằm trong `.gitignore`).
2. Biên dịch main + test với `javac --release 17 -encoding UTF-8`.
3. Chạy JUnit Platform Console (`--scan-classpath build/test`).
4. Script không chạy manual SQL probe; chạy riêng lệnh `psql` ở mục 3.3.

Bộ lọc `-TestClassFilter` / tham số đầu tiên nhận glob, ví dụ `*TransactionIsolation*`; script tự chuyển sang regex cho JUnit. Script tự quét classpath, không cần khai báo từng class test.

### 3.5 Tự động bật/tắt PostgreSQL khi chạy dự án

Dùng `scripts/with-postgres.ps1` để PostgreSQL chỉ sống trong thời gian lệnh chạy:

```powershell
# Hiện tại: chạy test với database tự bật/tắt
.\scripts\with-postgres.ps1 { .\scripts\run-tests.ps1 }

# Sau khi Person 2 hoàn tất Tomcat deployment
.\scripts\with-postgres.ps1 { & "$env:CATALINA_HOME\bin\catalina.bat" run }
```

Cách hoạt động:
1. Nếu PostgreSQL đã chạy sẵn, script dùng lại và **không tắt** khi lệnh kết thúc.
2. Nếu chưa chạy, script bật `postgres.exe` nền, chờ `pg_isready`, rồi chạy lệnh.
3. Khi lệnh kết thúc (kể cả lỗi), script gọi `pg_ctl stop -m fast` nếu chính nó đã bật database.
4. Log bật database nằm ở `build/logs/postgres.out.log` và `build/logs/postgres.err.log`.

Mặc định script dùng `%USERPROFILE%\dev\pgsql-16.15\bin` và `%USERPROFILE%\dev\pgdata-library`; có thể ghi đè bằng biến môi trường `PG_BIN` và `PGDATA`.

Lưu ý: phải giữ cửa sổ terminal trong lúc chạy. Nếu đóng terminal bằng nút X, PowerShell có thể không kịp chạy khối `finally`; lần chạy sau script sẽ nhận ra database đang chạy và dùng lại.

### 3.6 Mã dùng chung sau refactor

Các phần lặp lại đã được gom về một nơi, hành vi DAO/UI không đổi:

| Thành phần | Vai trò |
|---|---|
| `dao/jdbc/JdbcSupport.java` | Gắn tham số, escape LIKE, query một/nhiều dòng, đếm, insert lấy khóa tự sinh, update/delete và chạy cặp count + data cho phân trang |
| `dao/jdbc/AbstractNamedEntityDAO.java` | CRUD/tìm kiếm/kiểm tra trùng tên dùng chung cho Author, Category, Publisher; mỗi DAO con chỉ khai báo metadata và map dòng |
| `model/criteria/PageCriteria.java` | Trạng thái phân trang dùng chung (`page`, `size`) và giới hạn theo `AppConfig`; ba criteria kế thừa thay vì lặp field/getter/setter |
| `test/dao/support/TestFixtures.java` | Tạo dữ liệu test chuẩn (sách, bản sao, thành viên, phiếu mượn) cho toàn bộ suite |
| `WEB-INF/tags/pagination.tag` | Điều khiển Previous/Next dùng chung cho 4 màn hình danh sách, giữ nguyên query parameter hiện tại trừ `page` |

Ngoài ra, `JdbcDashboardDAO` lấy số liệu mượn đang hoạt động/quá hạn từ `JdbcLoanDAO` để predicate chỉ tồn tại một chỗ; các hàm JavaScript không còn được gọi (`showFieldError`, `clearFieldErrors`, `initTogglePassword`) đã được xóa khỏi `app.js`. Kiểm tra SHA-256 xác nhận không có file nào trùng byte trong repo chính. Thư mục `Library-Management-System-tests` bên ngoài repo là bản bàn giao riêng, không bị sửa.

## 4. Giao diện (Person 1)

15 JSP sử dụng JSTL 3.0.1 (`jakarta.tags.*`) + EL, CSS và JavaScript thuần (`app.js`). Không có Java GUI (Swing/AWT/JavaFX). Pagination được tách thành tag dùng chung `WEB-INF/tags/pagination.tag`.

Màn hình: dashboard, danh sách/tìm sách, chi tiết sách, thêm/sửa sách, xóa sách, danh sách/thêm/sửa thành viên, mượn/trả, lịch sử mượn, sách quá hạn, trang lỗi 404/500.

Tất cả JSP và tag dùng chung đã biên dịch bằng Tomcat `JspC` (16 file Java sinh ra từ 15 JSP + 1 tag, 0 lỗi) và qua kiểm tra tham chiếu thuộc tính EL (320 biểu thức, 214 thuộc tính mô hình).

## 5. Phạm vi hoàn thành và phân công

| Thành phần | Person 1 | Person 2 |
|---|---|---|
| PostgreSQL schema, constraints, indexes, seed | ✅ Hoàn thành | — |
| DAO interfaces + JDBC implementations | ✅ Hoàn thành | — |
| DAO tests (JUnit standalone) | ✅ Hoàn thành | — |
| JSP / CSS / JS | ✅ Hoàn thành | — |
| Tài liệu database, thiết kế, audit runtime | ✅ Hoàn thành | — |
| `pom.xml`, Maven Wrapper | ❌ Chưa có | Person 2 |
| `web.xml`, `context.xml` | ❌ Chưa có | Person 2 |
| Service, Controller/Servlet, Filter | ❌ Chưa có | Person 2 |
| WAR, Tomcat deployment, HTTP flow | ❌ Chưa có | Person 2 |
| Surefire / `mvn test` | ❌ Chưa có | Person 2 |

## 6. Known gaps (chưa làm, chờ Person 2 hoặc nhóm quyết định)

- Chưa có `pom.xml` / Maven Wrapper, nên chưa có `mvn test`, `mvn package`, WAR.
- Chưa có `web.xml`, `context.xml`, Controller, Service, Filter, transaction orchestration.
- Chưa triển khai HTTP / Tomcat deployment, chưa kiểm tra luồng end-to-end qua trình duyệt.
- Các trigger khóa bản sao / chặn thành viên `INACTIVE` mượn sách (E01/E02) **chưa thêm vào database chính** — cần thống nhất cơ chế khóa và thứ tự transaction với Person 2 trước khi áp dụng.
- Unique cho tên tác giả / nhà xuất bản (E09) cần nhóm chốt quy tắc trùng tên trước khi đặt constraint.
- Quy tắc ngày mượn/trả không được ở tương lai (E11), kiểu `updated_at`/`TIMESTAMPTZ` (E13/E14), soft-delete (E21) là quyết định nghiệp vụ, chưa áp dụng.

## 7. Tài liệu liên quan

- `docs/database-design.md` — ERD, bảng, ràng buộc, invariant, indexes, seed, SQLState mapping.
- `docs/runtime-toolchain-audit.md` — môi trường, công cụ, bằng chứng kiểm chứng độc lập.
- `docs/java-concepts-mapping.md` — ánh xạ khái niệm Java với mã nguồn (`file:dòng`) và chỉ dẫn trả lời khi dạy hỏi.
- `docs/implementation-plan.md` — hợp đồng DAO, quy tắc transaction, kế hoạch UI/UX, Definition of Done.
- `docs/person1-implementation-plan.md` — kế hoạch chi tiết Person 1 (gốc).
- `docs/test-suite-report.md` — báo cáo bộ test mở rộng (logic nghiệp vụ, data consistency, cross-table, transaction) và kết quả chạy thực tế.
