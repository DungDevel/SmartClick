# TV Runtime Modularization Test Audit & Restoration Report (TEST-AUDIT)

## 1. Tổng quan khôi phục test
Tại commit gốc `a21112f`, module `:tv:runtime` chứa tổng cộng **52 file** (51 file `.kt` và 1 file tài nguyên `test_server.pem`). 
- **Phân loại 51 file `.kt`**: 
  - **47 file test** chứa các annotation kiểm thử (`@Test`).
  - **4 file helper / transport phụ trợ không chứa `@Test`**:
    1. `FakeDriver.kt` (`tv/testing/src/main/.../fake/`)
    2. `MockAndroidTvServer.kt` (`tv/androidtv/src/test/.../androidtv/`)
    3. `FakeLgWebOsCastTransport.kt` (`tv/lg/src/test/.../lg/cast/`)
    4. `FakeSamsungDlnaTransport.kt` (`tv/samsung/src/test/.../samsung/dlna/`)
- **Tài nguyên**: 1 file `test_server.pem` đặt tại `tv/core/src/test/resources/`.

## 2. Thống kê `@Test` và sự chênh lệch (275 gốc vs 294 chạy thực tế)
- **Bản gốc (`a21112f`)**: Đếm tĩnh được **275** test annotations trong mã nguồn.
- **Thực thi thực tế tại các module `tv/*` và `:tv:testing`**: Đạt **294** test cases được ghi nhận qua XML report (+19 test cases).
- **Nguyên nhân chênh lệch (+24 - 4 - 1 = +19)**:
  - Có **6 lớp driver test** kế thừa từ `AbstractDriverContractTest`: `AndroidTvDriverTest`, `DialDriverTest`, `LgWebOsDriverTest`, `RokuDriverTest`, `SamsungTizenDriverTest`, `FakeDriverTest`.
  - Mỗi lớp kế thừa +4 test contract (`driver protocol must match device protocol`, `controller creation returns valid instance`, `controller app methods respect declared capability`, `fingerprint returns null for completely irrelevant protocol hints`).
  - Trừ đi 4 phương thức abstract định nghĩa trong lớp cha `AbstractDriverContractTest` (không sinh XML test suite riêng) = +20.
  - Trừ đi 1 test trong `GoogleCastProtocolClientTest` (có 13 `@Test` tĩnh nhưng chỉ 12 chạy vì test `heartbeat timeout triggers disconnect` bị comment code — lưu ý dòng comment này đã có sẵn từ commit gốc `a21112f`) = +19.

### Bảng đối chiếu Contract Test Inheritance (6 lớp)
| Lớp Test | Kế thừa AbstractDriverContractTest | Số test tĩnh (Local) | Số test chạy (XML) |
|---|---|---|---|
| `AndroidTvDriverTest` | Có | 6 | 10 |
| `DialDriverTest` | Có | 2 | 6 |
| `LgWebOsDriverTest` | Có | 16 | 20 |
| `RokuDriverTest` | Có | 3 | 7 |
| `SamsungTizenDriverTest` | Có | 5 | 9 |
| `FakeDriverTest` | Có | 0 | 4 |

## 3. Khai báo Interface `ITvController` & Rationale cho `getApps`/`launchApp`
- Trong `core/tv-api/.../ITvController.kt`, hai hàm `getApps()` và `launchApp(appId)` được khai báo là **abstract functions** (không có default implementation trong interface).
- **Rationale**: Mọi class cài đặt `ITvController` (kể cả mock/fake controller trong `FakeDriver.kt`) bắt buộc phải override tường minh hai phương thức này để tuân thủ strict contract kiểm thử capability `INSTALLED_APPS` qua `AbstractDriverContractTest`.

## 4. Phân tích chênh lệch `@Test` tại module `:app` (217 tĩnh vs 212 chạy)
- Trước đó, `git grep "@Test"` đếm được 217 do tồn tại tệp sao lưu `.orig` (`RemoteViewModelTest.kt.orig`) không được tracking. Sau khi xóa tệp `.orig`, số lượng `@Test` tĩnh khớp hoàn toàn **212**, khớp tuyệt đối với **212** test cases thực thi trong XML test reports.

## 5. Bảng kết quả chạy test từng module (Xác thực qua XML report)
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

## 6. Trạng thái Git & Bước tiếp theo (Section e)
- **Git status cuối**: `nothing to commit, working tree clean`
- **Commit HEAD cuối**: `38b7226e1a4cadc749f74c0650da3ec32516ffde`
- **Bước tiếp theo**: Chuẩn bị sẵn sàng thực hiện hợp nhất nhánh qua `git merge --squash`.
