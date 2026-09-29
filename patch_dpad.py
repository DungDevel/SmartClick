with open("app/src/main/java/com/example/smartclick/app/features/remote/components/DPad.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace('"Up"', 'stringResource(R.string.dir_up)')
text = text.replace('"Down"', 'stringResource(R.string.dir_down)')
text = text.replace('"Left"', 'stringResource(R.string.dir_left)')
text = text.replace('"Right"', 'stringResource(R.string.dir_right)')
text = text.replace('"OK"', 'stringResource(R.string.ok_btn)')

if "import androidx.compose.ui.res.stringResource" not in text:
    text = text.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")

with open("app/src/main/java/com/example/smartclick/app/features/remote/components/DPad.kt", "w", encoding="utf-8") as f:
    f.write(text)
