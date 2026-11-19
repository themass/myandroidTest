#!/usr/bin/env bash
# 本地重建 Tao 的 JNI 主库 libtaoavatar.so（上游只发 Java/AAR，不发这个 .so），
# 并把 seed-assets.sh tao 需要的六件套归位到 .superpowers/dh-packs/tao-build-arm64-v8a/。
#
# 依赖：MNN 源码树（含 apps/Android/MnnTaoAvatar）、NDK 27.x、cmake、ninja。
# 上游 app/build.gradle 写死 NDK 27.2.12479018，本机没有时改 NDK_DIR 指到已装的 27.1 即可
# （实测 27.1.12297006 构建通过）。
#
# 用法: scripts/dh-lab/build-tao-native.sh
set -euo pipefail

MNN_ROOT="${MNN_ROOT:-$HOME/work/android/MNN}"
NDK_DIR="${NDK_DIR:-$HOME/Library/Android/sdk/ndk/27.1.12297006}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$MNN_ROOT/apps/Android/MnnTaoAvatar/app/src/main"
BUILD="${TAO_BUILD_DIR:-/tmp/tao-build}"
OUT="$REPO_ROOT/.superpowers/dh-packs/tao-build-arm64-v8a"

[ -d "$SRC/cpp" ] || { echo "!! 找不到 $SRC/cpp（MNN_ROOT 对吗？）"; exit 1; }
[ -x "$NDK_DIR/build/cmake/android.toolchain.cmake" ] || { echo "!! NDK 不存在: $NDK_DIR"; exit 1; }
for zip in native-libs-arm64-v8a.zip; do
  [ -s "$REPO_ROOT/.superpowers/dh-packs/$zip" ] || { echo "!! 先跑 scripts/dh-lab/seed-assets.sh 里 fetch 的同名下载，或手动放 $zip"; exit 1; }
done

# 上游 CMakeLists 从 src/main/libs/{MNN,NNR}/lib/<abi>/ 找 IMPORTED 库；从官方 zip 解出来。
mkdir -p "$SRC/libs"
unzip -q -o "$REPO_ROOT/.superpowers/dh-packs/native-libs-arm64-v8a.zip" 'app/src/main/libs/*' -d "$SRC/libs-staging"
mkdir -p "$SRC/libs/MNN" "$SRC/libs/NNR"
rsync -a "$SRC/libs-staging/app/src/main/libs/" "$SRC/libs/"
rm -rf "$SRC/libs-staging"

# 官方 zip 里没有 sherpa 依赖头？——不需要：本模块只编 common/a2bs/tts/llm/jni，
# 不编 sherpa。上游 cpp/project/include（PROJECT_INCLUDE_DIR）缺失也无妨，没有源文件引用它。
rm -rf "$BUILD"
cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DANDROID_STL=c++_shared -DANDROID_TOOLCHAIN=clang \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" -j"$(sysctl -n hw.ncpu)"

mkdir -p "$OUT"
cp "$BUILD/libtaoavatar.so" "$BUILD/cpp/a2bs/libmnn_a2bs.so" "$BUILD/cpp/tts/libmnn_tts_SDK.so" "$OUT/"
cp "$NDK_DIR/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$OUT/"
echo "== 完成: $OUT"
ls -la "$OUT"
echo "下一步: scripts/dh-lab/seed-assets.sh tao"
