import re

strings = """
    <string name="login_welcome_back">Welcome Back</string>
    <string name="login_forgot_password">Forgot password?</string>
    <string name="login_or">OR</string>
    <string name="login_dont_have_account">Don\'t have an account?</string>
    <string name="sign_up">Sign Up</string>
    
    <string name="restore_purchase_terms">Restore purchase · Terms &amp; Privacy</string>
    <string name="failed_to_load_plans">Failed to load plans. Tap to retry.</string>
    <string name="perk_unlimited_devices">Unlimited devices</string>
    <string name="perk_channels_library">Channels library</string>
    <string name="perk_priority_support">Priority support</string>
    
    <string name="welcome_title">Control Your TV\nEffortlessly</string>
    
    <string name="manage_your_profile">Manage your profile</string>
    <string name="data_and_privacy">Data &amp; privacy</string>
    <string name="legal_information">Legal information</string>
    <string name="system_default">System Default</string>
    <string name="system">System</string>
    <string name="on_text">On</string>
    <string name="off_text">Off</string>
    <string name="user_name_default">User</string>
    <string name="user_email_default">user@example.com</string>
    <string name="app_version_info">SmartClick v2.0.0 · © 2026</string>
    
    <string name="dir_up">Up</string>
    <string name="dir_down">Down</string>
    <string name="dir_left">Left</string>
    <string name="dir_right">Right</string>
    <string name="ok_btn">OK</string>
"""

with open("app/src/main/res/values/strings.xml", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("</resources>", strings + "\n</resources>")

with open("app/src/main/res/values/strings.xml", "w", encoding="utf-8") as f:
    f.write(content)
