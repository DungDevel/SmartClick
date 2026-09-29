with open("app/src/main/java/com/example/smartclick/app/features/settings/SettingsMainScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace('"User"', 'stringResource(R.string.user_name_default)')
text = text.replace('"user@example.com"', 'stringResource(R.string.user_email_default)')
text = text.replace('"Manage your profile"', 'stringResource(R.string.manage_your_profile)')
text = text.replace('"System Default"', 'stringResource(R.string.system_default)')
text = text.replace('"Data & privacy"', 'stringResource(R.string.data_and_privacy)')
text = text.replace('"Legal information"', 'stringResource(R.string.legal_information)')
text = text.replace('"System"', 'stringResource(R.string.system)')
text = text.replace('"On"', 'stringResource(R.string.on_text)')
text = text.replace('"Off"', 'stringResource(R.string.off_text)')
text = text.replace('"SmartClick v2.0.0 · © 2026"', 'stringResource(R.string.app_version_info)')

with open("app/src/main/java/com/example/smartclick/app/features/settings/SettingsMainScreen.kt", "w", encoding="utf-8") as f:
    f.write(text)
