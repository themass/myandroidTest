#!/usr/bin/env bash
# 生成 Play 上传密钥（只运行一次）。
#
# 生成后务必把 app/release.jks 与 keystore.properties 备份到密码管理器。
# 两个文件都已加入 .gitignore，不会误提交。
# 密钥丢失 = 永远无法再更新这个应用，只能换包名重新上架。
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f app/release.jks ]; then
  echo "app/release.jks 已存在，拒绝覆盖。" >&2
  exit 1
fi

read -rsp "设置 keystore 密码: " PASS; echo
read -rsp "再输一次: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "两次输入不一致" >&2; exit 1; }

keytool -genkeypair -v \
  -keystore app/release.jks \
  -alias shieldwing \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=ShieldWing, OU=Mobile, O=ShieldWing, L=Hong Kong, C=HK"

cat > keystore.properties <<PROPS
storeFile=app/release.jks
storePassword=$PASS
keyAlias=shieldwing
keyPassword=$PASS
PROPS

echo
echo "已生成："
echo "  app/release.jks        （上传密钥，务必离线备份）"
echo "  keystore.properties    （已 gitignore）"
echo
echo "下一步：备份这两个文件，然后 ./gradlew :app:bundleFreeRelease"
