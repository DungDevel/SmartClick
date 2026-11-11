# Báo cáo Đối chiếu Chức năng Thiếu — Android SmartClick

| | |
|---|---|
| **Ngày lập** | 21/10/2026 |
| **Dự án** | Android SmartClick (Kotlin, Jetpack Compose, Hilt, Room, Coroutines) |
| **Thư mục dự án** | `D:\SmartClick` |
| **Tài liệu thiết kế** | `D:\SmartClickPhase` (`SmartClick_MoTaChucNang.md`, `SmartClick_TaiLieuXayDungDuAn_TongHop.md`, `SmartClick_Audit_Report.md`) |
| **Mục đích** | Báo cáo chi tiết các chức năng còn thiếu, làm dở, giả (stub) hoặc khác tài liệu so với thiết kế, dựa trên việc đọc tĩnh source code và đối chiếu tài liệu. |

---

## 1. TÓM TẮT TỔNG QUAN

Dự án Android SmartClick hiện đang ở giai đoạn cuối G3-a / đầu G3-b (khoảng 60-65% tổng thể hoàn thành về mặt code khung, nhưng **chưa có kiểm chứng trên thiết bị phần cứng thật** và còn một số luồng giả/chưa nối).

### Thống kê số lượng yêu cầu theo nhóm trạng thái:
- **Đã hoàn thành (✅):** 22 yêu cầu (Khung kiến trúc 5 module, Room DB, PairingLimitPolicy giới hạn 1 TV cho Free, SwipeToDpadMapper, phần lớn màn Remote cơ bản, kết nối Roku cơ bản).
- **Làm dở / Một phần (🟠):** 36 yêu cầu (Nút nguồn mới có 4 trạng thái nhưng kẹt lớp phủ; Mute chưa có trạng thái thực; Cast backend 4 nền tảng đã có nhưng chưa nối UI và chưa thử phần cứng; các driver Samsung/LG/Android TV có code và test hợp đồng nhưng chưa nghiệm thu TV thật; Billing có tuần/năm nhưng thiếu lifetime và quyền lợi mới).
- **Giả / Stub (🔴/🟡):** 12 yêu cầu (`launchApp`/`getApps` mặc định trả giả ở Samsung/Android TV; login bản release giả thành công; khối 8 nút phụ cũ chỉ hiện toast "sắp ra mắt"; một số thông báo/chuỗi cứng tiếng Việt trong Billing).
- **Chưa có (🔴):** 15 yêu cầu (Hàng 3 nút phụ mới F-REM-12; điều khiển giọng nói F-VOI; màn quản lý thiết bị riêng F-DEV-01; đăng nhập Firebase thật; cấu hình Release R8/ký APK; `channels.json` thực tế).
- **Khác tài liệu (🟡):** 3 yêu cầu (Channels được chốt miễn phí nhưng usecase và viewmodel vẫn còn đoạn kiểm tra Premium cũ; hành vi Touchpad fallback).

### Tỷ lệ hoàn thành ước tính theo Phase:
- **Phase 3 (Nền tảng & Remote):** ~75% (Core UI và driver khung đã xong, còn thiếu hoàn thiện nút cam, Mute, bàn phím gõ tiếng Việt, và sửa lỗi build B17).
- **Phase 5 (Premium & Billing):** ~65% (Billing 7.0.0, đăng ký tuần/năm đã có; thiếu gói lifetime INAPP, cập nhật 4 quyền lợi mới, dọn chuỗi cứng).
- **Phase 4 (Channels & Cast):** ~40% (Cast backend 4 nền tảng đã có trong `:tv:runtime` nhưng chưa nối vào `app/` và chưa test phần cứng; Channels thiếu danh mục `channels.json` và gỡ chặn usecase).
- **Phase 6 (Voice & Auth):** ~5% (Chỉ có khung cấu trúc thư mục/định nghĩa, chưa có logic xử lý thực tế, thiếu Firebase).

---

## 2. BẢNG CHI TIẾT ĐỐI CHIẾU CHỨC NĂNG

