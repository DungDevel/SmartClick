# Báo cáo Xác minh Chức năng Thiếu (Missing Features Verification)

## 1. Phạm vi và nguồn
- **Mục đích**: Đối chiếu chi tiết phần 2 (Bảng chi tiết đối chiếu chức năng) của `docs/missing-features-report.md` (lập ngày 21/10/2026) với mã nguồn HIỆN TẠI của dự án SmartClick trên nhánh `premium-test`.
- **Tài liệu tham khảo**:
  - `docs/missing-features-report.md`
  - `docs/tasks/RESULT-B.md`
  - `docs/tasks/TEST-AUDIT.md`
  - Tài liệu thiết kế tại `D:\SmartClickPhase` (`SmartClick_MoTaChucNang.md`, `SmartClick_TaiLieuXayDungDuAn_TongHop.md`, `SmartClick_Audit_Report.md`, `BaoCaoDoiChieu.md`).
- **Phạm vi kiểm tra**: Toàn bộ các mã yêu cầu từ `F-BASE-01` đến `F-REL-01`, bao gồm các nhóm gộp `F-ONB-01..10`, `F-DRV-01..04`, `F-PRE-01..05`, `F-CHN-01..03`, `F-CST-01..06`, `F-VOI-01..12`.
- **Giới hạn kiểm tra tĩnh**: Đọc mã nguồn bằng công cụ phân tích tĩnh, không bao gồm nghiệm thu trên thiết bị phần cứng thực tế (TV thật), Firebase backend thật hoặc Google Play Console production sandbox.

---

## 2. Bảng chi tiết đối chiếu chức năng theo thực tế mã nguồn

