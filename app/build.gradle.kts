plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.smartclick"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartclick"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// E1: cá»•ng cháº·n chuá»—i tiáº¿ng Anh hardcode trong UI Compose.
// QuÃ©t cÃ¡c Text("...") / label = "..." / contentDescription = "..." cÃ²n Ä‘á»ƒ literal.
tasks.register("checkHardcodedStrings") {
    group = "verification"
    description = "Fails the build if hardcoded UI string literals remain in Compose screens (E1)."

    val uiDir = file("src/main/java/com/example/smartclick/app")
    doLast {
        // Báº¯t: Text("Chá»¯ cÃ³ chá»¯ cÃ¡i"), contentDescription = "..."
        // LÆ°u Ã½: KHÃ”NG quÃ©t `label = "..."` vÃ¬ Ä‘Ã³ lÃ  label cá»§a animation inspector
        // (rememberInfiniteTransition / animateFloat), khÃ´ng pháº£i text hiá»ƒn thá»‹.
        // Label tháº­t cá»§a Material3 lÃ  `label = { Text("...") }` -> Ä‘Ã£ bá»‹ pattern Text(...) báº¯t.
        val patterns = listOf(
            Regex("""Text\(\s*"[^"]*[A-Za-z]{2,}[^"]*"""),
            Regex("""contentDescription\s*=\s*"[^"]*[A-Za-z]{2,}[^"]*""""),
            Regex("""Toast\.makeText\([^,]+,\s*"[^"]*[A-Za-z]{2,}[^"]*""""),
            Regex("""title\s*=\s*"[^"]*[A-Za-z]{2,}[^"]*""""),
            Regex("""placeholder\s*=\s*\{\s*Text\(\s*"[^"]*[A-Za-z]{2,}[^"]*"""")
        )
        // Cho phÃ©p: emoji/kÃ½ tá»± Ä‘Æ¡n, vÃ  cÃ¡c chuá»—i ká»¹ thuáº­t Ä‘Ã£ whitelisted
        val allow = Regex("""Text\(\s*"[^A-Za-z]*"""")
        val offenders = mutableListOf<String>()

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { idx, line ->
                    if (line.trimStart().startsWith("//")) return@forEachIndexed
                    if (allow.containsMatchIn(line)) return@forEachIndexed
                    if (patterns.any { it.containsMatchIn(line) }) {
                        offenders += "${f.relativeTo(projectDir)}:${idx + 1}: ${line.trim()}"
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "E1: Found ${offenders.size} hardcoded UI string(s). Move them to strings.xml:\n" +
                    offenders.joinToString("\n")
            )
        }
        println("E1 OK: no hardcoded UI strings found.")
    }
}

dependencies {
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.okhttp3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.billing)
    implementation(libs.androidx.security.crypto)
    implementation(libs.protobuf.lite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
