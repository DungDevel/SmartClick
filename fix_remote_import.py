import sys

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

if lines[0].startswith("import androidx.compose.ui.draw.alpha"):
    lines.pop(0)
    lines.insert(2, "import androidx.compose.ui.draw.alpha\n")

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "w", encoding="utf-8") as f:
    f.writelines(lines)
