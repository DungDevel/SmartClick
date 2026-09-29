path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\DeviceFoundScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace('snackbarHostState.showSnackbar("Failed to connect: ${error.message}")', 'snackbarHostState.showSnackbar(context.getString(R.string.failed_to_connect, error.message))')
if "context.getString" in c and "val context = LocalContext.current" not in c:
    c = c.replace('val colors = SmartClickThemeTokens.colors', 'val colors = SmartClickThemeTokens.colors\n    val context = androidx.compose.ui.platform.LocalContext.current')

with open(path, "w", encoding="utf-8") as f:
    f.write(c)