| Mã yêu cầu | Phase | Mô tả chức năng | Trạng thái | File và vị trí trong code | Ghi chú / Bằng chứng |
|---|---|---|---|---|---|
| `F-BASE-01` | G3 | Nút ⌫ trên Samsung (B10) | ✅ | `app/src/main/java/com/example/smartclick/app/domain/usecase/SendCommandUseCase.kt`<br>`tv:runtime/.../SamsungKeyMapper.kt` | `BACKSPACE` capability khai báo; Samsung trả `NOT_SUPPORTED`; Numpad làm mờ DEL qua `canDelete`. |
| `F-BASE-02` | G3 | Phản hồi kết quả lệnh & thông báo | ✅ | `app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt`<br>`RemoteViewModel.kt` | Đã tích hợp Toast phản hồi kết quả lệnh (thành công/thất bại) với cơ chế chống nghẽn (throttling) sự kiện UI. |
| `F-BASE-03` | G3 | Khai báo năng lực `TvCapability` | ✅ | `core:model/.../TvCapability.kt`<br>`tv:runtime/.../` | Đã mở rộng lên 19 giá trị (`CAST_*`, `POWER_*`, `MUTE_ON_OFF`, `BACKSPACE`, v.v.). |
| `F-BASE-04` | G3 | Dọn repo, gitattributes, CI | 🟠 | Gốc dự án (`.github/workflows/android-ci.yml`, `.gitattributes`) | CI đã có cấu hình nhưng còn 4 file `.patch` bị theo dõi (I35), CRLF chưa chuẩn hoá hoàn toàn. |
| `F-BASE-05` | G3 | Kiểm chứng đổi ngôn ngữ trên máy | 🔴 | `app/src/main/java/com/example/smartclick/MainActivity.kt` | Code đã dùng `AppCompatDelegate.setApplicationLocales`, chưa có biên bản thử nghiệm runtime. |
| `F-BASE-06` | G3 | An toàn mạng (lọc IP riêng) | ✅ | `tv/core/.../NetworkSecurityUtils.kt`<br>`ConnectToTvUseCase.kt`<br>`PairTvUseCase.kt` | Đã chuẩn hóa kiểm tra lọc IP riêng tư LAN (`NetworkSecurityUtils.isPrivateLanAddress`) áp dụng chung cho cả kết nối và ghép nối. |
| `F-BASE-07` | G3 | Sửa lỗi build B17 (chuỗi vi + cứng) | ✅ | `app/src/main/res/values-vi/strings.xml`<br>`app/src/main/java/com/example/smartclick/app/features/onboarding/DeviceFoundScreen.kt` | Đã bổ sung chuỗi `remote_is_locked` ở `values-vi` và đưa chuỗi cứng vào tài nguyên. |
| `F-BASE-08` | G3 | Nối `SwipeToDpadMapper` | ✅ | `app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt` (dòng 459) | Đã kết nối mapper vuốt sang D-pad. |
| `F-BASE-09` | G3 | Bỏ mặc định giả `launchApp`/`getApps` (B13) | ✅ | `core:tv-api/.../ITvController.kt`<br>`tv/*/` | Đã chuyển `getApps` và `launchApp` thành abstract trong `ITvController`, yêu cầu tất cả các driver ghi đè tường minh. |
| `F-BASE-10` | G1 | Cấu trúc module (FW-9) | ✅ | `settings.gradle.kts`<br>`tv/core/`, `tv/samsung/`, `tv/androidtv/`, v.v. | Đã tách module nguyên khối `:tv:runtime` thành 11 module độc lập theo hãng và giao thức (`:tv:core`, `:tv:samsung`, `:tv:androidtv`, `:tv:lg`, `:tv:roku`, `:tv:sony`, `:tv:vizio`, `:tv:firetv`, `:tv:dial`, `:tv:cast`, `:tv:vidaa`). |
| `F-ONB-01`..`10` | G2 | Luồng Onboarding & Ghép nối | 🟠 | `app/src/main/java/com/example/smartclick/app/features/onboarding/` | Các màn Splash, Welcome, Language, Brand, Scanning, DeviceFound, NotFound đã có; giới hạn 1 TV cho Free hoạt động tốt (`PairingLimitPolicy`). |
| `F-DEV-01` | G3 | Màn danh sách thiết bị riêng | ✅ | `app/src/main/java/com/example/smartclick/app/features/devices/DeviceListScreen.kt`<br>`DevicesViewModel.kt` | Đã hoàn thành màn quản lý thiết bị riêng biệt phân chia 3 nhóm (Đang kết nối, TV đã lưu, Tìm thấy trên mạng), hỗ trợ nhập IP thủ công, quét lại và tích hợp Paywall. |
| `F-DEV-02` | G3 | Chuyển / Xoá thiết bị | 🟠 | `app/src/main/java/com/example/smartclick/app/domain/usecase/RemoveDeviceUseCase.kt`<br>`SwitchActiveDeviceUseCase.kt` | UseCase đã có, chưa nối hoàn chỉnh vào UI quản lý thiết bị. |
| `F-DEV-03` | G3 | Kết nối lại tự động (`ReconnectWatcher`) | 🟠 | `app/src/main/java/com/example/smartclick/app/domain/connection/ReconnectWatcher.kt` | Có watcher, cần tinh chỉnh kịch bản app tự tắt TV để tránh vòng lặp reconnect liên tục (I38). |
| `F-DEV-05` | G3 | Lưu địa chỉ MAC cho WoL | 🟠 | `tv:runtime/.../` | `macAddress` hiện đang để trống ở `RokuController`, `LgWebOsController`, và `DeviceFingerprintEngine`. |
| `F-REM-03` | G3 | Nút cam 5 trạng thái & WoL (R1) | 🟠 | `app/src/main/java/com/example/smartclick/app/features/remote/RemotePowerButton.kt` | Có 4 kiểu hiển thị, nhưng bị lớp phủ `fillMaxSize` chặn ở `RemoteScreen.kt` (I27); thiếu trì hoãn chạm đơn 250ms. |
| `F-REM-04` | G3 | Nút Mute 3 trạng thái | 🟠 | `app/src/main/java/com/example/smartclick/app/features/remote/RemoteSecondaryActions.kt` | Domain có `MuteOn/MuteOff`, UI vẫn gửi lệnh đảo `Mute`, chưa lưu trạng thái theo thiết bị (I28). |
| `F-REM-06` | G3 | Nút Khoá (R8) | 🟠 | `app/src/main/java/com/example/smartclick/app/features/remote/RemoteViewModel.kt` | Đã có `isLocked`, Toast nhắc (thiếu bản dịch `vi`), nhưng thời gian giữ mở khoá phụ thuộc hệ thống thay vì cố định 1 giây. |
| `F-REM-07` | G3 | Chế độ Dpad & Hàng media | 🟠 | `app/src/main/java/com/example/smartclick/app/features/remote/components/DPadModeContent.kt` | Hàng media 3 nút lõi đã có; tuy nhiên khối 8 nút phụ giả vẫn còn (I16). |
| `F-REM-08` | G3 | Chế độ Touchpad | ✅ | `app/src/main/java/com/example/smartclick/app/features/remote/components/TouchPad.kt` | Đã nối `SwipeToDpadMapper`. |
| `F-REM-09` | G3 | Chế độ Numpad | ✅ | `app/src/main/java/com/example/smartclick/app/features/remote/components/Numpad.kt` | Phím số hoạt động, phím DEL mờ theo capability `BACKSPACE`. |
| `F-REM-10` | G3 | Bàn phím chữ (`KeyboardModal`) | 🟠 | `app/src/main/java/com/example/smartclick/app/features/remote/components/KeyboardModal.kt` | Đã có modal, nhưng gửi ký tự ngay lập tức khi gõ làm hỏng bộ gõ tiếng Việt Telex/VNI (cần đổi sang "gõ xong bấm Gửi"). |
| `F-REM-12` | G3 | Hàng 3 nút phụ mới (R7 sửa) | 🔴 | `app/src/main/java/com/example/smartclick/app/features/remote/components/DPadModeContent.kt` | Chưa thay khối 8 nút cũ bằng hàng 3 nút (Headphone, Mic nhập chữ, Keyboard). |
| `F-REM-13` | G3 | Nhập chữ bằng giọng nói (Mic) | 🔴 | `app/src/main/java/com/example/smartclick/app/features/remote/components/` | Chưa làm; phụ thuộc STT (lỗi I29). |
| `F-REM-14` | G3 | Chế độ tai nghe (Headphone) | 🔴 | `app/src/main/java/com/example/smartclick/app/features/remote/components/` | Chưa định nghĩa rõ nghĩa (D-Headphone-1) và chưa có driver hỗ trợ. |
| `F-DRV-01`..`04`| G3 | Driver Roku, Android TV, Samsung, LG | 🟠 | `tv:runtime/src/main/java/com/example/smartclick/app/data/driver/` | Code và test hợp đồng (`AbstractDriverContractTest`) đã có, nhưng **chưa có biên bản nghiệm thu trên thiết bị phần cứng thật**. |
| `F-PRE-01`..`05`| P5 | Premium & Google Play Billing | 🟠 | `app/src/main/java/com/example/smartclick/app/data/repository/PlayBillingRepository.kt`<br>`UpdateToPremiumScreen.kt` | Billing 7.0.0 có truy vấn `weekly`, `annual` (SUBS); còn thiếu gói `lifetime` (INAPP); màn Premium còn chứa quyền lợi cũ (`perk_channels_library`, `perk_priority_support`) và thông báo viết cứng tiếng Việt. |
| `F-PRE-06` | P5 | FeatureGate & PairingLimitPolicy | 🟠 | `app/src/main/java/com/example/smartclick/app/domain/policy/FeatureGate.kt` | Giới hạn 1 TV cho Free đã xong; Channels đã bật tự do (`isChannelsFeatureFree = true`), nhưng `LaunchAppOnTvUseCase` vẫn còn chặn Premium (I37). |
| `F-CHN-01`..`03`| G4 | Channels (Mở app trên TV) | 🔴 | `app/src/main/java/com/example/smartclick/app/features/channels/` | Chưa có file `channels.json` thực tế; release đang dùng `NoOpChannelRegistryRepository`; `ChannelsViewModel` còn `TODO(P8)`. |
| `F-CST-01`..`06`| G4 | Media Casting (Đẩy ảnh/video lên TV) | 🟠 | `tv:runtime/.../cast/`<br>`app/src/main/java/com/example/smartclick/app/features/cast/` | **Backend Cast 4 nền tảng đã có trong module `:tv:runtime`** (Google Cast V2, DLNA Samsung, LG webOS media viewer, Roku), nhưng **chưa được nối vào UI của `:app`** (`CastViewModel` vẫn dùng giả) và chưa test phần cứng (I33, I34). |
| `F-VOI-01`..`12`| G6 | Voice Control (Giọng nói) | 🔴 | `app/src/main/java/com/example/smartclick/app/features/remote/VoiceSearchSheet.kt`<br>`app/src/main/java/com/example/smartclick/app/domain/voice/` | Khung `AndroidSpeechToText` và `IntentParser` sơ khai đã có, nhưng chưa hoàn thiện (lỗi singleton I29, phân tích cú pháp I30), chưa có UI đầy đủ và chưa kiểm tra Premium. |
| `F-AUTH-01` | G6 | Đăng nhập Firebase (Google + Email) | 🔴 | `app/src/main/java/com/example/smartclick/app/data/repository/` | Vẫn dùng `NoOpAuthRepository` và màn đăng nhập giả thành công (I32). |
| `F-REL-01` | G7 | Cấu hình Phát hành (Release, R8, ký) | 🔴 | `app/build.gradle.kts` | File Gradle chưa cấu hình `buildTypes` release (chưa bật R8/thu nhỏ, chưa cấu hình ký APK, I24). |

