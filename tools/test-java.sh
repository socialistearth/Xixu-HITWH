#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/offline-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/offline-tests app/src/main/java/edu/hitwh/fieldnote/ScheduleMath.java tests/ScheduleMathTest.java
java -cp build/offline-tests edu.hitwh.fieldnote.ScheduleMathTest
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/offline-tests tests/JavaSyntaxCheck.java
java -cp build/offline-tests JavaSyntaxCheck
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/offline-tests app/src/main/java/edu/hitwh/fieldnote/LaunchGate.java tests/LaunchGateTest.java
java -cp build/offline-tests edu.hitwh.fieldnote.LaunchGateTest
java --add-modules jdk.compiler com.sun.tools.javac.Main -d build/offline-tests app/src/main/java/edu/hitwh/fieldnote/AutoSyncPolicy.java tests/AutoSyncPolicyTest.java
java -cp build/offline-tests edu.hitwh.fieldnote.AutoSyncPolicyTest
