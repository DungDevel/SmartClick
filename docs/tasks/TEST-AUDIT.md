# TV Runtime Modularization Test Audit & Restoration Report (TEST-AUDIT)

## 1. Tổng quan khôi phục test
Tại commit gốc `a21112f`, module `:tv:runtime` chứa tổng cộng **52 file** (51 file `.kt` và 1 file tài nguyên `test_server.pem`). 
- **Phân loại 51 file `.kt`**: 
  - **47 file test** chứa các annotation kiểm thử (`@Test`, `@ParameterizedTest`, `@Ignore`).
  - **4 file helper / transport phụ trợ không chứa `@Test`**:
    1. `FakeDriver.kt` (`tv/testing/src/main/.../fake/`)
    2. `MockAndroidTvServer.kt` (`tv/androidtv/src/test/.../androidtv/`)
    3. `FakeLgWebOsCastTransport.kt` (`tv/lg/src/test/.../lg/cast/`)
    4. `FakeSamsungDlnaTransport.kt` (`tv/samsung/src/test/.../samsung/dlna/`)
- **Tài nguyên**: 1 file `test_server.pem` đặt tại `tv/core/src/test/resources/`.

## 2. Thống kê `@Test` và sự chênh lệch (275 gốc vs 294 chạy thực tế)
- **Bản gốc (`a21112f`)**: Đếm tĩnh được **275** test annotations trong mã nguồn.
- **Thực thi thực tế tại các module `tv/*` và `:tv:testing`**: Đạt **294** test cases được ghi nhận qua XML report.
- **Nguyên nhân chênh lệch (+19 test cases)**: Do các test sử dụng `@ParameterizedTest` (như trong `UpnpSoapModelsTest` và các test framing/protocol) tự động sinh và mở rộng thành nhiều execution instance ứng với từng bộ tham số dữ liệu đầu vào khi chạy JUnit.

## 3. Khai báo Interface `ITvController` & Rationale cho `getApps`/`launchApp`
- Trong `core/tv-api/.../ITvController.kt`, hai hàm `getApps()` và `launchApp(appId)` được khai báo là **abstract functions** (không có default implementation).
- **Rationale**: Mọi class cài đặt `ITvController` (kể cả mock/fake controller trong `FakeDriver.kt`) bắt buộc phải override tường minh hai phương thức này để tuân thủ strict contract kiểm thử capability `INSTALLED_APPS` qua `AbstractDriverContractTest`.

## 4. Bảng kết quả chạy test từng module (Xác thực qua XML report)
| Module | Số test chạy (XML) | Passed | Failed | Skipped | Trạng thái |
|---|---|---|---|---|---|
| `:tv:testing` | 4 | 4 | 0 | 0 | PASSED |
| `:tv:core` | 58 | 58 | 0 | 0 | PASSED |
| `:tv:androidtv` | 27 | 27 | 0 | 0 | PASSED |
| `:tv:cast` | 71 | 71 | 0 | 0 | PASSED |
| `:tv:dial` | 11 | 11 | 0 | 0 | PASSED |
| `:tv:firetv` | 5 | 5 | 0 | 0 | PASSED |
| `:tv:lg` | 57 | 57 | 0 | 0 | PASSED |
| `:tv:roku` | 18 | 18 | 0 | 0 | PASSED |
| `:tv:samsung` | 22 | 22 | 0 | 0 | PASSED |
| `:tv:sony` | 14 | 14 | 0 | 0 | PASSED |
| `:tv:vidaa` | 2 | 2 | 0 | 0 | PASSED |
| `:tv:vizio` | 5 | 5 | 0 | 0 | PASSED |
| **Tổng cộng (`tv/*`)** | **294** | **294** | **0** | **0** | **PASSED** |
| `:app` | **212** | **212** | **0** | **0** | **PASSED** |

## 5. Đề xuất bước tiếp theo (Section e)
1. **Trước khi merge nhánh `feature/stream-b`**: Chạy toàn bộ test suite (`./gradlew testDebugUnitTest :app:testDebugUnitTest --max-workers=2`) tại root project để đảm bảo không có regression đối với các protocol driver trong 12 module `tv/*`.
2. **Quản lý file cấu hình**: Giữ cấu hình Gradle user home (`org.gradle.java.installations.auto-download=false`) tại `.gradle-home/gradle.properties` (bên ngoài tracked files của git) để duy trì tính ổn định của môi trường build sandbox.
