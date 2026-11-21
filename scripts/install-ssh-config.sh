#!/usr/bin/env bash
# 将 scripts/ssh-config-snippet.txt 合并到 ~/.ssh/config（不覆盖已有文件）
set -euo pipefail
SNIP="$(cd "$(dirname "$0")" && pwd)/ssh-config-snippet.txt"
CFG="$HOME/.ssh/config"
mkdir -p "$HOME/.ssh/cm"
if [[ -f "$CFG" ]]; then
  if grep -q '^Host test\.io$' "$CFG" 2>/dev/null; then
    echo "~/.ssh/config 已包含 Host test.io，未修改。"
    exit 0
  fi
  echo "" >> "$CFG"
  echo "# --- added by install-ssh-config.sh ---" >> "$CFG"
  cat "$SNIP" >> "$CFG"
else
  cp "$SNIP" "$CFG"
  chmod 600 "$CFG"
fi
echo "已写入 $CFG"

