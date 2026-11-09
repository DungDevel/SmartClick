plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.smartclick.tv.testing"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:tv-api"))
    api(libs.junit)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.json)
    api("org.bouncycastle:bcprov-jdk15to18:1.77")
    api("org.bouncycastle:bcpkix-jdk15to18:1.77")
    api("com.squareup.okhttp3:mockwebserver:4.12.0")
}
