# Hướng dẫn thực thi cho Claude B (Luồng B) — SmartClick

Chào Claude B. Bạn phụ trách **Luồng B**, tập trung vào **Remote UI nâng cao, Bàn phím gõ tiếng Việt, Channels (kèm LaunchAppOnTvUseCase) và Media Casting UI**.

---

## 1. THÔNG TIN NHÁNH & WORKTREE
- **Tên nhánh git:** `feature/stream-b`
- **Tên thư mục worktree:** `D:\SmartClick-worktree-B`
- **Phạm vi file được phép sửa:**
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`, `app/src/main/res/values-de/strings.xml`, `app/src/main/res/values-es/strings.xml`, `app/src/main/res/values-pt-rBR/strings.xml` (Mọi khoá chuỗi mới bắt buộc phải thêm vào **đủ cả 5 file ở CUỐI file** trước `</resources>` để qua `LocaleResourcesConsistencyTest`).
  - `app/src/main/java/com/example/smartclick/app/features/remote/**/*.kt` (`RemoteScreen.kt`, `RemotePowerButton.kt`, `RemoteSecondaryActions.kt`, `DPadModeContent.kt`, `KeyboardModal.kt`, `KeyboardTextDraft.kt`, v.v.)
  - `app/src/main/java/com/example/smartclick/app/features/channels/**/*.kt` (`ChannelsViewModel.kt`, `ChannelsScreen.kt`)
  - `app/src/main/java/com/example/smartclick/app/domain/usecase/LaunchAppOnTvUseCase.kt` (gỡ bỏ kiểm tra Premium thừa cho channels)
  - `app/src/main/assets/channels.json` (tạo mới)
  - `app/src/main/java/com/example/smartclick/app/features/cast/**/*.kt` (`CastViewModel.kt`, `CastScreen.kt`, `CastErrorPresentation.kt`)
- **QUY ĐỊNH NGHIÊM NGẶT:** **B không được sửa bất kỳ file nào trong thư mục `tv/`** (mọi chỉnh sửa driver thuộc về Luồng A; B chỉ gọi các API/controllers có sẵn từ `:tv:cast` và `:tv:core` trong module `:app`).
- **KHÔNG ĐƯỢC SỬA:** Các file của Luồng A (UpdateToPremiumScreen, PlayBillingRepository, DeviceListScreen, thư mục `tv/`, build.gradle.kts, v.v.).

---

## 1.1. GHI CHÚ QUYẾT ĐỊNH (DESIGN DECISIONS)
- **Nút Headphone (`F-REM-14`):** Giữ nút trên UI, khi người dùng bấm sẽ hiển thị thông báo Toast "Chưa hỗ trợ" (not supported).
- **Mic nhập chữ (`F-REM-13`):** Hoàn toàn miễn phí cho mọi người dùng, giống như tính năng Keyboard.
- **Xác thực Google Cast (I34):** Giữ nguyên cơ chế hiện tại, không thay đổi trong đợt này.

---

## 2. DANH SÁCH VIỆC THEO THỨ TỰ (TỪ ÍT RỦI RO ĐẾN CAO)

### Việc B1: Sửa lỗi nút nguồn & Lớp phủ RemoteScreen (`F-REM-03`)
- **Lưu ý:** Màn Remote đã được sửa nhiều ở các commit trước, hãy đọc code hiện tại kỹ trước khi làm, chỉ bổ sung phần chưa có (gỡ lớp phủ `fillMaxSize` che nút cam trong `RemoteScreen.kt`, thêm trì hoãn chạm đơn 250ms cho `RemotePowerButton.kt`, tách biệt logic WoL/Power).
- **Tiêu chí hoàn thành:** Nút nguồn phản hồi nhạy, không bị lớp phủ vô hình chặn touch.

### Việc B2: Nút Mute 3 trạng thái & Nút Khoá R8 (`F-REM-04`, `F-REM-06`)
- **Lưu ý:** Đọc code hiện tại của `RemoteSecondaryActions.kt` và `RemoteViewModel.kt`, chỉ triển khai phần còn thiếu (lưu trữ trạng thái Mute theo thiết bị, bổ sung đa ngôn ngữ vào 5 file strings cho Toast nút Khoá, cố định thời gian giữ mở khoá 1 giây).
- **Tiêu chí hoàn thành:** Trạng thái Mute và Khoá hoạt động đúng thiết kế.

### Việc B3: Thay khối 8 nút giả bằng hàng 3 nút phụ mới (`F-REM-07`, `F-REM-12`)
- **Lưu ý:** Hàng 3 nút Keyboard / Headphone / Mic đã có sẵn UI khung nổi 54dp cùng các drawables tương ứng trong `DPadModeContent.kt`. Hãy đọc code hiện tại, gỡ bỏ khối 8 nút phụ giả cũ (cùng các toast "sắp ra mắt") và kết nối logic điều khiển cho hàng 3 nút (Keyboard mở modal, Mic mở giọng nói miễn phí, Headphone bắn Toast "Chưa hỗ trợ").
- **Tiêu chí hoàn thành:** UI remote hiển thị đúng hàng 3 nút mới hoạt động thực tế.

### Việc B4: Sửa bàn phím chữ (`KeyboardModal`) cho tiếng Việt (`F-REM-10`)
- **Lưu ý:** Đọc code hiện tại của `KeyboardModal.kt` và `KeyboardTextDraft.kt`, chuyển từ chế độ gửi từng ký tự ngay lập tức sang cơ chế soạn thảo toàn bộ chuỗi rồi bấm nút "Gửi" (Submit) để không làm hỏng bộ gõ Telex/VNI.
- **Tiêu chí hoàn thành:** Gõ tiếng Việt có dấu ổn định, chỉ gửi lệnh khi bấm Gửi.

### Việc B5: Hoàn thiện Channels & Gỡ chặn LaunchAppOnTvUseCase (`F-CHN-01..03`)
- **Mô tả:** Tạo file `app/src/main/assets/channels.json` với danh sách ứng dụng mẫu. Hoàn thiện `ChannelsViewModel.kt` (xóa TODO P8). Đồng thời, cập nhật/gỡ bỏ logic kiểm tra Premium thừa trong `LaunchAppOnTvUseCase.kt` để người dùng Free có thể mở app qua tính năng Channels.
- **Tiêu chí hoàn thành:** Màn Channels tải danh sách từ assets json thành công và usecase mở app không bị chặn nhầm.

### Việc B6: Nối Backend Cast (`:tv:cast`, `:tv:core`) vào UI ứng dụng (`F-CST-01..06`)
- **Mô tả:** Nối các controller từ `:tv:cast` và `:tv:core` vào `CastViewModel.kt` and `CastScreen.kt`. Áp dụng `FeatureGate` kiểm tra Premium cho tính năng Cast. (Không sửa code trong thư mục `tv/`).
- **Tiêu chí hoàn thành:** `CastViewModel` gọi đúng controller backend từ `:tv:cast`/`:tv:core`, màn hình Cast hoạt động thực tế.

---

## 3. QUY TRÌNH LÀM VIỆC & KIỂM TRA
1. Đọc code thật trước khi sửa bất kỳ file nào.
2. Commit sau mỗi việc nhỏ với thông điệp rõ ràng, kết thúc bằng `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
3. **Trước khi báo xong:** Chạy `./gradlew.bat :app:testDebugUnitTest` và ghi kết quả vào `docs/tasks/RESULT-B.md`.
4. Không tự ý sửa `missing-features-report.md`.