---

## 3. DANH SÁCH CHỨC NĂNG THIẾU XẾP THEO MỨC ƯU TIÊN

### MỨC CAO (Chặn luồng chính hoặc phase hiện tại — Cần làm ngay)
1. **Sửa lỗi build B17 (`F-BASE-07`):**
   - *Mô tả:* Thiếu bản dịch `remote_is_locked` ở `values-vi/strings.xml` và 3 chuỗi cứng trong `DeviceFoundScreen.kt`.
   - *Độ khó:* Dễ (S).
   - *File cần sửa:* `app/src/main/res/values-vi/strings.xml`, `app/src/main/java/com/example/smartclick/app/features/onboarding/DeviceFoundScreen.kt`.
2. **Sửa lỗi nút nguồn và Wake-on-LAN (`F-REM-03`, `I27`, `I28`):**
   - *Mô tả:* Lớp phủ chặn nút cam, lệnh `Power` bị gọi vô điều kiện, thiếu địa chỉ MAC lưu khi ghép nối.
   - *Độ khó:* Vừa (M).
   - *File cần sửa:* `app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt`, `RemoteViewModel.kt`, các driver (`RokuDriver.kt`, `LgWebOsDriver.kt`, v.v.).
3. **Hoàn thiện gói Premium & Billing theo quyết định mới (`F-PRE`, `I31`):**
   - *Mô tả:* Thêm sản phẩm `lifetime` (`INAPP`), cập nhật 4 quyền lợi mới trên màn Premium (`UpdateToPremiumScreen.kt`), chuyển thông báo Billing sang `strings.xml`.
   - *Độ khó:* Khó (L).
   - *File cần sửa:* `PlayBillingRepository.kt`, `UpdateToPremiumScreen.kt`, `PremiumViewModel.kt`, `strings.xml`, `strings.xml (vi)`.
