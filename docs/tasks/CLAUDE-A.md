# Hướng dẫn thực thi cho Claude A (Luồng A) — SmartClick

Chào Claude A. Bạn phụ trách **Luồng A**, tập trung vào **Nền tảng, Quản lý thiết bị, Google Play Billing & Cấu hình Release**.

---

## 1. THÔNG TIN NHÁNH & WORKTREE
- **Tên nhánh git:** `feature/stream-a`
- **Tên thư mục worktree:** `D:\SmartClick-worktree-A` (hoặc làm việc trên nhánh hiện tại tùy setup hướng dẫn trong SETUP.md).
- **Phạm vi file được phép sửa:**
  - `.github/workflows/android-ci.yml`, `.gitattributes`
  - `app/src/main/res/values-vi/strings.xml`, `app/src/main/res/values/strings.xml` (chỉ thêm ở cuối file)
  - `app/src/main/java/com/example/smartclick/app/features/onboarding/DeviceFoundScreen.kt`
  - `app/src/main/java/com/example/smartclick/app/features/devices/**/*.kt`
  - `app/src/main/java/com/example/smartclick/app/domain/usecase/RemoveDeviceUseCase.kt`, `SwitchActiveDeviceUseCase.kt`, `LaunchAppOnTvUseCase.kt`
  - `app/src/main/java/com/example/smartclick/app/domain/connection/ReconnectWatcher.kt`
  - `app/src/main/java/com/example/smartclick/app/data/repository/PlayBillingRepository.kt`
  - `app/src/main/java/com/example/smartclick/app/features/settings/UpdateToPremiumScreen.kt`, `PremiumViewModel.kt`
  - `app/build.gradle.kts`
- **KHÔNG ĐƯỢC SỬA:** Các file của Luồng B (RemoteScreen, DPadModeContent, CastScreen, ChannelsViewModel, KeyboardModal, v.v.).

---

## 2. DANH SÁCH VIỆC THEO THỨ TỰ (TỪ ÍT RỦI RO ĐẾN CAO)

### Việc A1: Dọn repo & Sửa lỗi build B17 (`F-BASE-04`, `F-BASE-07`)
- **Mô tả:** Bổ sung chuỗi `remote_is_locked` vào `app/src/main/res/values-vi/strings.xml` và chuyển 3 chuỗi cứng trong `DeviceFoundScreen.kt` vào tài nguyên (`strings.xml`). Dọn dẹp gitattributes / file patch thừa nếu cần.
- **Tiêu chí hoàn thành:** Build không lỗi thiếu chuỗi, `values-vi` đầy đủ.

### Việc A2: Hoàn thiện màn quản lý thiết bị & Chuyển/Xoá thiết bị (`F-DEV-01`, `F-DEV-02`)
- **Mô tả:** Nối `RemoveDeviceUseCase` và `SwitchActiveDeviceUseCase` vào `DevicesViewModel` và `DeviceListScreen.kt` để người dùng có thể xoá hoặc chuyển đổi thiết bị active trực tiếp.
- **Tiêu chí hoàn thành:** Chức năng xoá và chuyển thiết bị hoạt động qua UI ViewModel.

### Việc A3: Tinh chỉnh ReconnectWatcher & Lưu địa chỉ MAC cho WoL (`F-DEV-03`, `F-DEV-05`)
- **Mô tả:** Tinh chỉnh `ReconnectWatcher.kt` để tránh vòng lặp kết nối liên tục khi TV tắt có chủ ý. Bổ sung gán `macAddress` vào `DeviceFingerprintEngine` và các driver liên quan.
- **Tiêu chí hoàn thành:** Watcher hoạt động ổn định, không spam kết nối khi TV offline.

### Việc A4: Mở rộng Google Play Billing & FeatureGate (`F-PRE-01..06`)
- **Mô tả:** Thêm gói `lifetime` (`INAPP`) vào `PlayBillingRepository.kt`. Cập nhật 4 quyền lợi mới trên `UpdateToPremiumScreen.kt`. Dọn dẹp thông báo chuỗi cứng sang `strings.xml`. Gỡ bỏ kiểm tra Premium thừa trong `LaunchAppOnTvUseCase.kt`.
- **Tiêu chí hoàn thành:** Billing hỗ trợ SUBS lẫn INAPP lifetime, usecase không còn chặn nhầm.

### Việc A5: Cấu hình Phát hành Release & R8 (`F-REL-01`)
- **Mô tả:** Cấu hình `buildTypes` release trong `app/build.gradle.kts` (bật minifyEnabled/R8, cấu hình cờ ProGuard, chuẩn bị sẵn khối signingConfigs).
- **Tiêu chí hoàn thành:** `./gradlew assembleRelease` chạy thành công (hoặc cấu hình build release sẵn sàng).

---

## 3. QUY TRÌNH LÀM VIỆC & KIỂM TRA
1. Đọc code thật trước khi sửa bất kỳ file nào.
2. Commit sau mỗi việc nhỏ với thông điệp rõ ràng, kết thúc bằng `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
3. **Trước khi báo xong:** Chạy `./gradlew.bat :app:testDebugUnitTest` và ghi kết quả vào `docs/tasks/RESULT-A.md`.
4. Không tự ý sửa `missing-features-report.md`.
