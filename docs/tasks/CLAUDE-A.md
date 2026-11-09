# Hướng dẫn thực thi cho Claude A (Luồng A) — SmartClick

Chào Claude A. Bạn phụ trách **Luồng A**, tập trung vào **Nền tảng, Quản lý thiết bị (xóa/chuyển), Google Play Billing, FeatureGate & Cấu hình Release (R8)**.

---

## 1. THÔNG TIN NHÁNH & WORKTREE
- **Tên nhánh git:** `feature/stream-a`
- **Tên thư mục worktree:** `D:\SmartClick-worktree-A`
- **Phạm vi file được phép sửa:**
  - `.github/workflows/android-ci.yml`, `.gitattributes`
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`, `app/src/main/res/values-de/strings.xml`, `app/src/main/res/values-es/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml` (Mọi khoá chuỗi mới bắt buộc phải thêm vào **đủ cả 5 file ở CUỐI file** trước `</resources>` để qua `LocaleResourcesConsistencyTest`).
  - `app/src/main/java/com/example/smartclick/app/domain/usecase/RemoveDeviceUseCase.kt`, `SwitchActiveDeviceUseCase.kt`
  - `app/src/main/java/com/example/smartclick/app/features/devices/**/*.kt` (Lưu ý: `F-DEV-01` về màn danh sách thiết bị đã có người khác làm ở thư mục chính, A chỉ tập trung `F-DEV-02` chuyển và xóa thiết bị).
  - `app/src/main/java/com/example/smartclick/app/domain/connection/ReconnectWatcher.kt`
  - `app/src/main/java/com/example/smartclick/app/domain/policy/FeatureGate.kt` (cho `F-PRE-06`)
  - `app/src/main/java/com/example/smartclick/app/data/repository/PlayBillingRepository.kt`
  - `app/src/main/java/com/example/smartclick/app/features/settings/UpdateToPremiumScreen.kt`, `PremiumViewModel.kt`
  - `tv/core/**`, `tv/roku/**`, `tv/lg/**`, `tv/samsung/**` (cho `F-DEV-05` lưu địa chỉ MAC)
  - `app/build.gradle.kts`, `app/proguard-rules.pro`, và các file `consumer-rules.pro` của `tv/*`
- **Lưu ý đặc biệt:** `F-DEV-01` (Màn danh sách thiết bị riêng) đã được một Claude Code khác làm riêng trong thư mục chính, không sửa phần đó, chỉ phối hợp `F-DEV-02` (chuyển và xóa thiết bị) và các file liên quan.
- **KHÔNG ĐƯỢC SỬA:** Các file của Luồng B (RemoteScreen, DPadModeContent, CastScreen, ChannelsViewModel, LaunchAppOnTvUseCase, KeyboardModal, v.v.).

---

## 2. DANH SÁCH VIỆC THEO THỨ TỰ (TỪ ÍT RỦI RO ĐẾN CAO)

### Việc A1: Dọn repo & Kiểm tra lỗi build B17 (`F-BASE-04`, `F-BASE-07`)
- **Mô tả:** Kiểm tra file `.gitattributes` và CI. Lưu ý: `F-BASE-07` đã được hoàn thành sẵn trong working tree hiện tại, xác nhận và bỏ qua việc sửa lại code phần này.
- **Tiêu chí hoàn thành:** Repo sạch, CI cấu hình chuẩn.

### Việc A2: Chuyển / Xoá thiết bị (`F-DEV-02`)
- **Mô tả:** Kiểm tra `DeviceListScreen` và `DevicesViewModel` trước, chỉ làm phần chưa có của `F-DEV-02` (gắn `RemoveDeviceUseCase` và `SwitchActiveDeviceUseCase`).
- **Tiêu chí hoàn thành:** Chức năng xoá và chuyển thiết bị hoạt động qua ViewModel.

### Việc A3: Tinh chỉnh ReconnectWatcher & Lưu địa chỉ MAC cho WoL (`F-DEV-03`, `F-DEV-05`)
- **Mô tả:** Tinh chỉnh `ReconnectWatcher.kt` để tránh vòng lặp kết nối liên tục khi TV tắt có chủ ý. Bổ sung gán `macAddress` vào `DeviceFingerprintEngine` và các driver trong `tv/core/`, `tv/roku/`, `tv/lg/`, `tv/samsung/`.
- **Tiêu chí hoàn thành:** Watcher hoạt động ổn định, lưu trữ MAC address thành công.

### Việc A4: Mở rộng Google Play Billing & FeatureGate (`F-PRE-01..06`)
- **Mô tả:** Billing đã bị sửa một phần từ trước, hãy xem `git log -p` các file Billing để nắm hiện trạng, chỉ làm phần còn thiếu (bổ sung gói `lifetime` INAPP, cập nhật 4 quyền lợi mới trên `UpdateToPremiumScreen.kt`, dọn chuỗi thông báo Billing vào 5 file strings). Cập nhật `FeatureGate.kt` (`F-PRE-06`).
- **Tiêu chí hoàn thành:** Billing hỗ trợ SUBS lẫn INAPP lifetime, FeatureGate đúng đắn.

### Việc A5: Cấu hình Phát hành Release, R8 & ProGuard (`F-REL-01`)
- **Mô tả:** Cấu hình `buildTypes` release trong `app/build.gradle.kts` (bật minifyEnabled/R8). **Bắt buộc thêm các quy tắc giữ lại (keep rules)** cho Hilt, Protobuf, OkHttp trong `app/proguard-rules.pro` và các `consumer-rules.pro` của `tv/*` trước khi bật R8 để tránh lỗi runtime reflection.
- **Tiêu chí hoàn thành:** `./gradlew assembleRelease` hoặc cấu hình ProGuard hoàn thiện, không mất class do obfuscation.

---

## 3. QUY TRÌNH LÀM VIỆC & KIỂM TRA
1. Đọc code thật trước khi sửa bất kỳ file nào.
2. Commit sau mỗi việc nhỏ với thông điệp rõ ràng, kết thúc bằng `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
3. **Trước khi báo xong:** Chạy `./gradlew.bat :app:testDebugUnitTest` và ghi kết quả vào `docs/tasks/RESULT-A.md`.
4. Không tự ý sửa `missing-features-report.md`.