4. **Nghiệm thu phần cứng tối thiểu trên TV thật (`F-DRVX`, `M3`):**
   - *Mô tả:* Kiểm chứng kết nối, ghép nối, lệnh điều khiển của Roku, Samsung, LG trên thiết bị phần cứng thật để đóng Milestone M3.
   - *Độ khó:* Khó (L).
   - *File cần sửa:* Thiết bị phần cứng thực tế và log driver tương ứng trong `:tv:runtime`.

### MỨC TRUNG BÌNH (Chặn tính năng bổ trợ hoặc Phase 4 / Phase 5)
5. **Thay khối 8 nút phụ giả bằng hàng 3 nút phụ mới (`F-REM-12`):**
   - *Mô tả:* Gỡ bỏ 6 nút giả cũ, dựng hàng 3 nút (Keyboard, Mic nhập chữ, Headphone) theo điều kiện `capabilities`.
   - *Độ khó:* Vừa (M).
   - *File cần sửa:* `app/src/main/java/com/example/smartclick/app/features/remote/components/DPadModeContent.kt`.
6. **Nối Backend Cast đã có vào UI ứng dụng (`F-CST`, `I33`):**
   - *Mô tả:* `ICastController` và các cài đặt Cast đã có trong `:tv:runtime`, cần nối vào `CastViewModel` và `CastScreen` của `:app`, đồng thời áp dụng `FeatureGate` Premium.
   - *Độ khó:* Khó (L).
   - *File cần sửa:* `app/src/main/java/com/example/smartclick/app/features/cast/CastViewModel.kt`, `CastScreen.kt`.
