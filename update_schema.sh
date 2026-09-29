#!/bin/bash
node -e "
const fs = require('fs');
let bg = fs.readFileSync('D:/SmartClick/app/build.gradle.kts', 'utf8');

if (!bg.includes('room.schemaLocation')) {
    bg = bg.replace('kapt {', 'kapt {\n    arguments {\n        arg(\"room.schemaLocation\", \"\$projectDir/schemas\")\n    }\n}');
    if (!bg.includes('arguments {')) {
        bg = bg.replace('dependencies {', 'kapt {\n    arguments {\n        arg(\"room.schemaLocation\", \"\$projectDir/schemas\")\n    }\n}\n\ndependencies {');
    }
}
fs.writeFileSync('D:/SmartClick/app/build.gradle.kts', bg);
"
