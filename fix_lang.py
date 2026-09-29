path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\LanguageScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    orig = f.read()
    
c = orig
c = c.replace('"Choose Language"', 'stringResource(R.string.choose_language)')
c = c.replace('"Select your preferred language"', 'stringResource(R.string.choose_language_desc)')
c = c.replace('"Continue"', 'stringResource(R.string.continue_btn)')

if orig != c:
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)
