import re

filepath = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\core\ui\components\ReconnectBanner.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace(
    '"Connection lost. Reconnecting... (Attempt $attempt)"',
    'stringResource(R.string.connection_lost_reconnecting, attempt)'
)

def add_imports(content):
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = content.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")
    return content

content = add_imports(content)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
