with open("gradle/libs.versions.toml", "r") as f:
    text = f.read()

text = text.replace('json = { group = "org.json", name = "json", version = "20240303" }', 'json = { group = "org.json", name = "json", version = "20240303" }\nplay-billing = { group = "com.android.billingclient", name = "billing-ktx", version = "7.0.0" }')

with open("gradle/libs.versions.toml", "w") as f:
    f.write(text)

with open("app/build.gradle.kts", "r") as f:
    text = f.read()

text = text.replace('implementation(libs.androidx.core.splashscreen)', 'implementation(libs.androidx.core.splashscreen)\n    implementation(libs.play.billing)')

with open("app/build.gradle.kts", "w") as f:
    f.write(text)
