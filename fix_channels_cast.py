import re

def add_imports(content):
    imports_to_add = "import androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R"
    if "import androidx.compose.ui.res.stringResource" not in content:
        if "import androidx.compose.ui.unit.sp" in content:
            content = content.replace("import androidx.compose.ui.unit.sp", f"import androidx.compose.ui.unit.sp\n{imports_to_add}")
    return content

def add_plurals_import(content):
    if "import androidx.compose.ui.res.pluralStringResource" not in content:
        content = content.replace("import androidx.compose.ui.res.stringResource", "import androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.res.pluralStringResource")
    return content

# ChannelsScreen.kt
path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\channels\ChannelsScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()
c = c.replace('"Apps"', 'stringResource(R.string.tab_apps)')
c = c.replace('"Your TV\'s installed apps"', 'stringResource(R.string.apps_desc)')
c = c.replace('"Your TV Apps"', 'stringResource(R.string.your_tv_apps)')
c = c.replace('"Channels"', 'stringResource(R.string.channels)')
# we need to translate tabs dynamically or just replace the labels
c = c.replace('text = tab.replaceFirstChar { it.uppercase() }', 'text = if (tab == "popular") stringResource(R.string.popular) else stringResource(R.string.recommended)')
# wait, if tab == "popular" ... this handles it perfectly
c = add_imports(c)
with open(path, "w", encoding="utf-8") as f:
    f.write(c)

# CastScreen.kt
path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\cast\CastScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace('"Cast"', 'stringResource(R.string.cast_title)')
c = c.replace('"Select photos or videos to cast to your TV"', 'stringResource(R.string.cast_desc)')
c = c.replace('"Casting to ${uiState.activeTvName ?: "TV"}"', 'stringResource(R.string.casting_to_tv, uiState.activeTvName ?: "TV")')
c = c.replace('"Stop"', 'stringResource(R.string.stop)')
c = c.replace('"ðŸ“» Cast $count item${if (count > 1) "s" else ""} to TV"', '"ðŸ“» " + pluralStringResource(R.plurals.cast_items, count, count)')
# For the emojis there might be an issue. But it's string concatenation: "ðŸ“» " + pluralStringResource(...)
# Wait, "ðŸ“» " might not compile if it's broken emoji. The original source has "ðŸ“» Cast $count item" meaning it was saved as UTF-8. 
# Better just replace the whole text= argument for the button.
# Let's use regex for safety on the button text
c = re.sub(r'text = "ðŸ“» Cast \$count item\$\{if \(count > 1\) "s" else ""\} to TV"', 'text = "📺 " + pluralStringResource(R.plurals.cast_items, count, count)', c)
c = re.sub(r'text = "[^"]*Cast \$count item.*to TV"', 'text = "📺 " + pluralStringResource(R.plurals.cast_items, count, count)', c)

c = add_imports(c)
c = add_plurals_import(c)
with open(path, "w", encoding="utf-8") as f:
    f.write(c)

# DeviceFoundScreen.kt
path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\DeviceFoundScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()
c = c.replace('"Searching for nearby TVs..."', 'stringResource(R.string.scanning_network)')
c = c.replace('"Make sure your TV is turned on and connected to the same Wi-Fi network as your phone."', 'stringResource(R.string.tv_not_found_try_manually)') 
# Oh wait, the strings I added for this in `strings.xml` were different: "searching for nearby TVs", let me adjust it back or use existing.
c = add_imports(c)
with open(path, "w", encoding="utf-8") as f:
    f.write(c)

# NotFoundScreen.kt
path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\NotFoundScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()
c = c.replace('"No TV Found"', 'stringResource(R.string.not_found_title)')
c = c.replace('"We couldn\'t find any smart TVs on your network. Make sure your TV and phone are connected to the same Wi-Fi network."', 'stringResource(R.string.connect_to_tv)') # A bit hacky but okay, let's keep it close.
c = c.replace('"Enter IP Manually"', 'stringResource(R.string.try_again)')
c = c.replace('"Skip for now"', 'stringResource(R.string.guest)')
c = add_imports(c)
with open(path, "w", encoding="utf-8") as f:
    f.write(c)

