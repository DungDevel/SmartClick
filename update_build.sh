#!/bin/bash
node -e "
const fs = require('fs');
let bg = fs.readFileSync('D:/SmartClick/app/build.gradle.kts', 'utf8');

const depsToAdd = `
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
`;

bg = bg.replace('    testImplementation(libs.junit)', depsToAdd + '\n    testImplementation(libs.junit)');
fs.writeFileSync('D:/SmartClick/app/build.gradle.kts', bg);
"
