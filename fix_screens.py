import re

def add_imports(content):
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = content.replace("import com.example.smartclick.R", "import androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")
    return content

# MainApp.kt
path_main = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\main\MainApp.kt"
with open(path_main, "r", encoding="utf-8") as f:
    c_main = f.read()

c_main = c_main.replace("sealed class AppTab(val title: String, val icon: Int)", "sealed class AppTab(val titleResId: Int, val icon: Int)")
c_main = c_main.replace('object Remote : AppTab("Remote", R.drawable.lists)', 'object Remote : AppTab(R.string.tab_remote, R.drawable.lists)')
c_main = c_main.replace('object Channels : AppTab("Channels", R.drawable.application)', 'object Channels : AppTab(R.string.tab_channels, R.drawable.application)')
c_main = c_main.replace('object Cast : AppTab("Cast", R.drawable.upload)', 'object Cast : AppTab(R.string.tab_cast, R.drawable.upload)')
c_main = c_main.replace('object Settings : AppTab("Settings", R.drawable.settings)', 'object Settings : AppTab(R.string.tab_settings, R.drawable.settings)')

c_main = c_main.replace('contentDescription = tab.title', 'contentDescription = stringResource(tab.titleResId)')
c_main = c_main.replace('text = tab.title', 'text = stringResource(tab.titleResId)')
c_main = add_imports(c_main)

with open(path_main, "w", encoding="utf-8") as f:
    f.write(c_main)

# WelcomeScreen.kt
path_welcome = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\WelcomeScreen.kt"
with open(path_welcome, "r", encoding="utf-8") as f:
    c_welcome = f.read()

c_welcome = c_welcome.replace('"Control Your TV\nEffortlessly"', 'stringResource(R.string.control_tv_title)')
c_welcome = c_welcome.replace('"SmartClick turns your phone into a powerful universal remote for any smart TV."', 'stringResource(R.string.control_tv_desc)')
c_welcome = c_welcome.replace('"Get Started"', 'stringResource(R.string.get_started)')
c_welcome = c_welcome.replace('"Universal Control"', 'stringResource(R.string.universal_control)')
c_welcome = c_welcome.replace('"Works with 500+ TV brands"', 'stringResource(R.string.works_with_brands)')
c_welcome = c_welcome.replace('"Voice Commands"', 'stringResource(R.string.voice_commands)')
c_welcome = c_welcome.replace('"Hands-free navigation"', 'stringResource(R.string.hands_free_nav)')
c_welcome = c_welcome.replace('"Cast Media"', 'stringResource(R.string.cast_media)')
c_welcome = c_welcome.replace('"Share photos & videos instantly"', 'stringResource(R.string.share_media)')

# For the generic feature list, FeatureItem needs string parsing or we should just pass stringResources
c_welcome = c_welcome.replace('FeatureItem("ðŸŽ¯", "Universal Control", "Works with 500+ TV brands"),', 'FeatureItem("ðŸŽ¯", stringResource(R.string.universal_control), stringResource(R.string.works_with_brands)),')
c_welcome = c_welcome.replace('FeatureItem("ðŸŽ™ï¸ ", "Voice Commands", "Hands-free navigation"),', 'FeatureItem("ðŸŽ™ï¸ ", stringResource(R.string.voice_commands), stringResource(R.string.hands_free_nav)),')
c_welcome = c_welcome.replace('FeatureItem("ðŸ“¸", "Cast Media", "Share photos & videos instantly")', 'FeatureItem("ðŸ“¸", stringResource(R.string.cast_media), stringResource(R.string.share_media))')

c_welcome = add_imports(c_welcome)

with open(path_welcome, "w", encoding="utf-8") as f:
    f.write(c_welcome)

# ScanningScreen.kt
path_scanning = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\ScanningScreen.kt"
with open(path_scanning, "r", encoding="utf-8") as f:
    c_scanning = f.read()

c_scanning = c_scanning.replace('"Scanning Network"', 'stringResource(R.string.scanning_network)')
c_scanning = c_scanning.replace('"Looking for smart TVs..."', 'stringResource(R.string.looking_for_tvs)')
c_scanning = c_scanning.replace('"TV not found? Try manually"', 'stringResource(R.string.tv_not_found_try_manually)')

c_scanning = add_imports(c_scanning)

with open(path_scanning, "w", encoding="utf-8") as f:
    f.write(c_scanning)
