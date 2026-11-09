# TASKS.md — Kế hoạch phân công công việc song song cho 2 Claude Code (SmartClick)

Báo cáo này lập kế hoạch chi tiết chia các việc còn lại từ `docs/missing-features-report.md` (mục 2) thành 2 luồng độc lập (**Claude A** và **Claude B**) để làm việc song song qua git worktree.

---

## 1. BẢNG VIỆC ĐÃ LỌC & PHÂN CHIA (NHÓM A)

| Mã yêu cầu | Mô tả chức năng | Độ lớn | Luồng | Thứ tự | File chính liên quan |
|---|---|---|---|---|---|
| `F-BASE-04` | Dọn repo, gitattributes, CI | S | A | 1 | `.github/workflows/android-ci.yml`, `.gitattributes`, xóa file patch thừa |
| `F-BASE-07` | Sửa lỗi build B17 (chuỗi vi + cứng) | S | A | 2 | **Đã hoàn thành sẵn** (xác minh từ working tree), bỏ qua việc sửa code. |
| `F-DEV-01` | Màn danh sách thiết bị riêng | M | - | - | **Đã giao riêng cho Claude khác làm trong thư mục chính**, bỏ khỏi luồng A, chờ gộp. |
| `F-DEV-02` | Chuyển / Xoá thiết bị (Kiểm tra DeviceListScreen/DevicesViewModel, chỉ làm phần chưa có) | S | A | 3 | `RemoveDeviceUseCase.kt`, `SwitchActiveDeviceUseCase.kt`, `DevicesViewModel.kt` |
| `F-DEV-03` | Kết nối lại tự động (`ReconnectWatcher`) | M | A | 4 | `app/src/main/java/com/example/smartclick/app/domain/connection/ReconnectWatcher.kt` |
| `F-DEV-05` | Lưu địa chỉ MAC cho WoL | S | A | 5 | `DeviceFingerprintEngine.kt`, `tv/core/**`, `tv/roku/**`, `tv/lg/**`, `tv/samsung/**` |
| `F-PRE-01..05` | Premium & Google Play Billing (kiểm tra git log Billing, bổ sung phần thiếu, lifetime, 4 quyền lợi) | L | A | 6 | `PlayBillingRepository.kt`, `UpdateToPremiumScreen.kt`, `PremiumViewModel.kt`, 5 file strings |
| `F-PRE-06` | FeatureGate & PairingLimitPolicy | S | A | 7 | `FeatureGate.kt` |
| `F-REL-01` | Cấu hình Phát hành (Release, R8 keep rules cho Hilt/Protobuf/OkHttp, ProGuard) | M | A | 8 | `app/build.gradle.kts`, `app/proguard-rules.pro`, `tv/*/consumer-rules.pro` |
| `F-REM-03` | Nút nguồn 5 trạng thái & WoL (đọc code hiện tại, bổ sung phần thiếu) | M | B | 1 | `RemoteScreen.kt`, `RemotePowerButton.kt`, `RemoteViewModel.kt` |
| `F-REM-04` | Nút Mute 3 trạng thái & lưu theo thiết bị | M | B | 2 | `RemoteSecondaryActions.kt`, `RemoteViewModel.kt` |
| `F-REM-06` | Nút Khoá (R8) (bổ sung đa ngôn ngữ, cố định 1s) | S | B | 3 | `RemoteViewModel.kt`, 5 file strings |
| `F-REM-07`, `12` | Hàng 3 nút phụ mới (Keyboard, Mic, Headphone - đã có UI 54dp, nối logic) | M | B | 4 | `DPadModeContent.kt`, `BottomActions.kt`, 5 file strings |
| `F-REM-10` | Sửa bàn phím chữ (`KeyboardModal` dạng gõ xong bấm Gửi) | M | B | 5 | `KeyboardModal.kt`, `KeyboardTextDraft.kt` |
| `F-CHN-01..03` | Hoàn thiện Channels (`channels.json` + ViewModel + Gỡ chặn `LaunchAppOnTvUseCase`) | M | B | 6 | `app/src/main/assets/channels.json` (tạo mới), `ChannelsViewModel.kt`, `LaunchAppOnTvUseCase.kt` |
| `F-CST-01..06` | Nối Backend Cast (`:tv:cast`, `:tv:core`) vào UI (`CastViewModel`, `CastScreen`) | L | B | 7 | `CastViewModel.kt`, `CastScreen.kt`, `CastErrorPresentation.kt` |

---

## 2. BẢNG FILE DÙNG CHUNG VÀ CÁC XỬ LÝ XUNG ĐỘT

| File dùng chung | Các luồng đụng | Nguyên tắc xử lý xung đột |
|---|---|---|
| 5 file strings (`values/strings.xml`, `values-vi/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-pt-rBR/strings.xml`) | Cả A và B | **Thêm mới tuyệt đối ở CUỐI file**, trước thẻ đóng `</resources>`. Mọi chuỗi mới phải thêm vào **đủ cả 5 file** để qua `LocaleResourcesConsistencyTest`. |
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

## 4. QUYẾT ĐỊNH ĐÃ CHỐT VÀ NHÓM (C) BỊ CHẶN

### Các quyết định thiết kế đã chốt (Nhóm b):
1. **Ý nghĩa nút Headphone (`F-REM-14`, `D-Headphone-1`):** Giữ nút trên UI, khi bấm sẽ hiện thông báo Toast "Chưa hỗ trợ" (not supported).
2. **Mic nhập chữ (`F-REM-13`):** Miễn phí cho mọi người dùng giống như Keyboard.
3. **Xác thực Google Cast (I34):** Giữ nguyên cơ chế hiện tại, không thay đổi trong đợt này.

### Nhóm (c) — Chặn bởi phần cứng / dịch vụ ngoài (Không giao cho Claude):
- `F-BASE-05`: Kiểm chứng đổi ngôn ngữ runtime trên thiết bị thật.
- `F-DRV-01..04`: Nghiệm thu driver Roku, Android TV, Samsung, LG trên TV phần cứng thực tế.
- `F-AUTH-01`: Đăng nhập Firebase thật (cần `google-services.json` thực và Firebase Console).
- `F-PRE` (Live Purchase): Thanh toán thật trên Google Play Console.
- `F-VOI`: Xử lý âm thanh micro phần cứng thiết bị.
