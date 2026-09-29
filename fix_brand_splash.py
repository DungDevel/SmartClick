def add_imports(content):
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = content.replace("import com.example.smartclick.R", "import androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")
    return content

path_brand = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\BrandScreen.kt"
with open(path_brand, "r", encoding="utf-8") as f:
    c_brand = f.read()

c_brand = c_brand.replace('"Select TV Brand"', 'stringResource(R.string.select_tv_brand)')
c_brand = c_brand.replace('"What brand is your TV?"', 'stringResource(R.string.what_brand_tv)')
c_brand = c_brand.replace('"Connect TV"', 'stringResource(R.string.connect_tv)')
c_brand = c_brand.replace('"Skip"', 'stringResource(R.string.skip)')
# add imports if needed, it may not have com.example.smartclick.R
if "import com.example.smartclick.R" not in c_brand:
    c_brand = c_brand.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")
with open(path_brand, "w", encoding="utf-8") as f:
    f.write(c_brand)

path_splash = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\SplashScreen.kt"
with open(path_splash, "r", encoding="utf-8") as f:
    c_splash = f.read()
c_splash = c_splash.replace('"SmartClick"', 'stringResource(R.string.app_name)')
c_splash = c_splash.replace('"Universal Smart TV Remote"', 'stringResource(R.string.universal_remote_subtitle)')
if "import com.example.smartclick.R" not in c_splash:
    c_splash = c_splash.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.res.stringResource\nimport com.example.smartclick.R")
with open(path_splash, "w", encoding="utf-8") as f:
    f.write(c_splash)
