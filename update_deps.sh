#!/bin/bash
cat << 'TOML' >> D:/SmartClick/gradle/libs.versions.toml
room = "2.6.1"
datastore = "1.1.1"

[libraries_append]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
TOML
