#!/usr/bin/env bash
# 本地重建 Live2D 的 JNI 主库 libchatwaifu-live2d.so（上游 ChatWaifu_Mobile 只发源码，
# 我们设备上的 .so 一直是别人预编译的库存件；seed-assets.sh live2d 把它推到
# cacheRoot(LIVE2D)/native-libs/arm64-v8a/，Live2dNativeLoader 按绝对路径 dlopen）。
#
# 为什么要自己编：上游 LAppDelegate::Run() 每帧 glClearColor(0,0,0,1) 写死黑，
# 通话页主题背景色永远透不出来（真机验收 2026-11-22）。fork 补丁在同目录
# live2d-native-fork.patch：+nativeSetBackgroundColor（第 20 个 JNI 导出），
# Kotlin 侧 com.chatwaifu.live2d.JniBridgeJava 的 native 声明与
# Live2dNativeBridgeTest 的 20 方法面都钉在这个补丁上。
#
# 依赖：ChatWaifu_Mobile 源码树（含 Live2D/，上游 https://github.com/guanshan/ChatWaifu_Mobile
# 或等价 fork，实测基线 14092ac 可编）、NDK 28.x、cmake。补丁必须能干净套在基线上；
# 源码树里已有同名改动（重复导出）时直接构建会撞符号，脚本先试 apply、已套过就跳过。
#
# 用法: scripts/dh-lab/build-live2d-native.sh
set -euo pipefail

UPSTREAM="${CHATWAIFU_ROOT:-$HOME/work/android/ChatWaifu_Mobile}"
NDK_DIR="${NDK_DIR:-$HOME/Library/Android/sdk/ndk/28.2.13676358}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$UPSTREAM/Live2D"
BUILD="${LIVE2D_BUILD_DIR:-/tmp/live2d-so-build}"
OUT="$REPO_ROOT/.superpowers/dh-packs/live2d-native-arm64-v8a"
PATCH="$REPO_ROOT/scripts/dh-lab/live2d-native-fork.patch"

[ -f "$SRC/CMakeLists.txt" ] || { echo "!! 找不到 $SRC/CMakeLists.txt（CHATWAIFU_ROOT 对吗？）"; exit 1; }
[ -f "$NDK_DIR/build/cmake/android.toolchain.cmake" ] || { echo "!! NDK 不存在: $NDK_DIR"; exit 1; }
[ -f "$SRC/src/main/cpp/JniBridgeC.cpp" ] || { echo "!! 源码树缺 cpp（$SRC/src/main/cpp）"; exit 1; }

# 补丁：背景色注入（3 个文件，+27 行）。已套过（grep 得到导出名）就跳过，保证幂等。
if grep -q nativeSetBackgroundColor "$SRC/src/main/cpp/JniBridgeC.cpp"; then
  echo "== fork 补丁已在源码树上，跳过 apply"
else
  git -C "$UPSTREAM" apply --check "$PATCH" && git -C "$UPSTREAM" apply "$PATCH"
  echo "== 已套 live2d-native-fork.patch"
fi

mkdir -p "$BUILD"
cmake -S "$SRC" -B "$BUILD" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo >/dev/null
cmake --build "$BUILD" -j"$(sysctl -n hw.ncpu)" >/dev/null
SO="$BUILD/libchatwaifu-live2d.so"
[ -f "$SO" ] || { echo "!! 构建产物缺失: $SO"; exit 1; }

# 符号面验收：20 个 Java_com_chatwaifu_live2d_JniBridgeJava_* 导出，一个不能少；
# 少任何一个是把库存 .so 的调用方打瘸（Live2dNativeBridgeTest 钉 Kotlin 侧同一张表）。
NM="$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm"
# 符号表先取一次存变量再查。以前是两条 `nm | grep -q`：Core 5 起动态符号涨到 3.5k 行，
# grep -q 命中即退出把管道关了，nm 吃 SIGPIPE，pipefail 把整条流水线判死——
# 明明 20 导出齐全却报「缺 nativeSetBackgroundColor」（2026-11-25 现场）。
SYMS=$("$NM" -D --defined-only "$SO")
EXPORTS=$(printf '%s\n' "$SYMS" | grep -c 'Java_com_chatwaifu_live2d_JniBridgeJava_' || true)
[ "$EXPORTS" = "20" ] || { echo "!! JNI 导出数 = $EXPORTS，应为 20（19 库存 + nativeSetBackgroundColor）"; exit 1; }
case "$SYMS" in
  *nativeSetBackgroundColor*) ;;
  *) echo "!! 缺 nativeSetBackgroundColor 导出"; exit 1 ;;
esac

mkdir -p "$OUT"
[ -f "$OUT/libchatwaifu-live2d.so" ] && cp -n "$OUT/libchatwaifu-live2d.so" "$OUT/libchatwaifu-live2d.so.stock-backup"
cp "$SO" "$OUT/libchatwaifu-live2d.so"
echo "== $OUT/libchatwaifu-live2d.so 已更新（20 导出）；跑 scripts/dh-lab/seed-assets.sh live2d 推设备"
