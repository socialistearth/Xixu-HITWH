#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
news_signing_dir="${1:?Provide the absolute path to the private signing backup directory}"
news_output_apk="${2:?Provide an absolute APK output path}"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$news_signing_dir/Xixu-news-release.p12" --ks-key-alias xixu-news --ks-pass "file:$news_signing_dir/password.txt" --out "$news_output_apk" app/build/outputs/apk/release/app-release-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$news_output_apk"
