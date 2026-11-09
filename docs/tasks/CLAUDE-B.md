# Hướng dẫn thực thi cho Claude B (Luồng B) — SmartClick

Chào Claude B. Bạn phụ trách **Luồng B**, tập trung vào **Remote UI nâng cao, Bàn phím gõ tiếng Việt, Channels và Media Casting UI**.

---

## 1. THÔNG TIN NHÁNH & WORKTREE
- **Tên nhánh git:** `feature/stream-b`
- **Tên thư mục worktree:** `D:\SmartClick-worktree-B` (hoặc làm việc trên nhánh hiện tại tùy setup hướng dẫn trong SETUP.md).
- **Phạm vi file được phép sửa:**
  - `app/src/main/java/com/example/smartclick/app/features/remote/**/*.kt` (`RemoteScreen.kt`, `RemotePowerButton.kt`, `RemoteSecondaryActions.kt`, `DPadModeContent.kt`, `KeyboardModal.kt`, `KeyboardTextDraft.kt`, v.v.)
  - `app/src/main/java/com/example/smartclick/app/features/channels/**/*.kt` (`ChannelsViewModel.kt`, `ChannelsScreen.kt`)
  - `app/src/main/assets/channels.json` (tạo mới)
  - `app/src/main/java/com/example/smartclick/app/features/cast/**/*.kt` (`CastViewModel.kt`, `CastScreen.kt`, `CastErrorPresentation.kt`)
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml` (chỉ thêm ở cuối file)
- **KHÔNG ĐƯỢC SỬA:** Các file của Luồng A (UpdateToPremiumScreen, PlayBillingRepository, DeviceListScreen, build.gradle.kts, v.v.).

---

## 2. DANH SÁCH VIỆC THEO THỨ TỰ (TỪ ÍT RỦI RO ĐẾN CAO)

### Việc B1: Sửa lỗi nút nguồn & Lớp phủ RemoteScreen (`F-REM-03`)
- **Mô tả:** Gỡ bỏ lớp phủ `fillMaxSize` che nút cam trong `RemoteScreen.kt`. Thêm trì hoãn chạm đơn 250ms cho `RemotePowerButton.kt` và tách biệt logic Wake-on-LAN / Power toggle.
- **Tiêu chí hoàn thành:** Nút nguồn phản hồi nhạy, không bị lớp phủ vô hình chặn touch.

### Việc B2: Nút Mute 3 trạng thái & Nút Khoá R8 (`F-REM-04`, `F-REM-06`)
- **Mô tả:** Triển khai lưu trữ trạng thái Mute theo thiết bị trong `RemoteSecondaryActions.kt` / `RemoteViewModel.kt`. Bổ sung bản dịch tiếng Việt cho Toast nút Khoá và cố định thời gian giữ mở khoá 1 giây.
- **Tiêu chí hoàn thành:** Trạng thái Mute và Khoá hoạt động đúng thiết kế.

### Việc B3: Thay khối 8 nút giả bằng hàng 3 nút phụ mới (`F-REM-07`, `F-REM-12`)
- **Mô tả:** Xóa bỏ khối 8 nút phụ cũ (và toast "sắp ra mắt") trong `DPadModeContent.kt`, thay bằng hàng 3 nút phụ mới: Bàn phím, Mic nhập chữ, Headphone.
- **Tiêu chí hoàn thành:** UI remote hiển thị đúng hàng 3 nút mới.

### Việc B4: Sửa bàn phím chữ (`KeyboardModal`) cho tiếng Việt (`F-REM-10`)
- **Mô tả:** Chỉnh sửa `KeyboardModal.kt` và `KeyboardTextDraft.kt` từ chế độ gửi từng ký tự ngay lập tức sang cơ chế soạn thảo toàn bộ chuỗi rồi bấm nút "Gửi" (Submit) để không làm hỏng bộ gõ Telex/VNI.
- **Tiêu chí hoàn thành:** Gõ tiếng Việt có dấu ổn định, chỉ gửi lệnh khi bấm Gửi.

### Việc B5: Hoàn thiện Channels thực tế (`F-CHN-01..03`)
- **Mô tả:** Tạo file `app/src/main/assets/channels.json` với danh sách ứng dụng mẫu. Hoàn thiện `ChannelsViewModel.kt` (xóa TODO P8) và nối mở app qua TV controller.
- **Tiêu chí hoàn thành:** Màn Channels tải danh sách từ assets json thành công.

### Việc B6: Nối Backend Cast vào UI ứng dụng (`F-CST-01..06`)
- **Mô tả:** Nối `ICastController` từ module `:tv:runtime` vào `CastViewModel.kt` và `CastScreen.kt`. Áp dụng `FeatureGate` kiểm tra Premium cho tính năng Cast.
- **Tiêu chí hoàn thành:** `CastViewModel` gọi đúng controller backend, màn hình Cast hoạt động thực tế.

---

## 3. QUY TRÌNH LÀM VIỆC & KIỂM TRA
1. Đọc code thật trước khi sửa bất kỳ file nào.
2. Commit sau mỗi việc nhỏ với thông điệp rõ ràng, kết thúc bằng `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
3. **Trước khi báo xong:** Chạy `./gradlew.bat :app:testDebugUnitTest` và ghi kết quả vào `docs/tasks/RESULT-B.md`.
4. Không tự ý sửa `missing-features-report.md`.
