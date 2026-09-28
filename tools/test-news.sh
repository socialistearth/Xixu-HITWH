#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
news_json_jar="${1:?Provide the path to org.json json-20240303.jar}"
news_fixture="${2:-tests/fixtures/inbox-contract.json}"
mkdir -p build/offline-news-tests
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp "$news_json_jar" -d build/offline-news-tests app/src/main/java/edu/hitwh/fieldnote/NewsData.java tests/NewsDataTest.java
java -cp "build/offline-news-tests:$news_json_jar" edu.hitwh.fieldnote.NewsDataTest "$news_fixture" "${3:-tests/fixtures/manual-contract.json}"
