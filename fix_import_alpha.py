import sys

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if line.startswith("import alpha"):
        lines[i] = "import androidx.compose.ui.draw.alpha\n"

with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "w", encoding="utf-8") as f:
    f.writelines(lines)