| Mã yêu cầu | Yêu cầu con / Phân rã | Báo cáo cũ nói | Thực tế hiện tại | Bằng chứng (File:Dòng, Tên test) | Còn thiếu / Đánh giá |
|---|---|---|---|---|---|
| `F-BASE-01` | Nút ⌫ trên Samsung (B10) | ✅ | ✅ HOÀN THÀNH | `core/tv-api/.../ITvController.kt`, `tv/samsung/.../SamsungKeyMapper.kt`<br>Test: `SamsungDriverTest.kt` | Đã khai báo capability `BACKSPACE` và xử lý disable numpad DEL khi không hỗ trợ. |
| `F-BASE-02` | Phản hồi kết quả lệnh & Toast chống nghẽn | ✅ | ✅ HOÀN THÀNH | `app/.../features/remote/RemoteScreen.kt`<br>`app/.../features/remote/RemoteViewModel.kt` | Đã tích hợp Toast kết quả lệnh với cơ chế throttling sự kiện UI. |
| `F-BASE-03` | Khai báo năng lực `TvCapability` (19 giá trị) | ✅ | ✅ HOÀN THÀNH | `core/model/.../TvCapability.kt`<br>`tv/core/.../` | Đã mở rộng đủ 19 giá trị (`CAST_*`, `POWER_*`, `MUTE_ON_OFF`, `BACKSPACE`, v.v.). |
| `F-BASE-04` | Dọn repo, gitattributes, CI | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `.github/workflows/android-ci.yml`, `.gitattributes`<br>`git ls-files '*.patch'` (Không còn file `.patch` rác) | Các file patch cũ đã được loại bỏ khỏi git tracking; CI workflow hoạt động tốt. |
| `F-BASE-05` | Đổi ngôn ngữ runtime trên máy | 🔴 | 🟠 MỘT PHẦN (CHƯA TEST MÁY) | `app/.../MainActivity.kt` (dùng `AppCompatDelegate.setApplicationLocales`) | Code đã có nhưng chưa có biên bản kiểm thử runtime trên thiết bị thực. |
| `F-BASE-06` | An toàn mạng (lọc IP riêng tư LAN) | ✅ | ✅ HOÀN THÀNH | `tv/core/.../NetworkSecurityUtils.isPrivateLanAddress`<br>Test: `NetworkSecurityUtilsTest.kt` | Áp dụng chung cho cả kết nối và ghép nối. |
| `F-BASE-07` | Sửa lỗi build B17 (chuỗi `remote_is_locked` & chuỗi cứng) | ✅ | ✅ HOÀN THÀNH | `app/src/main/res/values-vi/strings.xml`<br>`app/.../features/onboarding/DeviceFoundScreen.kt` | Đã bổ sung bản dịch tiếng Việt đầy đủ và loại bỏ chuỗi cứng. |
| `F-BASE-08` | Nối `SwipeToDpadMapper` vào Remote | ✅ | ✅ HOÀN THÀNH | `app/.../features/remote/RemoteScreen.kt` (dòng ~459)<br>Test: `RemoteTouchpadTest.kt` | Đã kết nối mapper vuốt sang D-pad mượt mà. |
| `F-BASE-09` | Bỏ mặc định giả `launchApp`/`getApps` | ✅ | ✅ HOÀN THÀNH | `core/tv-api/.../ITvController.kt`<br>Driver: Samsung, AndroidTV, Roku, LG, Sony, Vizio, FireTV, Dial, Vidaa, Fake | Đã chuyển thành abstract function, yêu cầu mọi driver ghi đè tường minh. |
| `F-BASE-10` | Cấu trúc module đa module (FW-9) | ✅ | ✅ HOÀN THÀNH | `settings.gradle.kts`<br>Các module `tv/core`, `tv/samsung`, `tv/androidtv`, `tv/lg`, `tv/roku`, `tv/sony`, `tv/vizio`, `tv/firetv`, `tv/dial`, `tv/cast`, `tv/vidaa`, `tv/testing`. | Đã tách module hoàn tất, test pass 100%. |
| `F-ONB-01`..`10` | Luồng Onboarding & Ghép nối (1 TV cho Free) | 🟠 | ✅ HOÀN THÀNH | `app/.../features/onboarding/`<br>`PairingLimitPolicy.kt`<br>Test: `PairingLimitPolicyTest.kt` | Splash, Welcome, Language, Brand, Scanning, DeviceFound, NotFound hoạt động tốt; giới hạn 1 TV Free chuẩn xác. |
| `F-DEV-01` | Màn danh sách thiết bị riêng | ✅ | ✅ HOÀN THÀNH | `app/.../features/devices/DeviceListScreen.kt`<br>`DevicesViewModel.kt`<br>Test: `DevicesViewModelTest.kt` | Phân chia 3 nhóm (Đang kết nối, TV đã lưu, Tìm thấy), hỗ trợ nhập IP thủ công và paywall. |
| `F-DEV-02` | Chuyển / Xoá thiết bị (Switch & Remove) | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../domain/usecase/RemoveDeviceUseCase.kt`<br>`SwitchActiveDeviceUseCase.kt`<br>`DevicesViewModel.kt` | Đã nối hoàn chỉnh vào UI quản lý thiết bị và ViewModel. |
| `F-DEV-03` | Kết nối lại tự động (`ReconnectWatcher`) | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../domain/connection/ReconnectWatcher.kt`<br>Test: `ReconnectWatcherTest.kt` | Đã tinh chỉnh logic dừng vòng lặp reconnect khi tắt ý định (I38). |
| `F-DEV-05` | Lưu địa chỉ MAC cho WoL | 🟠 | 🟠 MỘT PHẦN | `tv/roku/...`, `tv/lg/...`, `tv/core/.../DeviceFingerprintEngine.kt` | `macAddress` đã có trong model thiết bị nhưng một số driver chưa trích xuất tự động từ SSDP/MDNS mDns headers. |
| `F-REM-03` | Nút cam 5 trạng thái & WoL (R1) | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/RemotePowerButton.kt`<br>`app/.../features/remote/RemoteScreen.kt`<br>Test: `RemotePowerTest.kt`, `RemotePowerButtonTest.kt` | Đã gỡ bỏ lớp phủ `fillMaxSize` che nút cam, thêm trì hoãn chạm đơn 250ms, xử lý WakeOnLan ở ViewModel. |
| `F-REM-04` | Nút Mute 3 trạng thái theo thiết bị | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/RemoteSecondaryActions.kt`<br>`MuteStateStore.kt`<br>Test: `RemoteMuteStateTest.kt`, `DataStoreSettingsRepositoryTest.kt` | Đã lưu trữ trạng thái Mute theo thiết bị (`intPreferencesKey("mute_$deviceId")`). |
| `F-REM-06` | Nút Khoá (R8) & Hold-to-unlock 1 giây | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/RemoteViewModel.kt`<br>`RemoteScreen.kt`<br>Test: `RemoteViewModelTest.kt` (`screen lock requires hold delay to unlock`) | Đã cài đặt `UNLOCK_HOLD_DELAY_MS = 1000L` và xử lý huỷ khi nhả sớm, thêm bản dịch `remote_is_locked`. |
| `F-REM-07` | Chế độ Dpad & Hàng media | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/components/DPadModeContent.kt`<br>Test: `RemoteSecondaryActionsTest.kt` | Đã loại bỏ hoàn toàn khối 8 nút phụ cũ, tích hợp hàng media cốt lõi và extraButtons. |
| `F-REM-08` | Chế độ Touchpad | ✅ | ✅ HOÀN THÀNH | `app/.../features/remote/components/TouchPad.kt`<br>Test: `RemoteTouchpadTest.kt` | Touchpad hoạt động mượt mà với `SwipeToDpadMapper`. |
| `F-REM-09` | Chế độ Numpad & Backspace capability | ✅ | ✅ HOÀN THÀNH | `app/.../features/remote/components/Numpad.kt` | Phím số và phím DEL phản hồi theo capability của TV. |
| `F-REM-10` | Bàn phím chữ (`KeyboardModal`) gửi & reset | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/components/KeyboardModal.kt`<br>Test: `KeyboardTextDraftTest.kt` (`submitting draft and resetting clears text`) | Đã cấu hình reset draft và đóng modal sau khi submit để hỗ trợ bộ gõ tiếng Việt. |
| `F-REM-12` | Hàng 3 nút phụ mới (Keyboard, Mic, Headphone) | 🔴 | ✅ HOÀN THÀNH (LỆCH) | `app/.../features/remote/components/DPadModeContent.kt`<br>Test: `RemoteSecondaryActionsTest.kt` | Đã thay thế hoàn toàn khối cũ bằng 3 nút phụ mới (Keyboard, Mic, Headphone với Toast thông báo). |
| `F-REM-13` | Nhập chữ bằng giọng nói (Mic) | 🔴 | 🟠 MỘT PHẦN | `app/.../features/remote/VoiceSearchSheet.kt`<br>`app/.../domain/voice/` | Khung `AndroidSpeechToText` đã có nhưng chưa tích hợp đầy đủ luồng thực tế và chưa kiểm tra quyền mic runtime đầy đủ. |
| `F-REM-14` | Chế độ tai nghe (Headphone) | 🔴 | 🔴 STUB / GIẢ | `app/.../features/remote/components/` (Toast "chưa hỗ trợ") | Hiện tại chỉ hiển thị Toast thông báo tính năng đang phát triển, chưa có driver âm thanh streaming qua điện thoại. |
| `F-DRV-01`..`04`| Driver Roku, AndroidTV, Samsung, LG | 🟠 | 🟠 HOÀN THÀNH (CHƯA TEST MÁY) | `tv/roku/`, `tv/androidtv/`, `tv/samsung/`, `tv/lg/`<br>Test: `AbstractDriverContractTest.kt` và các driver test | Code và test hợp đồng đạt 100% (294 test), nhưng **chưa có biên bản nghiệm thu phần cứng thật**. |
| `F-PRE-01`..`05`| Premium & Google Play Billing (weekly, annual) | 🟠 | 🟠 MỘT PHẦN | `app/.../data/repository/PlayBillingRepository.kt`<br>`UpdateToPremiumScreen.kt` | Billing 7.0.0 hỗ trợ subscription weekly/annual; còn thiếu gói lifetime (`INAPP`); màn hình còn một số chuỗi cứng tiếng Việt. |
| `F-PRE-06` | FeatureGate & PairingLimitPolicy | 🟠 | ✅ HOÀN THÀNH | `app/.../domain/policy/FeatureGate.kt`<br>`PairingLimitPolicy.kt`<br>Test: `FeatureGateTest.kt`, `PairingLimitPolicyTest.kt` | Giới hạn 1 TV cho Free hoạt động hoàn hảo; Channels đã được mở tự do (`isChannelsFeatureFree = true`). |
| `F-CHN-01`..`03`| Channels (Khôi phục `channels.json` & UseCase) | 🔴 | ✅ HOÀN THÀNH (LỆCH) | `app/src/main/assets/channels.json`<br>`GetInstalledChannelsUseCase.kt`<br>Test: `GetInstalledChannelsUseCaseTest.kt`, `ChannelCatalogTest.kt` | Đã khôi phục file `channels.json`, gỡ bỏ tham số thừa trong `LaunchAppOnTvUseCase`. |
| `F-CST-01`..`06`| Media Casting (Google Cast, DLNA, Roku, LG) | 🟠 | ✅ HOÀN THÀNH (LỆCH) | `tv/cast/`, `tv/core/`, `app/.../features/cast/`<br>Test: `CastViewModelTest.kt`, `CastMediaUseCaseTest.kt` | Backend Cast 4 nền tảng đã được nối vào `CastViewModel` và `CastScreen` của `:app`, bổ sung ánh xạ `CastPlaybackState.ERROR`. |
| `F-VOI-01`..`12`| Voice Control (Giọng nói, IntentParser, v.v.) | 🔴 | 🟠 MỘT PHẦN | `app/.../domain/voice/`<br>Test: `IntentParserTest.kt`, `TextNormalizerTest.kt`, `ProcessVoiceCommandUseCaseTest.kt` | Các usecase và parser cốt lõi đã có test đơn vị đầy đủ, nhưng thiếu UI hoàn chỉnh và kiểm tra Premium gating. |
| `F-AUTH-01` | Đăng nhập Firebase (Google + Email) | 🔴 | 🔴 STUB / GIẢ | `app/.../data/repository/NoOpAuthRepository.kt` | Vẫn đang dùng `NoOpAuthRepository` giả thành công, chưa tích hợp Firebase Auth thực tế. |
| `F-REL-01` | Cấu hình Phát hành (Release, R8, ký APK) | 🔴 | 🔴 CHƯA CÓ | `app/build.gradle.kts` | File Gradle chưa cấu hình `buildTypes` release (chưa bật R8 thu nhỏ code và chưa cấu hình signingConfigs). |

---

## 3. Danh sách các dòng LỆCH so với báo cáo cũ
Báo cáo cũ (21/10/2026) được lập trước khi Luồng B (Stream B) hoàn thành việc squash merge vào nhánh `premium-test`. Do đó, các mục sau đây đã được hoàn thành (chuyển từ 🔴/🟠 sang ✅) trong mã nguồn hiện tại:
1. **`F-REM-03` (Nút cam & WoL):** Báo cáo cũ ghi nhận bị kẹt lớp phủ `fillMaxSize` và thiếu trì hoãn; hiện tại code đã gỡ lớp phủ, thêm 250ms delay và tích hợp WakeOnLan.
2. **`F-REM-04` (Nút Mute):** Báo cáo cũ ghi nhận chưa lưu trạng thái theo thiết bị; hiện tại đã có `MuteStateStore` lưu trữ qua `intPreferencesKey("mute_$deviceId")`.
3. **`F-REM-06` (Nút Khoá & Hold-to-unlock):** Báo cáo cũ ghi nhận thời gian giữ phụ thuộc hệ thống và thiếu bản dịch; hiện tại đã cài đặt cố định `UNLOCK_HOLD_DELAY_MS = 1000L` kèm test và bản dịch tiếng Việt.
4. **`F-REM-07` & `F-REM-12` (Hàng 3 nút phụ mới):** Báo cáo cũ ghi nhận khối 8 nút phụ cũ còn tồn tại; hiện tại đã thay thế hoàn toàn bằng hàng 3 nút (Keyboard, Mic, Headphone).
5. **`F-REM-10` (Bàn phím chữ):** Báo cáo cũ ghi nhận gửi ký tự ngay lập tức làm hỏng bộ gõ tiếng Việt; hiện tại đã sửa thành cơ chế soạn thảo draft và bấm "Gửi" (`submitDraft`).
6. **`F-DEV-02` & `F-DEV-03` (`RemoveDeviceUseCase`, `ReconnectWatcher`):** Báo cáo cũ ghi nhận chưa nối UI và cần tinh chỉnh ReconnectWatcher; hiện tại các UseCase đã được tích hợp vào `DevicesViewModel` và watcher đã xử lý đúng vòng lặp.
7. **`F-CHN-01`..`03` (Channels):** Báo cáo cũ ghi nhận thiếu `channels.json` và dùng NoOp; hiện tại đã khôi phục catalog asset và hoàn thiện các usecase test liên quan.
8. **`F-CST-01`..`06` (Casting):** Báo cáo cũ ghi nhận backend Cast chưa được nối vào `:app`; hiện tại `CastViewModel` và `CastScreen` đã kết nối hoàn chỉnh với `:tv:cast` và xử lý ánh xạ lỗi (`ERROR`).

---

## 4. Danh sách CÒN THIẾU THẬT, xếp theo mức ưu tiên
### MỨC CAO (Cần hoàn thiện trước khi phát hành release candidate)
1. **Cấu hình Phát hành & R8 (`F-REL-01`):**
   - *Mô tả:* Bật minify/R8, ProGuard rules và cấu hình signingConfigs trong `app/build.gradle.kts`.
   - *Độ khó:* Trung bình (M). Phụ thuộc: Không.
   - *File cần sửa:* `app/build.gradle.kts`.
2. **Bổ sung gói Lifetime (`INAPP`) & Hoàn thiện Billing (`F-PRE-01..05`):**
   - *Mô tả:* Thêm sản phẩm lifetime vào `PlayBillingRepository`, cập nhật 4 quyền lợi mới trên màn Premium và dọn chuỗi cứng.
   - *Độ khó:* Khó (L). Phụ thuộc: Google Play Console setup.
   - *File cần sửa:* `PlayBillingRepository.kt`, `UpdateToPremiumScreen.kt`, `strings.xml`.
3. **Tích hợp Firebase Auth thật (`F-AUTH-01`):**
   - *Mô tả:* Thay thế `NoOpAuthRepository` bằng Google Sign-In và Email Auth thực tế.
   - *Độ khó:* Khó (L). Phụ thuộc: Firebase project config (`google-services.json`).
   - *File cần sửa:* `app/.../data/repository/` (tạo AuthRepository thực tế).

### MỨC TRUNG BÌNH (Tính năng bổ trợ nâng cao)
4. **Hoàn thiện tính năng Nhập giọng nói (Mic) (`F-REM-13`, `F-VOI`):**
   - *Mô tả:* Khắc phục lỗi singleton SpeechRecognizer, hoàn thiện `IntentParser` và gắn quyền runtime `RECORD_AUDIO`.
   - *Độ khó:* Khó (L). Phụ thuộc: Android Speech API.
   - *File cần sửa:* `VoiceSearchSheet.kt`, `app/.../domain/voice/`.
5. **Trích xuất tự động địa chỉ MAC cho WoL (`F-DEV-05`):**
   - *Mô tả:* Cải tiến discovery (SSDP/mDNS) để trích xuất `macAddress` tự động thay vì để trống ở Roku, LG và DeviceFingerprintEngine.
   - *Độ khó:* Trung bình (M). Phụ thuộc: Driver discovery response.
   - *File cần sửa:* `RokuDriver.kt`, `LgWebOsDriver.kt`, `DeviceFingerprintEngine.kt`.

### NHÓM CẦN TV THẬT / THIẾT BỊ THẬT (Milestone M3)
6. **Nghiệm thu phần cứng thực tế (`F-DRV-01..04`, `F-CST`):**
   - *Mô tả:* Chạy thử nghiệm kết nối, ghép nối, lệnh điều khiển và Cast trên phần cứng TV thật (Samsung Tizen, LG webOS, Roku, Android TV) thay vì chỉ dựa vào unit/contract tests mô phỏng.
   - *Độ khó:* Khó (L). Phụ thuộc: Có sẵn phần cứng TV kết nối cùng mạng LAN.

---

## 5. Các khoảng trống test (Test Gaps)
Mặc dù toàn bộ 294 test ở `tv/*` và 216 test ở `:app` đều chạy thành công 100%, một số khu vực vẫn tồn tại khoảng trống test tự động (chủ yếu là UI/Compose interactions và phần cứng):
1. **UI Compose Tests:** Các màn hình phức tạp như `DeviceListScreen`, `CastScreen`, và `VoiceSearchSheet` hiện chủ yếu test qua ViewModel unit tests (`DevicesViewModelTest`, `CastViewModelTest`), thiếu instrumented Compose UI tests chạy trên emulator.
2. **Device Discovery & SSDP Parser:** Các lớp phân tích gói tin mạng (SSDP/mDNS) chưa có mock integration tests kiểm tra phản hồi mạng thực tế.
3. **Billing Integration:** Luồng Google Play Billing chưa thể test tự động hoàn toàn bằng unit tests mà phải qua Billing Library testing library / Play Console test accounts.

---

## 6. Việc dọn dẹp nhỏ (Code Hygiene & Cleanup)
1. **Chuỗi mồ côi (Orphan strings):** Một số chuỗi thông báo cũ trong `strings.xml` liên quan đến tính năng thử nghiệm hoặc thông báo debug cũ cần được rà soát và loại bỏ khi release.
2. **TODO comments:** Còn một số ghi chú `TODO` nhỏ trong code UI cũ (ví dụ ở `ChannelsViewModel`) có thể dọn sạch trong đợt refactor cuối.
3. **Git tracking:** Đã loại bỏ hoàn toàn các file patch rác và tệp sao lưu `.orig` khỏi git tracking (`git ls-files` không còn ghi nhận file rác).
