# 回归测试

这些脚本驱动真机做端到端验证，不依赖人工点击。需要：

- `adb`（默认读 `~/Library/Android/sdk/platform-tools/adb`，可用 `ADB` 环境变量覆盖）
- 已在设备上安装 debug 包并登录（节点列表需要有效账号）
- 设备已解锁、屏幕常亮

## vip2_suite.py — VIP2 全节点回归

逐个连接 VIP2 下的每个节点，判定：探针通过、节点列表页能显示节点、设备端访问
`google.com/generate_204` 返回 204 且路由走隧道、读得到隧道 MTU、断开干净、进程 pid 不变。
全部通过才算 PASS。

```bash
# 全部节点
python3 scripts/regression/vip2_suite.py

# 只跑指定节点
NODES=Test-IKEv2,Test-WireGuard python3 scripts/regression/vip2_suite.py

# 多设备时指定序列号
ANDROID_SERIAL=01edc291a38a python3 scripts/regression/vip2_suite.py
```

输出：`runs/vip2_<时间>/summary.csv`（每个节点一行，含 MTU 列）和每个节点的 logcat 原文。

### 注意事项

- **屏幕尺寸**：首页按钮坐标按 `wm size` 查表（`PROFILES`），新增机型要在脚本里补一组坐标。
  节点列表页的元素是按文字查找的，不需要坐标。
- **没有 curl 的设备**（如华为）：脚本自动改用 `app_process` 跑一个小 dex 发请求，
  同样以 shell 身份、走 VPN。dex 需先用 `tools/httpcheck/` 里的源码构建并 push 到
  `/data/local/tmp/httpcheck.dex`。
- **首页 `uiautomator dump` 可能拿不到快照**（地球是 WebView + 动画，永远不 idle）。
  脚本因此对首页只做坐标点击，选中节点通过读取 App 保存的 `LOCATION_CHOOSE` 校验。
- 每次 dump 前会先删掉设备上的旧 `/sdcard/ui.xml`：dump 失败时留下的旧文件会被误读成当前界面。
