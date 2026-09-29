import re

with open("app/src/main/java/com/example/smartclick/app/features/settings/UpdateToPremiumScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

# Replace predefined list with one inside Composable
text = text.replace("""private val PREMIUM_PERKS = listOf(
    "Unlimited devices",       // BR-01: Free chỉ 1 thiết bị
    "Channels library",        // BR-07: Channels có thể yêu cầu Premium

    "Priority support"
)""", "")

text = text.replace("val colors = SmartClickThemeTokens.colors", """val colors = SmartClickThemeTokens.colors
    val premiumPerks = listOf(
        stringResource(R.string.perk_unlimited_devices),
        stringResource(R.string.perk_channels_library),
        stringResource(R.string.perk_priority_support)
    )""")

text = text.replace("PREMIUM_PERKS.forEach", "premiumPerks.forEach")
text = text.replace('"Restore purchase · Terms & Privacy"', 'stringResource(R.string.restore_purchase_terms)')
text = text.replace('"Failed to load plans. Tap to retry."', 'stringResource(R.string.failed_to_load_plans)')

with open("app/src/main/java/com/example/smartclick/app/features/settings/UpdateToPremiumScreen.kt", "w", encoding="utf-8") as f:
    f.write(text)
