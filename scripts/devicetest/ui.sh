#!/bin/bash
# 真机 UI 测试小工具。用法: ./ui.sh <命令> [参数]
#   launch | to_editor | wait_text <文字> [秒] | tap_text <文字> | texts | shot <名字> | crashes | top
# 约定: 先 wait_text 再 tap_text，不要用固定 sleep 或猜坐标；崩溃用 crashes 的 fatal count 判断。
# 真机 UI 测试小工具：dump 控件、按文字点击、截图、查崩溃
export PATH=/Users/gqli/Library/Android/sdk/platform-tools:$PATH
OUT=${OUT:-/tmp/devicetest-ui}; mkdir -p "$OUT"
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $OUT/ui.xml >/dev/null 2>&1; }
texts() { dump; python3 - <<'PY'
import re
s=open('/tmp/devicetest-ui/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'(?:text|content-desc)="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',s):
    t,x1,y1,x2,y2=m.group(1),*map(int,m.groups()[1:])
    print(f"{t}\t{(x1+x2)//2},{(y1+y2)//2}")
PY
}
tap_text() { dump; local xy; xy=$(python3 - "$1" <<'PY'
import re,sys
want=sys.argv[1]
s=open('/tmp/devicetest-ui/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'(?:text|content-desc)="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',s):
    if want in m.group(1):
        x1,y1,x2,y2=map(int,m.groups()[1:]); print(f"{(x1+x2)//2} {(y1+y2)//2}"); break
PY
); if [ -z "$xy" ]; then sleep 1; dump; xy=$(python3 - "$1" <<'PY2'
import re,sys
want=sys.argv[1]
s=open('/tmp/devicetest-ui/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'(?:text|content-desc)="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',s):
    if want in m.group(1):
        x1,y1,x2,y2=map(int,m.groups()[1:]); print(f"{(x1+x2)//2} {(y1+y2)//2}"); break
PY2
); fi; if [ -z "$xy" ]; then echo "NOT FOUND: $1"; return 1; fi; adb shell input tap $xy; echo "tapped '$1' at $xy"; }
shot() { adb exec-out screencap -p > $OUT/$1.png; echo "$OUT/$1.png"; }
crashes() { adb logcat -d 2>/dev/null | grep -A8 "FATAL EXCEPTION" | grep -E "FATAL|Process: com.openapi|Exception|at com.openapi" | head -20; echo "fatal count: $(adb logcat -d | grep -c 'FATAL EXCEPTION')"; }
top() { adb shell dumpsys activity activities | grep mResumedActivity | head -1; }
launch() { adb shell am start -n com.openapi.ks.chat/com.openapi.ks.chatfree.ui.main.LaunchActivity >/dev/null 2>&1; }

# 等到界面出现某段文字（最多 $2 秒），出现返回 0
wait_text() { local want="$1" limit="${2:-20}" i=0; while [ $i -lt $limit ]; do dump; if grep -q "$want" $OUT/ui.xml 2>/dev/null; then return 0; fi; sleep 1; i=$((i+1)); done; echo "TIMEOUT waiting for: $want"; return 1; }
# 从任意状态进入编辑器：启动 → 等对话页 → 拍摄 → 拍照 → 确定 → 等编辑器
to_editor() {
  launch; wait_text "说点什么" 30 || return 1
  adb shell input tap 115 2486; wait_text "拍照" 15 || return 1
  adb shell input tap 627 2353; wait_text "确定" 10 || return 1
  adb shell input tap 1134 234; wait_text "编辑图片" 15 || return 1
  echo "in editor"
}
"$@"
