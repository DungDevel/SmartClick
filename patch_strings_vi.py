import re

strings = """
    <string name="login_welcome_back">Chào mừng trở lại</string>
    <string name="login_forgot_password">Quên mật khẩu?</string>
    <string name="login_or">HOẶC</string>
    <string name="login_dont_have_account">Chưa có tài khoản?</string>
    <string name="sign_up">Đăng ký</string>
    
    <string name="restore_purchase_terms">Khôi phục mua hàng · Điều khoản &amp; Bảo mật</string>
    <string name="failed_to_load_plans">Không tải được gói. Chạm để thử lại.</string>
    <string name="perk_unlimited_devices">Không giới hạn thiết bị</string>
    <string name="perk_channels_library">Thư viện Kênh</string>
    <string name="perk_priority_support">Hỗ trợ ưu tiên</string>
    
    <string name="welcome_title">Điều khiển TV của bạn\nmột cách dễ dàng</string>
    
    <string name="manage_your_profile">Quản lý hồ sơ của bạn</string>
    <string name="data_and_privacy">Dữ liệu &amp; quyền riêng tư</string>
    <string name="legal_information">Thông tin pháp lý</string>
    <string name="system_default">Mặc định hệ thống</string>
    <string name="system">Hệ thống</string>
    <string name="on_text">Bật</string>
    <string name="off_text">Tắt</string>
    <string name="user_name_default">Người dùng</string>
    <string name="user_email_default">user@example.com</string>
    <string name="app_version_info">SmartClick v2.0.0 · © 2026</string>
    
    <string name="dir_up">Lên</string>
    <string name="dir_down">Xuống</string>
    <string name="dir_left">Trái</string>
    <string name="dir_right">Phải</string>
    <string name="ok_btn">OK</string>
"""

with open("app/src/main/res/values-vi/strings.xml", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("</resources>", strings + "\n</resources>")

with open("app/src/main/res/values-vi/strings.xml", "w", encoding="utf-8") as f:
    f.write(content)
