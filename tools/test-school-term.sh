#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
classes=app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
mkdir -p build/offline-tests
javac -cp "$classes:$ANDROID_HOME/platforms/android-35/android.jar" -d build/offline-tests tests/SchoolTermTest.java
java -cp "build/offline-tests:$classes:$ANDROID_HOME/platforms/android-35/android.jar" edu.hitwh.fieldnote.SchoolTermTest