7. **Sửa cơ chế bàn phím gõ chữ (`F-REM-10`, `I36`):**
   - *Mô tả:* Đổi `KeyboardModal` từ gửi từng ký tự ngay khi gõ sang chế độ gõ xong bấm nút "Gửi" để tránh hỏng bộ gõ tiếng Việt Telex/VNI.
   - *Độ khó:* Vừa (M).
   - *File cần sửa:* `app/src/main/java/com/example/smartclick/app/features/remote/components/KeyboardModal.kt`.
8. **Hoàn thiện Channels thực tế (`F-CHN`, `I37`):**
   - *Mô tả:* Đưa `channels.json` vào assets, gỡ bỏ kiểm tra Premium còn sót lại trong `LaunchAppOnTvUseCase`, triển khai `launchApp` thực tế cho Samsung và Android TV.
   - *Độ khó:* Khó (L).
   - *File cần sửa:* `app/src/main/assets/channels.json` (tạo mới), `LaunchAppOnTvUseCase.kt`, các driver tương ứng.

### MỨC THẤP (Phase 6 & 7 — Hoàn thiện nâng cao)
9. **Xây dựng tính năng Điều khiển Giọng nói hoàn chỉnh (`F-VOI`):**
   - *Mô tả:* Khắc phục lỗi singleton STT (I29), hoàn thiện `IntentParser` (I30), gắn cổng Premium và quyền `RECORD_AUDIO`.
   - *Độ khó:* Rất khó (XL).
   - *File cần sửa:* Thư mục `app/.../domain/voice/` và `features/remote/`.
10. **Tích hợp Firebase Auth thật và cấu hình Release (`F-AUTH`, `F-REL`):**
    - *Mô tả:* Thay thế `NoOpAuthRepository` bằng Firebase Auth thật (Google + Email), cấu hình `buildTypes` (R8, ProGuard, ký APK).
    - *Độ khó:* Khó (L).
    - *File cần sửa:* `app/build.gradle.kts`, các module repository liên quan.

