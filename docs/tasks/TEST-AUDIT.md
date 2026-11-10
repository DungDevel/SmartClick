# TV Runtime Modularization Test Audit & Restoration Report (TEST-AUDIT)

## 1. Tổng quan khôi phục test
Tại commit gốc `a21112f`, module `:tv:runtime` chứa tổng cộng **52 file** (51 file `.kt` + 1 tài nguyên `test_server.pem`). Khi modular hóa thành các module độc lập theo hãng (`tv/androidtv`, `tv/cast`, `tv/core`, `tv/dial`, `tv/firetv`, `tv/lg`, `tv/roku`, `tv/samsung`, `tv/sony`, `tv/vidaa`, `tv/vizio`) và `:tv:testing`, toàn bộ file test đã được phân bổ thành 49 file test + các helper class cốt lõi.

## 2. Bảng đối chiếu file cũ (`tv/runtime/src/test`) sang module mới (`tv/*/src/test`)
| STT | File gốc (`tv/runtime/src/test/...`) | Module & đường dẫn mới | Trạng thái |
|---|---|---|---|
| 1 | `domain/connection/contract/AbstractDriverContractTest.kt` | `tv/testing/src/main/.../contract/` | Present |
| 2 | `domain/connection/fake/FakeDriver.kt` | `tv/testing/src/main/.../fake/` | Present |
| 3 | `domain/connection/fake/FakeDriverTest.kt` | `tv/testing/src/test/.../fake/` | PASSED (4 tests) |
| 4-11 | `tvconnection/androidtv/*` (8 files) | `tv/androidtv/src/test/.../androidtv/` | PASSED (27 tests) |
| 12 | `tvconnection/androidtv/MockAndroidTvServer.kt` | `tv/androidtv/src/test/.../androidtv/` | Helper (no @Test) |
| 13 | `tvconnection/cast/LocalMediaServerTest.kt` | `tv/core/src/test/.../cast/` | PASSED (18 tests) |
| 14-21 | `tvconnection/cast/google/*` (8 files) | `tv/cast/src/test/.../google/` | PASSED (71 tests) |
| 22-24 | `tvconnection/dial/*` (3 files) | `tv/dial/src/test/.../dial/` | PASSED (11 tests) |
| 25-28 | `tvconnection/discovery/*` (4 files) | `tv/core/src/test/.../discovery/` | PASSED (19 tests) |
| 29-31 | `tvconnection/firetv/*` (3 files) | `tv/firetv/src/test/.../firetv/` | PASSED (5 tests) |
| 32-35 | `tvconnection/lg/*` (4 files) | `tv/lg/src/test/.../lg/` | PASSED (57 tests) |
| 36-40 | `tvconnection/samsung/*` (5 files) | `tv/samsung/src/test/.../samsung/` | PASSED (22 tests) |
| 41-42 | `tvconnection/security/*` (2 files) | `tv/core/src/test/.../security/` | PASSED (17 tests) |
| 43-45 | `tvconnection/sony/*` (3 files) | `tv/sony/src/test/.../sony/` | PASSED (14 tests) |
| 46 | `tvconnection/utils/NetworkSecurityUtilsTest.kt` | `tv/core/src/test/.../utils/` | PASSED (4 tests) |
| 47 | `tvconnection/vidaa/VidaaDriverTest.kt` | `tv/vidaa/src/test/.../vidaa/` | PASSED (2 tests) |
| 48-49 | `tvconnection/vizio/*` (2 files) | `tv/vizio/src/test/.../vizio/` | PASSED (5 tests) |
| 50-51 | `tvconnection/wifi/*` (2 files) | `tv/roku/src/test/.../wifi/` | PASSED (18 tests) |
| 52 | `resources/test_server.pem` | `tv/core/src/test/resources/test_server.pem` | Present |

## 3. Thống kê `@Test` (trước vs hiện tại)
- **Bản gốc (`a21112f`)**: 275 test annotations (`@Test`, `@ParameterizedTest`, `@Ignore`).
- **Hiện tại**: 271 test annotations trong source, thực thi qua Gradle XML test suites đạt tổng **294** test cases (cùng **212** test cases cho `:app`).

## 4. Bảng kết quả chạy test từng module (Xác thực qua XML report)
| Module | Số test chạy | Passed | Failed | Skipped | Trạng thái XML |
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
