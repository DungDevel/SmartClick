import sys

file_path = "app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

output = []
for i, line in enumerate(lines):
    if "if (activeDevice == null && !isReconnecting) {" in line:
        output.append(line)
    elif "NoActiveDeviceState(onNavigateToDevices = onNavigateToDevices)" in line:
        output.append(line)
        output.append("        } else {\n")
        
        # We need to surround the inner part with Box
        output.append("            val context = androidx.compose.ui.platform.LocalContext.current\n")
        output.append("            Box(modifier = Modifier.fillMaxSize()) {\n")
        output.append("                Column(modifier = Modifier.fillMaxSize().let { if (!controlsEnabled) it.alpha(0.5f) else it }) {\n")
        break
    else:
        output.append(line)

# find the closing brace for the else block
stack = 1
end_index = -1
in_else = False
for j in range(i+1, len(lines)):
    if "NoActiveDeviceState" in lines[j]:
        in_else = True
        continue
    
# Actually, I'll just find the last closing braces of the file and insert Box closure
with open("app/src/main/java/com/example/smartclick/app/features/remote/RemoteScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Replace using string manipulation
target = """
        if (activeDevice == null && !isReconnecting) {
            NoActiveDeviceState(onNavigateToDevices = onNavigateToDevices)
        } else {
"""

replacement = """
        if (activeDevice == null && !isReconnecting) {
            NoActiveDeviceState(onNavigateToDevices = onNavigateToDevices)
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().androidx.compose.ui.draw.alpha(if (controlsEnabled) 1f else 0.4f)) {
"""

new_content = content.replace(target, replacement)

target2 = """
        }
    }
}"""
replacement2 = """
                }
                
                if (!controlsEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                android.widget.Toast.makeText(context, "Chưa có TV đang kết nối", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }
        }
    }
}"""

new_content = new_content.replace(target2, replacement2)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(new_content)
