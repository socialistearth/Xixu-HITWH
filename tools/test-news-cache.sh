#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
news_cache_json_jar="${1:?Provide the path to org.json json-20240303.jar}"
mkdir -p build/offline-news-cache-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp "$news_cache_json_jar" -d build/offline-news-cache-tests app/src/main/java/edu/hitwh/fieldnote/NewsCache.java tests/NewsCacheTest.java
java -cp "build/offline-news-cache-tests:$news_cache_json_jar" edu.hitwh.fieldnote.NewsCacheTest
