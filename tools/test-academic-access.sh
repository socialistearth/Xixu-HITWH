#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
json_jar=${1:?Pass the path to an org.json jar}
mkdir -p build/academic-access-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main \
  -cp "$json_jar:$ANDROID_HOME/platforms/android-35/android.jar" \
  -d build/academic-access-tests \
  app/src/main/java/edu/hitwh/fieldnote/Vault.java \
  app/src/main/java/edu/hitwh/fieldnote/SchoolPages.java \
  app/src/main/java/edu/hitwh/fieldnote/AcademicSyncAccess.java \
  tests/AcademicSyncAccessTest.java
java -cp "build/academic-access-tests:$json_jar" edu.hitwh.fieldnote.AcademicSyncAccessTest
node --test tests/academic-access.test.cjs
