# TASKS.md — Kế hoạch phân công công việc song song cho 2 Claude Code (SmartClick)

Báo cáo này lập kế hoạch chi tiết chia các việc còn lại từ `docs/missing-features-report.md` (mục 2) thành 2 luồng độc lập (**Claude A** và **Claude B**) để làm việc song song qua git worktree.

---

## 1. BẢNG VIỆC ĐÃ LỌC & PHÂN CHIA (NHÓM A)

| Mã yêu cầu | Mô tả chức năng | Độ lớn | Luồng | Thứ tự | File chính liên quan |
|---|---|---|---|---|---|
| `F-BASE-04` | Dọn repo, gitattributes, CI | S | A | 1 | `.github/workflows/android-ci.yml`, `.gitattributes`, xóa file patch thừa |
| `F-BASE-07` | Sửa lỗi build B17 (chuỗi vi + cứng) | S | A | 2 | `app/src/main/res/values-vi/strings.xml`, `DeviceFoundScreen.kt` |
| `F-DEV-01` | Màn danh sách thiết bị riêng | M | A | 3 | `app/src/main/java/com/example/smartclick/app/features/devices/DeviceListScreen.kt`, `DevicesViewModel.kt` |
| `F-DEV-02` | Chuyển / Xoá thiết bị | S | A | 4 | `RemoveDeviceUseCase.kt`, `SwitchActiveDeviceUseCase.kt`, `DevicesViewModel.kt` |
| `F-DEV-03` | Kết nối lại tự động (`ReconnectWatcher`) | M | A | 5 | `app/src/main/java/com/example/smartclick/app/domain/connection/ReconnectWatcher.kt` |
| `F-DEV-05` | Lưu địa chỉ MAC cho WoL | S | A | 6 | `DeviceFingerprintEngine.kt`, các driver (`RokuController.kt`, v.v.) |
| `F-PRE-01..05` | Premium & Google Play Billing (thêm lifetime, 4 quyền lợi, strings) | L | A | 7 | `PlayBillingRepository.kt`, `UpdateToPremiumScreen.kt`, `PremiumViewModel.kt`, `strings.xml` |
| `F-PRE-06` | FeatureGate & gỡ chặn LaunchAppOnTvUseCase | S | A | 8 | `LaunchAppOnTvUseCase.kt`, `FeatureGate.kt` |
| `F-REL-01` | Cấu hình Phát hành (Release, R8, ký APK) | M | A | 9 | `app/build.gradle.kts` |
| `F-REM-03` | Nút nguồn 5 trạng thái & WoL (sửa lớp phủ `RemoteScreen`) | M | B | 1 | `RemoteScreen.kt`, `RemotePowerButton.kt`, `RemoteViewModel.kt` |
| `F-REM-04` | Nút Mute 3 trạng thái & lưu theo thiết bị | M | B | 2 | `RemoteSecondaryActions.kt`, `RemoteViewModel.kt` |
| `F-REM-06` | Nút Khoá (R8) (bổ sung vi, cố định 1s) | S | B | 3 | `RemoteViewModel.kt`, `values-vi/strings.xml` |
| `F-REM-07`, `12` | Hàng 3 nút phụ mới (Keyboard, Mic, Headphone) & gỡ nút giả | M | B | 4 | `DPadModeContent.kt`, `BottomActions.kt` |
| `F-REM-10` | Sửa bàn phím chữ (`KeyboardModal` dạng gõ xong bấm Gửi) | M | B | 5 | `KeyboardModal.kt`, `KeyboardTextDraft.kt` |
| `F-CHN-01..03` | Hoàn thiện Channels (`channels.json` + ViewModel) | M | B | 6 | `app/src/main/assets/channels.json` (tạo mới), `ChannelsViewModel.kt`, `ChannelsScreen.kt` |
| `F-CST-01..06` | Nối Backend Cast (`ICastController`) vào UI (`CastViewModel`, `CastScreen`) | L | B | 7 | `CastViewModel.kt`, `CastScreen.kt`, `CastErrorPresentation.kt` |

---

## 2. BẢNG FILE DÙNG CHUNG VÀ CÁC XỬ LÝ XUNG ĐỘT

| File dùng chung | Các luồng đụng | Nguyên tắc xử lý xung đột |
|---|---|---|
| `app/src/main/res/values/strings.xml` & `values-vi/strings.xml` | Cả A và B | **Thêm mới tuyệt đối ở CUỐI file**, trước thẻ đóng `</resources>`. Không sửa hoặc đảo thứ tự dòng cũ của nhau. |
| `app/src/main/java/com/example/smartclick/app/features/remote/RemoteViewModel.kt` | Cả A và B có thể dùng, nhưng trong phân công thực tế: **Chỉ Luồng B** chỉnh sửa file này để xử lý Remote state (Mute, Khoá, Power). |
| `settings.gradle.kts`, `build.gradle.kts` (root) | Không ai sửa trong nhóm (a). Chỉ Luồng A sửa `app/build.gradle.kts` cho Release. |

---

## 3. THỨ TỰ GỘP NHÁNH VÀ KIỂM TRA

1. **Gộp Luồng A trước (`feature/stream-a` -> `premium-test` / `main`):**
   - Chạy `./gradlew :app:testDebugUnitTest` để đảm bảo unit test của A pass.
   - Kiểm tra Billing, Onboarding, Device List, và cấu hình Release.
2. **Gộp Luồng B sau (`feature/stream-b` -> `premium-test` / `main`):**
   - Resolve các file strings.xml (nếu có xung đột thêm chuỗi ở cuối, gộp cả hai khối chuỗi mới).
   - Chạy `./gradlew :app:testDebugUnitTest` toàn bộ.
3. **Kiểm tra tích hợp:**
   - Kiểm tra kết nối Remote UI, Modal bàn phím, Channels, và Cast UI.

---

## 4. NHÓM (B) CẦN QUYẾT ĐỊNH VÀ NHÓM (C) BỊ CHẶN

### Nhóm (b) — Cần người dùng quyết định trước khi triển khai sâu:
1. **Ý nghĩa nút Headphone (`F-REM-14`, `D-Headphone-1`):**
   - *Đề xuất:* Tạm thời cấu hình nút Headphone mở menu cài đặt âm thanh trên TV (Phương án C) hoặc ẩn cho đến khi có firmware chuyên biệt.
2. **Mức độ xác thực Google Cast (I34):**
   - *Đề xuất:* Dùng chung cơ chế xác thực chứng chỉ ghim của cổng điều khiển hiện tại cho đến khi có phần cứng Cast thực tế để test.
3. **Quyền lợi Mic / Giọng nói (`F-REM-13`):**
   - *Đề xuất:* Đưa tính năng nhập giọng nói vào nhóm tính năng Premium hoặc cho phép Free giới hạn 3 lần/ngày.

### Nhóm (c) — Chặn bởi phần cứng / dịch vụ ngoài (Không giao cho Claude):
- `F-BASE-05`: Kiểm chứng đổi ngôn ngữ runtime trên thiết bị thật.
- `F-DRV-01..04`: Nghiệm thu driver Roku, Android TV, Samsung, LG trên TV phần cứng thực tế.
- `F-AUTH-01`: Đăng nhập Firebase thật (cần `google-services.json` thực và Firebase Console).
- `F-PRE` (Live Purchase): Thanh toán thật trên Google Play Console.
- `F-VOI`: Xử lý âm thanh micro phần cứng thiết bị.
