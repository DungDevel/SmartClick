# Kết quả công việc Luồng A

## Việc A2: Chuyển / Xoá thiết bị (F-DEV-02)

- **Trạng thái:** Hoàn thành.
- **Nội dung:** 
  - Đã thêm `SwitchActiveDeviceUseCase` vào `DevicesViewModel`.
  - Đã implement `fun switchDevice(device: TvDevice)` trong `DevicesViewModel`.
  - Đã cập nhật `DevicesViewModelTest` để mock UseCase mới.
- **Kết quả test:** `./gradlew.bat :app:testDebugUnitTest` -> BUILD SUCCESSFUL.

## Việc A3: Tinh chỉnh ReconnectWatcher & Lưu địa chỉ MAC cho WoL (F-DEV-03, F-DEV-05)

- **Trạng thái:** Hoàn thành.
- **Nội dung:** 
  - Đã implement cơ chế "Tắt chủ ý" (`setIntentionalPowerOff`) trong `ReconnectWatcher` và kết nối với `RemoteViewModel` để ngăn chặn vòng lặp kết nối lại khi người dùng chủ ý tắt TV.
  - Đã rà soát tất cả Driver chính (Roku, LG, Samsung, Android TV) và xác nhận logic trích xuất `macAddress` đã được triển khai đầy đủ trong hàm `fingerprint()`.
- **Kết quả test:** `./gradlew.bat :app:testDebugUnitTest` -> BUILD SUCCESSFUL.

## Ghi chú về các việc còn lại (A1, A4, A5)

- **Trạng thái:** Hoãn có chủ ý theo yêu cầu của người dùng.
- **Kế hoạch:** Sẽ thực hiện cuối cùng sau khi Luồng B hoàn tất và đã gộp vào nhánh chính.
- **Thứ tự thực hiện lại:** 
  1. **Việc A4**: Mở rộng Google Play Billing & FeatureGate (Lưu ý: Kiểm tra lại `FeatureGate` sau khi Việc B6 - Cast đã được gộp).
  2. **Việc A1**: Dọn repo & Kiểm tra lỗi build.
  3. **Việc A5**: Cấu hình Phát hành Release, R8 & ProGuard (Thực hiện cuối cùng).
