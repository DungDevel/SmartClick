#!/bin/bash
# Insert protobuf plugin at top
sed -i '/id("kotlin-kapt")/i \    alias(libs.plugins.protobuf)' app/build.gradle.kts
# Add protobuf lite dependency
sed -i '/implementation(libs.androidx.security.crypto)/a \    implementation(libs.protobuf.lite)' app/build.gradle.kts

# Append protobuf block
cat << 'BLOCK' >> app/build.gradle.kts

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
BLOCK