---

## 4. CÁC ĐIỂM MƠ HỒ HOẶC MÂU THUẪN TRONG TÀI LIỆU CẦN QUYẾT ĐỊNH

1. **Ý nghĩa chính xác của nút Headphone (`F-REM-14`, `D-Headphone-1`):** Tài liệu đề cập 3 cách hiểu (A: Nghe riêng âm thanh TV truyền qua điện thoại; B: Chuyển đầu ra âm thanh TV sang tai nghe Bluetooth; C: Phím tắt mở cài đặt âm thanh trên TV). *Đề xuất:* Tạm ẩn nút Headphone ở bản đầu cho đến khi có spike kỹ thuật cụ thể.
2. **Phương thức gửi chữ của bàn phím (`F-REM-10`):** Code hiện tại gửi từng ký tự ngay lập tức (`Text(delta)`), gây lỗi với tiếng Việt có dấu (Telex/VNI). *Đề xuất:* Chuyển sang cơ chế soạn thảo toàn bộ chuỗi rồi bấm nút "Gửi" (Submit).
3. **Phạm vi hiển thị Channels và Cast cho người dùng Free:** Channels đã được chốt là miễn phí (`isChannelsFeatureFree = true`), nhưng một số đoạn code trong UseCase vẫn còn sót logic kiểm tra Premium cũ cần được đồng bộ dứt điểm.
4. **Mức độ xác thực Google Cast (I34):** Tài liệu kỹ thuật ghi "verified", nhưng thực tế Device Auth chưa được thực thi đầy đủ và dùng chung chứng chỉ ghim của cổng điều khiển (6466/6467) cho cổng Cast (8009). Cần quyết định tách riêng chứng chỉ trước khi phát hành tính năng Cast.

---

## 5. ĐỀ XUẤT THỨ TỰ LÀM TIẾP (10 VIỆC ĐẦU TIÊN)

1. **Việc 1:** Sửa ngay lỗi build B17 (`F-BASE-07`: bổ sung chuỗi `remote_is_locked` vào `values-vi/strings.xml` và chuyển 3 chuỗi cứng trong `DeviceFoundScreen.kt` vào tài nguyên).
2. **Việc 2:** Chạy lại toàn bộ bộ lệnh build, lint và unit test cục bộ (`./gradlew lintDebug assembleDebug testDebugUnitTest`) để xác minh trạng thái source sạch lỗi.
3. **Việc 3:** Dọn dẹp file rác (`update_settings.patch`, chuẩn hóa CRLF) và cấu hình Remote Git / CI xanh.
4. **Việc 4:** Sửa lỗi nút nguồn (`I27`: bỏ lớp phủ che nút cam) và tách biệt rõ logic `PowerOn`/`PowerOff`/`Power` đảo trạng thái (`I28`).
5. **Việc 5:** Cập nhật Google Play Billing (`F-PRE`): bổ sung gói `lifetime` (`INAPP`), cập nhật 4 quyền lợi mới trên màn Premium, đưa thông báo lỗi billing vào `strings.xml`.
6. **Việc 6:** Nối `SwipeToDpadMapper` vào kiểm thử và thêm thông báo Toast cho `RemoteUiEvent.CommandFailed` khi lệnh thất bại (ngay cả khi tắt Haptic).
7. **Việc 7:** Thay khối 8 nút phụ giả bằng hàng 3 nút phụ mới (`F-REM-12`: Keyboard, Mic, Headphone) và gỡ bỏ hoàn toàn các Toast "sắp ra mắt".
8. **Việc 8:** Cập nhật cơ chế `KeyboardModal` sang dạng "gõ xong bấm Gửi" để hỗ trợ tốt tiếng Việt.
9. **Việc 9:** Nối Backend Cast (`ICastController`) đã có trong `:tv:runtime` vào `CastViewModel` và áp dụng `FeatureGate` Premium.
10. **Việc 10:** Tiến hành biên bản nghiệm thu thực tế trên thiết bị phần cứng TV thật (Samsung / Roku / LG / Android TV) để đóng Milestone M3.

---
*Báo cáo được lập tự động theo yêu cầu kiểm tra dự án SmartClick. Không có file code nào bị sửa đổi trong quá trình phân tích này.*
