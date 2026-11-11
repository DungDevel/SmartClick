# Hướng dẫn Cài đặt Môi trường & Git Worktree (SETUP.md)

Tài liệu này hướng dẫn cách thiết lập môi trường để 2 instance Claude Code (Luồng A và Luồng B) làm việc song song trên máy cục bộ.

---

## 1. TẠO CÁC NHÁNH VÀ WORKTREE GIT

Mở terminal tại thư mục gốc dự án `D:\SmartClick` và chạy các lệnh PowerShell sau:

```powershell
# 1. Đảm bảo đang ở nhánh gốc hiện tại (ví dụ premium-test hoặc main) và working tree sạch
git status

# 2. Tạo nhánh và worktree cho Luồng A
git branch feature/stream-a
git worktree add D:\SmartClick-worktree-A feature/stream-a

# 3. Tạo nhánh và worktree cho Luồng B
git branch feature/stream-b
git worktree add D:\SmartClick-worktree-B feature/stream-b
```

Sau khi tạo xong, bạn có thể khởi động 2 phiên làm việc riêng biệt:
- **Phiên A:** Làm việc trong thư mục `D:\SmartClick-worktree-A`
- **Phiên B:** Làm việc trong thư mục `D:\SmartClick-worktree-B`

---

## 2. CÁC FILE KHÔNG ĐƯỢC ĐƯA LÊN GIT (CẦN CHÉP THỦ CÔNG NẾU CẦN)

Một số file cấu hình cục bộ không nằm trong Git repository. Nếu worktree mới chưa có, hãy copy từ thư mục gốc `D:\SmartClick` sang 2 thư mục worktree (`D:\SmartClick-worktree-A` và `D:\SmartClick-worktree-B`):
- `local.properties` (chứa đường dẫn Android SDK)
- `google-services.json` (nếu có, dùng cho Firebase)
- Các file keystore (`*.jks` hoặc `*.keystore`) nếu thử nghiệm ký release.

---

## 3. TỐI ƯU HÓA GRADLE CHO MÁY ÍT RAM

Nếu máy tính của bạn có RAM hạn chế và chạy đồng thời 2 bản Gradle build, hãy cấu hình giới hạn worker trong file `gradle.properties` hoặc truyền tham số khi build:

```properties
# Thêm vào gradle.properties của dự án hoặc worktree
org.gradle.workers.max=2
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

Hoặc khi chạy test từ dòng lệnh:
```powershell
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

---

## 4. HƯỚNG DẪN GỘP NHÁNH SAU KHI HOÀN THÀNH

Khi cả Luồng A và Luồng B báo hoàn thành (`RESULT-A.md` và `RESULT-B.md`):
1. Gộp Luồng A vào nhánh chính trước:
   ```powershell
   git checkout premium-test
   git merge feature/stream-a
   ./gradlew :app:testDebugUnitTest
   ```
2. Gộp Luồng B vào sau:
   ```powershell
   git merge feature/stream-b
   # Xử lý xung đột strings.xml (thêm mới ở cuối file) nếu có
   ./gradlew :app:testDebugUnitTest
   ```
3. Xóa worktree khi không còn dùng:
   ```powershell
   git worktree remove D:\SmartClick-worktree-A
   git worktree remove D:\SmartClick-worktree-B
   ```
