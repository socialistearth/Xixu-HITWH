#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
news_json_jar="${1:?Provide the path to org.json json-20240303.jar}"
mkdir -p build/offline-cache-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp "$news_json_jar" -d build/offline-cache-tests app/src/main/java/edu/hitwh/fieldnote/QueryCache.java tests/QueryCacheTest.java
java -cp "build/offline-cache-tests:$news_json_jar" edu.hitwh.fieldnote.QueryCacheTest
