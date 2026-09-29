with open("app/src/main/java/com/example/smartclick/app/features/onboarding/DeviceFoundScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace('contentDescription = "Remove"', 'contentDescription = stringResource(R.string.remove)')
text = text.replace('contentDescription = "Disconnect"', 'contentDescription = stringResource(R.string.disconnect)')

if "import androidx.compose.ui.res.stringResource" not in text:
    text = text.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")

with open("app/src/main/java/com/example/smartclick/app/features/onboarding/DeviceFoundScreen.kt", "w", encoding="utf-8") as f:
    f.write(text)
