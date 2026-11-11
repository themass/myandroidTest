#!/bin/bash
# 构建设备端 HTTP 检查器（给没有 curl/nc 的设备用）。
# 用法：./build.sh && adb push classes.dex /data/local/tmp/httpcheck.dex
set -e
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)"
cd "$(dirname "$0")"
javac --release 8 -cp "$PLATFORM/android.jar" HttpCheck.java 2>/dev/null
"$BUILD_TOOLS/d8" --min-api 26 --output . HttpCheck.class
echo "built: $(pwd)/classes.dex"
